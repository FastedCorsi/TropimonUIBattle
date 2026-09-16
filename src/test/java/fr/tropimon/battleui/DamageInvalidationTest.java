package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

final class DamageInvalidationTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }
    @Test void validatesEveryDynamicInputWithoutATickOrJournalRevision() {
        List<Consumer<DamageCalcState>> mutations=List.of(
                s->s.attacker.currentHp=1, s->s.defender.currentHp=2,
                s->s.attacker.observedMaxHp=777, s->s.defender.observedMaxHp=333,
                s->s.attacker.item="Choice Band", s->s.defender.item="Assault Vest",
                s->s.attacker.itemKnown=false, s->s.defender.itemKnown=false,
                s->s.attacker.ability="Huge Power", s->s.defender.ability="Levitate",
                s->s.attacker.abilityKnown=false, s->s.defender.abilityKnown=false,
                s->s.attacker.nature=BattleCalcDex.nature("adamant"),
                s->s.defender.nature=BattleCalcDex.nature("bold"),
                s->s.attacker.level=34, s->s.defender.level=77,
                s->s.attacker.evs.put(Stat.ATK,252), s->s.defender.ivs.put(Stat.DEF,0),
                s->s.attacker.boosts.put(Stat.ATK,6), s->s.defender.boosts.put(Stat.DEF,-3),
                s->s.attacker.status=StatusCondition.BURN, s->s.defender.status=StatusCondition.POISON,
                s->s.attacker.terastallized=true, s->s.defender.teraType=PokeType.FAIRY,
                s->s.attacker.species.baseStats().put(Stat.ATK,30),
                s->s.defender.species=new SpeciesData("newform","New form",PokeType.GHOST,PokeType.NORMAL,
                        new java.util.EnumMap<>(s.defender.species.baseStats()),false,""),
                s->s.field.weather=Weather.RAIN, s->s.field.terrain=Terrain.GRASSY,
                s->s.field.doubles=true, s->s.field.alliedTarget=true, s->s.field.gravity=true, s->s.field.trickRoom=true,
                s->s.field.criticalHit=true, s->s.field.attackerSide.tailwind=true,
                s->s.field.defenderSide.reflect=true, s->s.field.defenderSide.lightScreen=true,
                s->s.field.defenderSide.auroraVeil=true, s->s.field.attackerSide.helpingHand=true,
                s->s.field.defenderSide.friendGuard=true, s->s.field.defenderSide.wideGuard=true,
                s->s.field.attackerSide.partnerAbility="Battery", s->s.field.defenderSide.partnerAbility="Steely Spirit",
                s->s.field.attackerSide.partnerAbilities=List.of("Battery", "Power Spot"),
                s->s.field.attackerSide.spreadTargets=1, s->s.field.defenderSide.spreadTargets=1,
                s->s.attacker.battleHistoryKnown=true, s->s.attacker.timesHit=5,
                s->s.attacker.lastDamageTaken=90, s->s.attacker.lastDamageCategory=DamageCategory.PHYSICAL,
                s->s.attacker.consecutiveMoveUses=3, s->s.attacker.faintedAllies=4,
                s->s.attacker.lastMoveFailed=true, s->s.attacker.flashFireActive=true,
                s->s.attacker.paradoxBoostActive=true, s->s.attacker.turnsActive=3);
        for (int i=0;i<mutations.size();i++) {
            DamageCalcState s=DamageCacheParityTest.basicState();
            DamageCalculationCache cache=new DamageCalculationCache();
            assertTrue(cache.prepare(s));
            assertFalse(cache.prepare(s));
            mutations.get(i).accept(s);
            assertTrue(cache.prepare(s),"mutation "+i);
            assertFalse(cache.prepare(s),"stable after "+i);
            assertEquals(DamageCalculator.calculate(s.attacker,s.defender,s.attacker.moveAt(0),s.field,
                    s.field.attackerSide,s.field.defenderSide),s.calculateMove(true,0));
        }
    }

    @Test void presentationOnlyPokemonFieldsDoNotDiscardDamageResults() {
        DamageCalcState state=DamageCacheParityTest.basicState();
        DamageCalculationCache cache=new DamageCalculationCache();
        assertTrue(cache.prepare(state));
        assertFalse(cache.prepare(state));

        state.attacker.hpObservation=0.0004f;
        state.defender.hpObservation=0.8f;
        state.attacker.movePp.add(3);
        state.defender.movePp.add(1);
        state.attacker.runtimeEffects=List.of("encore");
        state.defender.runtimeEffects=List.of("substitute");
        state.attacker.movesKnown=!state.attacker.movesKnown;

        assertFalse(cache.prepare(state));
    }
    @Test void differentMovesTargetsAndResourcesCannotReuseWrongResults() {
        var s=DamageCacheParityTest.basicState();
        DamageResult first=s.calculateMove(true,0);
        assertSame(first,s.calculateMove(true,0));
        s.attacker.moves.set(0,new MoveData("flamethrower","Flamethrower",PokeType.FIRE,DamageCategory.SPECIAL,90,false,false));
        assertNotEquals(first.move(),s.calculateMove(true,0).move());
        PokemonSet old=s.defender;
        s.defender=DamageCacheParityTest.pokemon("water",PokeType.WATER,PokeType.NONE,50);
        var water=s.calculateMove(true,0);
        s.defender=DamageCacheParityTest.pokemon("grass",PokeType.GRASS,PokeType.NONE,50);
        assertNotEquals(water.minDamage(),s.calculateMove(true,0).minDamage());
        DamageCalculationCache cache=new DamageCalculationCache();
        assertTrue(cache.prepare(s));
        DamageCalculationCache.clearShared();
        assertTrue(cache.prepare(s));
    }
    @Test void cachedResultsAndKeysDoNotAliasMutableInputs() {
        var s=DamageCacheParityTest.basicState();
        var first=s.calculateMove(true,0);
        assertThrows(UnsupportedOperationException.class,()->first.rolls().add(12));
        s.defender.species.baseStats().put(Stat.DEF,1);
        assertNotEquals(first.minDamage(),s.calculateMove(true,0).minDamage());
    }
}
