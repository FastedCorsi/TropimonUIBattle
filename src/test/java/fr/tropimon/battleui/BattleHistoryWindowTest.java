package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleHistoryWindowTest {
    @TempDir Path temporary;

    @Test void headerDragIsClampedAndDoesNotChangeSizeOrJumpAtMouseDown() {
        var window = new BattleHistoryWindow();
        var before = window.layout(960, 540, 8, 360, 220);
        assertEquals(new BattleHistoryWindow.Rect(592, 312, 360, 220), before);
        assertTrue(window.begin(before.x() + 20, before.y() + 12, 0, 28));
        assertTrue(window.drag(before.x() + 20, before.y() + 12, 960, 540, 8));
        assertEquals(before, window.layout(960, 540, 8, 360, 220));
        window.drag(-500, -500, 960, 540, 8);
        var moved = window.layout(960, 540, 8, 360, 220);
        assertEquals(0, moved.x()); assertEquals(0, moved.y());
        assertEquals(before.width(), moved.width()); assertEquals(before.height(), moved.height());
        assertTrue(window.finish()); assertFalse(window.finish());
        assertFalse(window.drag(500, 300, 960, 540, 8));
    }

    @Test void edgesAndCornersResizeWithoutOverflowAndEnforceMinimumSize() {
        for (int sw : new int[]{160, 320, 640, 960}) {
            for (int sh : new int[]{90, 180, 540}) {
                for (int edge = 0; edge < 8; edge++) {
                    var window = new BattleHistoryWindow();
                    var before = window.layout(sw, sh, 4, Math.min(240, sw - 8), Math.min(130, sh - 8));
                    double x = switch (edge) { case 0, 4, 6 -> before.x() + 1; case 1, 5, 7 -> before.x() + before.width() - 1; default -> before.x() + before.width() / 2; };
                    double y = switch (edge) { case 2, 4, 5 -> before.y() + 1; case 3, 6, 7 -> before.y() + before.height() - 1; default -> before.y() + before.height() / 2; };
                    assertTrue(window.onResizeBorder(x, y));
                    assertTrue(window.begin(x, y, 0, 28));
                    for (int delta : new int[]{-5000, 5000}) {
                        window.drag(x + delta, y + delta, sw, sh, 4);
                        var box = window.layout(sw, sh, 4, 240, 130);
                        assertTrue(box.x() >= 0 && box.y() >= 0);
                        assertTrue(box.x() + box.width() <= sw);
                        assertTrue(box.y() + box.height() <= sh);
                        assertTrue(box.width() >= Math.min(190, sw));
                        assertTrue(box.height() >= Math.min(90, sh));
                    }
                }
            }
        }
    }

    @Test void bodyAndOtherButtonsDoNotStartMovingAndResizeRewrapsTheRealWidth() {
        var window = new BattleHistoryWindow();
        var before = window.layout(960, 540, 8, 300, 200);
        assertFalse(window.begin(before.x() + 30, before.y() + 40, 0, 28));
        assertFalse(window.begin(before.x() + 30, before.y() + 12, 1, 28));
        assertFalse(window.begin(-1, -1, 0, 28));
        assertTrue(window.begin(before.x() + 1, before.y() + 50, 0, 28));
        window.drag(before.x() - 99, before.y() + 50, 960, 540, 8);
        assertEquals(400, window.width(960, 8, 300));
        assertEquals(before.x() + before.width(), window.layout(960, 540, 8, 300, 200).x() + 400);
    }

    @Test void geometryPersistsAcrossSessionsAndRescalesInsideSmallerGui() throws Exception {
        var window = new BattleHistoryWindow();
        var before = window.layout(960, 540, 8, 300, 200);
        window.begin(before.x() + 30, before.y() + 12, 0, 28);
        window.drag(before.x() - 70, before.y() - 38, 960, 540, 8);
        window.finish();
        Path config = temporary.resolve("ui-battle/history-window.properties");
        window.save(config);
        var restored = new BattleHistoryWindow();
        restored.load(config);
        assertEquals(window.layout(960, 540, 8, 300, 200), restored.layout(960, 540, 8, 300, 200));
        for (int[] viewport : new int[][]{{320,180},{640,360},{1920,1080},{160,90}}) {
            var box = restored.layout(viewport[0], viewport[1], 4, 300, 200);
            assertTrue(box.x() >= 0 && box.y() >= 0);
            assertTrue(box.x() + box.width() <= viewport[0]);
            assertTrue(box.y() + box.height() <= viewport[1]);
        }
        restored.reset(); restored.save(config);
        var reset = new BattleHistoryWindow(); reset.load(config);
        assertEquals(before, reset.layout(960, 540, 8, 300, 200));
    }

    @Test void invalidSettingsFallBackAndViewportChangeEndsAnActiveDrag() throws Exception {
        Path config = temporary.resolve("history-window.properties");
        Files.writeString(config, "customized=true\nanchorX=NaN\nanchorY=0\nwidthRatio=0.4\nheightRatio=0.3");
        var window = new BattleHistoryWindow(); window.load(config);
        var before = window.layout(960, 540, 8, 300, 200);
        assertEquals(652, before.x());
        window.begin(before.x() + 20, before.y() + 12, 0, 28);
        window.layout(320, 180, 4, 220, 120);
        assertFalse(window.interacting());
    }
}
