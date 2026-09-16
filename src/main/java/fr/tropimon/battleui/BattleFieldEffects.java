package fr.tropimon.battleui;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BattleFieldEffects {
    private static final Map<String, ActiveEffect> ACTIVE = new LinkedHashMap<>();
    private static final Map<String, HazardObservations> HAZARD_OBSERVATIONS = new LinkedHashMap<>();
    private static final Map<String, EffectSide> SIDE_EFFECT_SOURCES = new LinkedHashMap<>();
    private static final Map<String, InferredSideEffect> INFERRED_SIDE_EFFECTS = new LinkedHashMap<>();
    private static boolean messageBatch;
    private static ObservationMode observationMode = ObservationMode.PARTICIPANT;
    private static int observationTurn = Integer.MIN_VALUE;

    private BattleFieldEffects() {
    }

    static synchronized void reset() {
        ACTIVE.clear();
        HAZARD_OBSERVATIONS.clear();
        SIDE_EFFECT_SOURCES.clear();
        INFERRED_SIDE_EFFECTS.clear();
        messageBatch = false;
        observationMode = ObservationMode.PARTICIPANT;
        observationTurn = Integer.MIN_VALUE;
    }

    static synchronized void beginMessageBatch() {
        beginMessageBatch(false);
    }

    static synchronized void beginMessageBatch(boolean spectating) {
        ObservationMode nextMode = spectating ? ObservationMode.SPECTATOR : ObservationMode.PARTICIPANT;
        if (observationMode != nextMode) {
            INFERRED_SIDE_EFFECTS.keySet().forEach(ACTIVE::remove);
            INFERRED_SIDE_EFFECTS.clear();
            HAZARD_OBSERVATIONS.clear();
            SIDE_EFFECT_SOURCES.clear();
            observationMode = nextMode;
        }
        messageBatch = true;
    }

    static synchronized void endMessageBatch() {
        messageBatch = false;
    }

    static synchronized void accept(String key, int turn) {
        accept(key, turn, EffectSide.FIELD);
    }

    static synchronized void accept(String key, int turn, EffectSide announcedSide) {
        if (key == null || key.isBlank()) return;
        advanceObservationTurn(turn);
        expireTimedEffects(turn);
        String normalized = key.toLowerCase(Locale.ROOT);

        if (normalized.contains(".activate.courtchange")) {
            swapSideEffects();
            return;
        }

        if (normalized.contains(".weather.")) {
            acceptWeather(normalized, turn);
            return;
        }
        if (normalized.contains(".fieldstart.")) {
            String id = suffix(normalized, ".fieldstart.");
            if (isTerrain(id)) removePrefix("field:", BattleFieldEffects::isTerrain);
            put("field:" + id, id, turn, duration(id), false);
            return;
        }
        if (normalized.contains(".fieldend.")) {
            ACTIVE.remove("field:" + suffix(normalized, ".fieldend."));
            return;
        }
        if (normalized.contains(".sidestart.")) {
            String suffix = suffix(normalized, ".sidestart.");
            String id = suffix.substring(suffix.lastIndexOf('.') + 1);
            EffectSide resolvedSide = announcedSide;
            if (!suffix.startsWith("ally.") && !suffix.startsWith("opponent.")
                    && resolvedSide == EffectSide.FIELD) {
                resolvedSide = rememberedSideEffectSource(id, turn);
            }
            String side = sideId(suffix, resolvedSide);
            if (isHazard(id) && !side.equals("field")) {
                observeHazard(id, side.equals("ally") ? EffectSide.PLAYER_FIELD : EffectSide.OPPONENT_FIELD,
                        turn, HazardSignal.EXPLICIT);
            } else {
                String effectKey = "side:" + side + ":" + id;
                InferredSideEffect inferred = INFERRED_SIDE_EFFECTS.get(effectKey);
                if (observationMode == ObservationMode.SPECTATOR) {
                    // A server fork may expose side-start but still omit side-end to spectators.
                    // Preserve the move-derived start on a same-turn echo and retain local expiry.
                    EffectSide effectSide = side.equals("ally") ? EffectSide.PLAYER_FIELD
                            : side.equals("opponent") ? EffectSide.OPPONENT_FIELD : EffectSide.FIELD;
                    boolean newMoveObserved = rememberedSideEffectSource(id, turn) == effectSide;
                    if (inferred == null
                            || (inferred.startedTurn() != Math.max(1, turn) && newMoveObserved)) {
                        put(effectKey, side + "." + id, turn, duration(id), false);
                        INFERRED_SIDE_EFFECTS.put(effectKey,
                                new InferredSideEffect(Math.max(1, turn), observationMode));
                    }
                } else {
                    put(effectKey, side + "." + id, turn, duration(id), false);
                    INFERRED_SIDE_EFFECTS.remove(effectKey);
                }
            }
            return;
        }
        if (normalized.contains(".sideend.")) {
            String suffix = suffix(normalized, ".sideend.");
            String side = sideId(suffix, announcedSide);
            String id = suffix.substring(suffix.lastIndexOf('.') + 1);
            if (isHazard(id) && side.equals("field")) {
                // Some Cobblemon translations use the generic side-end key when a
                // grounded Poison type absorbs Toxic Spikes. If its actor argument
                // cannot be resolved, a single visible matching side is unambiguous.
                String ally = "side:ally:" + id;
                String opponent = "side:opponent:" + id;
                boolean onAlly = ACTIVE.containsKey(ally);
                boolean onOpponent = ACTIVE.containsKey(opponent);
                if (onAlly != onOpponent) ACTIVE.remove(onAlly ? ally : opponent);
            } else {
                String effectKey = "side:" + side + ":" + id;
                ACTIVE.remove(effectKey);
                INFERRED_SIDE_EFFECTS.remove(effectKey);
            }
        }
    }

    private static String sideId(String suffix, EffectSide announcedSide) {
        if (suffix.startsWith("ally.")) return "ally";
        if (suffix.startsWith("opponent.")) return "opponent";
        return announcedSide == EffectSide.PLAYER_FIELD ? "ally"
                : announcedSide == EffectSide.OPPONENT_FIELD ? "opponent" : "field";
    }

    private static void acceptWeather(String key, int turn) {
        String suffix = suffix(key, ".weather.");
        int separator = suffix.indexOf('.');
        if (separator < 0) return;
        String id = suffix.substring(0, separator);
        String action = suffix.substring(separator + 1);
        if (action.startsWith("end")) {
            ACTIVE.remove("weather:" + id);
        } else if (action.startsWith("start") || (action.startsWith("upkeep") && !ACTIVE.containsKey("weather:" + id))) {
            removePrefix("weather:", ignored -> true);
            put("weather:" + id, id, turn, duration(id), false);
        }
    }

    private static void removePrefix(String prefix, java.util.function.Predicate<String> labelFilter) {
        ACTIVE.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix) &&
                labelFilter.test(entry.getValue().labelId()));
    }

    private static void swapSideEffects() {
        Map<String, ActiveEffect> swapped = new LinkedHashMap<>();
        Map<String, InferredSideEffect> swappedInferred = new LinkedHashMap<>();
        for (Map.Entry<String, ActiveEffect> entry : ACTIVE.entrySet()) {
            String key = entry.getKey();
            ActiveEffect effect = entry.getValue();
            if (key.startsWith("side:ally:")) {
                String id = key.substring("side:ally:".length());
                String swappedKey = "side:opponent:" + id;
                swapped.put(swappedKey, effect.withLabel("opponent." + id));
                if (INFERRED_SIDE_EFFECTS.containsKey(key)) {
                    swappedInferred.put(swappedKey, INFERRED_SIDE_EFFECTS.get(key));
                }
            } else if (key.startsWith("side:opponent:")) {
                String id = key.substring("side:opponent:".length());
                String swappedKey = "side:ally:" + id;
                swapped.put(swappedKey, effect.withLabel("ally." + id));
                if (INFERRED_SIDE_EFFECTS.containsKey(key)) {
                    swappedInferred.put(swappedKey, INFERRED_SIDE_EFFECTS.get(key));
                }
            } else {
                swapped.put(key, effect);
                if (INFERRED_SIDE_EFFECTS.containsKey(key)) {
                    swappedInferred.put(key, INFERRED_SIDE_EFFECTS.get(key));
                }
            }
        }
        ACTIVE.clear();
        ACTIVE.putAll(swapped);
        INFERRED_SIDE_EFFECTS.clear();
        INFERRED_SIDE_EFFECTS.putAll(swappedInferred);
    }

    private static boolean isTerrain(String id) {
        return id.endsWith("terrain");
    }

    private static boolean isRoom(String id) {
        return id.equals("trickroom") || id.equals("magicroom") || id.equals("wonderroom");
    }

    private static void put(String key, String labelId, int turn, EffectDuration duration, boolean layered) {
        ActiveEffect previous = ACTIVE.get(key);
        if (previous != null && layered) {
            int maxLayers = labelId.endsWith("toxicspikes") ? 2 : 3;
            ACTIVE.put(key, new ActiveEffect(labelId, previous.startedTurn(), duration,
                    Math.min(maxLayers, previous.layers() + 1), true));
            return;
        }
        ACTIVE.put(key, new ActiveEffect(labelId, Math.max(1, turn), duration, 1, layered));
    }

    static synchronized List<EffectView> snapshot(int currentTurn) {
        expireTimedEffects(currentTurn);
        List<EffectView> result = new ArrayList<>();
        for (Map.Entry<String, ActiveEffect> entry : ACTIVE.entrySet()) {
            ActiveEffect effect = entry.getValue();
            int elapsed = Math.max(1, currentTurn - effect.startedTurn() + 1);
            int minimumRemaining = remaining(effect.duration().standardTurns(), elapsed);
            int maximumRemaining = remaining(effect.duration().extendedTurns(), elapsed);
            boolean inferredLifecycle = INFERRED_SIDE_EFFECTS.containsKey(entry.getKey());
            // If the normal duration has already elapsed but the effect is
            // still active, an extender is no longer a hypothesis.
            if (!inferredLifecycle && elapsed > effect.duration().standardTurns() && maximumRemaining > 0) {
                minimumRemaining = maximumRemaining;
            }
            result.add(new EffectView(effect.labelId(), minimumRemaining, maximumRemaining,
                    effect.duration().standardTurns(), effect.duration().extendedTurns(),
                    effect.layers(), effect.layered(), sideOf(effect.labelId())));
        }
        return List.copyOf(result);
    }

    static synchronized boolean active(String id) {
        if (id == null || id.isBlank()) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return ACTIVE.values().stream().anyMatch(effect -> {
            String label = effect.labelId();
            int separator = label.lastIndexOf('.');
            String simple = separator < 0 ? label : label.substring(separator + 1);
            return simple.equals(normalized);
        });
    }

    static synchronized boolean activeOnSide(String id, EffectSide side) {
        if (id == null || id.isBlank() || side == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return ACTIVE.values().stream().anyMatch(effect -> {
            String label = effect.labelId();
            int separator = label.lastIndexOf('.');
            String simple = separator < 0 ? label : label.substring(separator + 1);
            return simple.equals(normalized) && sideOf(label) == side;
        });
    }

    static synchronized boolean activeOnSide(String id, EffectSide side, int currentTurn) {
        expireTimedEffects(currentTurn);
        if (id == null || id.isBlank() || side == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return ACTIVE.entrySet().stream().anyMatch(entry -> {
            ActiveEffect effect = entry.getValue();
            String label = effect.labelId();
            int separator = label.lastIndexOf('.');
            String simple = separator < 0 ? label : label.substring(separator + 1);
            if (!simple.equals(normalized) || sideOf(label) != side) return false;
            if (!INFERRED_SIDE_EFFECTS.containsKey(entry.getKey())
                    || effect.duration().standardTurns() == effect.duration().extendedTurns()) return true;
            int elapsed = Math.max(1, currentTurn - effect.startedTurn() + 1);
            // Once an unknown Light Clay becomes the only way the screen could remain,
            // the HUD keeps the possibility range but boolean damage calculations do not
            // claim that the screen is certainly active.
            return elapsed <= effect.duration().standardTurns();
        });
    }

    static synchronized void startSlotEffect(String id, EffectSide side, int turn) {
        startSlotEffect(id, side, "", turn);
    }

    static synchronized void startSlotEffect(String id, EffectSide side, String slot, int turn) {
        if (id == null || id.isBlank() || side == null || side == EffectSide.FIELD) return;
        String simple = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
        String normalizedSlot = slot == null || slot.isBlank() ? "any" : slot.toLowerCase(Locale.ROOT);
        String prefix = "slot:" + sideId + ":";
        String key = prefix + normalizedSlot + ":" + simple;
        ActiveEffect previous = ACTIVE.get(key);
        if (previous == null && !normalizedSlot.equals("any")) {
            previous = ACTIVE.remove(prefix + "any:" + simple);
        }
        int startedTurn = previous == null ? Math.max(1, turn) : previous.startedTurn();
        ACTIVE.put(key, new ActiveEffect(sideId + "." + simple, startedTurn,
                duration(simple), 1, false));
    }

    static synchronized void startHazard(String id, EffectSide side, int turn) {
        observeSemanticHazard(id, side, turn, HazardSignal.INFERRED);
    }

    static synchronized void rememberSideEffectMove(String id, EffectSide side, int turn) {
        if (id == null || id.isBlank() || side == null || side == EffectSide.FIELD) return;
        advanceObservationTurn(turn);
        String simple = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String prefix = observationMode + ":" + turn + ":";
        SIDE_EFFECT_SOURCES.put(prefix + simple, side);
    }

    static synchronized SideEffectMoveActivation startSideEffectFromMove(String id, EffectSide side, int turn) {
        if (id == null || id.isBlank() || side == null || side == EffectSide.FIELD) {
            return SideEffectMoveActivation.unchanged(observationMode);
        }
        rememberSideEffectMove(id, side, turn);
        if (observationMode != ObservationMode.SPECTATOR) {
            return SideEffectMoveActivation.unchanged(observationMode);
        }
        expireTimedEffects(turn);
        String simple = normalizeSideEffect(id);
        if (!isSideEffect(simple)) return SideEffectMoveActivation.unchanged(observationMode);
        String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
        String key = "side:" + sideId + ":" + simple;
        ActiveEffect previous = ACTIVE.get(key);
        InferredSideEffect previousInference = INFERRED_SIDE_EFFECTS.get(key);
        if (previous != null) {
            int elapsed = Math.max(1, turn - previous.startedTurn() + 1);
            boolean uncertainScreenCanBeReplaced = previousInference != null
                    && previous.duration().standardTurns() < previous.duration().extendedTurns()
                    && elapsed > previous.duration().standardTurns();
            if (!uncertainScreenCanBeReplaced) {
                return SideEffectMoveActivation.unchanged(observationMode);
            }
        }
        put(key, sideId + "." + simple, turn, duration(simple), false);
        INFERRED_SIDE_EFFECTS.put(key, new InferredSideEffect(Math.max(1, turn), observationMode));
        return new SideEffectMoveActivation(key, Math.max(1, turn), observationMode,
                previous, previousInference, true);
    }

    static synchronized void cancelMoveSideEffect(SideEffectMoveActivation activation) {
        if (activation == null || !activation.changed() || activation.mode != observationMode) return;
        InferredSideEffect inferred = INFERRED_SIDE_EFFECTS.get(activation.key);
        ActiveEffect active = ACTIVE.get(activation.key);
        if (inferred == null || active == null || inferred.mode() != activation.mode
                || inferred.startedTurn() != activation.startedTurn
                || active.startedTurn() != activation.startedTurn) return;
        if (activation.previous == null) ACTIVE.remove(activation.key);
        else ACTIVE.put(activation.key, activation.previous);
        if (activation.previousInference == null) INFERRED_SIDE_EFFECTS.remove(activation.key);
        else INFERRED_SIDE_EFFECTS.put(activation.key, activation.previousInference);
    }

    /**
     * Applies a side-condition removal reconstructed from a public move or ability announcement.
     * Participant battles keep using Cobblemon's authoritative side-end messages instead.
     */
    static synchronized SideRemovalActivation removeObservedSideConditions(
            Map<EffectSide, Set<String>> removals) {
        if (observationMode != ObservationMode.SPECTATOR || removals == null || removals.isEmpty()) {
            return SideRemovalActivation.unchanged(observationMode);
        }
        Map<String, ActiveEffect> removed = new LinkedHashMap<>();
        Map<String, InferredSideEffect> removedInferences = new LinkedHashMap<>();
        for (Map.Entry<EffectSide, Set<String>> entry : removals.entrySet()) {
            EffectSide side = entry.getKey();
            if (side == null || side == EffectSide.FIELD || entry.getValue() == null) continue;
            String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
            for (String value : entry.getValue()) {
                String id = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (id.isBlank()) continue;
                String key = "side:" + sideId + ":" + id;
                ActiveEffect previous = ACTIVE.remove(key);
                InferredSideEffect previousInference = INFERRED_SIDE_EFFECTS.remove(key);
                if (previous != null) removed.put(key, previous);
                if (previousInference != null) removedInferences.put(key, previousInference);
            }
        }
        return new SideRemovalActivation(observationMode, removed, removedInferences);
    }

    /** Restores only the state removed by the still-current failed move. */
    static synchronized void cancelObservedSideRemoval(SideRemovalActivation activation) {
        if (activation == null || !activation.changed() || activation.mode != observationMode) return;
        activation.removed.forEach(ACTIVE::putIfAbsent);
        activation.removedInferences.forEach((key, value) -> {
            if (ACTIVE.containsKey(key)) INFERRED_SIDE_EFFECTS.putIfAbsent(key, value);
        });
    }

    private static EffectSide rememberedSideEffectSource(String id, int turn) {
        if (id == null || id.isBlank()) return EffectSide.FIELD;
        String simple = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SIDE_EFFECT_SOURCES.getOrDefault(
                observationMode + ":" + turn + ":" + simple, EffectSide.FIELD);
    }

    static synchronized boolean startHazardFromMove(String id, EffectSide side, int turn) {
        return observeSemanticHazard(id, side, turn, HazardSignal.MOVE);
    }

    static synchronized void cancelMoveHazard(String id, EffectSide side, int turn) {
        cancelMoveHazard(id, side, turn, true);
    }

    static synchronized void cancelMoveHazard(String id, EffectSide side, int turn, boolean applied) {
        if (id == null || side == null || side == EffectSide.FIELD) return;
        String simple = normalizeHazard(id);
        if (!isHazard(simple) || !messageBatch) return;
        String key = hazardObservationKey(simple, side, turn);
        HazardObservations observations = HAZARD_OBSERVATIONS.get(key);
        if (observations == null || observations.moves == 0) return;
        int before = observations.activations();
        observations.moves--;
        int after = observations.activations();
        if (applied && after < before && observations.appliedActivations > 0
                && removeHazardActivation(simple, side)) {
            observations.appliedActivations--;
        }
    }

    private static boolean observeSemanticHazard(String id, EffectSide side, int turn, HazardSignal signal) {
        if (id == null || side == null || side == EffectSide.FIELD) return false;
        String simple = normalizeHazard(id);
        if (!isHazard(simple)) return false;
        return observeHazard(simple, side, turn, signal);
    }

    private static boolean observeHazard(String id, EffectSide side, int turn, HazardSignal signal) {
        advanceObservationTurn(turn);
        if (!messageBatch) {
            return putHazard(id, side, turn);
        }
        // The inferred move and Cobblemon's side-start confirmation can be
        // separated by damage and animation packets. Pair them for the whole turn.
        String key = hazardObservationKey(id, side, turn);
        HazardObservations observations = HAZARD_OBSERVATIONS.computeIfAbsent(key,
                ignored -> new HazardObservations(turn, observationMode));
        int before = observations.activations();
        switch (signal) {
            case EXPLICIT -> observations.explicit++;
            case INFERRED -> observations.inferred++;
            case MOVE -> observations.moves++;
        }
        boolean applied = observations.activations() > before && putHazard(id, side, turn);
        if (applied) observations.appliedActivations++;
        return applied;
    }

    private static boolean putHazard(String simple, EffectSide side, int turn) {
        String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
        boolean layered = simple.equals("spikes") || simple.equals("toxicspikes");
        String key = "side:" + sideId + ":" + simple;
        ActiveEffect previous = ACTIVE.get(key);
        int previousLayers = previous == null ? 0 : previous.layers();
        put("side:" + sideId + ":" + simple, sideId + "." + simple,
                turn, duration(simple), layered);
        ActiveEffect current = ACTIVE.get(key);
        return previous == null || current.layers() > previousLayers;
    }

    private static boolean removeHazardActivation(String simple, EffectSide side) {
        String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
        String key = "side:" + sideId + ":" + simple;
        ActiveEffect current = ACTIVE.get(key);
        if (current == null) return false;
        if (current.layers() <= 1) ACTIVE.remove(key);
        else ACTIVE.put(key, new ActiveEffect(current.labelId(), current.startedTurn(), current.duration(),
                current.layers() - 1, true));
        return true;
    }

    private static String normalizeHazard(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalizeSideEffect(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isSideEffect(String id) {
        return id.equals("tailwind") || id.equals("reflect") || id.equals("lightscreen")
                || id.equals("auroraveil") || id.equals("safeguard") || id.equals("mist")
                || id.equals("luckychant");
    }

    private static void expireTimedEffects(int currentTurn) {
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveEffect> entry = iterator.next();
            ActiveEffect active = entry.getValue();
            int duration = active.duration().extendedTurns();
            int elapsed = Math.max(1, currentTurn - active.startedTurn() + 1);
            if (duration > 0 && elapsed > duration) {
                iterator.remove();
                INFERRED_SIDE_EFFECTS.remove(entry.getKey());
            }
        }
        INFERRED_SIDE_EFFECTS.keySet().removeIf(key -> !ACTIVE.containsKey(key));
    }

    private static String hazardObservationKey(String id, EffectSide side, int turn) {
        return observationMode + ":" + turn + ":" + side + ":" + id;
    }

    /** Provenance is intentionally scoped to one turn and one observation mode. */
    private static void advanceObservationTurn(int turn) {
        if (observationTurn == turn) return;
        HAZARD_OBSERVATIONS.clear();
        SIDE_EFFECT_SOURCES.clear();
        observationTurn = turn;
    }

    private static boolean isHazard(String id) {
        return id.equals("spikes") || id.equals("toxicspikes")
                || id.equals("stealthrock") || id.equals("stickyweb");
    }

    static synchronized void endSlotEffect(String id, EffectSide side) {
        endSlotEffect(id, side, "");
    }

    static synchronized void endSlotEffect(String id, EffectSide side, String slot) {
        if (id == null || id.isBlank() || side == null || side == EffectSide.FIELD) return;
        String simple = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String sideId = side == EffectSide.PLAYER_FIELD ? "ally" : "opponent";
        String prefix = "slot:" + sideId + ":";
        if (slot == null || slot.isBlank()) {
            ACTIVE.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix)
                    && entry.getKey().endsWith(":" + simple));
        } else {
            ACTIVE.remove(prefix + slot.toLowerCase(Locale.ROOT) + ":" + simple);
            ACTIVE.remove(prefix + "any:" + simple);
        }
    }

    private static int remaining(int duration, int elapsed) {
        return duration <= 0 ? 0 : Math.max(0, duration - elapsed + 1);
    }

    private static EffectSide sideOf(String labelId) {
        if (labelId.startsWith("ally.")) return EffectSide.PLAYER_FIELD;
        if (labelId.startsWith("opponent.")) return EffectSide.OPPONENT_FIELD;
        return EffectSide.FIELD;
    }

    private static EffectDuration duration(String id) {
        return switch (id) {
            case "tailwind" -> new EffectDuration(4, 4);
            case "raindance", "sunnyday", "sandstorm", "hail", "snow",
                    "electricterrain", "grassyterrain", "mistyterrain", "psychicterrain",
                    "reflect", "lightscreen", "auroraveil" -> new EffectDuration(5, 8);
            case "trickroom", "magicroom", "wonderroom", "gravity", "safeguard", "mist",
                    "luckychant", "mudsport", "watersport" -> new EffectDuration(5, 5);
            case "wish" -> new EffectDuration(2, 2);
            case "futuresight", "doomdesire" -> new EffectDuration(3, 3);
            default -> EffectDuration.DYNAMIC;
        };
    }

    private static String suffix(String value, String marker) {
        int index = value.indexOf(marker);
        return index < 0 ? "" : value.substring(index + marker.length());
    }

    enum EffectSide {
        PLAYER_FIELD,
        OPPONENT_FIELD,
        FIELD
    }

    record EffectView(String id, int minimumRemainingTurns, int maximumRemainingTurns,
                      int standardDuration, int extendedDuration, int layers, boolean layered,
                      EffectSide side) {
        Text text() {
            String labelKey = "text.tropimon_ui_battle.effect." + id.replace('.', '_');
            Text label = Text.translatable(labelKey);
            if (layered) return Text.translatable("text.tropimon_ui_battle.effect.layers", label, layers);
            if (maximumRemainingTurns <= 0) return label;
            if (minimumRemainingTurns != maximumRemainingTurns) {
                return Text.translatable("text.tropimon_ui_battle.effect.remaining_range", label,
                        minimumRemainingTurns, maximumRemainingTurns);
            }
            String key = minimumRemainingTurns == 1
                    ? "text.tropimon_ui_battle.effect.remaining_one"
                    : "text.tropimon_ui_battle.effect.remaining";
            return Text.translatable(key, label, minimumRemainingTurns);
        }

        List<Text> tooltipLines() {
            Text title = Text.translatable("text.tropimon_ui_battle.effect." + id.replace('.', '_'))
                    .styled(style -> style.withBold(true));
            Text description;
            if (layered) {
                description = Text.translatable("text.tropimon_ui_battle.effect.layer_count", layers);
            } else if (minimumRemainingTurns != maximumRemainingTurns && maximumRemainingTurns > 0) {
                description = Text.translatable("text.tropimon_ui_battle.effect.remaining_range_tooltip",
                        minimumRemainingTurns, maximumRemainingTurns);
            } else if (maximumRemainingTurns > 0) {
                description = Text.translatable("text.tropimon_ui_battle.effect.remaining_tooltip",
                        maximumRemainingTurns, standardDuration);
            } else {
                description = Text.translatable("text.tropimon_ui_battle.effect.dynamic_duration");
            }
            // Never send a literal newline through DrawContext's single-line Text overload.
            return List.of(title, description);
        }

        Text counter() {
            if (layered) return Text.literal("×" + layers);
            if (maximumRemainingTurns <= 0) return Text.empty();
            if (minimumRemainingTurns != maximumRemainingTurns) {
                return Text.literal(minimumRemainingTurns + "–" + maximumRemainingTurns);
            }
            return Text.literal(Integer.toString(maximumRemainingTurns));
        }

        Text iconCounter() {
            if (id.endsWith("stealthrock") || id.endsWith("stickyweb")) return Text.literal("×1");
            return counter();
        }
    }

    private record ActiveEffect(String labelId, int startedTurn, EffectDuration duration,
                                int layers, boolean layered) {
        ActiveEffect withLabel(String value) {
            return new ActiveEffect(value, startedTurn, duration, layers, layered);
        }
    }

    private record EffectDuration(int standardTurns, int extendedTurns) {
        private static final EffectDuration DYNAMIC = new EffectDuration(0, 0);
    }

    private record InferredSideEffect(int startedTurn, ObservationMode mode) {
    }

    static final class SideEffectMoveActivation {
        private final String key;
        private final int startedTurn;
        private final ObservationMode mode;
        private final ActiveEffect previous;
        private final InferredSideEffect previousInference;
        private final boolean changed;

        private SideEffectMoveActivation(String key, int startedTurn, ObservationMode mode,
                                         ActiveEffect previous, InferredSideEffect previousInference,
                                         boolean changed) {
            this.key = key;
            this.startedTurn = startedTurn;
            this.mode = mode;
            this.previous = previous;
            this.previousInference = previousInference;
            this.changed = changed;
        }

        private static SideEffectMoveActivation unchanged(ObservationMode mode) {
            return new SideEffectMoveActivation("", Integer.MIN_VALUE, mode,
                    null, null, false);
        }

        boolean changed() {
            return changed;
        }
    }

    static final class SideRemovalActivation {
        private final ObservationMode mode;
        private final Map<String, ActiveEffect> removed;
        private final Map<String, InferredSideEffect> removedInferences;

        private SideRemovalActivation(ObservationMode mode, Map<String, ActiveEffect> removed,
                                      Map<String, InferredSideEffect> removedInferences) {
            this.mode = mode;
            this.removed = Map.copyOf(removed);
            this.removedInferences = Map.copyOf(removedInferences);
        }

        private static SideRemovalActivation unchanged(ObservationMode mode) {
            return new SideRemovalActivation(mode, Map.of(), Map.of());
        }

        boolean changed() {
            return !removed.isEmpty();
        }
    }

    private static final class HazardObservations {
        private final int turn;
        private final ObservationMode mode;
        private int explicit;
        private int inferred;
        private int moves;
        private int appliedActivations;

        private HazardObservations(int turn, ObservationMode mode) {
            this.turn = turn;
            this.mode = mode;
        }

        private int activations() {
            return Math.max(explicit, inferred + moves);
        }
    }

    private enum ObservationMode { PARTICIPANT, SPECTATOR }
    private enum HazardSignal { EXPLICIT, INFERRED, MOVE }
}
