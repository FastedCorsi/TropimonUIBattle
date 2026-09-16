package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import fr.tropimon.battleui.SwitchMatchupRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleSwitchPokemonSelection.class, remap = false)
abstract class BattleSwitchRenderMixin {
    @Inject(method = "renderWidget", at = @At("TAIL"), remap = true)
    private void tropimonBattleUi$switchMatchup(DrawContext context, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        SwitchMatchupRenderer.render((BattleSwitchPokemonSelection) (Object) this, context, mouseX, mouseY);
    }
}
