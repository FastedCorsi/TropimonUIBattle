package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleEffectRowLayoutTest {
    @Test
    void centresActualRowWidthInsteadOfTheAvailableWidth() {
        var positions = BattleEffectRowLayout.calculate(3, 480, 18, 200,
                32, 34, 4, BattleEffectRowLayout.Alignment.CENTER);
        assertEquals(428, positions.getFirst().x());
        assertEquals(532, positions.getLast().x() + positions.getLast().width());
        assertTrue(positions.stream().allMatch(position -> position.y() == 18));
    }

    @Test
    void centresEveryWrappedRowIncludingTheShortLastRow() {
        var positions = BattleEffectRowLayout.calculate(5, 480, 18, 110,
                32, 34, 4, BattleEffectRowLayout.Alignment.CENTER);
        assertEquals(428, positions.get(0).x());
        assertEquals(446, positions.get(3).x());
        assertEquals(514, positions.get(4).x() + positions.get(4).width());
        assertEquals(56, positions.get(3).y());
    }

    @Test
    void sideEffectsKeepTheirOwnEdgesAndOrderWhenTheyWrap() {
        var left = BattleEffectRowLayout.calculate(3, 8, 70, 68,
                32, 34, 4, BattleEffectRowLayout.Alignment.LEFT);
        var right = BattleEffectRowLayout.calculate(3, 952, 70, 68,
                32, 34, 4, BattleEffectRowLayout.Alignment.RIGHT);
        assertEquals(8, left.getFirst().x());
        assertEquals(8, left.getLast().x());
        assertEquals(952, right.getFirst().x() + right.getFirst().width());
        assertEquals(952, right.getLast().x() + right.getLast().width());
        assertEquals(left.getLast().y(), right.getLast().y());
    }

    @Test
    void aNarrowAreaStillContainsEveryIcon() {
        var positions = BattleEffectRowLayout.calculate(2, 20, 2, 12,
                32, 34, 4, BattleEffectRowLayout.Alignment.CENTER);
        assertEquals(12, positions.getFirst().width());
        assertEquals(14, positions.getFirst().x());
        assertTrue(positions.getLast().y() > positions.getFirst().y());
        assertTrue(BattleEffectRowLayout.calculate(0, 20, 2, 12,
                32, 34, 4, BattleEffectRowLayout.Alignment.CENTER).isEmpty());
    }

    @Test
    void requiredHeightMatchesTheLastRenderedRow() {
        for (int count = 0; count <= 12; count++) {
            var positions = BattleEffectRowLayout.calculate(count, 8, 70, 68,
                    32, 34, 4, BattleEffectRowLayout.Alignment.LEFT);
            int actual = positions.isEmpty() ? 0
                    : positions.getLast().y() + positions.getLast().height() - 70;
            assertEquals(actual, BattleEffectRowLayout.requiredHeight(count, 68, 32, 34, 4));
        }
    }
}
