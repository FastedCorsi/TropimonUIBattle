package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import fr.tropimon.battleui.BattleUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ActiveClientBattlePokemon.class, remap = false)
abstract class ActiveBattlePokemonMixin {
    @Inject(method = "setBattlePokemon", at = @At("HEAD"))
    private void tropimonUiBattle$rememberAppearance(ClientBattlePokemon pokemon, CallbackInfo ci) {
        BattleUiState.beforeActivePokemonChanged((ActiveClientBattlePokemon) (Object) this, pokemon);
    }
}
