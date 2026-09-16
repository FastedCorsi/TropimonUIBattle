package fr.tropimon.battleui;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;

final class BattleCalcDex {
    private static final LinkedHashMap<String, NatureData> NATURES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, NatureData> NATURES_BY_NAME = new LinkedHashMap<>();
    private static List<NatureData> sortedNatures = List.of();
    private static volatile DexData data = DexData.empty();
    private static boolean naturesLoaded;
    private static boolean cobblemonLoading;
    private static int cobblemonLoadFailures;
    private static long nextCobblemonLoadAttempt;
    private static long loadedResourceEpoch = -1L;
    private static long loadGeneration;

    private BattleCalcDex() {
    }

    static void load() {
        loadNatures();
    }

    static SpeciesData species(String id) {
        load();
        DexData dex = data;
        SpeciesData species = dex.species().get(normalize(id));
        if (species != null) {
            return species;
        }
        return dex.species().values().stream().findFirst().orElseGet(() -> placeholderSpecies(id));
    }

    static MoveData move(String id) {
        load();
        DexData dex = data;
        MoveData move = dex.moves().get(normalize(id));
        if (move != null) {
            return move;
        }
        return dex.moves().values().stream().findFirst().orElseGet(() -> placeholderMove(id));
    }

    static NatureData nature(String id) {
        loadNatures();
        return NATURES.getOrDefault(normalize(id), NATURES.get("serious"));
    }

    static List<SpeciesData> speciesList() {
        load();
        return data.sortedSpecies();
    }

    static List<MoveData> moveListFor(SpeciesData species) {
        load();
        DexData dex = data;
        ArrayList<MoveData> output = new ArrayList<>(dex.sortedMoves().size());
        Set<String> legal = Set.copyOf(dex.legalMoveIds().getOrDefault(species.id(), List.of()));
        for (MoveData move : dex.sortedMoves()) {
            if (legal.contains(move.id())) output.add(move);
        }
        for (MoveData move : dex.sortedMoves()) {
            if (!legal.contains(move.id())) output.add(move);
        }
        return output;
    }

    static List<NatureData> natureList() {
        loadNatures();
        return sortedNatures;
    }

    static List<String> itemList() {
        load();
        return data.items();
    }

    static List<String> abilityList() {
        load();
        return data.abilities();
    }

    static String diagnosticSummary() {
        load();
        DexData dex = data;
        long flaggedMoves = dex.sortedMoves().stream().filter(move -> !move.flags().isEmpty()).count();
        long forms = dex.sortedSpecies().stream()
                .filter(species -> !normalize(species.id()).equals(normalize(species.cobblemonSpeciesId())))
                .count();
        return "species=" + dex.sortedSpecies().size() + ", forms=" + forms + ", moves=" + dex.sortedMoves().size()
                + ", items=" + dex.items().size() + ", abilities=" + dex.abilities().size()
                + ", natures=" + sortedNatures.size() + ", flaggedMoves=" + flaggedMoves + "/" + dex.sortedMoves().size();
    }

    static List<String> abilityList(SpeciesData species) {
        load();
        DexData dex = data;
        List<String> speciesAbilities = dex.legalAbilities().get(species.id());
        if (speciesAbilities == null || speciesAbilities.isEmpty()) {
            speciesAbilities = dex.legalAbilities().get(species.cobblemonSpeciesId());
        }
        return speciesAbilities == null || speciesAbilities.isEmpty() ? List.of("None") : speciesAbilities;
    }

