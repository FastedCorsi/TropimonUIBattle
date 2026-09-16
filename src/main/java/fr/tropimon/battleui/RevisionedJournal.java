package fr.tropimon.battleui;

import java.util.ArrayList;
import java.util.List;

/** Owns all writes, so an append can be distinguished from a mutation of earlier entries in O(1). */
final class RevisionedJournal<E> {
    private static final int MAX_TRIM_BATCH = 1_024;
    private final ArrayList<E> entries = new ArrayList<>();
    private final int limit;
    private final int trimBatch;
    private final int trimThreshold;
    private long revision;
    private long epoch;

    RevisionedJournal(int limit) {
        this.limit = Math.max(1, limit);
        this.trimBatch = Math.max(1, Math.min(MAX_TRIM_BATCH, this.limit / 16));
        this.trimThreshold = (int) Math.min(Integer.MAX_VALUE,
                (long) this.limit + this.trimBatch - 1L);
    }
    synchronized void add(E entry) {
        entries.add(entry);
        if (entries.size() > trimThreshold) {
            int removed = Math.min(trimBatch, entries.size());
            entries.subList(0, removed).clear();
            epoch++;
        }
        revision++;
    }
    synchronized void replace(int index, E entry) { entries.set(index, entry); epoch++; revision++; }
    synchronized void clear() { entries.clear(); epoch++; revision++; }
    synchronized long revision() { return revision; }
    synchronized List<E> snapshot() { return List.copyOf(entries); }
    synchronized Delta<E> since(long previousEpoch, int count, boolean force) {
        int from = force || previousEpoch != epoch || count < 0 || count > entries.size() ? 0 : count;
        return new Delta<>(epoch, revision, from, List.copyOf(entries.subList(from, entries.size())));
    }
    record Delta<E>(long epoch, long revision, int from, List<E> entries) { }
}
