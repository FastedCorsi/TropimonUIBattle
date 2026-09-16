package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleReplacePokemonHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleReplacePokemonPacket;
import fr.tropimon.battleui.BattleUiState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleReplacePokemonHandler.class, remap = false)
abstract class BattleReplacePokemonHandlerMixin {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleReplacePokemonPacket;Lnet/minecraft/client/MinecraftClient;)V", at = @At("HEAD"))
    private void tropimonUiBattle$revealIllusion(BattleReplacePokemonPacket packet, MinecraftClient client, CallbackInfo ci) {
        BattleUiState.acceptIdentityReplacement(packet);
    }

    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleReplacePokemonPacket;Lnet/minecraft/client/MinecraftClient;)V", at = @At("RETURN"))
    private void tropimonUiBattle$refreshIdentity(BattleReplacePokemonPacket packet, MinecraftClient client, CallbackInfo ci) {
        BattleUiState.refreshTeams();
    }
}
