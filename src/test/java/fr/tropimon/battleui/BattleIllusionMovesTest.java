package fr.tropimon.battleui;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BattleIllusionMovesTest {
    private static MoveView move() { return new MoveView("surf", Text.literal("Surf"), Text.empty(), "water", 16, 16, 0, true); }

    @Test void onlyNewUsesTransferAndExistingRealPpArePreserved() {
        var before = move().spendPp(5);
        var during = before.spendPp(2);
        var real = move().spendPp(1);
        var transferred = BattleIllusionMoves.transfer(Map.of("surf", before), Map.of("surf", during), Map.of("surf", real)).get("surf");
        assertEquals(3, transferred.ppUsed());
        assertEquals(13, transferred.currentPp());
        assertEquals(5, before.ppUsed());
        assertEquals(1, real.ppUsed());
    }

    @Test void oldDecoyMovesAreNotRevealedAsZoroarksMovesAndCalledMovesCostNoPp() {
        var before = move().spendPp(3);
        assertTrue(BattleIllusionMoves.transfer(Map.of("surf", before), Map.of("surf", before), Map.of()).isEmpty());
        var called = BattleIllusionMoves.transfer(Map.of(), Map.of("surf", move().asCalled()), Map.of()).get("surf");
        assertEquals(MoveOrigin.CALLED, called.origin());
        assertEquals(0, called.ppUsed());
    }

    @Test void ppRestorationAndGrudgeRemainAttachedToTheRevealedPokemon() {
        var before = move().spendPp(8);
        var restored = before.spendPp(1).restorePp(4);
        var real = move().spendPp(6);
        var result = BattleIllusionMoves.transfer(Map.of("surf", before), Map.of("surf", restored), Map.of("surf", real)).get("surf");
        assertEquals(13, result.currentPp());
        assertEquals(7, result.ppUsed());
        var grudge = BattleIllusionMoves.transfer(Map.of("surf", before), Map.of("surf", before.exhaustPp()),
                Map.of("surf", real)).get("surf");
        assertEquals(0, grudge.currentPp());
    }
}
