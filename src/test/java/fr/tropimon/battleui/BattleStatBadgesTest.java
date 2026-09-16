package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleStatBadgesTest {
    @Test void badgesReadTheCurrentStageNotTheLastLogDelta() {
        Map<Stat,Integer> stages = new HashMap<>();
        stages.put(Stats.ATTACK, 2);
        assertEquals(List.of(new BattleStatBadges.Stage("atk",2)), BattleStatBadges.stages(stages));
        stages.put(Stats.ATTACK, 1);
        stages.put(Stats.DEFENCE, -1);
        assertEquals(List.of(new BattleStatBadges.Stage("atk",1),new BattleStatBadges.Stage("def",-1)), BattleStatBadges.stages(stages));
        stages.put(Stats.ATTACK, 0);
        assertEquals(List.of(new BattleStatBadges.Stage("def",-1)), BattleStatBadges.stages(stages));
        stages.clear();
        assertTrue(BattleStatBadges.stages(stages).isEmpty());
    }

    @Test void allSevenStatsHaveStableOrderAndLegalLimits() {
        Map<Stat,Integer> stages = new HashMap<>();
        for (Stats stat : Stats.values()) stages.put(stat, 9);
        var result = BattleStatBadges.stages(stages);
        assertEquals(List.of("atk","def","spa","spd","spe","accuracy","evasion"), result.stream().map(BattleStatBadges.Stage::id).toList());
        assertTrue(result.stream().allMatch(stage -> stage.value() == 6));
        stages.put(Stats.ATTACK, -9);
        assertEquals(-6, BattleStatBadges.stages(stages).getFirst().value());
    }

    @Test void rowsWrapInsideTheHpBarAndMirrorForTheOpponent() {
        for (int index = 0; index < 7; index++) {
            var own = BattleStatBadges.position(index, false);
            var foe = BattleStatBadges.position(index, true);
            assertTrue(own.x() >= 0 && own.x() + BattleStatBadges.BADGE_WIDTH <= 97);
            assertEquals(97 - BattleStatBadges.BADGE_WIDTH - own.x(), foe.x());
            assertEquals(own.y(), foe.y());
            assertEquals(index / 3 * 10, own.y());
        }
    }

    @Test void nativeStatusesAndEveryBoostRowReserveEnoughSpaceForTheNextPokemon() {
        for (boolean compact : new boolean[]{false,true}) for (boolean status : new boolean[]{false,true}) {
            assertEquals(0, BattleStatBadges.extraHeight(0,compact,status));
            for (int count = 1; count <= 7; count++) {
                int badgeBottom = BattleStatBadges.top(compact,status) + ((count + 2) / 3) * 10;
                assertTrue((compact ? 28 : 40) + BattleStatBadges.extraHeight(count,compact,status) >= badgeBottom);
            }
            assertEquals(7, BattleStatBadges.top(compact,true) - BattleStatBadges.top(compact,false));
        }
    }

    @Test void compactBattlesLabelOnlyTheFirstDisplayedPokemonOfEachTrainer() {
        var side = new ClientBattleSide();
        var trainerA = new ClientBattleActor("p1", Text.literal("Trainer A"), UUID.randomUUID(), ActorType.PLAYER);
        var trainerB = new ClientBattleActor("p3", Text.literal("Trainer B"), UUID.randomUUID(), ActorType.PLAYER);
        trainerA.setSide(side); trainerB.setSide(side);
        side.getActors().add(trainerA); side.getActors().add(trainerB);
        var firstA = new ActiveClientBattlePokemon(trainerA, null);
        var secondA = new ActiveClientBattlePokemon(trainerA, null);
        var firstB = new ActiveClientBattlePokemon(trainerB, null);
        trainerA.getActivePokemon().add(firstA); trainerA.getActivePokemon().add(secondA);
        trainerB.getActivePokemon().add(firstB);

        assertTrue(BattleStatBadges.trainerVisible(firstA, true, 0, true));
        assertFalse(BattleStatBadges.trainerVisible(secondA, true, 1, true));
        assertTrue(BattleStatBadges.trainerVisible(firstB, true, 2, true));

        // Opponent cards are rendered in reverse native order: the top card is still the only label.
        assertFalse(BattleStatBadges.trainerVisible(firstA, false, 2, true));
        assertTrue(BattleStatBadges.trainerVisible(secondA, false, 1, true));
        assertTrue(BattleStatBadges.trainerVisible(firstB, false, 0, true));
        assertTrue(BattleStatBadges.trainerVisible(secondA, true, 1, false));
    }

    @Test void arrowsAreTheOfficialCobblemonAssetsAndRemainTransparent() throws Exception {
        var mod = FabricLoader.getInstance().getModContainer("cobblemon").orElseThrow();
        for (var asset : List.of(BattleStatBadges.INCREASE, BattleStatBadges.DECREASE)) {
            assertEquals("cobblemon", asset.getNamespace());
            try (var stream = Files.newInputStream(mod.findPath("assets/cobblemon/"+asset.getPath()).orElseThrow())) {
                var image = javax.imageio.ImageIO.read(stream);
                assertEquals(8, image.getWidth()); assertEquals(6,image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());
            }
        }
    }

    @Test void newHooksAreActuallyAppliedToTheOfficialRuntime() {
        for (var entry : Map.of(
                com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection.class,"tropimonBattleUi$moveActions",
                com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection.class,"tropimonBattleUi$moveActions",
                com.cobblemon.mod.common.client.gui.battle.BattleOverlay.class,"tropimonBattleUi$nightTileColor").entrySet()) {
            assertTrue(Arrays.stream(entry.getKey().getDeclaredMethods()).anyMatch(method -> method.getName().contains(entry.getValue())),entry.getKey().getSimpleName());
        }
        assertTrue(Arrays.stream(com.cobblemon.mod.common.client.gui.battle.BattleOverlay.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$statBadges")));
        for (var type : List.of(
                com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection.class,
                com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("tropimonBattleUi$moveActionClicks")), type.getSimpleName());
        }
        assertTrue(Arrays.stream(com.cobblemon.mod.common.client.gui.battle.BattleGUI.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().contains("beginActionTail")
                        || method.getName().contains("endActionTail")));
        for (String hook : List.of("beginActionExtensions", "localExtensionRenderX", "restoreExtensionRenderX",
                "endActionExtensions")) {
            assertTrue(Arrays.stream(com.cobblemon.mod.common.client.gui.battle.BattleGUI.class.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains(hook)), hook);
        }
    }
}
