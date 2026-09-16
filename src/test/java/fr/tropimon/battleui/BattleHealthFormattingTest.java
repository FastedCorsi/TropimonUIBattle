package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleHealthFormattingTest {
    @Test
    void convertsFlatHealthUsingMaximumHealth() {
        assertEquals(100.0F, BattleHealthFormatting.percent(true, 307.0F, 307.0F));
        assertEquals(1.0F, BattleHealthFormatting.percent(true, 3.07F, 307.0F), 0.001F);
    }

    @Test
    void convertsHiddenHealthFractionDirectlyToPercentage() {
        assertEquals(100.0F, BattleHealthFormatting.percent(false, 1.0F, 100.0F));
        assertEquals(42.0F, BattleHealthFormatting.percent(false, 0.42F, 100.0F), 0.001F);
    }

    @Test
    void clampsOutOfRangeHealth() {
        assertEquals(100.0F, BattleHealthFormatting.percent(false, 1.2F, 100.0F));
        assertEquals(0.0F, BattleHealthFormatting.percent(false, -0.1F, 100.0F));
    }
}
