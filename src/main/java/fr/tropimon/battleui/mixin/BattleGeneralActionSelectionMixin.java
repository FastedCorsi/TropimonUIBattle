package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.battles.ForfeitActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.llamalad7.mixinextras.sugar.Local;
import fr.tropimon.battleui.WildBattleFlee;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleGeneralActionSelection.class, remap = false)
abstract class BattleGeneralActionSelectionMixin {
    // Capture the selection by type: the native callback's other arguments changed
    // in 1.8. Keep cancellation directly in this handler, not in a delegated helper.
    @Inject(method = "lambda$2$1", at = @At("HEAD"),
            cancellable = true, require = 1, remap = false)
    private static void tropimonBattleUi$runImmediately(CallbackInfoReturnable<Unit> cir,
            @Local(argsOnly = true) BattleGeneralActionSelection selection) {
        if (tropimonBattleUi$fleeWildBattle(selection)) cir.setReturnValue(Unit.INSTANCE);
    }

    private static boolean tropimonBattleUi$fleeWildBattle(BattleGeneralActionSelection selection) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        SingleActionRequest request = selection.getRequest();
        if (battle == null || !battle.isPvW() || request.getResponse() != null) return false;

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
            return false;
        }

        battle.setMinimised(true);
        return true;
    }
}