    static boolean isHiddenAbility(SpeciesData species, String ability) {
        load();
        if (species == null || ability == null) {
            return false;
        }
        String normalized = normalize(ability);
        for (String hidden : data.hiddenAbilities().getOrDefault(species.id(), List.of())) {
            if (normalize(hidden).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    static String defaultAbility(SpeciesData species) {
        List<String> speciesAbilities = abilityList(species);
        if (speciesAbilities.isEmpty()) {
            return "None";
        }
        for (String ability : speciesAbilities) {
            if (!isHiddenAbility(species, ability)) {
                return ability;
            }
        }
        return speciesAbilities.getFirst();
    }

    static String megaStoneForSpecies(SpeciesData species) {
        load();
        if (species == null || !normalize(species.name()).startsWith("mega")) {
            return null;
        }
        String normalizedName = normalize(species.name());
        String wanted = switch (normalizedName) {
            case "megaabomasnow" -> "Abomasite";
            case "megaabsol" -> "Absolite";
            case "megaaerodactyl" -> "Aerodactylite";
            case "megaaggron" -> "Aggronite";
            case "megaalakazam" -> "Alakazite";
            case "megaaltaria" -> "Altarianite";
            case "megaampharos" -> "Ampharosite";
            case "megaaudino" -> "Audinite";
            case "megabanette" -> "Banettite";
            case "megabeedrill" -> "Beedrillite";
            case "megablastoise" -> "Blastoisinite";
            case "megablaziken" -> "Blazikenite";
            case "megacamerupt" -> "Cameruptite";
            case "megacharizardx" -> "Charizardite X";
            case "megacharizardy" -> "Charizardite Y";
            case "megadiancie" -> "Diancite";
            case "megagallade" -> "Galladite";
            case "megagarchomp" -> "Garchompite";
            case "megagardevoir" -> "Gardevoirite";
            case "megagengar" -> "Gengarite";
            case "megaglalie" -> "Glalitite";
            case "megagyarados" -> "Gyaradosite";
            case "megaheracross" -> "Heracronite";
            case "megahoundoom" -> "Houndoominite";
            case "megakangaskhan" -> "Kangaskhanite";
            case "megalatias" -> "Latiasite";
            case "megalatios" -> "Latiosite";
            case "megalopunny" -> "Lopunnite";
            case "megalucario" -> "Lucarionite";
            case "megamanectric" -> "Manectite";
            case "megamawile" -> "Mawilite";
            case "megamedicham" -> "Medichamite";
            case "megametagross" -> "Metagrossite";
            case "megamewtwox" -> "Mewtwonite X";
            case "megamewtwoy" -> "Mewtwonite Y";
            case "megapidgeot" -> "Pidgeotite";
            case "megapinsir" -> "Pinsirite";
            case "megasableye" -> "Sablenite";
            case "megasalamence" -> "Salamencite";
            case "megascizor" -> "Scizorite";
            case "megasceptile" -> "Sceptilite";
            case "megasharpedo" -> "Sharpedonite";
            case "megaslowbro" -> "Slowbronite";
            case "megasteelix" -> "Steelixite";
            case "megaswampert" -> "Swampertite";
            case "megatyranitar" -> "Tyranitarite";
            case "megavenusaur" -> "Venusaurite";
            default -> null;
        };
        return wanted == null ? null : findItemByQuery(wanted);
    }

    static List<PokeType> teraTypes() {
        return List.of(PokeType.NONE, PokeType.NORMAL, PokeType.FIRE, PokeType.WATER, PokeType.ELECTRIC, PokeType.GRASS,
                PokeType.ICE, PokeType.FIGHTING, PokeType.POISON, PokeType.GROUND, PokeType.FLYING, PokeType.PSYCHIC,
                PokeType.BUG, PokeType.ROCK, PokeType.GHOST, PokeType.DRAGON, PokeType.DARK, PokeType.STEEL, PokeType.FAIRY);
    }

    static SpeciesData findSpeciesByDisplayName(String displayName) {
        load();
        DexData dex = data;
        String normalized = normalize(displayName);
        SpeciesData exact = dex.species().get(normalized);
        if (exact == null) {
            exact = dex.speciesByName().get(normalized);
        }
        if (exact != null) {
            return exact;
        }
        SpeciesData best = null;
        int bestLength = -1;
        for (Map.Entry<String, SpeciesData> entry : dex.species().entrySet()) {
            String name = normalize(entry.getValue().name());
            if (normalized.contains(entry.getKey()) || normalized.contains(name)) {
                int matchLength = Math.max(entry.getKey().length(), name.length());
                if (matchLength > bestLength) {
                    best = entry.getValue();
                    bestLength = matchLength;
                }
            }
        }
        return best;
    }

    static SpeciesData findSpeciesByQuery(String query) {
        load();
        DexData dex = data;
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        SpeciesData exact = dex.species().get(normalized);
        if (exact == null) {
            exact = dex.speciesByName().get(normalized);
        }
        if (exact != null) {
            return exact;
        }
        for (SpeciesData species : dex.sortedSpecies()) {
            String name = normalize(species.name());
            if (name.startsWith(normalized) || species.id().startsWith(normalized)) {
                return species;
            }
        }
        for (SpeciesData species : dex.sortedSpecies()) {
            String name = normalize(species.name());
            if (name.contains(normalized) || species.id().contains(normalized)) {
                return species;
            }
        }
        return null;
    }

    static SpeciesData findFormSpecies(String baseSpeciesId, String formName, String formShowdownId, List<String> aspects) {
        load();
        return selectFormSpecies(data.species().values(), baseSpeciesId, formName, formShowdownId, aspects);
    }

    static SpeciesData selectFormSpecies(Iterable<SpeciesData> candidates, String baseSpeciesId,
                                         String formName, String formShowdownId, List<String> aspects) {
        String base = normalize(baseSpeciesId);
        String form = normalizeFormName(formName);
        String showdown = normalize(formShowdownId);
        ArrayList<String> normalizedAspects = new ArrayList<>();
        for (String aspect : aspects == null ? List.<String>of() : aspects) {
            String normalized = normalizeFormAspect(aspect);
            if (!normalized.isBlank()) {
                normalizedAspects.add(normalized);
            }
        }
        String formAspect = normalizeFormAspect(formName);
        if (!formAspect.isBlank() && !normalizedAspects.contains(formAspect)) {
            normalizedAspects.add(formAspect);
        }
        SpeciesData bestNameMatch = null;
        SpeciesData bestAspectMatch = null;
        SpeciesData exactShowdown = null;
        int bestAspectScore = 0;
        boolean bestAspectMatchesName = false;
        for (SpeciesData species : candidates) {
            if (!base.isBlank() && !normalize(species.cobblemonSpeciesId()).equals(base) && !species.id().startsWith(base)) {
                continue;
            }
            if (!showdown.isBlank() && species.id().equals(showdown)) {
                exactShowdown = species;
                if (isSpecificForm(species, base)) {
                    return species;
                }
            }
            String speciesName = normalizeFormName(species.name());
            boolean matchesFormName = !form.isBlank()
                    && (species.id().contains(form) || speciesName.contains(form));
            if (!normalizedAspects.isEmpty()) {
                int aspectScore = matchingAspectCount(species, normalizedAspects);
                if (aspectScore > bestAspectScore
                        || aspectScore == bestAspectScore && aspectScore > 0
                        && (matchesFormName && !bestAspectMatchesName
                        || matchesFormName == bestAspectMatchesName && bestAspectMatch != null
                        && species.id().compareTo(bestAspectMatch.id()) < 0)) {
                    bestAspectMatch = species;
                    bestAspectScore = aspectScore;
                    bestAspectMatchesName = matchesFormName;
                }
            }
            if (matchesFormName
                    && (bestNameMatch == null || species.id().compareTo(bestNameMatch.id()) < 0)) {
                bestNameMatch = species;
            }
        }
        if (bestAspectMatch != null) {
            return bestAspectMatch;
        }
        if (bestNameMatch != null) {
            return bestNameMatch;
        }
        return exactShowdown;
    }

    private static boolean isSpecificForm(SpeciesData species, String base) {
        String speciesId = normalize(species.id());
        String cobblemonBase = normalize(species.cobblemonSpeciesId());
        return !speciesId.equals(cobblemonBase);
    }

    private static int matchingAspectCount(SpeciesData species, List<String> normalizedAspects) {
        if (species.aspects().isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (String speciesAspect : species.aspects()) {
            String normalizedSpeciesAspect = normalizeFormAspect(speciesAspect);
            if (normalizedSpeciesAspect.isBlank()) continue;
            if (!normalizedAspects.contains(normalizedSpeciesAspect)) return 0;
            matches++;
        }
        return matches;
    }

    private static String normalizeFormAspect(String aspect) {
        return switch (normalizeFormName(aspect)) {
            case "alola", "alolan" -> "alola";
            case "galar", "galarian" -> "galar";
            case "hisui", "hisuian" -> "hisui";
            case "paldea", "paldean" -> "paldea";
            default -> normalizeFormName(aspect);
        };
    }

    static String normalizeFormName(String value) {
        return normalize(value == null ? "" : value.replace("!", "exclamation").replace("?", "question"));
    }

    static MoveData findMoveByQuery(String query) {
        load();
        DexData dex = data;
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        MoveData exact = dex.moves().get(normalized);
        if (exact == null) {
            exact = dex.movesByName().get(normalized);
        }
        if (exact != null) {
            return exact;
        }
        for (MoveData move : dex.sortedMoves()) {
            String name = normalize(move.name());
            if (name.startsWith(normalized) || move.id().startsWith(normalized)) {
                return move;
            }
        }
        for (MoveData move : dex.sortedMoves()) {
            String name = normalize(move.name());
            if (name.contains(normalized) || move.id().contains(normalized)) {
                return move;
            }
        }
        return null;
    }

    static String findItemByQuery(String query) {
        load();
        DexData dex = data;
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        String exact = dex.itemsByName().get(normalized);
        if (exact != null) {
            return exact;
        }
        for (String item : dex.items()) {
            if (normalize(item).startsWith(normalized)) {
                return item;
            }
        }
        for (String item : dex.items()) {
            if (normalize(item).contains(normalized)) {
                return item;
            }
        }
        return null;
    }

    static NatureData findNatureByQuery(String query) {
        loadNatures();
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        NatureData exact = NATURES.get(normalized);
        if (exact == null) {
            exact = NATURES_BY_NAME.get(normalized);
        }
        if (exact != null) {
            return exact;
        }
        for (NatureData nature : sortedNatures) {
            if (normalize(nature.name()).startsWith(normalized)) {
                return nature;
            }
        }
        for (NatureData nature : sortedNatures) {
            if (normalize(nature.name()).contains(normalized)) {
                return nature;
            }
        }
        return null;
    }

    static String findAbilityByQuery(SpeciesData species, String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        List<String> candidates = abilityList(species);
        for (String ability : candidates) {
            if (normalize(ability).equals(normalized)) {
                return ability;
            }
        }
        for (String ability : candidates) {
            if (normalize(ability).startsWith(normalized)) {
                return ability;
            }
        }
        for (String ability : candidates) {
            if (normalize(ability).contains(normalized)) {
                return ability;
            }
        }
        return null;
    }

    static String findAbilityByQuery(String query) {
        load();
        List<String> knownAbilities = data.abilities();
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return null;
        }
        for (String ability : knownAbilities) {
            if (normalize(ability).equals(normalized)) {
                return ability;
            }
        }
        for (String ability : knownAbilities) {
            if (normalize(ability).startsWith(normalized)) {
                return ability;
            }
        }
        return null;
    }

    static List<MoveData> defaultMoves(SpeciesData species) {
        load();
        DexData dex = data;
        ArrayList<MoveData> selected = new ArrayList<>();
        List<String> legalMoveIds = dex.legalMoveIds().getOrDefault(species.id(), List.of());
        for (String moveId : legalMoveIds) {
            MoveData move = dex.moves().get(moveId);
            if (move != null && move.category() != DamageCategory.STATUS) {
                selected.add(move);
            }
        }
        selected.sort(Comparator
                .comparing((MoveData move) -> species.types().contains(move.type())).reversed()
                .thenComparing(MoveData::basePower, Comparator.reverseOrder()));
        if (selected.size() >= 4) {
            return new ArrayList<>(selected.subList(0, 4));
        }
        for (String moveId : legalMoveIds) {
            if (selected.size() >= 4) {
                break;
            }
            MoveData move = dex.moves().get(moveId);
            if (move != null && !selected.contains(move)) {
                selected.add(move);
            }
        }
        if (selected.isEmpty() && !dex.loaded()) {
            selected.add(placeholderMove("loading"));
        }
        return selected;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    /**
     * Loads the live Cobblemon database before any battle tooltip can request it.
     * ResourceManager access deliberately stays on the client thread. The fully
     * immutable snapshot is published through one volatile write.
     */
    static void preloadOnClientTick(MinecraftClient client) {
        if (client == null || !client.isOnThread()) return;
        long resourceEpoch = UiResourceEpoch.current();
        long generation;
        synchronized (BattleCalcDex.class) {
            if (loadedResourceEpoch != resourceEpoch) {
                data = DexData.empty();
                loadedResourceEpoch = resourceEpoch;
                nextCobblemonLoadAttempt = 0L;
                cobblemonLoadFailures = 0;
                loadGeneration++;
            }
            long now = System.currentTimeMillis();
            if (data.loaded() || cobblemonLoading || now < nextCobblemonLoadAttempt) return;
            cobblemonLoading = true;
            generation = loadGeneration;
        }

        CobblemonDexSnapshot snapshot = CobblemonDexDataProvider.loadSnapshot();
        DexData replacement = snapshot.loaded() && !snapshot.species().isEmpty() && !snapshot.moves().isEmpty()
                ? DexData.from(snapshot) : null;
        boolean published = false;
        synchronized (BattleCalcDex.class) {
            cobblemonLoading = false;
            if (generation != loadGeneration || resourceEpoch != UiResourceEpoch.current()) return;
            if (replacement != null) {
                data = replacement;
                cobblemonLoadFailures = 0;
                nextCobblemonLoadAttempt = Long.MAX_VALUE;
                published = true;
            } else {
                cobblemonLoadFailures++;
                nextCobblemonLoadAttempt = System.currentTimeMillis()
                        + cobblemonRetryDelayMillis(cobblemonLoadFailures);
            }
        }
        if (published) TropimonDamageCalcBridge.reset();
    }

    static synchronized void invalidateCobblemonData() {
        // The bridge also calls this when it first observes the current resource
        // epoch. Do not discard a snapshot already preloaded for that exact epoch.
        if (data.loaded() && loadedResourceEpoch == UiResourceEpoch.current()) return;
        data = DexData.empty();
        loadedResourceEpoch = -1L;
        nextCobblemonLoadAttempt = 0L;
        cobblemonLoadFailures = 0;
        loadGeneration++;
    }

    static long cobblemonRetryDelayMillis(int failureCount) {
        int shift = Math.clamp(failureCount - 1, 0, 5);
        return Math.min(60_000L, 2_000L << shift);
    }

    private static List<String> sortedCopy(List<String> source) {
        ArrayList<String> sorted = new ArrayList<>(source);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, List<String>> immutableLists(Map<String, List<String>> source, boolean sort) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((id, values) -> copy.put(id, sort ? sortedCopy(values) : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    private record DexData(boolean loaded, Map<String, SpeciesData> species, Map<String, MoveData> moves,
                           Map<String, List<String>> legalMoveIds, Map<String, List<String>> legalAbilities,
                           Map<String, List<String>> hiddenAbilities, Map<String, SpeciesData> speciesByName,
                           Map<String, MoveData> movesByName, Map<String, String> itemsByName,
                           List<SpeciesData> sortedSpecies, List<MoveData> sortedMoves,
                           List<String> items, List<String> abilities) {
        static DexData empty() {
            return new DexData(false, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), List.of(), List.of(), List.of("None"), List.of("None"));
        }

        static DexData from(CobblemonDexSnapshot snapshot) {
            Map<String, SpeciesData> species = immutableMap(snapshot.species());
            Map<String, MoveData> moves = immutableMap(snapshot.moves());
            List<String> abilities = snapshot.abilities().isEmpty() ? List.of("None") : sortedCopy(snapshot.abilities());
            List<String> items = snapshot.items().isEmpty() ? List.of("None") : sortedCopy(snapshot.items());
            LinkedHashMap<String, SpeciesData> speciesByName = new LinkedHashMap<>();
            for (SpeciesData value : species.values()) speciesByName.putIfAbsent(normalize(value.name()), value);
            LinkedHashMap<String, MoveData> movesByName = new LinkedHashMap<>();
            for (MoveData value : moves.values()) movesByName.putIfAbsent(normalize(value.name()), value);
            LinkedHashMap<String, String> itemsByName = new LinkedHashMap<>();
            for (String value : items) itemsByName.putIfAbsent(normalize(value), value);
            List<SpeciesData> sortedSpecies = species.values().stream()
                    .sorted(Comparator.comparing(SpeciesData::name, String.CASE_INSENSITIVE_ORDER)).toList();
            List<MoveData> sortedMoves = moves.values().stream()
                    .sorted(Comparator.comparing(MoveData::name, String.CASE_INSENSITIVE_ORDER)).toList();
            return new DexData(true, species, moves, immutableLists(snapshot.legalMoveIds(), false),
                    immutableLists(snapshot.legalAbilities(), true), immutableLists(snapshot.hiddenAbilities(), true),
                    immutableMap(speciesByName), immutableMap(movesByName), immutableMap(itemsByName),
                    sortedSpecies, sortedMoves, items, abilities);
        }
    }

    private static SpeciesData placeholderSpecies(String id) {
        EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            stats.put(stat, 1);
        }
        String name = id == null || id.isBlank() ? "Chargement Cobblemon" : id;
        return new SpeciesData(normalize(name), name, PokeType.NORMAL, PokeType.NONE, stats, false, "");
    }

    private static MoveData placeholderMove(String id) {
        String name = id == null || id.isBlank() ? "Chargement" : id;
        return new MoveData(normalize(name), name, PokeType.NORMAL, DamageCategory.STATUS, 0, false, false);
    }

    private static void loadNatures() {
        if (naturesLoaded) {
            return;
        }
        naturesLoaded = true;
        nature("hardy", "Hardy", null, null);
        nature("lonely", "Lonely", Stat.ATK, Stat.DEF);
        nature("brave", "Brave", Stat.ATK, Stat.SPE);
        nature("adamant", "Adamant", Stat.ATK, Stat.SPA);
        nature("naughty", "Naughty", Stat.ATK, Stat.SPD);
        nature("bold", "Bold", Stat.DEF, Stat.ATK);
        nature("relaxed", "Relaxed", Stat.DEF, Stat.SPE);
        nature("impish", "Impish", Stat.DEF, Stat.SPA);
        nature("lax", "Lax", Stat.DEF, Stat.SPD);
        nature("timid", "Timid", Stat.SPE, Stat.ATK);
        nature("hasty", "Hasty", Stat.SPE, Stat.DEF);
        nature("jolly", "Jolly", Stat.SPE, Stat.SPA);
        nature("naive", "Naive", Stat.SPE, Stat.SPD);
        nature("modest", "Modest", Stat.SPA, Stat.ATK);
        nature("mild", "Mild", Stat.SPA, Stat.DEF);
        nature("quiet", "Quiet", Stat.SPA, Stat.SPE);
        nature("rash", "Rash", Stat.SPA, Stat.SPD);
        nature("calm", "Calm", Stat.SPD, Stat.ATK);
        nature("gentle", "Gentle", Stat.SPD, Stat.DEF);
        nature("sassy", "Sassy", Stat.SPD, Stat.SPE);
        nature("careful", "Careful", Stat.SPD, Stat.SPA);
        nature("serious", "Serious", null, null);
        NATURES_BY_NAME.clear();
        for (NatureData nature : NATURES.values()) {
            NATURES_BY_NAME.putIfAbsent(normalize(nature.name()), nature);
        }
        sortedNatures = NATURES.values().stream()
                .sorted(Comparator.comparing(NatureData::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static void nature(String id, String name, Stat plus, Stat minus) {
        NATURES.put(id, new NatureData(id, name, plus, minus));
    }
}
