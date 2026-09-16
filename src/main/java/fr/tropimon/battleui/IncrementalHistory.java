package fr.tropimon.battleui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Append-only wrapping; edits/trimming/reset and layout/resource changes explicitly invalidate it. */
final class IncrementalHistory<E, L> {
    private final ArrayList<L> lines = new ArrayList<>();
    private final List<L> view = Collections.unmodifiableList(lines);
    private final LinkedHashMap<Integer, BattleHistoryNavigation.Turn> turns = new LinkedHashMap<>();
    private List<BattleHistoryNavigation.Turn> turnView = List.of();
    private long epoch = -1;
    private int entries;
    private Object layout;

    int entryCount() { return entries; }
    long epoch() { return epoch; }
    List<L> lines() { return view; }
    List<BattleHistoryNavigation.Turn> turns() { return turnView; }
    boolean layoutMatches(Object next) { return java.util.Objects.equals(layout, next); }

    void update(long nextEpoch, Object nextLayout, int from, List<E> changes,
                Function<E, List<L>> wrap, ToIntFunction<L> turnOf) {
        if (nextEpoch != epoch || !layoutMatches(nextLayout) || from != entries) {
            if (from != 0) throw new IllegalArgumentException("A full snapshot is required after invalidation");
            clear();
        }
        epoch = nextEpoch;
        layout = nextLayout;
        boolean newTurn = false;
        for (E entry : changes) {
            for (L line : wrap.apply(entry)) {
                int number = Math.max(0, turnOf.applyAsInt(line));
                if (!turns.containsKey(number)) {
                    turns.put(number, new BattleHistoryNavigation.Turn(number, lines.size()));
                    newTurn = true;
                }
                lines.add(line);
            }
            entries++;
        }
        if (newTurn) turnView = List.copyOf(turns.values());
    }

    void clear() {
        lines.clear();
        turns.clear();
        turnView = List.of();
        entries = 0;
        epoch = -1;
        layout = null;
    }
}
