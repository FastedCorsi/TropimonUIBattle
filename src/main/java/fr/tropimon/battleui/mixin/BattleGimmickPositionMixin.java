package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGimmickButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleActionPanel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Moves native and Mega Showdown gimmicks together with their actual hitboxes. */
@Mixin(value = BattleGimmickButton.class, remap = false)
abstract class BattleGimmickPositionMixin {
    @Shadow @Final private float x;
    @Shadow @Final private float y;

    @WrapMethod(method = "render")
    private void tropimonBattleUi$render(MatrixStack matrices, int mouseX, int mouseY, float delta,
                                         Operation<Void> original) {
        var placement = BattleActionPanel.gimmickPlacement(this);
        if (placement == null) { original.call(matrices, mouseX, mouseY, delta); return; }
        matrices.push();
        try {
            matrices.translate(placement.x(), placement.y(), 0);
            matrices.scale(placement.scale(), placement.scale(), 1);
            matrices.translate(-x, -y, 0);
            // isHovered receives the unmodified panel coordinates and maps them once below.
            original.call(matrices, mouseX, mouseY, delta);
        } finally { matrices.pop(); }
    }

    @WrapMethod(method = "isHovered")
    private boolean tropimonBattleUi$hover(double mouseX, double mouseY, Operation<Boolean> original) {
        var placement = BattleActionPanel.gimmickPlacement(this);
        if (placement == null) return original.call(mouseX, mouseY);
        return original.call(placement.nativeX(mouseX, x), placement.nativeY(mouseY, y));
    }
}
