package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleActionPanel;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps Cobblemon and replacement click handlers on the relocated Back button. */
@Mixin(value = BattleBackButton.class, remap = false)
abstract class BattleMoveBackHitboxMixin {
    @WrapMethod(method = "isHovered")
    private boolean tropimonBattleUi$placeBackHitbox(double mouseX, double mouseY,
                                                      Operation<Boolean> original) {
        if (!BattleActionPanel.moveMenuActive()) return original.call(mouseX, mouseY);
        return original.call(mouseX - BattleActionPanel.moveBackExtraX(),
                mouseY - BattleActionPanel.moveBackExtraY());
    }
}
