package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleHistoryMenuLayoutTest {
    @Test void dropdownStaysInsideScreenAfterMovingAndResizingTheHistoryWindow() {
        for (int width : new int[]{160,320,640,960}) {
            for (int height : new int[]{90,180,540}) {
                var layout = BattleUiLayout.calculate(width, height, 6, 6);
                for (int top : new int[]{layout.margin(), height / 2, height - layout.margin() - 30}) {
                    var menu = BattleUiRenderer.calculateTurnMenu(layout, Math.min(300, width - 8),
                            top, width - layout.margin(), 200, 500);
                    assertTrue(menu.x() >= layout.margin());
                    assertTrue(menu.y() >= layout.margin());
                    assertTrue(menu.x() + menu.width() <= width - layout.margin());
                    assertTrue(menu.y() + menu.height() <= height - layout.margin());
                    assertTrue(menu.visibleRows() >= 1);
                }
            }
        }
    }

    @Test
    void dropdownStaysInsideScreenAcrossGuiScalesAndBattleLengths() {
        for (int width : new int[]{160, 240, 320, 480, 960, 1920}) {
            for (int height : new int[]{90, 150, 240, 540, 1080}) {
                var layout = BattleUiLayout.calculate(width, height, 6, 6);
                for (int turns : new int[]{0, 1, 3, 8, 25, 500}) {
                    for (int historyHeight : new int[]{Math.min(123, layout.historyMaxHeight()), layout.historyMaxHeight()}) {
                        int top = layout.screenHeight() - historyHeight - layout.margin() + 22;
                        var menu = BattleUiRenderer.calculateTurnMenu(layout, layout.historyWidth(), top,
                                width - layout.margin() - 50, 200, turns);
                        assertTrue(menu.x() >= layout.margin());
                        assertTrue(menu.x() + menu.width() <= width - layout.margin());
                        assertEquals(top, menu.y());
                        assertTrue(menu.y() + menu.height() <= height - layout.margin());
                        assertTrue(menu.visibleRows() <= Math.min(8, turns));
                        assertTrue(turns == 0 || menu.visibleRows() >= 1);
                    }
                }
            }
        }
    }
}
