package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import fr.tropimon.battleui.BattleActionExtensionScope;
import fr.tropimon.battleui.BattleActionPanel;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Closes after ordinary optional BattleGUI tail renderers. */
@Mixin(value = BattleGUI.class, priority = 2000, remap = false)
abstract class BattleGuiActionExtensionEndMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"), remap = true)
    private void tropimonBattleUi$endActionExtensions(DrawContext context, int mouseX, int mouseY,
                                                      float delta, CallbackInfo ci) {
        BattleActionExtensionScope.end(context);
        BattleActionPanel.redrawOptionalCalc(context, mouseX, mouseY);
    }
}
