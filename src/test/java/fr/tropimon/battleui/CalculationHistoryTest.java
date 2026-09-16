package fr.tropimon.battleui;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
final class CalculationHistoryTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }
    @BeforeEach void reset() { BattleCalculationHistory.reset(); }
    @Test void turnsDamageAndChainsRemainDynamic() {
        var own=UUID.randomUUID(); var foe=UUID.randomUUID();
        BattleCalculationHistory.observe(new Object(),own,BattleFieldEffects.EffectSide.PLAYER_FIELD);
        BattleCalculationHistory.observe(new Object(),foe,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(own,"rollout","physical",1);
        BattleCalculationHistory.accept(foe,"cobblemon.battle.damage_dealt",new Object[]{foe,30},1);
        var state=DamageCacheParityTest.basicState();
        BattleCalculationHistory.apply(own,state.attacker);
        BattleCalculationHistory.apply(foe,state.defender);
        assertEquals(1,state.attacker.consecutiveMoveUses);
        assertEquals(1,state.defender.timesHit);
        assertEquals(30,state.defender.lastDamageTaken);
        BattleCalculationHistory.accept(null,"cobblemon.battle.turn",new Object[]{2},2);
        BattleCalculationHistory.apply(foe,state.defender);
        assertEquals(0,state.defender.lastDamageTaken);
        BattleCalculationHistory.move(own,"rollout","physical",2);
        BattleCalculationHistory.apply(own,state.attacker);
        assertEquals(2,state.attacker.consecutiveMoveUses);
    }
    @Test void illusionAndDoublesKeepIndependentHistory() {
        var fake=UUID.randomUUID(); var real=UUID.randomUUID(); var own=UUID.randomUUID();
        Object slot=new Object();
        BattleCalculationHistory.observe(slot,fake,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.observe(new Object(),own,BattleFieldEffects.EffectSide.PLAYER_FIELD);
        BattleCalculationHistory.move(own,"surf","special",1);
        BattleCalculationHistory.accept(fake,"cobblemon.battle.damage_dealt",new Object[]{fake,10},1);
        BattleCalculationHistory.reveal(slot,fake,real);
        var state=DamageCacheParityTest.basicState();
        BattleCalculationHistory.apply(real,state.defender);
        assertEquals(1,state.defender.timesHit);
        BattleCalculationHistory.apply(fake,state.defender);
        assertEquals(0,state.defender.timesHit);
    }
    @Test void missWithoutSubjectMultihitsAndTurnResetUseNativeEvents() {
        var own=UUID.randomUUID(); var foe=UUID.randomUUID();
        BattleCalculationHistory.observe(new Object(),own,BattleFieldEffects.EffectSide.PLAYER_FIELD);
        BattleCalculationHistory.observe(new Object(),foe,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        var state=DamageCacheParityTest.basicState();
        BattleCalculationHistory.move(own,"rollout","physical",1);
        BattleCalculationHistory.accept(null,"cobblemon.battle.missed",new Object[0],1);
        BattleCalculationHistory.apply(own,state.attacker);
        assertTrue(state.attacker.lastMoveFailed);
        assertEquals(0,state.attacker.consecutiveMoveUses);
        BattleCalculationHistory.move(own,"bulletseed","physical",2);
        BattleCalculationHistory.accept(foe,"cobblemon.battle.damage_dealt",new Object[]{foe,30},2);
        BattleCalculationHistory.accept(null,"cobblemon.battle.hit_count",new Object[]{5},2);
        BattleCalculationHistory.apply(foe,state.defender);
        assertEquals(5,state.defender.timesHit);
        BattleCalculationHistory.move(own,"tackle","physical",3);
        BattleCalculationHistory.accept(foe,"cobblemon.battle.damage_dealt",new Object[]{foe,10},3);
        BattleCalculationHistory.accept(null,"cobblemon.battle.hit_count_singular",new Object[0],3);
        BattleCalculationHistory.apply(foe,state.defender);
        assertEquals(6,state.defender.timesHit);
        BattleCalculationHistory.accept(null,"cobblemon.battle.turn",new Object[]{4},4);
        BattleCalculationHistory.accept(foe,"cobblemon.battle.damage_dealt",new Object[]{foe,8},4);
        BattleCalculationHistory.apply(foe,state.defender);
        assertEquals(6,state.defender.timesHit,"previous turn's attacker must not classify residual damage as a hit");
        BattleCalculationHistory.move(own,"wideguard","status",4);
        assertTrue(BattleCalculationHistory.wideGuard(BattleFieldEffects.EffectSide.PLAYER_FIELD));
        BattleCalculationHistory.accept(null,"cobblemon.battle.turn",new Object[]{5},5);
        assertFalse(BattleCalculationHistory.wideGuard(BattleFieldEffects.EffectSide.PLAYER_FIELD));
    }
    @Test void illusionTransfersOnlyNewHitsAndKeepsBothPreviousHistories() {
        var own=UUID.randomUUID(); var fake=UUID.randomUUID(); var real=UUID.randomUUID();
        var ownSlot=new Object(); var foeSlot=new Object();
        BattleCalculationHistory.observe(ownSlot,own,BattleFieldEffects.EffectSide.PLAYER_FIELD);
        BattleCalculationHistory.observe(foeSlot,real,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(own,"tackle","physical",1);
        BattleCalculationHistory.accept(real,"cobblemon.battle.damage_dealt",new Object[]{real,10},1);
        BattleCalculationHistory.observe(foeSlot,fake,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(own,"bulletseed","physical",2);
        BattleCalculationHistory.accept(fake,"cobblemon.battle.damage_dealt",new Object[]{fake,10},2);
        BattleCalculationHistory.accept(null,"cobblemon.battle.hit_count",new Object[]{3},2);
        BattleCalculationHistory.leave(foeSlot);
        BattleCalculationHistory.observe(foeSlot,fake,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.move(own,"tackle","physical",3);
        BattleCalculationHistory.accept(fake,"cobblemon.battle.damage_dealt",new Object[]{fake,10},3);
        BattleCalculationHistory.reveal(foeSlot,fake,real);
        var state=DamageCacheParityTest.basicState();
        BattleCalculationHistory.apply(fake,state.defender);
        assertEquals(3,state.defender.timesHit);
        BattleCalculationHistory.apply(real,state.defender);
        assertEquals(2,state.defender.timesHit);
    }

    @Test void residualContactItemAndRecoilDamageNeverReuseThePreviousMoveContext() {
        var attacker=UUID.randomUUID(); var defender=UUID.randomUUID(); var partner=UUID.randomUUID();
        BattleCalculationHistory.observe(new Object(),attacker,BattleFieldEffects.EffectSide.PLAYER_FIELD);
        BattleCalculationHistory.observe(new Object(),defender,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        BattleCalculationHistory.observe(new Object(),partner,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        var state=DamageCacheParityTest.basicState();

        BattleCalculationHistory.move(attacker,"surf","special",7);
        BattleCalculationHistory.accept(defender,"cobblemon.battle.damage_dealt",new Object[]{defender,30},7);
        BattleCalculationHistory.accept(partner,"cobblemon.battle.damage_dealt",new Object[]{partner,25},7);
        BattleCalculationHistory.accept(defender,"cobblemon.status.poison.hurt",new Object[]{defender},7);
        BattleCalculationHistory.accept(defender,"cobblemon.battle.damage_dealt",new Object[]{defender,12},7);
        BattleCalculationHistory.apply(defender,state.defender);
        assertEquals(30,state.defender.lastDamageTaken);
        assertEquals(1,state.defender.timesHit);

        BattleCalculationHistory.move(attacker,"tackle","physical",7);
        BattleCalculationHistory.accept(attacker,"cobblemon.battle.damage.recoil",new Object[]{attacker},7);
        BattleCalculationHistory.accept(attacker,"cobblemon.battle.damage_dealt",new Object[]{attacker,8},7);
        BattleCalculationHistory.apply(attacker,state.attacker);
        assertEquals(0,state.attacker.lastDamageTaken);

        BattleCalculationHistory.move(attacker,"tackle","physical",7);
        BattleCalculationHistory.accept(defender,"cobblemon.battle.damage_dealt",new Object[]{defender,9},7);
        BattleCalculationHistory.accept(attacker,"cobblemon.battle.damage.rockyhelmet",new Object[]{attacker,defender},7);
        BattleCalculationHistory.accept(attacker,"cobblemon.battle.damage_dealt",new Object[]{attacker,5},7);
        BattleCalculationHistory.apply(defender,state.defender);
        assertEquals(9,state.defender.lastDamageTaken,
                "a later move in the same turn replaces rather than aggregates the prior move context");
        assertEquals(2,state.defender.timesHit);
    }
}
