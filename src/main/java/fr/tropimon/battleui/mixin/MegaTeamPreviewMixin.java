package fr.tropimon.battleui.mixin;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional presentation hook: hide the side columns, not battle state or gimmicks. */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.client.battle.hud.TeamPreviewWidget", remap = false)
abstract class MegaTeamPreviewMixin {
    // Named development and intermediary production names of the inherited widget method.
    @Inject(method = {"renderWidget", "method_48579"}, at = @At("HEAD"),
            cancellable = true, remap = false, require = 1)
    private void tropimonBattleUi$hideSideTeamPreview(DrawContext context, int mouseX, int mouseY,
            float delta, CallbackInfo ci) {
        ci.cancel();
    }
}
