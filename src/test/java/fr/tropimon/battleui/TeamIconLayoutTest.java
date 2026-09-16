package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeamIconLayoutTest {
    @Test void largerPortraitsStayCentredWithSeparateHealthAndStatusRows() {
        for (int width : new int[]{320, 427, 480, 640, 854, 960, 1366, 1920}) {
            for (int height : new int[]{180, 240, 480, 720, 1080}) {
                var layout = BattleUiLayout.calculate(width, height, 6, 6);
                for (boolean opponent : new boolean[]{false, true}) {
                    for (int slot = 0; slot < 6; slot++) {
                        int x = layout.teamSlotX(slot, 6, opponent);
                        var icon = TeamIconLayout.of(x, layout.teamTop(), layout.teamTileWidth(),
                                layout.teamTileHeight(), layout.teamPortraitSize());
                        int tileWidth = layout.teamTileWidth();
                        assertTrue(icon.portraitSize() > Math.floor(tileWidth * 0.80F));
                        assertTrue(icon.portraitX() > x);
                        assertEquals(layout.teamTop(), icon.portraitY());
                        assertEquals(tileWidth, 2 * (icon.portraitX() - x) + icon.portraitSize());
                        assertTrue(icon.portraitX() + icon.portraitSize() < x + tileWidth);
                        assertTrue(icon.portraitY() + icon.portraitSize() < icon.hpY());
                        assertTrue(icon.hpY() <= layout.teamTop() + tileWidth - 1, "HP and status move up with the compact model row");
                        assertTrue(icon.hpY() + icon.hpHeight() < icon.statusY());
                        assertTrue(icon.statusHeight() >= 9);
                        assertEquals(layout.teamTop() + layout.teamTileHeight(), icon.statusY() + icon.statusHeight());
                    }
                }
            }
        }
    }

    @Test void opponentsRunRightToLeftWhilePlayerAndSlotIdentityStayUnchanged() {
        for (int width : new int[]{320, 427, 640, 960, 1920}) {
            for (int opponentSlots = 1; opponentSlots <= 6; opponentSlots++) {
                var layout = BattleUiLayout.calculate(width, 540, 6, opponentSlots);
                int stride = layout.teamTileWidth() + layout.teamTileGap();
                for (int slot = 0; slot < opponentSlots; slot++) {
                    int x = layout.teamSlotX(slot, opponentSlots, true);
                    assertEquals(layout.opponentTeamX() + layout.opponentTeamWidth()
                            - layout.teamTileWidth() - slot * stride, x);
                    assertTrue(x >= layout.opponentTeamX());
                    assertTrue(x + layout.teamTileWidth() <= layout.screenWidth());
                    // Rendering and hover share this slot's bounds; unknown slots extend to its left.
                    if (slot > 0) assertEquals(stride, layout.teamSlotX(slot - 1, opponentSlots, true) - x);
                }
                for (int slot = 0; slot < 6; slot++)
                    assertEquals(layout.ownTeamX() + slot * stride, layout.teamSlotX(slot, 6, false));
            }
        }
    }
}
