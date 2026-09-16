package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import fr.tropimon.battleui.BattleUiRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleMessagePane.class, remap = false)
abstract class BattleMessagePaneMixin {
    @Inject(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void tropimonBattleUi$replaceNativeLog(DrawContext context, int mouseX, int mouseY,
                                                    float delta, CallbackInfo ci) {
        if (BattleUiRenderer.replacesNativeHistory()) ci.cancel();
    }
}
