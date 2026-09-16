package fr.tropimon.battleui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Own bundled Random Battle data, optionally overridden only by UI Battle's own configuration. */
final class BattleRandomSets {
    private static final String BUNDLED_RESOURCE =
            "/assets/tropimon_ui_battle/data/tropimon-random-battle-sets.json";
    private static final Map<String, List<RandomBattleSet>> SETS = new LinkedHashMap<>();
    private static final int INFERENCE_CACHE_LIMIT = 512;
    private static final Map<InferenceSignature, List<RandomBattleSet>> INFERENCE_CACHE =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<InferenceSignature, List<RandomBattleSet>> eldest) {
                    return size() > INFERENCE_CACHE_LIMIT;
                }
            };
    private static boolean loaded;

    private BattleRandomSets() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        SETS.clear();
        INFERENCE_CACHE.clear();
        for (Path candidate : localCandidates()) {
            try (Reader reader = Files.newBufferedReader(candidate)) {
                Map<String, List<RandomBattleSet>> parsed = parseDocument(reader);
                if (parsed.isEmpty()) {
                    continue;
                }
                SETS.putAll(parsed);
                int setCount = SETS.values().stream().mapToInt(List::size).sum();
                BattleCalcDiagnostics.LOGGER.info(
                        "Loaded Tropimon Random Battle sets from game files: {} Pokemon, {} sets, source={}",
                        SETS.size(), setCount, candidate);
                return;
            } catch (Exception exception) {
                BattleCalcDiagnostics.LOGGER.debug("Ignored invalid Random Battle set file: {}", candidate, exception);
            }
        }
        if (!loadBundledSnapshot()) {
            BattleCalcDiagnostics.LOGGER.warn("No Tropimon Random Battle set data is available");
        }
    }

    static synchronized void reload() {
        loaded = false;
        INFERENCE_CACHE.clear();
        load();
    }

    static List<RandomBattleSet> setsFor(String speciesId) {
        load();
        return SETS.getOrDefault(BattleCalcDex.normalize(speciesId), List.of());
    }

    static List<RandomBattleSet> setsFor(SpeciesData species) {
        if (species == null) {
            return List.of();
        }
        List<RandomBattleSet> sets = setsFor(species.id());
        if (!sets.isEmpty()) {
            return sets;
        }
        sets = setsFor(species.name());
        if (!sets.isEmpty()) {
            return sets;
        }
        return setsFor(species.cobblemonSpeciesId());
    }

    static int suggestedLevel(SpeciesData species, int fallback) {
        List<RandomBattleSet> sets = setsFor(species);
        if (sets.isEmpty()) {
            return fallback;
        }
        LinkedHashMap<Integer, Integer> weights = new LinkedHashMap<>();
        for (RandomBattleSet set : sets) {
            weights.merge(set.level(), Math.max(1, set.weight()), Integer::sum);
        }
        int selectedLevel = fallback;
        int selectedWeight = -1;
        for (Map.Entry<Integer, Integer> entry : weights.entrySet()) {
            if (entry.getValue() > selectedWeight) {
                selectedLevel = entry.getKey();
                selectedWeight = entry.getValue();
            }
        }
        return Math.max(1, Math.min(100, selectedLevel));
    }

    static int applyInference(PokemonSet pokemon) {
        applyStandardStats(pokemon);
        List<RandomBattleSet> candidates = matchingSets(pokemon);
        if (pokemon == null || candidates.isEmpty()) {
            return 0;
        }
        String commonItem = commonValue(candidates, RandomBattleSet::itemId);
        if (!pokemon.itemKnown && commonItem != null) {
            String item = BattleCalcDex.findItemByQuery(commonItem);
            pokemon.item = item == null ? prettyIdentifier(commonItem) : item;
            pokemon.itemKnown = true;
        }
        String commonAbility = commonValue(candidates, RandomBattleSet::abilityId);
        if (!pokemon.abilityKnown && commonAbility != null) {
            String ability = BattleCalcDex.findAbilityByQuery(pokemon.species, commonAbility);
            pokemon.ability = ability == null ? prettyIdentifier(commonAbility) : ability;
            pokemon.abilityKnown = true;
        }
        String commonTera = commonValue(candidates, RandomBattleSet::teraTypeId);
        if (pokemon.teraType == PokeType.NONE && commonTera != null) {
            pokemon.teraType = PokeType.byName(commonTera);
        }
        List<String> inferredMoves = commonMoves(candidates);
        for (String moveId : inferredMoves) {
            MoveData move = BattleCalcDex.findMoveByQuery(moveId);
            if (move != null) {
                addMoveIfMissing(pokemon, move);
            }
        }
        return candidates.size();
    }

    private static void applyStandardStats(PokemonSet pokemon) {
        if (pokemon == null || pokemon.statsKnown) return;
        for (Stat stat : Stat.values()) {
            pokemon.evs.put(stat, 85);
            pokemon.ivs.put(stat, 31);
        }
        if (!pokemon.natureKnown) pokemon.nature = BattleCalcDex.nature("serious");
    }

    static void applySet(PokemonSet pokemon, RandomBattleSet set) {
        if (pokemon == null || set == null) {
            return;
        }
        pokemon.level = Math.max(1, Math.min(100, set.level()));
        String item = BattleCalcDex.findItemByQuery(set.itemId());
        pokemon.item = item == null ? prettyIdentifier(set.itemId()) : item;
        pokemon.itemKnown = true;
        String ability = BattleCalcDex.findAbilityByQuery(pokemon.species, set.abilityId());
        pokemon.ability = ability == null ? prettyIdentifier(set.abilityId()) : ability;
        pokemon.abilityKnown = true;
        pokemon.teraType = PokeType.byName(set.teraTypeId());
        pokemon.moves.clear();
        for (String moveId : set.moveIds()) {
            MoveData move = BattleCalcDex.findMoveByQuery(moveId);
            if (move != null && pokemon.moves.size() < 4) {
                pokemon.moves.add(move);
            }
        }
        while (pokemon.moves.size() < 4) {
            pokemon.moves.add(null);
        }
        pokemon.movesKnown = pokemon.moves.stream().anyMatch(java.util.Objects::nonNull);
        java.util.Arrays.fill(pokemon.zMoves, false);
    }

    static synchronized List<RandomBattleSet> matchingSets(PokemonSet pokemon) {
        if (pokemon == null) {
            return List.of();
        }
        InferenceSignature signature = inferenceSignature(pokemon);
        List<RandomBattleSet> cached = INFERENCE_CACHE.get(signature);
        if (cached != null) return cached;
        List<RandomBattleSet> candidates = new ArrayList<>(setsFor(pokemon.species));
        if (candidates.isEmpty()) {
            INFERENCE_CACHE.put(signature, List.of());
            return List.of();
        }
        if (pokemon.level > 0) {
            candidates = filter(candidates, set -> set.level() == pokemon.level);
        }
        String item = BattleCalcDex.normalize(pokemon.item);
        if (pokemon.itemKnown && !item.isBlank() && !item.equals("none")) {
            candidates = filter(candidates, set -> BattleCalcDex.normalize(set.itemId()).equals(item));
        }
        String ability = BattleCalcDex.normalize(pokemon.ability);
        if (pokemon.abilityKnown && !ability.isBlank() && !ability.equals("none")) {
            candidates = filter(candidates, set -> BattleCalcDex.normalize(set.abilityId()).equals(ability));
        }
        Set<String> knownMoves = new HashSet<>();
        for (MoveData move : pokemon.moves) {
            if (move != null) {
                knownMoves.add(BattleCalcDex.normalize(move.id()));
            }
        }
        if (!knownMoves.isEmpty()) {
            candidates = filter(candidates, set -> {
                Set<String> setMoves = new HashSet<>();
                for (String moveId : set.moveIds()) {
                    setMoves.add(BattleCalcDex.normalize(moveId));
                }
                return setMoves.containsAll(knownMoves);
            });
        }
        if (pokemon.teraType != PokeType.NONE) {
            candidates = filter(candidates,
                    set -> PokeType.byName(set.teraTypeId()) == pokemon.teraType);
        }
        List<RandomBattleSet> result = List.copyOf(candidates);
        INFERENCE_CACHE.put(signature, result);
        return result;
    }

    static int speciesCount() {
        load();
        return SETS.size();
    }

    static synchronized void replaceFromReaderForTest(Reader reader) {
        SETS.clear();
        INFERENCE_CACHE.clear();
        SETS.putAll(parseDocument(reader));
        loaded = true;
    }

    private static InferenceSignature inferenceSignature(PokemonSet pokemon) {
        ArrayList<String> moves = new ArrayList<>();
        for (MoveData move : pokemon.moves) {
            if (move != null) moves.add(BattleCalcDex.normalize(move.id()));
        }
        Collections.sort(moves);
        String item = pokemon.itemKnown ? BattleCalcDex.normalize(pokemon.item) : "";
        String ability = pokemon.abilityKnown ? BattleCalcDex.normalize(pokemon.ability) : "";
        SpeciesData species = pokemon.species;
        return new InferenceSignature(
                species == null ? "" : BattleCalcDex.normalize(species.id()),
                species == null ? "" : BattleCalcDex.normalize(species.name()),
                species == null ? "" : BattleCalcDex.normalize(species.cobblemonSpeciesId()),
                pokemon.level, item, ability, List.copyOf(moves), pokemon.teraType);
    }

    private record InferenceSignature(String speciesId, String speciesName, String cobblemonSpeciesId,
                                      int level, String item, String ability, List<String> moves,
                                      PokeType teraType) {
    }

    private static boolean loadBundledSnapshot() {
        try (InputStream stream = BattleRandomSets.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (stream == null) {
                return false;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, List<RandomBattleSet>> parsed = parseDocument(reader);
                if (parsed.isEmpty()) {
                    return false;
                }
                SETS.putAll(parsed);
                int setCount = SETS.values().stream().mapToInt(List::size).sum();
                BattleCalcDiagnostics.LOGGER.info(
                        "Loaded bundled Tropimon Random Battle snapshot: {} Pokemon, {} sets",
                        SETS.size(), setCount);
                return true;
            }
        } catch (Exception exception) {
            BattleCalcDiagnostics.LOGGER.error(
                    "Could not load bundled Tropimon Random Battle snapshot", exception);
            return false;
        }
    }

    private static List<Path> localCandidates() {
        Path own = FabricLoader.getInstance().getConfigDir().resolve("tropimon_ui_battle").resolve("random-battle-sets.json");
        return Files.isRegularFile(own) ? List.of(own) : List.of();
    }

    private static Map<String, List<RandomBattleSet>> parseDocument(Reader reader) {
        LinkedHashMap<String, List<RandomBattleSet>> output = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> speciesEntry : root.entrySet()) {
                if (!speciesEntry.getValue().isJsonObject()) {
                    continue;
                }
                LinkedHashMap<String, RandomBattleSet> speciesSets = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> setEntry : speciesEntry.getValue().getAsJsonObject().entrySet()) {
                    RandomBattleSet parsed = parse(setEntry.getKey(), setEntry.getValue());
                    if (parsed != null) {
                        speciesSets.merge(setKey(parsed), parsed, BattleRandomSets::mergeDuplicateSets);
                    }
                }
                if (!speciesSets.isEmpty()) {
                    output.put(BattleCalcDex.normalize(speciesEntry.getKey()), List.copyOf(speciesSets.values()));
                }
            }
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        return output;
    }

    private static String setKey(RandomBattleSet set) {
        ArrayList<String> moves = new ArrayList<>();
        for (String move : set.moveIds()) {
            moves.add(BattleCalcDex.normalize(move));
        }
        moves.sort(String::compareTo);
        return set.level() + "|" + BattleCalcDex.normalize(set.itemId()) + "|"
                + BattleCalcDex.normalize(set.abilityId()) + "|" + String.join(",", moves) + "|"
                + BattleCalcDex.normalize(set.teraTypeId());
    }

    private static RandomBattleSet mergeDuplicateSets(RandomBattleSet first, RandomBattleSet duplicate) {
        long combinedWeight = (long) Math.max(1, first.weight()) + Math.max(1, duplicate.weight());
        return new RandomBattleSet(first.level(), first.itemId(), first.abilityId(), first.moveIds(),
                first.teraTypeId(), (int) Math.min(Integer.MAX_VALUE, combinedWeight));
    }

    private static List<RandomBattleSet> filter(List<RandomBattleSet> source,
                                                 java.util.function.Predicate<RandomBattleSet> predicate) {
        return source.stream().filter(predicate).toList();
    }

    private static String commonValue(List<RandomBattleSet> sets,
                                      Function<RandomBattleSet, String> getter) {
        if (sets.isEmpty()) {
            return null;
        }
        String first = getter.apply(sets.getFirst());
        String normalized = BattleCalcDex.normalize(first);
        for (int index = 1; index < sets.size(); index++) {
            if (!BattleCalcDex.normalize(getter.apply(sets.get(index))).equals(normalized)) {
                return null;
            }
        }
        return first;
    }

    private static List<String> commonMoves(List<RandomBattleSet> sets) {
        if (sets.isEmpty()) {
            return List.of();
        }
        ArrayList<String> common = new ArrayList<>(sets.getFirst().moveIds());
        for (int index = 1; index < sets.size(); index++) {
            Set<String> candidateMoves = new HashSet<>();
            for (String move : sets.get(index).moveIds()) {
                candidateMoves.add(BattleCalcDex.normalize(move));
            }
            common.removeIf(move -> !candidateMoves.contains(BattleCalcDex.normalize(move)));
        }
        return List.copyOf(common);
    }

    private static void addMoveIfMissing(PokemonSet pokemon, MoveData move) {
        for (MoveData known : pokemon.moves) {
            if (known != null && known.id().equals(move.id())) {
                return;
            }
        }
        for (int slot = 0; slot < pokemon.moves.size(); slot++) {
            if (pokemon.moves.get(slot) == null) {
                pokemon.moves.set(slot, move);
                pokemon.movesKnown = true;
                return;
            }
        }
        if (pokemon.moves.size() < 4) {
            pokemon.moves.add(move);
            pokemon.movesKnown = true;
        }
    }

    private static String prettyIdentifier(String id) {
        String spaced = id == null ? "" : id.replace('_', ' ').replace('-', ' ').trim();
        if (spaced.isBlank()) {
            return "None";
        }
        StringBuilder output = new StringBuilder(spaced.length());
        boolean upper = true;
        for (char character : spaced.toCharArray()) {
            if (character == ' ') {
                output.append(character);
                upper = true;
            } else {
                output.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return output.toString();
    }

    private static RandomBattleSet parse(String encodedSet, JsonElement weightElement) {
        String[] parts = encodedSet.split(",", -1);
        if (parts.length < 5) {
            return null;
        }
        try {
            int level = Integer.parseInt(parts[0]);
            int weight = weightElement.isJsonPrimitive() ? weightElement.getAsInt() : 1;
            ArrayList<String> moves = new ArrayList<>();
            for (int index = 3; index < parts.length - 1; index++) {
                if (!parts[index].isBlank()) {
                    moves.add(parts[index]);
                }
            }
            return new RandomBattleSet(level, parts[1], parts[2], List.copyOf(moves),
                    parts[parts.length - 1], weight);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record RandomBattleSet(int level, String itemId, String abilityId, List<String> moveIds,
                           String teraTypeId, int weight) {
    }
}
