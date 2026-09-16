package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleFaintHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket;
import fr.tropimon.battleui.BattleUiState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleFaintHandler.class, remap = false)
abstract class BattleFaintHandlerMixin {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleFaintPacket;Lnet/minecraft/client/MinecraftClient;)V", at = @At("HEAD"))
    private void tropimonUiBattle$trackFaint(BattleFaintPacket packet, MinecraftClient client, CallbackInfo ci) {
        BattleUiState.acceptFaint(packet);
    }
}
