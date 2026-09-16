package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import fr.tropimon.battleui.MoveTooltipRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleTargetSelection.class, remap = false)
abstract class BattleTargetRenderMixin {
    @Inject(method = "renderWidget", at = @At("TAIL"), remap = true)
    private void tropimonBattleUi$targetDetails(DrawContext context, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        MoveTooltipRenderer.renderTarget((BattleTargetSelection) (Object) this, context, mouseX, mouseY);
    }
}
