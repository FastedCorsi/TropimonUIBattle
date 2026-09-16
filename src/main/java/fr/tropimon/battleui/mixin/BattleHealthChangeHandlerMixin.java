package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleHealthChangeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import fr.tropimon.battleui.BattleUiState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleHealthChangeHandler.class, remap = false)
abstract class BattleHealthChangeHandlerMixin {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleHealthChangePacket;Lnet/minecraft/client/MinecraftClient;)V", at = @At("HEAD"))
    private void tropimonUiBattle$trackPercentage(BattleHealthChangePacket packet, MinecraftClient client,
                                                  CallbackInfo ci) {
        BattleUiState.acceptHealthChange(packet);
    }
}
