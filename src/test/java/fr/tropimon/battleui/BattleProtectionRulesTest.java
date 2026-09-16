package fr.tropimon.battleui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class BattleProtectionRulesTest {
    @BeforeEach void reset() { BattleCalculationHistory.reset(); }

    @Test void allSideGuardsExpireAtTheNextTurn() {
        UUID defender = UUID.randomUUID();
        BattleCalculationHistory.observe(new Object(), defender, BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(defender, "quickguard", "status", 4);
        BattleCalculationHistory.move(defender, "matblock", "status", 4);
        BattleCalculationHistory.move(defender, "craftyshield", "status", 4);
        assertTrue(BattleCalculationHistory.guard(BattleFieldEffects.EffectSide.OPPONENT_FIELD,
                BattleCalculationHistory.Guard.QUICK));
        assertTrue(BattleCalculationHistory.guard(BattleFieldEffects.EffectSide.OPPONENT_FIELD,
                BattleCalculationHistory.Guard.MAT_BLOCK));
        assertTrue(BattleCalculationHistory.guard(BattleFieldEffects.EffectSide.OPPONENT_FIELD,
                BattleCalculationHistory.Guard.CRAFTY));
        BattleCalculationHistory.accept(null, "cobblemon.battle.turn", new Object[]{5}, 5);
        assertFalse(BattleCalculationHistory.guard(BattleFieldEffects.EffectSide.OPPONENT_FIELD,
                BattleCalculationHistory.Guard.QUICK));
    }

    @Test void eachGuardOnlyBlocksItsMoveClassAndNeverFriendlyFire() {
        FieldState field = new FieldState();
        SideConditions side = new SideConditions();
        MoveData priority = new MoveData("machpunch", "Mach Punch", PokeType.FIGHTING,
                DamageCategory.PHYSICAL, 40, false, true, java.util.Set.of("contact"), 1);
        MoveData spread = new MoveData("rockslide", "Rock Slide", PokeType.ROCK,
                DamageCategory.PHYSICAL, 75, true, false);
        MoveData status = new MoveData("taunt", "Taunt", PokeType.DARK,
                DamageCategory.STATUS, 0, false, false);
        side.quickGuard = true;
        assertEquals("Quick Guard", BattleProtectionRules.blockedBy(priority, field, side));
        side.quickGuard = false; side.wideGuard = true;
        assertEquals("Wide Guard", BattleProtectionRules.blockedBy(spread, field, side));
        side.wideGuard = false; side.craftyShield = true;
        assertEquals("Crafty Shield", BattleProtectionRules.blockedBy(status, field, side));
        side.craftyShield = false; side.matBlock = true;
        assertEquals("Mat Block", BattleProtectionRules.blockedBy(spread, field, side));
        field.alliedTarget = true;
        assertEquals("", BattleProtectionRules.blockedBy(spread, field, side));
    }

    @Test void redirectorsRemainBoundToTheirSideForOneTurn() {
        UUID redirector = UUID.randomUUID();
        BattleCalculationHistory.observe(new Object(), redirector, BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(redirector, "followme", "status", 7);
        assertEquals(redirector, BattleCalculationHistory.redirect(
                BattleFieldEffects.EffectSide.OPPONENT_FIELD).pokemon());
        BattleCalculationHistory.accept(null, "cobblemon.battle.turn", new Object[]{8}, 8);
        assertNull(BattleCalculationHistory.redirect(BattleFieldEffects.EffectSide.OPPONENT_FIELD));
    }
}
