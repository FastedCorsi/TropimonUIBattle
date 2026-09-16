package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokemonSpeedRangeTest {
    @Test
    void calculatesPublicMinimumMaximumAndModifiers() {
        var neutral = PokemonSpeedRange.calculate(100, 100, 0, "", "", "",
                new PokemonSpeedRange.PublicEffects(false, false, false, false, false, false));
        assertEquals(184, neutral.minimum());
        assertEquals(328, neutral.maximum());

        var modified = PokemonSpeedRange.calculate(100, 100, 1, "par", "quickfeet", "choice_scarf",
                new PokemonSpeedRange.PublicEffects(false, false, false, false, false, true));
        assertEquals(1242, modified.effectiveMinimum());
        assertEquals(2214, modified.effectiveMaximum());
    }
}
