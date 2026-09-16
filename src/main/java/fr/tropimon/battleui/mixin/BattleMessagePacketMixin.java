package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import fr.tropimon.battleui.BattleUiState;
import fr.tropimon.battleui.WildBattleFlee;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = BattleMessagePacket.class, remap = false)
abstract class BattleMessagePacketMixin {
    @Unique
    private boolean tropimonBattleUi$tracked;
    @Unique
    private List<Text> tropimonBattleUi$rewrittenMessages;

    @Inject(method = "getMessages", at = @At("RETURN"), cancellable = true)
    private void tropimonBattleUi$track(CallbackInfoReturnable<List<Text>> cir) {
        if (tropimonBattleUi$rewrittenMessages == null) {
            tropimonBattleUi$rewrittenMessages = WildBattleFlee.rewriteMessages(cir.getReturnValue());
        }
        cir.setReturnValue(tropimonBattleUi$rewrittenMessages);
        if (tropimonBattleUi$tracked) return;
        tropimonBattleUi$tracked = true;
        BattleUiState.acceptMessages(tropimonBattleUi$rewrittenMessages);
    }
}
