package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleEffectPresentationTest {
    @Test void largerCountersAndOutlinesFitBelowIconsAtEveryGuiSize() {
        for (int[] size : new int[][]{{320,180},{480,240},{960,540},{1920,1080}}) {
            var metrics = BattleEffectPresentation.metrics(size[0], size[1]);
            for (int textWidth : new int[]{12, 18, 28}) {
                float scale = BattleEffectPresentation.counterScale(textWidth, metrics.tileWidth());
                assertTrue(scale > 0.9F, "Counters must be larger than the old 0.72 scale, including ranges");
                assertTrue(textWidth * scale + 2 * scale <= metrics.tileWidth());
                assertTrue(metrics.iconSize() + 2 + 10 * scale < metrics.tileHeight());
            }
        }
        assertTrue(BattleEffectPresentation.counterScale(28, 12) * 28 <= 12,
                "An unusually narrow slot still cannot overflow");
    }

    @Test void expiringTurnsUseWarmColorsButHazardLayersAreNotExpiryWarnings() {
        var last = effect(1,1,false,BattleFieldEffects.EffectSide.FIELD);
        var soon = effect(2,2,false,BattleFieldEffects.EffectSide.FIELD);
        var range = effect(2,5,false,BattleFieldEffects.EffectSide.FIELD);
        assertEquals(0xFFFF6E79, BattleEffectPresentation.counterColor(last));
        assertEquals(0xFFFFCA62, BattleEffectPresentation.counterColor(soon));
        assertEquals(0xFF6CE5ED, BattleEffectPresentation.counterColor(range));
        assertEquals("2–5", range.iconCounter().getString(), "Uncertainty is preserved");
        var hazard = effect(0,0,true,BattleFieldEffects.EffectSide.OPPONENT_FIELD);
        assertEquals(0xFFFF8891, BattleEffectPresentation.counterColor(hazard));
        assertEquals("×2", hazard.iconCounter().getString());
    }

    private static BattleFieldEffects.EffectView effect(int min, int max, boolean layered,
                                                        BattleFieldEffects.EffectSide side) {
        return new BattleFieldEffects.EffectView(layered ? "foe.toxicspikes" : "snow", min, max, 5, 8, 2, layered, side);
    }
}
