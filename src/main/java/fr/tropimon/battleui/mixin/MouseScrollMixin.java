package fr.tropimon.battleui.mixin;

import fr.tropimon.battleui.BattleUiRenderer;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseScrollMixin {
    @Shadow private double x;
    @Shadow private double y;
    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void tropimonBattleUi$release(long window, int button, int action, int modifiers, CallbackInfo ci) {
        // TAIL preserves Mouse's internal pressed-button state and normal release handling.
        if (button == 0 && action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            BattleUiRenderer.finishHistoryInteraction();
            fr.tropimon.battleui.BattleActionPanel.finish();
            fr.tropimon.battleui.BattlePokemonHudPanel.finish();
            fr.tropimon.battleui.BattleTeamHudPanel.finish();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void tropimonBattleUi$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (BattleUiRenderer.handleScroll(x, y, vertical)) ci.cancel();
    }
}
