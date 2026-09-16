package fr.tropimon.battleui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Public battle-message fallback for clients whose live stat map is temporarily empty. */
final class BattleStatStageTracker {
    private final Map<UUID, LinkedHashMap<String, Integer>> values = new java.util.HashMap<>();
    /**
     * Cobblemon exposes stat changes as a snapshot when an active Pokemon is
     * initialized/replaced, but battle messages do not mutate that client map.
     * Remember even an empty baseline so a later stale snapshot cannot overwrite
     * public changes already observed from messages.
     */
    private final java.util.Set<UUID> initialized = new java.util.HashSet<>();

    void initializeIfAbsent(UUID pokemon, Map<String, Integer> stages) {
        if (pokemon == null || !initialized.add(pokemon)) return;
        LinkedHashMap<String, Integer> sanitized = sanitize(stages);
        if (!sanitized.isEmpty()) values.put(pokemon, sanitized);
    }

    void change(UUID pokemon, String stat, int amount) {
        if (pokemon == null || stat == null || stat.isBlank() || amount == 0) return;
        initialized.add(pokemon);
        var stages = values.computeIfAbsent(pokemon, ignored -> new LinkedHashMap<>());
        int next = Math.clamp(stages.getOrDefault(stat, 0) + amount, -6, 6);
        if (next == 0) stages.remove(stat); else stages.put(stat, next);
        if (stages.isEmpty()) values.remove(pokemon);
    }

    void set(UUID pokemon, String stat, int value) {
        if (pokemon == null || stat == null || stat.isBlank()) return;
        initialized.add(pokemon);
        var stages = values.computeIfAbsent(pokemon, ignored -> new LinkedHashMap<>());
        set(stages, stat, Math.clamp(value, -6, 6));
        if (stages.isEmpty()) values.remove(pokemon);
    }

    void clear(UUID pokemon) {
        if (pokemon != null) {
            initialized.add(pokemon);
            values.remove(pokemon);
        }
    }

    /** Clear every observed value without reopening already-consumed native baselines. */
    void clearAll() {
        values.clear();
    }

    /** Forget an inactive appearance so its next native snapshot becomes the new baseline. */
    void forget(UUID pokemon) {
        if (pokemon == null) return;
        initialized.remove(pokemon);
        values.remove(pokemon);
    }

    void clearNegative(UUID pokemon) {
        if (pokemon != null) initialized.add(pokemon);
        var stages = values.get(pokemon);
        if (stages == null) return;
        stages.entrySet().removeIf(entry -> entry.getValue() < 0);
        if (stages.isEmpty()) values.remove(pokemon);
    }

    void invert(UUID pokemon) {
        if (pokemon != null) initialized.add(pokemon);
        var stages = values.get(pokemon);
        if (stages != null) stages.replaceAll((stat, stage) -> -stage);
    }

    void copy(UUID target, UUID source) {
        if (target == null) return;
        initialized.add(target);
        var sourceStages = values.get(source);
        if (sourceStages == null || sourceStages.isEmpty()) values.remove(target);
        else values.put(target, new LinkedHashMap<>(sourceStages));
    }

    void swap(UUID first, UUID second, java.util.Set<String> stats) {
        if (first == null || second == null) return;
        initialized.add(first);
        initialized.add(second);
        var firstStages = new LinkedHashMap<>(values.getOrDefault(first, new LinkedHashMap<>()));
        var secondStages = new LinkedHashMap<>(values.getOrDefault(second, new LinkedHashMap<>()));
        for (String stat : stats) {
            int left = firstStages.getOrDefault(stat, 0);
            int right = secondStages.getOrDefault(stat, 0);
            set(firstStages, stat, right);
            set(secondStages, stat, left);
        }
        store(first, firstStages);
        store(second, secondStages);
    }

    void transfer(UUID from, UUID to) {
        if (to == null) return;
        boolean sourceInitialized = initialized.remove(from);
        if (sourceInitialized) initialized.add(to);
        var stages = values.remove(from);
        if (stages == null || stages.isEmpty()) values.remove(to);
        else values.put(to, new LinkedHashMap<>(stages));
    }

    void replace(UUID pokemon, Map<String, Integer> stages) {
        if (pokemon == null) return;
        initialized.add(pokemon);
        LinkedHashMap<String, Integer> sanitized = sanitize(stages);
        store(pokemon, sanitized);
    }

    Map<String, Integer> stages(UUID pokemon) {
        var stages = values.get(pokemon);
        return stages == null ? Map.of() : Map.copyOf(stages);
    }

    void reset() {
        values.clear();
        initialized.clear();
    }

    private static LinkedHashMap<String, Integer> sanitize(Map<String, Integer> stages) {
        LinkedHashMap<String, Integer> sanitized = new LinkedHashMap<>();
        if (stages != null) stages.forEach((stat, value) -> {
            if (stat != null && !stat.isBlank() && value != null) {
                set(sanitized, stat, Math.clamp(value, -6, 6));
            }
        });
        return sanitized;
    }

    private static void set(Map<String, Integer> stages, String stat, int value) {
        if (value == 0) stages.remove(stat); else stages.put(stat, value);
    }

    private void store(UUID pokemon, LinkedHashMap<String, Integer> stages) {
        if (stages.isEmpty()) values.remove(pokemon); else values.put(pokemon, stages);
    }
}
