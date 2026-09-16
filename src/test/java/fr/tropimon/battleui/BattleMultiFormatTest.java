package fr.tropimon.battleui;

import com.cobblemon.mod.common.battles.MoveTarget;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BattleMultiFormatTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }

    @Test void spreadTargetCountsPreserveSinglesDoublesAndAllThreeTripleSlots() {
        assertEquals(1, BattleMultiTargeting.spreadTargetCount(MoveTarget.normal, 3));
        assertEquals(1, BattleMultiTargeting.spreadTargetCount(MoveTarget.allAdjacentFoes, 0));
        assertEquals(2, BattleMultiTargeting.spreadTargetCount(MoveTarget.allAdjacentFoes, 2));
        assertEquals(3, BattleMultiTargeting.spreadTargetCount(MoveTarget.allAdjacentFoes, 3));
        assertEquals(5, BattleMultiTargeting.spreadTargetCount(MoveTarget.allAdjacent, 5));
    }

    @Test void alliedSpreadDamageDoesNotIncorrectlyUseTheTargetsScreens() {
        DamageCalcState state = DamageCacheParityTest.basicState();
        state.field.doubles = true;
        state.field.defenderSide.reflect = true;
        state.field.attackerSide.spreadTargets = 3;
        state.attacker.moves.set(0, new MoveData("earthquake", "Earthquake", PokeType.GROUND,
                DamageCategory.PHYSICAL, 100, true, false));
        state.field.alliedTarget = false;
        int opposed = state.calculateMove(true, 0).maxDamage();
        state.field.alliedTarget = true;
        int allied = state.calculateMove(true, 0).maxDamage();
        assertTrue(allied > opposed, "an allied target must not receive the opponent-only screen reduction");
    }

    @Test void allTriplePartnerAbilitiesArePartOfTheCalculationIdentity() {
        DamageCalcState state = DamageCacheParityTest.basicState();
        state.field.doubles = true;
        state.field.attackerSide.partnerAbilities = List.of("Battery", "Power Spot");
        DamageCalculationCache cache = new DamageCalculationCache();
        assertTrue(cache.prepare(state));
        assertFalse(cache.prepare(state));
        state.field.attackerSide.partnerAbilities = List.of("Battery", "Steely Spirit");
        assertTrue(cache.prepare(state));
        assertFalse(cache.prepare(state));
    }
}
