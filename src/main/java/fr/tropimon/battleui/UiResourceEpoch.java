package fr.tropimon.battleui;

/** Resource/font reload invalidation, independent of battle messages and language selection. */
final class UiResourceEpoch {
    private static volatile long value;
    static long current() { return value; }
    static void invalidate() { value++; }
    private UiResourceEpoch() { }
}
