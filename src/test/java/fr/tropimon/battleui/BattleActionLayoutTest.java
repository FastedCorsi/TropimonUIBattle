package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleActionLayoutTest {
    @TempDir Path temporary;

    @Test void defaultPreservesTheNativeMenusAndOnlyTheHandleCanStartADrag() {
        var layout = new BattleActionLayout();
        var p = layout.layout(960, 540);
        assertEquals(0, p.dx()); assertEquals(0, p.dy());
        int handle = BattleActionLayout.GENERAL_HANDLE_OFFSET_X;
        assertFalse(layout.begin(p.handleX(handle) - 1, p.handleY() + 2, 0, handle));
        assertFalse(layout.begin(p.handleX(handle) + BattleActionLayout.HANDLE_WIDTH, p.handleY() + 2, 0, handle));
        assertFalse(layout.begin(p.handleX(handle) + 5, p.handleY() + 2, 1, handle));
        assertTrue(layout.begin(p.handleX(handle) + 5, p.handleY() + 2, 0, handle));
        layout.drag(p.handleX(handle) + 5, p.handleY() + 2, 960, 540);
        assertEquals(p, layout.layout(960, 540));
        assertEquals(p.x() + 190, p.handleX(BattleActionLayout.MOVE_HANDLE_OFFSET_X));
        assertTrue(p.handleY() + BattleActionLayout.HANDLE_HEIGHT < p.y() + 20);
    }

    @Test void sharedTranslationKeepsAllMoveAndActionHitboxesAligned() {
        var layout = new BattleActionLayout();
        var p = layout.layout(960, 540);
        int handle = BattleActionLayout.GENERAL_HANDLE_OFFSET_X;
        layout.begin(p.handleX(handle) + 5, p.handleY() + 2, 0, handle);
        layout.drag(p.handleX(handle) + 205, p.handleY() - 98, 960, 540);
        var moved = layout.layout(960, 540);
        assertEquals(200, moved.dx()); assertEquals(-100, moved.dy());
        for (int[] point : new int[][]{{12,455}, {105,455}, {20,456}, {125,485}, {9,518}}) {
            assertEquals(point[0], moved.nativeX(point[0] + moved.dx()));
            assertEquals(point[1], moved.nativeY(point[1] + moved.dy()));
        }
        assertTrue(layout.finish());
        assertFalse(layout.drag(500, 300, 960, 540));
        assertFalse(layout.finish());
    }

    @Test void dragClampsAllNativeControlsAndResizingStopsTheGesture() {
        var layout = new BattleActionLayout();
        var p = layout.layout(960, 540);
        int handle = BattleActionLayout.GENERAL_HANDLE_OFFSET_X;
        layout.begin(p.handleX(handle) + 5, p.handleY() + 2, 0, handle);
        layout.drag(-1000, -1000, 960, 540);
        assertEquals(0, layout.layout(960, 540).x());
        assertEquals(0, layout.layout(960, 540).y());
        layout.drag(5000, 5000, 960, 540);
        for (int[] size : new int[][]{{960,540},{683,384},{320,180}}) {
            var fit = layout.layout(size[0], size[1]);
            assertTrue(fit.x() >= 0 && fit.y() >= 0);
            assertTrue(fit.x() + BattleActionLayout.WIDTH <= size[0]);
            assertTrue(fit.y() + BattleActionLayout.HEIGHT <= size[1]);
        }
        assertFalse(layout.dragging());
    }

    @Test void generalActionRowsCanReachTheBottomWhileMoveControlsStayVisible() {
        var general = new BattleActionLayout();
        var start = general.layout(960, 540, BattleActionLayout.GENERAL_ACTION_HEIGHT);
        int handle = BattleActionLayout.GENERAL_HANDLE_OFFSET_X;
        assertTrue(general.begin(start.handleX(handle) + 5, start.handleY() + 2, 0, handle));
        general.drag(5000, 5000, 960, 540, BattleActionLayout.GENERAL_ACTION_HEIGHT);
        var atBottom = general.layout(960, 540, BattleActionLayout.GENERAL_ACTION_HEIGHT);
        assertEquals(540, atBottom.y() + BattleActionLayout.GENERAL_ACTION_HEIGHT);

        var moveSafe = general.layout(960, 540, BattleActionLayout.HEIGHT);
        assertEquals(540, moveSafe.y() + BattleActionLayout.HEIGHT);
        int extraX = BattleActionLayout.moveTileExtraX(atBottom, moveSafe, BattleActionLayout.MOVE_FIRST_X);
        int extraY = BattleActionLayout.moveTileExtraY(atBottom, moveSafe);
        assertEquals(BattleActionLayout.ACTION_FIRST_X + atBottom.dx(),
                BattleActionLayout.MOVE_FIRST_X + moveSafe.dx() + extraX);
        assertEquals(540 - BattleActionLayout.ACTION_FIRST_Y_FROM_BOTTOM + atBottom.dy(),
                540 - BattleActionLayout.MOVE_FIRST_Y_FROM_BOTTOM + moveSafe.dy() + extraY);
    }

    @Test void nativeActionsCanReachTheRightEdgeWithoutAnOptionalThirdTile() {
        var actions = new BattleActionLayout();
        var start = actions.layout(960, 540, BattleActionLayout.NATIVE_ACTION_WIDTH,
                BattleActionLayout.GENERAL_ACTION_HEIGHT);
        assertTrue(actions.begin(start.handleX(BattleActionLayout.NATIVE_HANDLE_OFFSET_X) + 5,
                start.handleY() + 2, 0, BattleActionLayout.NATIVE_HANDLE_OFFSET_X));
        actions.drag(5000, 5000, 960, 540, BattleActionLayout.NATIVE_ACTION_WIDTH,
                BattleActionLayout.GENERAL_ACTION_HEIGHT);
        var atEdge = actions.layout(960, 540, BattleActionLayout.NATIVE_ACTION_WIDTH,
                BattleActionLayout.GENERAL_ACTION_HEIGHT);
        assertEquals(960, atEdge.x() + BattleActionLayout.NATIVE_ACTION_WIDTH);
        assertEquals(540, atEdge.y() + BattleActionLayout.GENERAL_ACTION_HEIGHT);
    }

    @Test void actionsAndMovesShareOneAnchorAndBackFitsAboveEveryMove() {
        assertEquals(BattleActionLayout.MOVE_WIDTH, BattleActionLayout.sharedContentWidth(false));
        assertEquals(BattleActionLayout.WIDTH, BattleActionLayout.sharedContentWidth(true));
        int backX = BattleActionLayout.NATIVE_MOVE_BACK_OFFSET_X + BattleActionLayout.moveBackExtraX();
        int backY = BattleActionLayout.NATIVE_MOVE_BACK_OFFSET_Y + BattleActionLayout.moveBackExtraY();
        assertEquals(BattleActionLayout.MOVE_BACK_OFFSET_X, backX);
        assertEquals(BattleActionLayout.MOVE_BACK_OFFSET_Y, backY);
        assertTrue(backX + BattleActionLayout.MOVE_BACK_WIDTH + 2
                <= BattleActionLayout.MOVE_HANDLE_OFFSET_X);
        assertTrue(backY + BattleActionLayout.MOVE_BACK_HEIGHT < 19,
                "Back must end before the first move row starts");
    }

    @Test void firstMoveAlwaysOverlapsFightAcrossAnchorsAndPanelWidths() {
        assertEquals(20, BattleActionLayout.MOVE_FIRST_X,
                "Cobblemon creates the first MoveTile at the selection x");
        assertEquals(125, BattleActionLayout.MOVE_SECOND_X,
                "Cobblemon offsets the second MoveTile column to x=125");
        for (boolean optionalCalc : new boolean[]{false, true}) {
            var layout = new BattleActionLayout();
            int gridWidth = optionalCalc ? BattleActionLayout.WIDTH : BattleActionLayout.NATIVE_ACTION_WIDTH;
            var start = layout.layout(960, 540, gridWidth, BattleActionLayout.GENERAL_ACTION_HEIGHT);
            assertTrue(layout.begin(start.handleX(optionalCalc ? BattleActionLayout.GENERAL_HANDLE_OFFSET_X
                    : BattleActionLayout.NATIVE_HANDLE_OFFSET_X) + 2, start.handleY() + 2, 0,
                    optionalCalc ? BattleActionLayout.GENERAL_HANDLE_OFFSET_X
                            : BattleActionLayout.NATIVE_HANDLE_OFFSET_X));
            for (int[] pointer : new int[][]{{-1000,-1000},{480,270},{5000,5000}}) {
                layout.drag(pointer[0], pointer[1], 960, 540, gridWidth,
                        BattleActionLayout.GENERAL_ACTION_HEIGHT);
                var action = layout.layout(960, 540, gridWidth, BattleActionLayout.GENERAL_ACTION_HEIGHT);
                var controls = layout.layout(960, 540, BattleActionLayout.MOVE_WIDTH, BattleActionLayout.HEIGHT);
                int nativeFirstMoveX = BattleActionLayout.MOVE_FIRST_X;
                int moveX = nativeFirstMoveX + controls.dx()
                        + BattleActionLayout.moveTileExtraX(action, controls, nativeFirstMoveX);
                int moveY = 540 - BattleActionLayout.MOVE_FIRST_Y_FROM_BOTTOM + controls.dy()
                        + BattleActionLayout.moveTileExtraY(action, controls);
                assertEquals(BattleActionLayout.ACTION_FIRST_X + action.dx(), moveX);
                assertEquals(540 - BattleActionLayout.ACTION_FIRST_Y_FROM_BOTTOM + action.dy(), moveY);
                assertTrue(controls.x() >= 0 && controls.x() + BattleActionLayout.MOVE_WIDTH <= 960);
                assertTrue(controls.y() >= 0 && controls.y() + BattleActionLayout.HEIGHT <= 540);
                int nativeSecondMoveX = BattleActionLayout.MOVE_SECOND_X;
                int secondMoveX = nativeSecondMoveX + controls.dx()
                        + BattleActionLayout.moveTileExtraX(action, controls, nativeSecondMoveX);
                assertEquals(BattleActionLayout.ACTION_FIRST_X + action.dx()
                        + BattleActionLayout.MOVE_SECOND_X - BattleActionLayout.MOVE_FIRST_X, secondMoveX);
                assertEquals(BattleActionLayout.MOVE_SECOND_X - BattleActionLayout.MOVE_FIRST_X,
                        secondMoveX - moveX, "the native gap between the wider move cards must be retained");
            }
        }
    }

    @Test void positionPersistsAndResetRestoresTheNativeAnchor() throws Exception {
        var layout = new BattleActionLayout();
        var p = layout.layout(960, 540);
        int handle = BattleActionLayout.GENERAL_HANDLE_OFFSET_X;
        layout.begin(p.handleX(handle) + 5, p.handleY() + 2, 0, handle);
        layout.drag(500, 250, 960, 540);
        var expected = layout.layout(960, 540);
        Path file = temporary.resolve("ui-battle/action-position.properties");
        layout.save(file);
        var restored = new BattleActionLayout();
        restored.load(file);
        assertEquals(expected, restored.layout(960, 540));
        restored.reset(); restored.save(file);
        var reset = new BattleActionLayout(); reset.load(file);
        assertEquals(p, reset.layout(960, 540));
        assertTrue(Files.readString(file).contains("By FastedCorsi"));
    }

    @Test void malformedLocalPreferencesNeverProduceOffscreenOrNonfiniteCoordinates() throws Exception {
        Path file = temporary.resolve("invalid.properties");
        for (String value : new String[]{"NaN", "Infinity", "-1", "2", "invalid"}) {
            Files.writeString(file, "customized=true\nanchorX=" + value + "\nanchorY=0.5\n");
            var layout = new BattleActionLayout(); layout.load(file);
            assertEquals(0, layout.layout(960, 540).dx());
        }
    }
}
