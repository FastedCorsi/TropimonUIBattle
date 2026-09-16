package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleActionPanel;
import fr.tropimon.battleui.MoveTooltipRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {BattleGeneralActionSelection.class, BattleMoveSelection.class}, remap = false)
abstract class BattleActionRenderMixin {
    @WrapMethod(method = "renderWidget", remap = true)
    private void tropimonBattleUi$moveActions(DrawContext context, int mouseX, int mouseY, float delta,
                                             Operation<Void> original) {
        if (!BattleActionPanel.active()) {
            original.call(context, mouseX, mouseY, delta);
            return;
        }
        int dx = BattleActionPanel.offsetX(), dy = BattleActionPanel.offsetY();
        context.getMatrices().push();
        try {
            context.getMatrices().translate(dx, dy, 0);
            original.call(context, mouseX - dx, mouseY - dy, delta);
            if ((Object) this instanceof BattleGeneralActionSelection)
                BattleActionPanel.redrawNightGeneralActions(context, this, mouseX - dx, mouseY - dy);
        } finally { context.getMatrices().pop(); }
        // The tooltip must use real screen bounds, not the translated menu coordinates.
        if ((Object) this instanceof BattleMoveSelection moves)
            MoveTooltipRenderer.render(moves, context, mouseX, mouseY);
    }

    @WrapMethod(method = "mousePrimaryClicked", remap = true)
    private boolean tropimonBattleUi$moveActionClicks(double mouseX, double mouseY,
                                                       Operation<Boolean> original) {
        if (!BattleActionPanel.active()) return original.call(mouseX, mouseY);
        return original.call(mouseX - BattleActionPanel.offsetX(), mouseY - BattleActionPanel.offsetY());
    }
}
