package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the native left sidebar, not UI Battle's teams or the starter prompt. */
@Mixin(value = PartyOverlay.class, remap = false)
abstract class PartySidebarMixin {
    // Both supported versions handle the starter prompt before sizing/drawing the sidebar.
    // Do not cancel at HEAD: an empty party must still be able to choose its starter.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;getWindow()Lnet/minecraft/client/util/Window;"),
            cancellable = true, remap = true, require = 1)
    private void tropimonBattleUi$hideNativeSidebar(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ci.cancel();
    }
}
