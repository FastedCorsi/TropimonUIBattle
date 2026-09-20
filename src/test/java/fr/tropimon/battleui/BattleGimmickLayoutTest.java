package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleGimmickLayoutTest {
    @Test void allSpecialControlsHaveSeparateHitboxesAboveTheAttacks() {
        for (int height : new int[]{180, 360, 540}) {
            for (int count = 1; count <= 8; count++) {
                float previousEnd = 0;
                for (int index = 0; index < count; index++) {
                    var c = BattleActionLayout.gimmick(index, count, height);
                    assertTrue(c.x() >= previousEnd);
                    previousEnd = c.x() + 18 * c.scale();
                    assertTrue(previousEnd < BattleActionLayout.shift(height).x());
                    assertTrue(c.y() + 17 * c.scale() < height - 85);
                    assertEquals(90, c.nativeX(c.x() + 9 * c.scale(), 81), .001);
                    assertEquals(58, c.nativeY(c.y() + 8 * c.scale(), 50), .001);
                }
                var shift = BattleActionLayout.shift(height);
                assertTrue(shift.x() + 36 < 9 + BattleActionLayout.MOVE_BACK_OFFSET_X);
            }
        }
    }
}
