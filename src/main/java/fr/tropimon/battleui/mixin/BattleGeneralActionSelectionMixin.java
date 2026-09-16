package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.battles.ForfeitActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import fr.tropimon.battleui.WildBattleFlee;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleGeneralActionSelection.class, remap = false)
abstract class BattleGeneralActionSelectionMixin {
    // Cobblemon 1.8 replaced this legacy prompt callback with a native
    // FleeAttemptActionResponse callback that captures BattleGUI and the request.
    // Keep the 1.7.2 workaround strictly scoped to the old descriptor so the
    // modern native flee flow is never intercepted.
    @Inject(method = "lambda$2$1(Lcom/cobblemon/mod/common/client/gui/battle/subscreen/" +
            "BattleGeneralActionSelection;)Lkotlin/Unit;", at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private static void tropimonBattleUi$runImmediately(BattleGeneralActionSelection selection,
                                                         CallbackInfoReturnable<Unit> cir) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        SingleActionRequest request = selection.getRequest();
        if (battle == null || !battle.isPvW() || request.getResponse() != null) return;

        WildBattleFlee.begin(battle);
        selection.getBattleGUI().selectAction(request, new ForfeitActionResponse());

        // Multi battles can have more than one pending slot. Cobblemon's own
        // forfeit confirmation fills those slots with Pass before dispatching.
        int remainingSlots = 8;
        SingleActionRequest remaining = battle.getFirstUnansweredRequest();
        while (remaining != null && remainingSlots-- > 0) {
            selection.getBattleGUI().selectAction(remaining, PassActionResponse.INSTANCE);
            remaining = battle.getFirstUnansweredRequest();
        }

        if (remaining != null) {
            WildBattleFlee.cancel();
            return;
        }

        battle.setMinimised(true);
        cir.setReturnValue(Unit.INSTANCE);
    }
}
