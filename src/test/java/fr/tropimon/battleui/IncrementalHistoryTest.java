package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class IncrementalHistoryTest {
    record Entry(int turn, int lines) { }
    record Line(int turn, int line) { }
    @Test void wrapsOnlyAppendedEntriesAndKeepsTurnNavigationPaused() {
        var journal = new RevisionedJournal<Entry>(25_000);
        var cache = new IncrementalHistory<Entry, Line>();
        var calls = new AtomicInteger();
        for (int i=0; i<10_000; i++) journal.add(new Entry(i/10, 2));
        update(journal, cache, "width:200/fr", calls);
        assertEquals(10_000,calls.get());
        var nav=new BattleHistoryNavigation();
        nav.update(cache.turns(),cache.lines().size(),10);
        nav.jumpToTurn(250);
        int oldStart=nav.startLine();
        var firstLine=cache.lines().getFirst();
        for (int i=0;i<50;i++) { journal.add(new Entry(1000+i,3)); update(journal,cache,"width:200/fr",calls); }
        assertEquals(10_050,calls.get());
        assertSame(firstLine,cache.lines().getFirst());
        nav.update(cache.turns(),cache.lines().size(),10);
        assertEquals(oldStart,nav.startLine());
        assertFalse(nav.followingLatest());
        assertEquals(20_000,cache.turns().get(1000).firstLine());
        update(journal,cache,"width:200/fr",calls);
        assertEquals(10_050,calls.get());
    }
    @Test void rebuildsOnResizeLanguageReloadResetEditsAndTrimming() {
        var journal=new RevisionedJournal<Entry>(3);
        var cache=new IncrementalHistory<Entry,Line>();
        var calls=new AtomicInteger();
        journal.add(new Entry(1,1)); journal.add(new Entry(2,2));
        update(journal,cache,"200/fr/0",calls);
        for(String key:List.of("100/fr/0","100/en/0","100/en/1")) {
            int before=calls.get(); update(journal,cache,key,calls); assertEquals(before+2,calls.get());
        }
        journal.replace(0,new Entry(1,4)); update(journal,cache,"100/en/1",calls);
        assertEquals(6,cache.lines().size()); assertEquals(4,cache.turns().get(1).firstLine());
        journal.add(new Entry(3,1)); journal.add(new Entry(4,1));
        update(journal,cache,"100/en/1",calls);
        assertEquals(2,cache.turns().getFirst().number());
        journal.clear(); journal.add(new Entry(0,1)); update(journal,cache,"100/en/1",calls);
        assertEquals(1,cache.lines().size()); assertEquals(0,cache.turns().getFirst().number());
    }

    @Test void largeJournalDoesNotRewrapEverythingForEveryNewMessageAfterItsLimit() {
        var journal = new RevisionedJournal<Entry>(25_000);
        var cache = new IncrementalHistory<Entry, Line>();
        var calls = new AtomicInteger();
        for (int i = 0; i < 25_000; i++) journal.add(new Entry(i, 1));
        update(journal, cache, "large", calls);
        assertEquals(25_000, calls.get());

        for (int i = 0; i < 1_024; i++) {
            journal.add(new Entry(25_000 + i, 1));
            update(journal, cache, "large", calls);
        }
        int afterBatchedTrim = calls.get();
        assertEquals(25_000, cache.entryCount());
        for (int i = 1; i <= 100; i++) {
            journal.add(new Entry(26_023 + i, 1));
            update(journal, cache, "large", calls);
        }
        assertEquals(afterBatchedTrim + 100, calls.get(),
                "only the periodic batched trim may rebuild the visible history");
    }
    private static void update(RevisionedJournal<Entry> journal, IncrementalHistory<Entry,Line> cache,
                               String layout, AtomicInteger calls) {
        var delta=journal.since(cache.epoch(),cache.entryCount(),!cache.layoutMatches(layout));
        cache.update(delta.epoch(),layout,delta.from(),delta.entries(),entry->{
            calls.incrementAndGet();
            var lines=new ArrayList<Line>();
            for(int i=0;i<entry.lines;i++) lines.add(new Line(entry.turn,i));
            return lines;
        },Line::turn);
    }
}
