package fr.tropimon.battleui;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveViewPpTest {
    @Test
    void tracksOpponentUsesRestorationAndForcedDepletion() {
        MoveView move = new MoveView("stoneedge", Text.literal("Stone Edge"), Text.empty(),
                "rock", 8, 8, 0, true);

        move = move.spendPp(1).spendPp(2);
        assertEquals(5, move.currentPp());
        assertEquals(3, move.ppUsed());
        assertTrue(move.ppEstimated());

        move = move.restorePp(10);
        assertEquals(8, move.currentPp());
        assertEquals(3, move.ppUsed());

        move = move.exhaustPp();
        assertEquals(0, move.currentPp());
        assertEquals(3, move.ppUsed());
    }

    @Test
    void estimatedOpponentPpKeepsBaseAndPpUpBoundsThroughPressureSpiteAndRestoration() {
        MoveView move = MoveView.estimatedRange("recover", Text.literal("Recover"), Text.empty(),
                "normal", 10, 16);
        assertEquals(10, move.minCurrentPp());
        assertEquals(16, move.currentPp());
        assertTrue(move.hasPpRange());

        move = move.spendPp(2); // Pressure
        assertEquals(8, move.minCurrentPp());
        assertEquals(14, move.currentPp());

        move = move.spendPp(4); // Spite
        assertEquals(4, move.minCurrentPp());
        assertEquals(10, move.currentPp());
        assertEquals(10, move.minMaxPp());
        assertEquals(16, move.maxPp());

        move = move.restorePp(10); // Leppa Berry caps each possible set at its own maximum.
        assertEquals(10, move.minCurrentPp());
        assertEquals(16, move.currentPp());
        move = move.exhaustPp();
        assertEquals(0, move.minCurrentPp());
        assertEquals(0, move.currentPp());
        assertTrue(move.hasPpRange(), "the unknown PP-Up maximum remains visible after Grudge");

        move = move.restorePpFromEmpty(10); // A Leppa Berry proves the move was at zero.
        assertEquals(10, move.minCurrentPp());
        assertEquals(10, move.currentPp());
        assertEquals(16, move.maxPp());
    }

    @Test
    void distinguishesCalledCopiedAndTransformedMovePp() {
        MoveView base = new MoveView("psychic", Text.literal("Psychic"), Text.empty(),
                "psychic", 16, 16, 0, true);

        MoveView called = base.asCalled();
        assertEquals(MoveOrigin.CALLED, called.origin());
        assertEquals(-1, called.currentPp());

        MoveView copied = base.asCopied(5).spendPp(1);
        assertEquals(MoveOrigin.COPIED, copied.origin());
        assertEquals(4, copied.currentPp());
        assertFalse(copied.hasPpRange());

        MoveView transformed = base.asTransformed().spendPp(2);
        assertEquals(MoveOrigin.TRANSFORMED, transformed.origin());
        assertEquals(3, transformed.currentPp());
        assertFalse(transformed.hasPpRange());
    }
}
