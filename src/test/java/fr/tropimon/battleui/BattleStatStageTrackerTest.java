package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BattleStatStageTrackerTest {
    @Test void clampsClearsAndInvertsPublicStages() {
        var tracker = new BattleStatStageTracker();
        UUID pokemon = UUID.randomUUID();
        tracker.change(pokemon, "atk", 2);
        tracker.change(pokemon, "atk", 8);
        tracker.change(pokemon, "def", -1);
        assertEquals(6, tracker.stages(pokemon).get("atk"));
        tracker.clearNegative(pokemon);
        assertFalse(tracker.stages(pokemon).containsKey("def"));
        tracker.invert(pokemon);
        assertEquals(-6, tracker.stages(pokemon).get("atk"));
        tracker.clear(pokemon);
        assertTrue(tracker.stages(pokemon).isEmpty());
    }

    @Test void copiesSwapsAndTransfersWithoutSharingMutableState() {
        var tracker = new BattleStatStageTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), incoming = UUID.randomUUID();
        tracker.change(first, "atk", 2);
        tracker.change(second, "def", -1);
        tracker.copy(second, first);
        tracker.change(first, "atk", 1);
        assertEquals(2, tracker.stages(second).get("atk"));
        tracker.swap(first, second, Set.of("atk"));
        assertEquals(2, tracker.stages(first).get("atk"));
        assertEquals(3, tracker.stages(second).get("atk"));
        tracker.transfer(second, incoming);
        assertTrue(tracker.stages(second).isEmpty());
        assertEquals(3, tracker.stages(incoming).get("atk"));
    }

    @Test void settingAnExactStageDoesNotDependOnThePreviousValue() {
        var tracker = new BattleStatStageTracker();
        UUID pokemon = UUID.randomUUID();
        tracker.change(pokemon, "atk", -2);
        tracker.set(pokemon, "atk", 6);
        assertEquals(6, tracker.stages(pokemon).get("atk"));
        tracker.set(pokemon, "atk", 0);
        assertFalse(tracker.stages(pokemon).containsKey("atk"));
    }

    @Test void nativeBaselineIsReadOnceAndPublicMessagesThenOwnTheAbsoluteState() {
        var tracker = new BattleStatStageTracker();
        UUID pokemon = UUID.randomUUID();
        tracker.initializeIfAbsent(pokemon, Map.of("atk", 1));
        tracker.change(pokemon, "atk", 1);
        tracker.initializeIfAbsent(pokemon, Map.of("atk", 5));
        assertEquals(2, tracker.stages(pokemon).get("atk"));

        tracker.clear(pokemon);
        tracker.initializeIfAbsent(pokemon, Map.of("atk", 4));
        assertTrue(tracker.stages(pokemon).isEmpty(), "a stale snapshot must not undo Clear Smog/Haze");

        tracker.forget(pokemon);
        tracker.initializeIfAbsent(pokemon, Map.of("atk", 4));
        assertEquals(4, tracker.stages(pokemon).get("atk"), "a new appearance accepts a new baseline");
    }

    @Test void clearAllKeepsConsumedNativeBaselinesWhileBattleResetForgetsThem() {
        var tracker = new BattleStatStageTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.initializeIfAbsent(first, Map.of("atk", 2));
        tracker.initializeIfAbsent(second, Map.of("spe", -1));

        tracker.clearAll();
        tracker.initializeIfAbsent(first, Map.of("atk", 2));
        tracker.initializeIfAbsent(second, Map.of("spe", -1));
        assertTrue(tracker.stages(first).isEmpty());
        assertTrue(tracker.stages(second).isEmpty());

        tracker.reset();
        tracker.initializeIfAbsent(first, Map.of("atk", 2));
        assertEquals(2, tracker.stages(first).get("atk"));
    }
}
