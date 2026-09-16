package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleCalculationResolvedStateTest {
    @BeforeAll static void bootstrap() {
        DamageCacheParityTest.bootstrap();
    }

    @AfterEach void clearObservedState() throws Exception {
        tracker().reset();
        confirmedCures().clear();
    }

    @Test void confirmedLumBerryCureAndObservedStagesFeedTheCalculatorSnapshot() throws Exception {
        UUID pokemon = UUID.randomUUID();
        tracker().change(pokemon, "atk", 2);
        var confirm = BattleUiState.class.getDeclaredMethod("confirmStatusCure", UUID.class);
        confirm.setAccessible(true);
        confirm.invoke(null, pokemon);

        PokemonSet set = DamageCacheParityTest.pokemon("fixture", PokeType.NORMAL, PokeType.NONE, 10.0);
        BattleCalculationInputs.applyResolvedBattleState(set, pokemon, Map.<Stat, Integer>of(), "par");

        assertEquals(2, set.boosts.get(fr.tropimon.battleui.Stat.ATK));
        assertEquals(StatusCondition.NONE, set.status);
    }

    @Test void staleNativeBaselineCannotOverwriteLaterObservedBoostsOrClears() throws Exception {
        UUID pokemon = UUID.randomUUID();
        PokemonSet set = DamageCacheParityTest.pokemon("fixture", PokeType.NORMAL, PokeType.NONE, 10.0);
        Map<Stat, Integer> nativeBaseline = Map.of(Stats.ATTACK, 1);

        BattleCalculationInputs.applyResolvedBattleState(set, pokemon, nativeBaseline, "");
        tracker().change(pokemon, "atk", 1);
        BattleCalculationInputs.applyResolvedBattleState(set, pokemon, nativeBaseline, "");
        assertEquals(2, set.boosts.get(fr.tropimon.battleui.Stat.ATK));

        tracker().clear(pokemon);
        BattleCalculationInputs.applyResolvedBattleState(set, pokemon, nativeBaseline, "");
        assertEquals(0, set.boosts.get(fr.tropimon.battleui.Stat.ATK));
    }

    @Test void clearAllBoostMessageCannotLetStaleNativeStagesReappear() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        PokemonSet firstSet = DamageCacheParityTest.pokemon("first", PokeType.NORMAL, PokeType.NONE, 10.0);
        PokemonSet secondSet = DamageCacheParityTest.pokemon("second", PokeType.NORMAL, PokeType.NONE, 10.0);
        Map<Stat, Integer> firstNative = Map.of(Stats.ATTACK, 2);
        Map<Stat, Integer> secondNative = Map.of(Stats.SPEED, -1);
        BattleCalculationInputs.applyResolvedBattleState(firstSet, first, firstNative, "");
        BattleCalculationInputs.applyResolvedBattleState(secondSet, second, secondNative, "");

        Method remember = BattleUiState.class.getDeclaredMethod("rememberStatStages", String.class, Object[].class);
        remember.setAccessible(true);
        remember.invoke(null, "cobblemon.battle.clearallboost", (Object) new Object[0]);

        BattleCalculationInputs.applyResolvedBattleState(firstSet, first, firstNative, "");
        BattleCalculationInputs.applyResolvedBattleState(secondSet, second, secondNative, "");
        assertEquals(0, firstSet.boosts.get(fr.tropimon.battleui.Stat.ATK));
        assertEquals(0, secondSet.boosts.get(fr.tropimon.battleui.Stat.SPE));
    }

    private static BattleStatStageTracker tracker() throws Exception {
        Field field = BattleUiState.class.getDeclaredField("OBSERVED_STAT_STAGES");
        field.setAccessible(true);
        return (BattleStatStageTracker) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> confirmedCures() throws Exception {
        Field field = BattleUiState.class.getDeclaredField("CONFIRMED_STATUS_CURES");
        field.setAccessible(true);
        return (Set<UUID>) field.get(null);
    }
}
