package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleUiLayoutTest {
    @Test
    void widensCardsAtCommonGuiSizes() {
        BattleUiLayout layout = BattleUiLayout.calculate(683, 384, 6, 6);
        assertTrue(layout.teamTileWidth() >= 28);
        assertTrue(layout.teamTileHeight() > 27);
    }

    @Test
    void defaultTeamRowsClearTheNativeHudAndItsMovementHandle() {
        int nativeEnvelope = com.cobblemon.mod.common.client.gui.battle.BattleOverlay.HORIZONTAL_INSET
                + com.cobblemon.mod.common.client.gui.battle.BattleOverlay.TILE_WIDTH
                + BattlePokemonHudLayout.HANDLE_SIZE;
        for (int ownSlots = 1; ownSlots <= 6; ownSlots++) {
            for (int opponentSlots = 1; opponentSlots <= 6; opponentSlots++) {
                BattleUiLayout layout = BattleUiLayout.calculate(960, 540, ownSlots, opponentSlots);
                assertEquals(nativeEnvelope + 2, layout.ownTeamX());
                assertEquals(layout.screenWidth() - nativeEnvelope - 2,
                        layout.opponentTeamX() + layout.opponentTeamWidth());
            }
        }
    }

    @Test
    void rowsAndTheirHandlesRemainInsideAndDisjointForEveryRosterSize() {
        for (int[] size : new int[][]{{320, 180}, {341, 171}, {683, 384}, {960, 540}, {1920, 1080}}) {
            for (int ownSlots = 1; ownSlots <= 6; ownSlots++) {
                for (int opponentSlots = 1; opponentSlots <= 6; opponentSlots++) {
                    BattleUiLayout ui = BattleUiLayout.calculate(size[0], size[1], ownSlots, opponentSlots);
                    assertTrue(ui.ownTeamX() >= 0);
                    assertTrue(ui.opponentTeamX() >= 0);
                    assertTrue(ui.ownTeamX() + ui.ownTeamWidth() <= ui.opponentTeamX());
                    assertTrue(ui.opponentTeamX() + ui.opponentTeamWidth() <= ui.screenWidth());

                    BattlePokemonHudLayout movable = new BattlePokemonHudLayout();
                    var own = movable.layout(true, ui.screenWidth(), ui.screenHeight(),
                            ui.ownTeamWidth(), ui.teamTileHeight(), ui.ownTeamX(), ui.teamTop());
                    var opponent = movable.layout(false, ui.screenWidth(), ui.screenHeight(),
                            ui.opponentTeamWidth(), ui.teamTileHeight(), ui.opponentTeamX(), ui.teamTop());
                    assertTrue(own.handleX() >= 0);
                    assertTrue(own.handleX() + BattlePokemonHudLayout.HANDLE_SIZE <= ui.screenWidth());
                    assertTrue(opponent.handleX() >= 0);
                    assertTrue(opponent.handleX() + BattlePokemonHudLayout.HANDLE_SIZE <= ui.screenWidth());
                    assertTrue(own.x() + own.width() <= opponent.x());
                }
            }
        }
    }

    @Test
    void compactsWithoutOverlappingAtLargeGuiScale() {
        BattleUiLayout layout = BattleUiLayout.calculate(341, 171, 6, 6);
        assertTrue(layout.teamTileWidth() >= 16);
        assertTrue(layout.ownTeamX() >= 0);
        assertTrue(layout.ownTeamX() + layout.ownTeamWidth() <= layout.opponentTeamX());
        assertTrue(layout.opponentTeamX() + layout.opponentTeamWidth() <= layout.screenWidth());
    }

    @Test
    void panelsAlwaysRemainInsideScaledScreen() {
        for (int[] size : new int[][]{{320, 180}, {683, 384}, {960, 540}, {1920, 1080}}) {
            BattleUiLayout layout = BattleUiLayout.calculate(size[0], size[1], 6, 6);
            assertTrue(layout.tooltipWidth() + layout.margin() * 2 <= layout.screenWidth());
            assertTrue(layout.historyWidth() + layout.margin() * 2 <= layout.screenWidth());
            assertTrue(layout.historyMaxHeight() + layout.margin() * 2 <= layout.screenHeight());
        }
    }

    @Test
    void globalEffectsUseTheTopCentreGapForBothWildAndPvp() {
        for (int opponentSlots : new int[]{1, 6}) {
            BattleUiLayout layout = BattleUiLayout.calculate(960, 540, 6, opponentSlots);
            assertEquals(18, layout.fieldEffectsY());
            int left = layout.screenWidth() / 2 - layout.fieldEffectsWidth() / 2;
            int right = left + layout.fieldEffectsWidth();
            assertTrue(left > layout.ownTeamX() + layout.ownTeamWidth());
            assertTrue(right < layout.opponentTeamX());
        }
    }

    @Test
    void globalEffectsMoveBelowTeamsWhenTheGuiScaleLeavesNoCentreSpace() {
        for (int[] size : new int[][]{{341, 171}, {683, 384}}) {
            BattleUiLayout layout = BattleUiLayout.calculate(size[0], size[1], 6, 6);
            assertTrue(layout.fieldEffectsY() > layout.teamTop() + layout.teamTileHeight());
            assertTrue(layout.fieldEffectsWidth() <= layout.screenWidth() - layout.margin() * 2);
        }
    }

    @Test
    void sideEffectsSitImmediatelyBelowNativeActivePortraitsAtEveryGuiScale() {
        for (int[] size : new int[][]{{320, 180}, {683, 384}, {960, 540}, {1920, 1080}}) {
            for (int opponents : new int[]{1, 6}) {
                BattleUiLayout layout = BattleUiLayout.calculate(size[0], size[1], 6, opponents);
                assertEquals(52, layout.sideEffectsY(1, 1));
                assertEquals(70, layout.sideEffectsY(2, 1));
                assertEquals(80, layout.sideEffectsY(2, 2));
                assertEquals(100, layout.sideEffectsY(3, 1));
            }
        }
    }

    @Test
    void sideRowsAreMirroredUnderTheActiveCardsAndNeverCrossTheGlobalEffects() {
        for (int[] size : new int[][]{{320, 180}, {341, 171}, {683, 384}, {960, 540}, {1920, 1080}}) {
            BattleUiLayout layout = BattleUiLayout.calculate(size[0], size[1], 6, 6);
            var metrics = BattleEffectPresentation.metrics(size[0], size[1]);
            for (int activeSlots : new int[]{1, 2}) {
                int inset = layout.sideEffectsInset(metrics.tileWidth(), activeSlots > 1);
                int width = layout.sideEffectsWidth(inset);
                int y = layout.sideEffectsY(activeSlots, 1);
                var left = BattleEffectRowLayout.calculate(8, inset, y, width,
                        metrics.tileWidth(), metrics.tileHeight(), metrics.gap(), BattleEffectRowLayout.Alignment.LEFT);
                var right = BattleEffectRowLayout.calculate(8, size[0] - inset, y, width,
                        metrics.tileWidth(), metrics.tileHeight(), metrics.gap(), BattleEffectRowLayout.Alignment.RIGHT);
                for (int i = 0; i < left.size(); i++) {
                    assertEquals(size[0] - left.get(i).x() - left.get(i).width(), right.get(i).x());
                    assertEquals(left.get(i).y(), right.get(i).y());
                    assertTrue(left.get(i).x() >= layout.margin());
                    assertTrue(left.get(i).x() + left.get(i).width()
                            < size[0] / 2 - layout.fieldEffectsWidth() / 2);
                }
                assertTrue(left.getLast().y() > y, "Extra effects wrap below their own card");
            }
        }
    }

    @Test
    void firstEffectIsCentredOnTheNativePokemonPortrait() {
        BattleUiLayout layout = BattleUiLayout.calculate(960, 540, 6, 6);
        var metrics = BattleEffectPresentation.metrics(960, 540);
        assertEquals(31, layout.sideEffectsInset(metrics.tileWidth(), false) + metrics.tileWidth() / 2);
        // Keep the full counter inside the screen even beside compact native portraits.
        assertTrue(layout.sideEffectsInset(metrics.tileWidth(), true) >= layout.margin());
    }
}
