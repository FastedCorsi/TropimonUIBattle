package fr.tropimon.battleui;

import java.util.Objects;
import java.util.function.Supplier;

/** Single immutable value cache; keys contain values, never a live mutable Minecraft object. */
final class LastValueCache<K, V> {
    private K key;
    private V value;
    V get(K next, Supplier<V> build) {
        if (value == null || !Objects.equals(key, next)) { value = build.get(); key = next; }
        return value;
    }
    void clear() { key = null; value = null; }
}
