package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BattlePokemonHudLayoutTest {
    @TempDir Path temporary;

    @Test void nativeDefaultsRemainUnchanged() {
        assertEquals(1, BattlePokemonHudPanel.DRAW_TILE_X_ARGUMENT);
        assertEquals(2, BattlePokemonHudPanel.DRAW_TILE_Y_ARGUMENT);
        var layout = new BattlePokemonHudLayout();
        assertEquals(new BattlePokemonHudLayout.Placement(12, 10, 140, 40, true),
                layout.layout(true, 960, 540, 140, 40));
        assertEquals(new BattlePokemonHudLayout.Placement(808, 10, 140, 40, false),
                layout.layout(false, 960, 540, 140, 40));
    }

    @Test void eachSideMovesIndependentlyToEveryScreenEdge() {
        var layout = new BattlePokemonHudLayout();
        var own = layout.layout(true, 960, 540, 140, 100);
        assertTrue(layout.begin(true, own, own.handleX() + 2, own.handleY() + 2, 0));
        assertTrue(layout.drag(-1000, -1000, 960, 540, 140, 100));
        assertEquals(new BattlePokemonHudLayout.Placement(0, 0, 140, 100, true),
                layout.layout(true, 960, 540, 140, 100));
        assertEquals(808, layout.layout(false, 960, 540, 140, 100).x());
        layout.drag(5000, 5000, 960, 540, 140, 100);
        assertEquals(new BattlePokemonHudLayout.Placement(806, 440, 140, 100, true),
                layout.layout(true, 960, 540, 140, 100));
        assertTrue(layout.finish());

        var opponent = layout.layout(false, 960, 540, 140, 100);
        assertTrue(layout.begin(false, opponent, opponent.handleX() + 2, opponent.handleY() + 2, 0));
        layout.drag(-1000, 5000, 960, 540, 140, 100);
        assertEquals(new BattlePokemonHudLayout.Placement(14, 440, 140, 100, false),
                layout.layout(false, 960, 540, 140, 100));
    }

    @Test void handlesSitOutsideTheRequestedTopCornersWithoutCoveringCobblemon() {
        var layout = new BattlePokemonHudLayout();
        var own = layout.layout(true, 960, 540, 140, 40);
        var opponent = layout.layout(false, 960, 540, 140, 40);
        assertEquals(own.x() + own.width(), own.handleX());
        assertEquals(opponent.x() - BattlePokemonHudLayout.HANDLE_SIZE, opponent.handleX());
        assertTrue(own.handleX() >= own.x() + own.width());
        assertTrue(opponent.handleX() + BattlePokemonHudLayout.HANDLE_SIZE <= opponent.x());
    }

    @Test void trainerAndEffectInsetsRemainInsideEveryScreenEdge() {
        var layout = new BattlePokemonHudLayout();
        var own = layout.layout(true, 320, 180, 140, 70, 12, 10, 10, 38);
        assertEquals(10, own.y());
        assertTrue(layout.begin(true, own, own.handleX() + 2, own.handleY() + 2, 0));
        layout.drag(-1_000, -1_000, 320, 180, 140, 70, 10, 38);
        var top = layout.layout(true, 320, 180, 140, 70, 12, 10, 10, 38);
        assertEquals(10, top.y(), "the trainer label must stay visible above the card");
        layout.drag(10_000, 10_000, 320, 180, 140, 70, 10, 38);
        var bottom = layout.layout(true, 320, 180, 140, 70, 12, 10, 10, 38);
        assertEquals(72, bottom.y());
        assertTrue(bottom.y() + bottom.height() + 38 <= 180,
                "side-effect rows must stay visible below the card");
    }

    @Test void impossibleInsetsStillProduceAValidPlacementOnTinyScreens() {
        var layout = new BattlePokemonHudLayout();
        var placement = layout.layout(false, 90, 45, 80, 40, 0, 0, 20, 30);
        assertTrue(placement.x() >= 0 && placement.x() + placement.width() <= 90);
        assertTrue(placement.y() >= 0 && placement.y() + placement.height() <= 45);
    }

    @Test void aCustomResponsiveDefaultCanBeUsedForTheTeamRows() {
        var layout = new BattlePokemonHudLayout();
        assertEquals(new BattlePokemonHudLayout.Placement(153, 3, 226, 48, true),
                layout.layout(true, 960, 540, 226, 48, 153, 3));
        assertEquals(new BattlePokemonHudLayout.Placement(581, 3, 226, 48, false),
                layout.layout(false, 960, 540, 226, 48, 581, 3));
    }

    @Test void positionsPersistRescaleAndCanBeResetSeparately() throws Exception {
        var layout = new BattlePokemonHudLayout();
        var own = layout.layout(true, 960, 540, 128, 118);
        layout.begin(true, own, own.handleX() + 2, own.handleY() + 2, 0);
        layout.drag(420, 230, 960, 540, 128, 118);
        layout.finish();
        Path file = temporary.resolve("ui-battle/pokemon-hud-position.properties");
        layout.save(file);

        var restored = new BattlePokemonHudLayout();
        restored.load(file);
        assertEquals(layout.layout(true, 960, 540, 128, 118),
                restored.layout(true, 960, 540, 128, 118));
        var smaller = restored.layout(true, 320, 180, 128, 118);
        assertTrue(smaller.x() >= 0 && smaller.x() + smaller.width() <= 320);
        assertTrue(smaller.y() >= 0 && smaller.y() + smaller.height() <= 180);
        restored.reset(true);
        assertEquals(12, restored.layout(true, 960, 540, 128, 118).x());
        assertEquals(808, restored.layout(false, 960, 540, 140, 40).x());
        assertTrue(Files.readString(file).contains("By FastedCorsi"));
    }

    @Test void invalidAnchorsAreIgnoredAndOnlyTheHandleStartsDragging() throws Exception {
        Path file = temporary.resolve("invalid.properties");
        Files.writeString(file, "own.customized=true\nown.anchorX=NaN\nown.anchorY=2\n");
        var layout = new BattlePokemonHudLayout();
        layout.load(file);
        var own = layout.layout(true, 960, 540, 140, 40);
        assertEquals(12, own.x());
        assertFalse(layout.begin(true, own, own.x() + 10, own.y() + 20, 0));
        assertFalse(layout.begin(true, own, own.handleX() + 2, own.handleY() + 2, 1));
        assertTrue(layout.begin(true, own, own.handleX() + 2, own.handleY() + 2, 0));
        layout.layout(true, 640, 360, 140, 40);
        assertFalse(layout.dragging());
    }
}
