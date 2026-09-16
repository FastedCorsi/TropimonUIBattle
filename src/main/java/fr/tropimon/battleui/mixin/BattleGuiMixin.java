package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import fr.tropimon.battleui.BattleUiRenderer;
import fr.tropimon.battleui.BattleActionPanel;
import fr.tropimon.battleui.BattlePokemonHudPanel;
import fr.tropimon.battleui.BattleTeamHudPanel;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleGUI.class, priority = 300, remap = false)
abstract class BattleGuiMixin {
    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void tropimonBattleUi$drag(double mouseX, double mouseY, int button, double deltaX, double deltaY,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (BattleUiRenderer.handleDrag(mouseX, mouseY, button)
                || BattleTeamHudPanel.drag(mouseX, mouseY, button)
                || BattlePokemonHudPanel.drag(mouseX, mouseY, button)
                || BattleActionPanel.drag(mouseX, mouseY, button))
            cir.setReturnValue(true);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("TAIL"),
            remap = true
    )
    private void tropimonBattleUi$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BattlePokemonHudPanel.renderHandles(context, mouseX, mouseY);
        BattleTeamHudPanel.renderHandles(context, mouseX, mouseY);
        BattleActionPanel.renderHandle(context, mouseX, mouseY);
        BattleUiRenderer.renderBattleScreen(context, mouseX, mouseY);
    }

    @Inject(
            method = "mouseClicked(DDI)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void tropimonBattleUi$click(double mouseX, double mouseY, int button,
                                        CallbackInfoReturnable<Boolean> cir) {
        // A missed native release must not leave one panel dragging while a
        // second panel starts. A second left click is the shared safe stop.
        if (BattleTeamHudPanel.dragging() || BattlePokemonHudPanel.dragging() || BattleActionPanel.dragging()) {
            if (button == 0) {
                BattleUiRenderer.finishHistoryInteraction();
                BattleTeamHudPanel.finish();
                BattlePokemonHudPanel.finish();
                BattleActionPanel.finish();
            }
            cir.setReturnValue(true);
            return;
        }
        if (BattleUiRenderer.handleClick(mouseX, mouseY, button)
                || BattleTeamHudPanel.click(mouseX, mouseY, button)
                || BattlePokemonHudPanel.click(mouseX, mouseY, button)
                || BattleActionPanel.click(mouseX, mouseY, button))
            cir.setReturnValue(true);
    }

}
