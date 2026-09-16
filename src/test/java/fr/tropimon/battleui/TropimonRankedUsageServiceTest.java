package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TropimonRankedUsageServiceTest {
    @Test void failedApiRequestsUseBoundedExponentialRetryDelays() {
        assertEquals(5_000, TropimonRankedUsageService.retryDelayMillis(0));
        assertEquals(5_000, TropimonRankedUsageService.retryDelayMillis(1));
        assertEquals(10_000, TropimonRankedUsageService.retryDelayMillis(2));
        assertEquals(40_000, TropimonRankedUsageService.retryDelayMillis(4));
        assertEquals(60_000, TropimonRankedUsageService.retryDelayMillis(99));
        assertEquals(2_000, BattleCalcDex.cobblemonRetryDelayMillis(1));
        assertEquals(60_000, BattleCalcDex.cobblemonRetryDelayMillis(99));
        assertTrue(TropimonRankedUsageService.successFresh(1_001, 1_000));
        assertFalse(TropimonRankedUsageService.successFresh(1_000, 1_000));
    }

    @Test void normalProfileUsesTheMostCommonValidEvSpread() {
        String json = """
                {
                  "items":{"Choice Scarf":58.0,"Leftovers":12.0},
                  "abilities":{"Good as Gold":99.0},
                  "natures":{"Timid":71.0},
                  "spreads":{"4 HP / 252 SpA / 252 Spe":64.0,"252 HP / 252 Def / 252 SpD":80.0}
                }
                """;
        var profile = TropimonRankedUsageService.profileFromJson("Gholdengo", json);
        assertEquals("gholdengo", profile.speciesKey());
        assertEquals("Choice Scarf", profile.item());
        assertNotNull(profile.evSpread());
        assertEquals(4, profile.evSpread().hp());
        assertEquals(252, profile.evSpread().spa());
        assertEquals(252, profile.evSpread().spe());

        PokemonSet pokemon = DamageCacheParityTest.pokemon("gholdengo", PokeType.GHOST, PokeType.STEEL, 30);
        pokemon.itemKnown = pokemon.abilityKnown = pokemon.natureKnown = true;
        pokemon.statsKnown = false;
        assertTrue(TropimonRankedUsageService.applyProfile(pokemon, profile));
        assertEquals(4, pokemon.evs.get(Stat.HP));
        assertEquals(252, pokemon.evs.get(Stat.SPA));
        assertEquals(0, pokemon.evs.get(Stat.DEF));
        assertTrue(pokemon.rankedEvsSuggested);
        assertFalse(pokemon.statsKnown, "an API suggestion must stay labelled as an estimate");
    }

    @Test void malformedOrIllegalEvSpreadsAreNeverAppliedAsZeroEvKnowledge() {
        var profile = TropimonRankedUsageService.profileFromJson("test", """
                {"spreads":{"252 HP / 252 Def / 252 SpD":99,"999 HP":98,"broken":97}}
                """);
        assertNull(profile.evSpread());

        PokemonSet pokemon = DamageCacheParityTest.pokemon("test", PokeType.NORMAL, PokeType.NONE, 30);
        pokemon.statsKnown = false;
        pokemon.natureKnown = false;
        assertTrue(TropimonRankedUsageService.applyProfile(pokemon, profile));
        assertFalse(pokemon.rankedEvsSuggested);
        assertFalse(TropimonDamageCalcBridge.normalStatsReady(pokemon));
        for (Stat stat : Stat.values()) assertNotEquals(85, pokemon.evs.get(stat));
    }
}
