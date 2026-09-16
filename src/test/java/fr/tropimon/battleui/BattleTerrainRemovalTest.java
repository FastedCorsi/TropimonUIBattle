package fr.tropimon.battleui;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTerrainRemovalTest {
    private static final List<String> TERRAIN_CLEARING_MOVES = List.of(
            "icespinner",
            "steelroller",
            "defog"
    );
    private static final List<String> TERRAINS = List.of(
            "electricterrain",
            "grassyterrain",
            "mistyterrain",
            "psychicterrain"
    );

    @TestFactory
    Stream<DynamicTest> publicFieldEndRemovesEveryTerrainForEveryObservationMode() {
        return TERRAIN_CLEARING_MOVES.stream().flatMap(move ->
                Stream.of(false, true).flatMap(spectating ->
                        TERRAINS.stream().map(terrain -> DynamicTest.dynamicTest(
                                move + " removes " + terrain + " while "
                                        + (spectating ? "spectating" : "participating"),
                                () -> assertPublicFieldEndRemovesTerrain(spectating, move, terrain)
                        ))));
    }

    private static void assertPublicFieldEndRemovesTerrain(boolean spectating, String move, String terrain) {
        BattleFieldEffects.reset();
        try {
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.fieldstart." + terrain, 2);
            BattleFieldEffects.endMessageBatch();
            assertTrue(BattleFieldEffects.active(terrain));

            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.used_move", 3);
            BattleFieldEffects.endMessageBatch();
            assertTrue(BattleFieldEffects.active(terrain),
                    move + " must wait for the authoritative field-end event");

            // Ice Spinner, Steel Roller and Defog all resolve terrain removal
            // through this public Cobblemon event, including for spectators.
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.fieldend." + terrain, 3);
            BattleFieldEffects.endMessageBatch();

            assertFalse(BattleFieldEffects.active(terrain));
            assertTrue(BattleFieldEffects.snapshot(3).isEmpty());
        } finally {
            BattleFieldEffects.endMessageBatch();
            BattleFieldEffects.reset();
        }
    }
}
