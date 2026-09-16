package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OpponentPpRulesTest {
    @Test void calledMovesAndInstructUseTheCorrectPpPool() {
        for (String move : new String[]{"Metronome", "Sleep Talk", "Copycat", "Assist", "Mirror Move",
                "Nature Power", "Me First"}) assertTrue(OpponentPpRules.callsAnotherMove(move), move);
        assertTrue(OpponentPpRules.repeatsWithoutPp("cobblemon.battle.singleturn.instruct"));
        assertFalse(OpponentPpRules.callsAnotherMove("Mimic"), "Mimic owns its replacement PP pool");
    }
}
