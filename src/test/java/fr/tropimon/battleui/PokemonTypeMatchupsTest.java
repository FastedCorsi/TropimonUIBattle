package fr.tropimon.battleui;

import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokemonTypeMatchupsTest {
    @AfterEach
    void resetEffects() {
        BattleFieldEffects.reset();
        PokemonBattleEffects.reset();
    }

    @Test
    void combinesDualTypeWeaknessesAndImmunities() {
        List<TypeView> waterFlying = List.of(
                new TypeView("water", Text.literal("Water")),
                new TypeView("flying", Text.literal("Flying"))
        );
        assertEquals(4.0D, PokemonTypeMatchups.effectiveness("electric", waterFlying));
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness("ground", waterFlying));
        assertEquals(2.0D, PokemonTypeMatchups.effectiveness("rock", waterFlying));
    }

    @Test
    void coversEveryMultiplierDisplayedOnMoveBadges() {
        assertEquals(0.0D, effectiveness("ground", "flying"));
        assertEquals(0.25D, effectiveness("fire", "water", "dragon"));
        assertEquals(0.5D, effectiveness("normal", "rock"));
        assertEquals(1.0D, effectiveness("water", "electric"));
        assertEquals(2.0D, effectiveness("ground", "electric"));
        assertEquals(4.0D, effectiveness("electric", "water", "flying"));

        assertEquals("0", MoveTooltipRenderer.formatMultiplier(0.0D));
        assertEquals("0.25", MoveTooltipRenderer.formatMultiplier(0.25D));
        assertEquals("0.5", MoveTooltipRenderer.formatMultiplier(0.5D));
        assertEquals("1", MoveTooltipRenderer.formatMultiplier(1.0D));
        assertEquals("2", MoveTooltipRenderer.formatMultiplier(2.0D));
        assertEquals("4", MoveTooltipRenderer.formatMultiplier(4.0D));
    }

    @Test
    void accountsForKnownAbilityImmunitiesAndSpecialMoves() {
        var levitateTarget = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Rotom",
                List.of(new TypeView("electric", Text.literal("Electric"))),
                new AbilityView("levitate", Text.literal("Levitate"), Text.empty()));
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness("earthquake", "ground", levitateTarget));

        var suppressedLevitate = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Rotom",
                List.of(new TypeView("electric", Text.literal("Electric"))),
                new AbilityView("levitate", Text.literal("Levitate"), Text.empty(), true));
        assertEquals(2.0D, PokemonTypeMatchups.effectiveness(
                "earthquake", "ground", suppressedLevitate));

        var waterTarget = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Gastrodon",
                List.of(new TypeView("water", Text.literal("Water"))), null);
        assertEquals(2.0D, PokemonTypeMatchups.effectiveness("freezedry", "ice", waterTarget));
    }

    @Test
    void updatesGroundImmunityForGravityAndIdentifiedTargets() {
        UUID flyingId = UUID.randomUUID();
        var flyingLevitateTarget = new BattleUiState.ActiveTargetView(flyingId, "Rotom-Fan",
                List.of(new TypeView("electric", Text.literal("Electric")),
                        new TypeView("flying", Text.literal("Flying"))),
                new AbilityView("levitate", Text.literal("Levitate"), Text.empty()));
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness(
                "earthquake", "ground", flyingLevitateTarget));

        BattleFieldEffects.accept("cobblemon.battle.fieldstart.gravity", 1);
        assertEquals(2.0D, PokemonTypeMatchups.effectiveness(
                "earthquake", "ground", flyingLevitateTarget));
        var earthEater = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Orthworm",
                List.of(new TypeView("steel", Text.literal("Steel"))),
                new AbilityView("eartheater", Text.literal("Earth Eater"), Text.empty()));
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness("earthquake", "ground", earthEater));

        UUID ghostId = UUID.randomUUID();
        var ghost = new BattleUiState.ActiveTargetView(ghostId, "Gengar",
                List.of(new TypeView("ghost", Text.literal("Ghost"))), null);
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness("tackle", "normal", ghost));
        PokemonBattleEffects.accept(ghostId, "cobblemon.battle.start.foresight", new Object[0], 1);
        assertEquals(1.0D, PokemonTypeMatchups.effectiveness("tackle", "normal", ghost));
    }

    @Test
    void appliesPublicItemImmunitiesAndGrounding() {
        List<TypeView> electricFlying = List.of(
                new TypeView("electric", Text.literal("Electric")),
                new TypeView("flying", Text.literal("Flying")));
        var balloon = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Electrode",
                List.of(new TypeView("electric", Text.literal("Electric"))), null, "air_balloon");
        assertEquals(0.0D, PokemonTypeMatchups.effectiveness("earthquake", "ground", balloon));

        var ironBall = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Zapdos",
                electricFlying, null, "iron_ball");
        assertEquals(1.0D, PokemonTypeMatchups.effectiveness("earthquake", "ground", ironBall));

        var ringTarget = new BattleUiState.ActiveTargetView(UUID.randomUUID(), "Gengar",
                List.of(new TypeView("ghost", Text.literal("Ghost"))), null, "ring_target");
        assertEquals(1.0D, PokemonTypeMatchups.effectiveness("tackle", "normal", ringTarget));
    }

    private static double effectiveness(String attack, String... defenders) {
        return PokemonTypeMatchups.effectiveness(attack,
                java.util.Arrays.stream(defenders)
                        .map(type -> new TypeView(type, Text.literal(type)))
                        .toList());
    }
}
