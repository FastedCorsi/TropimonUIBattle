package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleMoveDynamicsTest {
    @Test void gravityKeepsTheTargetsPositiveEvasionStage() {
        assertEquals(1.0D, BattleMoveDynamics.accuracyStageModifier(0, 2, true), 0.000001D);
        assertEquals(5.0D / 3.0D, BattleMoveDynamics.accuracyStageModifier(0, 0, true), 0.000001D);
    }

    @Test void victoryStarStacksForTheUserAndItsActivePartners() {
        assertEquals(1.0D, BattleMoveDynamics.victoryStarModifier(0), 0.000001D);
        assertEquals(1.1D, BattleMoveDynamics.victoryStarModifier(1), 0.000001D);
        assertEquals(1.21D, BattleMoveDynamics.victoryStarModifier(2), 0.000001D);
    }

    @Test void neutralizingGasRespectsAbilityShieldAndUnsuppressibleAbilities() {
        assertEquals("", BattleMoveDynamics.abilityUnderGas("No Guard", "", true));
        assertEquals("noguard", BattleMoveDynamics.abilityUnderGas("No Guard", "Ability Shield", true));
        assertEquals("neutralizinggas", BattleMoveDynamics.abilityUnderGas("Neutralizing Gas", "", true));
        assertEquals("cloudnine", BattleMoveDynamics.abilityUnderGas("Cloud Nine", "", false));
    }
}
