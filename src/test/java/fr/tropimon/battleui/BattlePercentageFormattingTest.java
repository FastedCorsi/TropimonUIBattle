package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattlePercentageFormattingTest {
    @Test
    void neverTurnsPositiveHealthIntoZero() {
        assertEquals("0.04%", BattlePercentageFormatting.format(0.04F));
        assertEquals("0.4%", BattlePercentageFormatting.format(0.4F));
        assertEquals("1%", BattlePercentageFormatting.format(1.0F));
        assertEquals("0%", BattlePercentageFormatting.format(0.0F));
        assertEquals("99.96%", BattlePercentageFormatting.format(99.96F));
    }

    @Test
    void suppliesNumberForTranslationsThatAlreadyContainPercentSign() {
        assertEquals("0.04", BattlePercentageFormatting.number(0.04F));
        assertEquals("55.8", BattlePercentageFormatting.number(55.8F));
    }

    @Test
    void animatedCounterMovesByTenthsWithoutRoundingDamageBackToOneHundred() {
        assertEquals("99.9%", BattlePercentageFormatting.animated(99.96F, 40F));
        assertEquals("99.9%", BattlePercentageFormatting.animated(99.9F, 40F));
        assertEquals("99.8%", BattlePercentageFormatting.animated(99.89F, 40F));
        assertEquals("99.0%", BattlePercentageFormatting.animated(99F, 40F));
        assertEquals("40.1%", BattlePercentageFormatting.animated(40.04F, 80F));
        assertEquals("0.04%", BattlePercentageFormatting.animated(0.04F, 0F));
        assertEquals("0%", BattlePercentageFormatting.format(0F));
    }
}
