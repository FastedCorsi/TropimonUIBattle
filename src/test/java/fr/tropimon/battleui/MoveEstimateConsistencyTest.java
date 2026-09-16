package fr.tropimon.battleui;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MoveEstimateConsistencyTest {
    @Test void smallDamageDoesNotBecomeZeroAndOverkillIsNotClampedTo100Percent() {
        assertEquals("0.04", MoveTooltipRenderer.damagePercent(0.04));
        assertEquals("<0.01", MoveTooltipRenderer.damagePercent(0.004));
        assertEquals("0", MoveTooltipRenderer.damagePercent(0));
        assertEquals("123.4", MoveTooltipRenderer.damagePercent(123.4));
    }

    @Test void multipliersRemainExactWithAThirdTypeInsteadOfRoundingAnEighthToPoint13() {
        assertEquals("0.125", MoveTooltipRenderer.formatMultiplier(0.125));
        assertEquals("0.25", MoveTooltipRenderer.formatMultiplier(0.25));
        assertEquals("0.5", MoveTooltipRenderer.formatMultiplier(0.5));
        assertEquals("1", MoveTooltipRenderer.formatMultiplier(1));
        assertEquals("8", MoveTooltipRenderer.formatMultiplier(8));
        assertEquals("?", MoveTooltipRenderer.formatMultiplier(Double.NaN));
    }

    @Test void singleTargetSummaryUsesDirectLabelsWithoutRepeatingThePokemonName() {
        assertTranslation(MoveTooltipRenderer.matchupSummary("FixtureMon", Text.literal("×1"), false, false),
                "text.tropimon_ui_battle.matchup", Text.literal("×1"));
        assertTranslation(MoveTooltipRenderer.damageSummary("FixtureMon", "25–30%", false),
                "text.tropimon_ui_battle.estimated_damage", "25–30%");
        assertTranslation(MoveTooltipRenderer.koSummary("FixtureMon", "guaranteed OHKO", false),
                "text.tropimon_ui_battle.ko_chance", "guaranteed OHKO");
    }

    @Test void multipleTargetsRemainIdentifiable() {
        assertTranslation(MoveTooltipRenderer.damageSummary("FixtureMon", "25–30%", true),
                "text.tropimon_ui_battle.estimated_damage_against", "FixtureMon", "25–30%");
    }

    @Test void rejectsAStaleWeatherFormOrTypeResultInsteadOfPresentingContradictoryDamage() {
        var matchup = new BattleMatchup.Result("water", 2, BattleMatchup.DamageKind.NORMAL, "", "");
        assertTrue(MoveTooltipRenderer.consistentEstimate(matchup, estimate("water", 2, 30, 40)));
        assertFalse(MoveTooltipRenderer.consistentEstimate(matchup, estimate("normal", 1, 30, 40)));
        assertFalse(MoveTooltipRenderer.consistentEstimate(matchup, estimate("water", 0.5, 30, 40)));
        assertFalse(MoveTooltipRenderer.consistentEstimate(matchup, estimate("water", 2, Double.NaN, 40)));
        assertFalse(MoveTooltipRenderer.consistentEstimate(matchup, estimate("water", 2, 60, 40)));
        var immune = new BattleMatchup.Result("water", 0, BattleMatchup.DamageKind.NORMAL, "ability", "waterabsorb");
        assertFalse(MoveTooltipRenderer.consistentEstimate(immune, estimate("water", 0, 30, 40)));
    }

    private static TropimonDamageCalcBridge.DamageEstimate estimate(String type, double multiplier, double min, double max) {
        return new TropimonDamageCalcBridge.DamageEstimate(min, max, multiplier, "", List.of(), false,
                null, false, 80, type, "Target");
    }

    private static void assertTranslation(Text text, String key, Object... args) {
        var content = (TranslatableTextContent) text.getContent();
        assertEquals(key, content.getKey());
        assertEquals(List.of(args), List.of(content.getArgs()));
    }
}
