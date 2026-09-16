package fr.tropimon.battleui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Autonomous, read-only client for normal-battle suggestions from the Tropimon API.
 * Random Battle inference never enters this service.
 */
final class TropimonRankedUsageService {
    static final TropimonRankedUsageService INSTANCE = new TropimonRankedUsageService();

    private static final String API = "https://rankedapi.tropimon.fr/api";
    private static final int REQUEST_QUEUE_CAPACITY = 32;
    private static final int MAX_CACHED_REQUESTS = 256;
    private static final long RETRY_BASE_MILLIS = 5_000L;
    private static final long RETRY_MAX_MILLIS = 60_000L;
    private static final long SUCCESS_TTL_MILLIS = 15L * 60_000L;
    private static final Gson GSON = new Gson();
    private static final Pattern EV_COMPONENT = Pattern.compile(
            "(\\d{1,3})\\s+(HP|Atk|Def|SpA|SpD|Spe)", Pattern.CASE_INSENSITIVE);

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "Tropimon-UIBattle-Ranked");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, CompletableFuture<UsageIndex>> indexRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<RankedProfile>> profileRequests = new ConcurrentHashMap<>();
    private final Map<String, Long> indexSuccessExpiresAt = new ConcurrentHashMap<>();
    private final Map<String, Long> profileSuccessExpiresAt = new ConcurrentHashMap<>();
    private final Map<String, Long> indexRetryAfter = new ConcurrentHashMap<>();
    private final Map<String, Long> profileRetryAfter = new ConcurrentHashMap<>();
    private final Map<String, Integer> indexFailures = new ConcurrentHashMap<>();
    private final Map<String, Integer> profileFailures = new ConcurrentHashMap<>();

    private TropimonRankedUsageService() { }

    /** Returns true only when a completed matching profile was applied. */
    boolean enrichOpponent(PokemonSet pokemon, String requestedFormat) {
        if (pokemon == null || pokemon.species == null) return false;
        String format = normalizeFormat(requestedFormat);
        CompletableFuture<UsageIndex> indexFuture = request(indexRequests, indexSuccessExpiresAt,
                indexRetryAfter, indexFailures, format,
                () -> loadIndex(format), (index, error) -> {
                    if (error != null) TropimonUIBattleClient.LOGGER.warn(
                            "Tropimon usage suggestions are unavailable for {}", format, rootCause(error));
                    else TropimonUIBattleClient.LOGGER.info(
                            "Tropimon usage suggestions ready: format={}, season={}, species={}",
                            format, index.season(), index.apiNamesByKey().size());
                });
        UsageIndex index = completedValue(indexFuture);
        if (index == null) return false;
        String apiName = index.apiNameFor(pokemon.species);
        if (apiName == null) return false;
        String requestKey = format + '\u0000' + apiName;
        CompletableFuture<RankedProfile> request = request(profileRequests, profileSuccessExpiresAt,
                profileRetryAfter, profileFailures,
                requestKey, () -> loadProfile(index.season(), format, apiName), (profile, error) -> {
                    if (error != null) TropimonUIBattleClient.LOGGER.debug(
                            "Tropimon usage profile unavailable for {} in {}", apiName, format, rootCause(error));
                });
        RankedProfile profile = completedValue(request);
        if (profile == null || !applyProfile(pokemon, profile)) return false;
        pokemon.rankedProfileKey = format + ":" + profile.speciesKey();
        return true;
    }

    private UsageIndex loadIndex(String format) {
        SeasonResponse[] seasons = get("/seasons", SeasonResponse[].class);
        SeasonResponse selected = Arrays.stream(seasons == null ? new SeasonResponse[0] : seasons)
                .filter(season -> season != null && season.name != null && !season.name.isBlank())
                .min(Comparator.comparing((SeasonResponse season) -> !season.active))
                .orElseThrow(() -> new RankedUsageException("No Tropimon season"));
        UsageEntryResponse[] entries = get("/species-list?season=" + encode(selected.name)
                + "&format=" + format + "&tier=all", UsageEntryResponse[].class);
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (UsageEntryResponse entry : entries == null ? new UsageEntryResponse[0] : entries) {
            if (entry != null && entry.name != null && !entry.name.isBlank())
                names.putIfAbsent(key(entry.name), entry.name);
        }
        return new UsageIndex(selected.name, Map.copyOf(names));
    }

    private RankedProfile loadProfile(String season, String format, String apiName) {
        return profileFromJson(key(apiName), getBody("/species?season=" + encode(season)
                + "&format=" + format + "&tier=all&name=" + encode(apiName)));
    }

    static RankedProfile profileFromJson(String speciesKey, String json) {
        try {
            SpeciesStatsResponse response = GSON.fromJson(json, SpeciesStatsResponse.class);
            if (response == null) throw new RankedUsageException("Empty Tropimon profile");
            return new RankedProfile(key(speciesKey), mostUsed(response.items), mostUsed(response.abilities),
                    mostUsed(response.natures), mostUsedEvSpread(response.spreads));
        } catch (JsonSyntaxException exception) {
            throw new RankedUsageException("Invalid Tropimon profile", exception);
        }
    }

    static boolean applyProfile(PokemonSet pokemon, RankedProfile profile) {
        if (pokemon == null || profile == null || profile.speciesKey().isBlank()) return false;
        if (!pokemon.itemKnown) {
            String item = BattleCalcDex.findItemByQuery(profile.item());
            if (item != null || !profile.item().isBlank()) {
                pokemon.item = item == null ? profile.item() : item;
                pokemon.rankedItemSuggested = true;
            }
        }
        if (!pokemon.abilityKnown) {
            String ability = BattleCalcDex.findAbilityByQuery(pokemon.species, profile.ability());
            if (ability == null) ability = BattleCalcDex.findAbilityByQuery(profile.ability());
            if (ability != null || !profile.ability().isBlank()) {
                pokemon.ability = ability == null ? profile.ability() : ability;
                pokemon.rankedAbilitySuggested = true;
            }
        }
        if (!pokemon.natureKnown) {
            NatureData nature = BattleCalcDex.findNatureByQuery(profile.nature());
            if (nature != null) {
                pokemon.nature = nature;
                pokemon.rankedNatureSuggested = true;
            }
        }
        if (!pokemon.statsKnown && profile.evSpread() != null && allEvsZero(pokemon)) {
            profile.evSpread().applyTo(pokemon);
            pokemon.rankedEvsSuggested = true;
        }
        pokemon.rankedProfileKey = profile.speciesKey();
        return true;
    }

    private static boolean allEvsZero(PokemonSet pokemon) {
        for (int value : pokemon.evs.values()) if (value != 0) return false;
        return true;
    }

    private static RankedEvSpread mostUsedEvSpread(Map<String, Double> spreads) {
        for (Map.Entry<String, Double> entry : rankedValues(spreads)) {
            if (!Double.isFinite(entry.getValue()) || entry.getValue() <= 0 || entry.getValue() > 100) continue;
            EnumMap<Stat, Integer> values = new EnumMap<>(Stat.class);
            int total = 0;
            boolean valid = true;
            for (String component : entry.getKey().split("/", -1)) {
                var match = EV_COMPONENT.matcher(component.trim());
                if (!match.matches()) { valid = false; break; }
                Stat stat = Stat.valueOf(match.group(2).toUpperCase(Locale.ROOT));
                int value = Integer.parseInt(match.group(1));
                if (value > 252 || values.putIfAbsent(stat, value) != null) { valid = false; break; }
                total += value;
            }
            if (valid && total <= 510 && !values.isEmpty()) return new RankedEvSpread(
                    values.getOrDefault(Stat.HP, 0), values.getOrDefault(Stat.ATK, 0),
                    values.getOrDefault(Stat.DEF, 0), values.getOrDefault(Stat.SPA, 0),
                    values.getOrDefault(Stat.SPD, 0), values.getOrDefault(Stat.SPE, 0), entry.getValue());
        }
        return null;
    }

    private <T> T get(String path, Class<T> type) {
        try { return GSON.fromJson(getBody(path), type); }
        catch (JsonSyntaxException exception) { throw new RankedUsageException("Invalid Tropimon response", exception); }
    }

    private String getBody(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json").header("User-Agent", "TropimonUIBattle").GET().build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new RankedUsageException("Tropimon API returned HTTP " + response.statusCode());
            return response.body();
        } catch (IOException exception) {
            throw new RankedUsageException("Unable to reach Tropimon API", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RankedUsageException("Tropimon API request interrupted", exception);
        }
    }

    private static List<Map.Entry<String, Double>> rankedValues(Map<String, Double> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static String mostUsed(Map<String, Double> values) {
        return rankedValues(values).stream().findFirst().map(Map.Entry::getKey).orElse("");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String key(String value) { return BattleCalcDex.normalize(value); }
    static String formatForPokemonPerSide(int pokemonPerSide) {
        // The API currently exposes SINGLES and DOUBLES. Its doubles profile is the
        // closest supported population for triples and remains cached separately.
        return pokemonPerSide <= 1 ? "SINGLES" : "DOUBLES";
    }
    private static String normalizeFormat(String format) {
        return "DOUBLES".equalsIgnoreCase(format) ? "DOUBLES" : "SINGLES";
    }
    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
        return current;
    }

    private <T> CompletableFuture<T> request(Map<String, CompletableFuture<T>> requests,
                                             Map<String, Long> successExpiresAt,
                                             Map<String, Long> retryAfter,
                                             Map<String, Integer> failures,
                                             String key, Supplier<T> supplier,
                                             BiConsumer<T, Throwable> completion) {
        long now = System.currentTimeMillis();
        Long blockedUntil = retryAfter.get(key);
        if (blockedUntil != null && blockedUntil > now) return null;
        if (blockedUntil != null) retryAfter.remove(key, blockedUntil);

        CompletableFuture<T> existing = requests.get(key);
        while (existing != null) {
            long successExpiry = successExpiresAt.getOrDefault(key, 0L);
            if (!existing.isDone() || (successExpiry == 0L && !existing.isCompletedExceptionally()
                    && !existing.isCancelled()) || successFresh(successExpiry, now)) return existing;
            if (requests.remove(key, existing)) {
                successExpiresAt.remove(key);
                break;
            }
            existing = requests.get(key);
        }
        if (requests.size() >= MAX_CACHED_REQUESTS) evictOneCompleted(requests, successExpiresAt);
        CompletableFuture<T> created = new CompletableFuture<>();
        CompletableFuture<T> raced = requests.putIfAbsent(key, created);
        if (raced != null) return raced;

        created.whenComplete((value, error) -> {
            try {
                completion.accept(value, error);
            } finally {
                if (error == null && !created.isCancelled()) {
                    failures.remove(key);
                    retryAfter.remove(key);
                    successExpiresAt.put(key, System.currentTimeMillis() + SUCCESS_TTL_MILLIS);
                } else {
                    successExpiresAt.remove(key);
                    int failure = failures.merge(key, 1, Integer::sum);
                    retryAfter.put(key, System.currentTimeMillis() + retryDelayMillis(failure));
                    requests.remove(key, created);
                }
            }
        });
        try {
            executor.execute(() -> {
                try { created.complete(supplier.get()); }
                catch (Throwable error) { created.completeExceptionally(error); }
            });
        } catch (RejectedExecutionException error) {
            created.completeExceptionally(error);
        }
        return created;
    }

    static long retryDelayMillis(int failureCount) {
        int exponent = Math.max(0, Math.min(4, failureCount - 1));
        return Math.min(RETRY_MAX_MILLIS, RETRY_BASE_MILLIS << exponent);
    }
    static boolean successFresh(long expiresAt, long now) { return expiresAt > now; }
    private static <T> void evictOneCompleted(Map<String, CompletableFuture<T>> requests,
                                              Map<String, Long> successExpiresAt) {
        String oldestKey = null;
        long oldestExpiry = Long.MAX_VALUE;
        for (var entry : successExpiresAt.entrySet()) {
            CompletableFuture<T> future = requests.get(entry.getKey());
            if (future != null && future.isDone() && entry.getValue() < oldestExpiry) {
                oldestKey = entry.getKey();
                oldestExpiry = entry.getValue();
            }
        }
        if (oldestKey != null) {
            CompletableFuture<T> future = requests.get(oldestKey);
            if (future != null && future.isDone()) requests.remove(oldestKey, future);
            successExpiresAt.remove(oldestKey);
        }
    }
    private static <T> T completedValue(CompletableFuture<T> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) return null;
        try { return future.getNow(null); }
        catch (CompletionException ignored) { return null; }
    }

    record RankedEvSpread(int hp, int atk, int def, int spa, int spd, int spe, double usage) {
        void applyTo(PokemonSet pokemon) {
            pokemon.evs.put(Stat.HP, hp); pokemon.evs.put(Stat.ATK, atk); pokemon.evs.put(Stat.DEF, def);
            pokemon.evs.put(Stat.SPA, spa); pokemon.evs.put(Stat.SPD, spd); pokemon.evs.put(Stat.SPE, spe);
        }
    }
    record RankedProfile(String speciesKey, String item, String ability, String nature, RankedEvSpread evSpread) {
        RankedProfile {
            speciesKey = key(speciesKey);
            item = item == null ? "" : item;
            ability = ability == null ? "" : ability;
            nature = nature == null ? "" : nature;
        }
    }
    private record UsageIndex(String season, Map<String, String> apiNamesByKey) {
        String apiNameFor(SpeciesData species) {
            for (String candidate : List.of(species.id(), species.name(), species.cobblemonSpeciesId())) {
                String match = apiNamesByKey.get(key(candidate));
                if (match != null) return match;
            }
            return null;
        }
    }
    private static final class SeasonResponse { String name; boolean active; }
    private static final class UsageEntryResponse { String name; }
    private static final class SpeciesStatsResponse {
        Map<String, Double> abilities = new LinkedHashMap<>();
        Map<String, Double> items = new LinkedHashMap<>();
        Map<String, Double> natures = new LinkedHashMap<>();
        Map<String, Double> spreads = new LinkedHashMap<>();
    }
    private static final class RankedUsageException extends RuntimeException {
        RankedUsageException(String message) { super(message); }
        RankedUsageException(String message, Throwable cause) { super(message, cause); }
    }
}
