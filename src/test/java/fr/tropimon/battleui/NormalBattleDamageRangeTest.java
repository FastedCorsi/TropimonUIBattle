package fr.tropimon.battleui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalBattleDamageRangeTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }

    @Test void unknownNormalOpponentNeverReceivesTheRandomBattleFallback() {
        PokemonSet defender = DamageCacheParityTest.basicState().defender;
        defender.statsKnown = false;
        defender.natureKnown = false;
        for (Stat stat : Stat.values()) {
            defender.evs.put(stat, 0);
            defender.ivs.put(stat, 0);
        }

        TropimonDamageCalcBridge.applyEstimatedStats(defender);

        for (Stat stat : Stat.values()) {
            assertEquals(31, defender.ivs.get(stat));
            assertEquals(0, defender.evs.get(stat));
        }
        assertFalse(defender.rankedEvsSuggested);
        assertFalse(TropimonDamageCalcBridge.normalStatsReady(defender));
    }

    @Test void usageSpreadIsPreservedAndExactVisibleHpCanRefineItsHpEvs() {
        PokemonSet defender = DamageCacheParityTest.basicState().defender;
        defender.statsKnown = false;
        defender.evs.put(Stat.HP, 4);
        defender.evs.put(Stat.SPA, 252);
        defender.evs.put(Stat.SPE, 252);
        defender.rankedEvsSuggested = true;
        defender.rankedNatureSuggested = true;
        for (Stat stat : Stat.values()) defender.ivs.put(stat, 3);
        defender.observedMaxHp = hpAt31Ivs(defender, 132);

        TropimonDamageCalcBridge.applyEstimatedStats(defender);

        // 252 SpA + 252 Spe leaves only 6 legal EVs. An impossible visible-HP
        // observation must not make the estimate exceed the 510-EV cap.
        assertEquals(4, defender.evs.get(Stat.HP));
        assertEquals(252, defender.evs.get(Stat.SPA));
        assertEquals(252, defender.evs.get(Stat.SPE));
        for (Stat stat : Stat.values()) assertEquals(31, defender.ivs.get(stat));
        assertTrue(TropimonDamageCalcBridge.normalStatsReady(defender));
    }

    @Test void visibleHpCanRefineHpEvsWhenTheResultStaysWithinTheGlobalCap() {
        PokemonSet defender = DamageCacheParityTest.basicState().defender;
        defender.statsKnown = false;
        defender.evs.put(Stat.HP, 4);
        defender.evs.put(Stat.SPA, 252);
        defender.evs.put(Stat.SPE, 120);
        defender.rankedEvsSuggested = true;
        defender.rankedNatureSuggested = true;
        defender.observedMaxHp = hpAt31Ivs(defender, 132);

        TropimonDamageCalcBridge.applyEstimatedStats(defender);

        assertEquals(132, defender.evs.get(Stat.HP));
        assertTrue(defender.evs.values().stream().mapToInt(Integer::intValue).sum() <= 510);
    }

    @Test void compactProfileUsesTheDefenseActuallyTargetedByTheMove() {
        DamageCalcState state = DamageCacheParityTest.basicState();
        state.defender.statsKnown = false;
        state.defender.evs.put(Stat.HP, 84);
        state.defender.evs.put(Stat.DEF, 92);
        state.defender.evs.put(Stat.SPD, 100);
        var physical = TropimonDamageCalcBridge.estimatedProfile(state.defender,
                new MoveData("tackle", "Tackle", PokeType.NORMAL, DamageCategory.PHYSICAL, 40, false, true));
        var special = TropimonDamageCalcBridge.estimatedProfile(state.defender,
                new MoveData("surf", "Surf", PokeType.WATER, DamageCategory.SPECIAL, 90, true, false));
        assertEquals(Stat.DEF, physical.defenseStat());
        assertEquals(92, physical.defenseEv());
        assertEquals(Stat.SPD, special.defenseStat());
        assertEquals(100, special.defenseEv());
    }

    @Test void battleSizeSelectsIndependentUsagePopulations() {
        assertEquals("SINGLES", TropimonRankedUsageService.formatForPokemonPerSide(1));
        assertEquals("DOUBLES", TropimonRankedUsageService.formatForPokemonPerSide(2));
        assertEquals("DOUBLES", TropimonRankedUsageService.formatForPokemonPerSide(3));
    }

    @Test void onlyExplicitBattleMetadataSelectsRandomMode() {
        assertFalse(BattleUiState.isRandomFormat("gen9", java.util.Set.of("Species Clause")));
        assertTrue(BattleUiState.isRandomFormat("gen9random", java.util.Set.of()));
        assertTrue(BattleUiState.isRandomFormat("gen9", java.util.Set.of("Random Battle")));
        assertTrue(BattleUiState.isRandomFormat("gen9", java.util.Set.of(), "Random Singles"));
    }

    private static int hpAt31Ivs(PokemonSet pokemon, int ev) {
        int base = pokemon.species.baseStats().get(Stat.HP);
        return (int) Math.floor(((2 * base + 31 + Math.floor(ev / 4.0)) * pokemon.level) / 100.0)
                + pokemon.level + 10;
    }
}
