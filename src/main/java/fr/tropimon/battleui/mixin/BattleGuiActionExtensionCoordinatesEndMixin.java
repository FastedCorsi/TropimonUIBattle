package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import fr.tropimon.battleui.BattleActionExtensionScope;
import fr.tropimon.battleui.BattleActionPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Restores BattleGUI's real coordinates after optional action-button callbacks. */
@Mixin(value = BattleGUI.class, priority = 1500, remap = false)
abstract class BattleGuiActionExtensionCoordinatesEndMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"),
            argsOnly = true, ordinal = 0, remap = true)
    private int tropimonBattleUi$restoreExtensionRenderX(int x) {
        return BattleActionExtensionScope.open() ? x + BattleActionPanel.offsetX() : x;
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"),
            argsOnly = true, ordinal = 1, remap = true)
    private int tropimonBattleUi$restoreExtensionRenderY(int y) {
        return BattleActionExtensionScope.open() ? y + BattleActionPanel.offsetY() : y;
    }

    @ModifyVariable(method = "mouseClicked(DDI)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = true)
    private double tropimonBattleUi$restoreExtensionClickX(double x) {
        return BattleActionPanel.active() ? x + BattleActionPanel.offsetX() : x;
    }

    @ModifyVariable(method = "mouseClicked(DDI)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = true)
    private double tropimonBattleUi$restoreExtensionClickY(double y) {
        return BattleActionPanel.active() ? y + BattleActionPanel.offsetY() : y;
    }
}
