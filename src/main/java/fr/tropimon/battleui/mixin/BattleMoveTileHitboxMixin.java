package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleActionPanel;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps every native and replacement caller on the same translated move hitbox. */
@Mixin(value = BattleMoveSelection.MoveTile.class, remap = false)
abstract class BattleMoveTileHitboxMixin {
    @WrapMethod(method = "isHovered")
    private boolean tropimonBattleUi$alignMoveHitbox(double mouseX, double mouseY,
                                                     Operation<Boolean> original) {
        if (!BattleActionPanel.active()) return original.call(mouseX, mouseY);
        BattleMoveSelection.MoveTile tile = (BattleMoveSelection.MoveTile) (Object) this;
        return original.call(mouseX - BattleActionPanel.moveTileExtraX(tile.getX()),
                mouseY - BattleActionPanel.moveTileExtraY());
    }
}
