package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BattleAppearanceTrackerTest {
    @Test void preservesPreAppearanceHealthUntilTheAuthoritativeIdentityReplacement() {
        var tracker = new BattleAppearanceTracker<Float>();
        Object slot = new Object();
        UUID apparent = UUID.randomUUID();
        tracker.begin(slot, apparent, 83.5F);
        tracker.observe(slot, apparent, () -> 24F);
        assertTrue(tracker.observes(slot, apparent), "a real switch can preserve outgoing animated HP");
        var revealed = tracker.reveal(slot, apparent, UUID.randomUUID());
        assertFalse(tracker.observes(slot, apparent), "the slot setter must not damage a restored Illusion decoy");
        assertEquals(83.5F, revealed.before());
        assertNull(tracker.reveal(slot, apparent, UUID.randomUUID()));
    }

    @Test void switchStartsANewSnapshotAndSameIdentityIsNotAnIllusionReveal() {
        var tracker = new BattleAppearanceTracker<Float>();
        Object slot = new Object();
        UUID apparent = UUID.randomUUID();
        tracker.begin(slot, apparent, 100F);
        tracker.begin(slot, apparent, 48F);
        assertNull(tracker.reveal(slot, apparent, apparent));
        assertEquals(48F, tracker.reveal(slot, apparent, UUID.randomUUID()).before());
    }

    @Test void doubleBattleSlotsAndNewBattlesDoNotShareAppearanceState() {
        var tracker = new BattleAppearanceTracker<Float>();
        Object left = new Object(), right = new Object();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.begin(left, first, 80F);
        tracker.begin(right, second, 20F);
        assertNull(tracker.reveal(left, second, UUID.randomUUID()));
        assertEquals(20F, tracker.reveal(right, second, UUID.randomUUID()).before());
        tracker.clear();
        assertNull(tracker.reveal(left, first, UUID.randomUUID()));
    }

    @Test void tripleSimultaneousReplacementsEmptySlotsAndFormRevealsStayIndependent() {
        var tracker = new BattleAppearanceTracker<String>();
        Object left = new Object(), centre = new Object(), right = new Object();
        UUID leftOld = UUID.randomUUID(), centreOld = UUID.randomUUID(), rightOld = UUID.randomUUID();
        tracker.begin(left, leftOld, "left-form");
        tracker.begin(centre, centreOld, "centre-form");
        tracker.begin(right, rightOld, "right-form");
        tracker.leave(centre); // K.O. left an empty native active slot.
        tracker.begin(left, UUID.randomUUID(), "left-replacement");
        tracker.begin(right, UUID.randomUUID(), "right-replacement");
        assertNull(tracker.reveal(centre, centreOld, UUID.randomUUID()));
        UUID apparent = UUID.randomUUID();
        tracker.begin(centre, apparent, "new-centre-form");
        assertEquals("new-centre-form", tracker.reveal(centre, apparent, UUID.randomUUID()).before());
        assertFalse(tracker.observes(left, leftOld));
        assertFalse(tracker.observes(right, rightOld));
    }
}
