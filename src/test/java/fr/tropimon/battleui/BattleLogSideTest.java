package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BattleLogSideTest {
    private static final List<String> OWN = List.of("FastedCorsi", "Cloyster");
    private static final List<String> FOE = List.of("tempete1", "Weezing-Galar");

    @Test void opponentMoveTargetingThePlayerIsRedNotAMentionHighlight() {
        assertEquals(BattleLogSide.OPPONENT, BattleLogSide.resolve("tempete1's Weezing-Galar",
                "tempete1's Weezing-Galar used Toxic on FastedCorsi's Cloyster!", OWN, FOE));
        assertEquals(BattleLogSide.PLAYER, BattleLogSide.resolve("FastedCorsi's Cloyster",
                "FastedCorsi's Cloyster used Surf on tempete1's Weezing-Galar!", OWN, FOE));
    }

    @Test void firstParticipantWinsWhenThereIsNoStructuredSubject() {
        assertEquals(BattleLogSide.OPPONENT, BattleLogSide.resolve("", "Weezing-Galar used Toxic on Cloyster!", OWN, FOE));
        assertEquals(BattleLogSide.PLAYER, BattleLogSide.resolve("", "Cloyster used Surf on Weezing-Galar!", OWN, FOE));
        assertEquals(BattleLogSide.NEUTRAL, BattleLogSide.resolve("", "Rain continues to fall.", OWN, FOE));
        assertEquals(BattleLogSide.NEUTRAL, BattleLogSide.resolve("", "tempete123", OWN, FOE));
    }

    @Test void explicitOpposingOrWildSubjectDoesNotTurnBlueOnMirrorMatches() {
        assertEquals(BattleLogSide.OPPONENT, BattleLogSide.resolve("The opposing Cloyster", "Cloyster used Surf", OWN, FOE));
        assertEquals(BattleLogSide.OPPONENT, BattleLogSide.resolve("Wild Cloyster", "", OWN, FOE));
        assertEquals(BattleLogSide.OPPONENT, BattleLogSide.resolve("Cloyster adverse", "", List.of("FastedCorsi"), FOE));
        assertNotEquals(BattleLogSide.PLAYER.background(), BattleLogSide.OPPONENT.background());
    }
}
