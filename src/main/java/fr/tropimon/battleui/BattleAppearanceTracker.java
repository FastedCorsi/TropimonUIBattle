package fr.tropimon.battleui;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** A reversible public observation per field slot, never a species/name-based identity guess. */
final class BattleAppearanceTracker<T> {
    private final Map<Object, Appearance<T>> slots = new IdentityHashMap<>();

    void begin(Object slot, UUID identity, T before) { slots.put(slot, new Appearance<>(identity, before)); }
    boolean observes(Object slot, UUID identity) {
        Appearance<T> appearance = slots.get(slot);
        return appearance != null && appearance.identity().equals(identity);
    }
    void observe(Object slot, UUID identity, Supplier<T> before) {
        Appearance<T> existing = slots.get(slot);
        if (existing == null || !existing.identity().equals(identity)) begin(slot, identity, before.get());
    }
    Appearance<T> reveal(Object slot, UUID apparent, UUID actual) {
        Appearance<T> appearance = slots.get(slot);
        if (appearance == null || apparent.equals(actual) || !appearance.identity().equals(apparent)) return null;
        slots.remove(slot);
        return appearance;
    }
    void clear() { slots.clear(); }
    void leave(Object slot) { slots.remove(slot); }

    record Appearance<T>(UUID identity, T before) { }
}
