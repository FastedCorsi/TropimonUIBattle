package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortraitAnimationTest {
    @Test void resolvesTheProfileRendererForTheLoadedCobblemonGeneration() {
        boolean modernApi;
        try {
            Class.forName("com.cobblemon.mod.common.client.gui.ProfileTransformType");
            modernApi = true;
        } catch (ClassNotFoundException ignored) {
            modernApi = false;
        }
        assertEquals(modernApi ? "MODERN" : "LEGACY", PokemonPortraitRenderer.profileApiGeneration());
    }

    @Test void nativeFloatingAnimationAdvancesAtTheSameSpeedAtDifferentFrameRates() {
        for (int fps : new int[]{30,60,144}) {
            var state = new FloatingState();
            for (int frame = 0; frame < fps; frame++)
                state.updatePartialTicks(PokemonPortraitRenderer.animationStep(20.0F / fps, false));
            assertEquals(1.0F, state.getAnimationSeconds(), 0.00001F);
        }
    }

    @Test void pauseAndBadDeltasCannotBreakOrFastForwardThePortrait() {
        assertEquals(0, PokemonPortraitRenderer.animationStep(1, true));
        assertEquals(0, PokemonPortraitRenderer.animationStep(Float.NaN, false));
        assertEquals(0, PokemonPortraitRenderer.animationStep(Float.POSITIVE_INFINITY, false));
        assertEquals(0, PokemonPortraitRenderer.animationStep(-1, false));
        assertEquals(2, PokemonPortraitRenderer.animationStep(100, false));
    }
}
