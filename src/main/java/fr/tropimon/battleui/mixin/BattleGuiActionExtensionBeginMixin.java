package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import fr.tropimon.battleui.BattleActionExtensionScope;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens before ordinary optional BattleGUI tail renderers; it has no optional-mod reference. */
@Mixin(value = BattleGUI.class, priority = 400, remap = false)
abstract class BattleGuiActionExtensionBeginMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"), remap = true)
    private void tropimonBattleUi$beginActionExtensions(DrawContext context, int mouseX, int mouseY,
                                                        float delta, CallbackInfo ci) {
        BattleActionExtensionScope.begin(context);
    }
}
