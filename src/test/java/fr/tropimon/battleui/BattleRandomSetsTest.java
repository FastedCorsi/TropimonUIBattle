package fr.tropimon.battleui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BattleRandomSetsTest {
    @BeforeAll static void bootstrap() {
        DamageCacheParityTest.bootstrap();
    }

    @AfterEach void restoreBundledData() {
        BattleRandomSets.reload();
    }

    @Test void deterministicRandomDataAndStandardStatsAreAppliedWithoutChoosingAnArbitrarySet() {
        BattleRandomSets.replaceFromReaderForTest(new StringReader("""
                {"fixturemon": {
                  "80,leftovers,pressure,tackle,protect,normal": 2,
                  "80,leftovers,pressure,tackle,rest,normal": 1
                }}
                """));
        PokemonSet pokemon = DamageCacheParityTest.pokemon("fixturemon", PokeType.NORMAL, PokeType.NONE, 10.0);
        pokemon.level = 80;
        pokemon.item = "None";
        pokemon.itemKnown = false;
        pokemon.ability = "None";
        pokemon.abilityKnown = false;
        pokemon.natureKnown = false;
        pokemon.statsKnown = false;
        pokemon.observedMaxHp = 999;
        pokemon.moves.clear();

        assertEquals(2, BattleRandomSets.applyInference(pokemon));
        assertEquals("leftovers", BattleCalcDex.normalize(pokemon.item));
        assertEquals("pressure", BattleCalcDex.normalize(pokemon.ability));
        assertTrue(pokemon.itemKnown);
        assertTrue(pokemon.abilityKnown);
        assertFalse(pokemon.statsKnown, "inferred EVs must remain labelled as an estimate");
        for (Stat stat : Stat.values()) assertEquals(85, pokemon.evs.get(stat));
        assertEquals("serious", pokemon.nature.id());
        assertFalse(pokemon.rankedEvsSuggested);
    }

    @Test void matchingInferenceIsCachedByObservedSetSignatureAndClearedWithTheData() {
        BattleRandomSets.replaceFromReaderForTest(new StringReader("""
                {"fixturemon": {
                  "80,leftovers,pressure,tackle,protect,normal": 2,
                  "80,choicescarf,unnerve,tackle,rest,dark": 1
                }}
                """));
        PokemonSet pokemon = DamageCacheParityTest.pokemon("fixturemon", PokeType.NORMAL, PokeType.NONE, 10.0);
        pokemon.level = 80;
        pokemon.itemKnown = false;
        pokemon.abilityKnown = false;
        pokemon.moves.clear();

        var first = BattleRandomSets.matchingSets(pokemon);
        assertSame(first, BattleRandomSets.matchingSets(pokemon));

        pokemon.abilityKnown = true;
        pokemon.ability = "Pressure";
        var filtered = BattleRandomSets.matchingSets(pokemon);
        assertNotSame(first, filtered);
        assertEquals(1, filtered.size());

        BattleRandomSets.replaceFromReaderForTest(new StringReader("""
                {"fixturemon": {"80,leftovers,pressure,tackle,protect,normal": 1}}
                """));
        assertNotSame(filtered, BattleRandomSets.matchingSets(pokemon));
    }
}
