package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.tropimon.battleui.BattleActionPanel;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the four move tiles on the exact same grid as the general action tiles. */
@Mixin(value = BattleMoveSelection.class, remap = false)
abstract class BattleMoveTileAlignmentMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/gui/battle/subscreen/BattleMoveSelection$MoveTile;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void tropimonBattleUi$alignMoveTile(BattleMoveSelection.MoveTile tile, DrawContext context,
                                                int mouseX, int mouseY, float delta,
                                                Operation<Void> original) {
        if (!BattleActionPanel.active()) {
            original.call(tile, context, mouseX, mouseY, delta);
            return;
        }
        int extraX = BattleActionPanel.moveTileExtraX(tile.getX());
        int extraY = BattleActionPanel.moveTileExtraY();
        context.getMatrices().push();
        try {
            context.getMatrices().translate(extraX, extraY, 0);
            // Hover coordinates are corrected in MoveTile.isHovered itself. Keeping that
            // correction on the tile also supports Mega Showdown's replacement click method.
            original.call(tile, context, mouseX, mouseY, delta);
        } finally {
            context.getMatrices().pop();
        }
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/gui/battle/subscreen/BattleBackButton;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void tropimonBattleUi$placeBackAboveMoves(BattleBackButton back, DrawContext context,
                                                      int mouseX, int mouseY, float delta,
                                                      Operation<Void> original) {
        if (!BattleActionPanel.moveMenuActive()) {
            original.call(back, context, mouseX, mouseY, delta);
            return;
        }
        context.getMatrices().push();
        try {
            context.getMatrices().translate(BattleActionPanel.moveBackExtraX(),
                    BattleActionPanel.moveBackExtraY(), 0);
            original.call(back, context, mouseX, mouseY, delta);
        } finally {
            context.getMatrices().pop();
        }
    }
}
