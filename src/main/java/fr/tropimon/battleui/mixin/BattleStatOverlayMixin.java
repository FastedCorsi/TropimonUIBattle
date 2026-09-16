package fr.tropimon.battleui.mixin;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.tropimon.battleui.BattleStatBadges;
import fr.tropimon.battleui.BattlePokemonHudPanel;
import fr.tropimon.battleui.BattleUiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = BattleOverlay.class, remap = false)
abstract class BattleStatOverlayMixin {
    private static final java.util.Set<Identifier> TROPIMON_UI_BATTLE$NIGHT_BASES = java.util.Set.of(
            Identifier.of("cobblemon", "textures/gui/battle/battle_info_base.png"),
            Identifier.of("cobblemon", "textures/gui/battle/battle_info_base_flipped.png"),
            Identifier.of("cobblemon", "textures/gui/battle/battle_info_base_condensed.png"),
            Identifier.of("cobblemon", "textures/gui/battle/battle_info_base_flipped_condensed.png"),
            Identifier.of("cobblemon", "textures/gui/battle/battle_info_underlay.png")
    );

    @Shadow public abstract double getOpacity();

    @WrapMethod(method = "drawTile")
    private void tropimonBattleUi$statBadges(DrawContext context, float delta, ActiveClientBattlePokemon active,
                                            boolean own, int index, PokedexEntryProgress dex, boolean selected,
                                            boolean inactive, boolean compact, Operation<Void> original) {
        int dx = BattlePokemonHudPanel.offsetX(own, compact);
        int dy = BattlePokemonHudPanel.offsetY(own, compact) + BattleStatBadges.before(active, index, own, compact);
        original.call(context, delta, active, own, index, dex, selected, inactive, compact);
        context.getMatrices().push();
        try {
            context.getMatrices().translate(dx, dy, 0);
            BattleStatBadges.renderTrainer(context, active, own, index, compact, (float) getOpacity());
            BattleStatBadges.render(context, active, own, index, compact, (float) getOpacity());
        } finally { context.getMatrices().pop(); }
    }

    @ModifyArgs(method = "drawTile", at = @At(value = "INVOKE", target =
            "Lcom/cobblemon/mod/common/client/gui/battle/BattleOverlay;drawBattleTile(" +
            "Lnet/minecraft/client/gui/DrawContext;FFFZLcom/cobblemon/mod/common/pokemon/Species;" +
            "ILnet/minecraft/text/MutableText;Lcom/cobblemon/mod/common/pokemon/Gender;" +
            "Lcom/cobblemon/mod/common/pokemon/status/PersistentStatus;" +
            "Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;Lkotlin/Triple;" +
            "FLcom/cobblemon/mod/common/client/battle/ClientBallDisplay;IFZZZ" +
            "Lnet/minecraft/text/MutableText;ZLcom/cobblemon/mod/common/api/pokedex/PokedexEntryProgress;)V"), require = 1)
    private void tropimonBattleUi$moveCompleteTile(Args args, DrawContext context, float delta,
                                                   ActiveClientBattlePokemon active, boolean own, int index,
                                                   PokedexEntryProgress dex, boolean selected,
                                                   boolean inactive, boolean compact) {
        // drawBattleTile(context, x, y, delta, ...): keep the animation delta untouched.
        args.set(BattlePokemonHudPanel.DRAW_TILE_X_ARGUMENT,
                (Float) args.get(BattlePokemonHudPanel.DRAW_TILE_X_ARGUMENT)
                        + BattlePokemonHudPanel.offsetX(own, compact));
        args.set(BattlePokemonHudPanel.DRAW_TILE_Y_ARGUMENT,
                (Float) args.get(BattlePokemonHudPanel.DRAW_TILE_Y_ARGUMENT)
                        + BattlePokemonHudPanel.offsetY(own, compact)
                + BattleStatBadges.before(active, index, own, compact));
    }

    @ModifyArgs(method = "drawBattleTile", at = @At(value = "INVOKE", target =
            "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default(" +
            "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Identifier;" +
            "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;" +
            "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;" +
            "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;" +
            "Ljava/lang/Number;ZFILjava/lang/Object;)V"), require = 0)
    private void tropimonBattleUi$nightTileColor(Args args) {
        if (!BattleUiTheme.night() || !(args.get(1) instanceof Identifier texture)) return;
        if (!TROPIMON_UI_BATTLE$NIGHT_BASES.contains(texture)) return;
        // Same blue-night multiplier used by the Calc and Move panel plates.
        args.set(11, 0.32F);
        args.set(12, 0.56F);
        args.set(13, 0.82F);
        args.set(17, BattleUiTheme.explicitNativeRgbMask((Integer) args.get(17)));
    }
}
