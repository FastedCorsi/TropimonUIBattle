package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleShiftButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleActionPanel;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BattleShiftButton.class, remap = false)
abstract class BattleShiftPositionMixin {
    @Shadow @Final private float x;
    @Shadow @Final private float y;
    @WrapMethod(method = "render")
    private void tropimonBattleUi$render(DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
        if (!BattleActionPanel.moveMenuActive()) { original.call(context, mouseX, mouseY, delta); return; }
        context.getMatrices().push();
        try {
            context.getMatrices().translate(BattleActionPanel.shiftX() - x, BattleActionPanel.shiftY() - y, 0);
            original.call(context, mouseX, mouseY, delta);
        } finally { context.getMatrices().pop(); }
    }
    @WrapMethod(method = "isHovered")
    private boolean tropimonBattleUi$hover(double mouseX, double mouseY, Operation<Boolean> original) {
        if (!BattleActionPanel.moveMenuActive()) return original.call(mouseX, mouseY);
        return original.call(mouseX - BattleActionPanel.shiftX() + x, mouseY - BattleActionPanel.shiftY() + y);
    }
}
