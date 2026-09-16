package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stat stages under the native HP bar; the native entry snapshot is advanced by public messages. */
public final class BattleStatBadges {
    static final Identifier INCREASE = Identifier.of("cobblemon", "textures/gui/summary/summary_stats_icon_increase.png");
    static final Identifier DECREASE = Identifier.of("cobblemon", "textures/gui/summary/summary_stats_icon_decrease.png");
    static final int BAR_WIDTH = 97, BADGE_WIDTH = 31, BADGE_HEIGHT = 9, GAP = 1, COLUMNS = 3;
    private static final List<String> ORDER = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
    private BattleStatBadges() { }

    record Stage(String id, int value) { }
    record Position(int x, int y) { }

    static List<Stage> stages(Map<Stat, Integer> changes) {
        if (changes == null || changes.isEmpty()) return List.of();
        List<Stage> stages = new ArrayList<>();
        for (String id : ORDER) {
            for (var entry : changes.entrySet()) {
                if (entry.getKey().getShowdownId().equals(id) && entry.getValue() != null && entry.getValue() != 0)
                    stages.add(new Stage(id, Math.clamp(entry.getValue(), -6, 6)));
            }
        }
        return stages;
    }

    private static List<Stage> stages(ClientBattlePokemon pokemon) {
        if (pokemon == null || pokemon.getHpValue() <= 0) return List.of();
        List<Stage> result = new ArrayList<>();
        for (StatStageView stage : BattleUiState.statStages(pokemon)) {
            if (ORDER.contains(stage.id()) && stage.stage() != 0) {
                result.add(new Stage(stage.id(), Math.clamp(stage.stage(), -6, 6)));
            }
        }
        return List.copyOf(result);
    }

    static Position position(int index, boolean opponent) {
        int column = index % COLUMNS;
        int x = opponent ? BAR_WIDTH - BADGE_WIDTH - column * (BADGE_WIDTH + GAP) : column * (BADGE_WIDTH + GAP);
        return new Position(x, index / COLUMNS * (BADGE_HEIGHT + GAP));
    }

    static int top(boolean compact, boolean status) {
        return (compact ? 22 : 28) + (status ? 7 : 0) + 1;
    }

    static int extraHeight(int count, boolean compact, boolean status) {
        if (count == 0) return 0;
        int rows = (count + COLUMNS - 1) / COLUMNS;
        int bottom = top(compact, status) + rows * (BADGE_HEIGHT + GAP);
        return Math.max(0, bottom - (compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT));
    }

    private static int extraHeight(ClientBattlePokemon pokemon, boolean compact) {
        if (pokemon == null || pokemon.getHpValue() <= 0) return 0;
        int count = stages(pokemon).size();
        return extraHeight(count, compact, pokemon.getStatus() != null);
    }

    /** Leave room between compact native cards when a previous card wraps to several rows. */
    public static int before(ActiveClientBattlePokemon active, int index, boolean own, boolean compact) {
        var side = active.getActor().getSide();
        int count = 0;
        for (var ignored : side.getActiveClientBattlePokemon()) count++;
        int result = 0, ordinal = 0;
        for (var slot : side.getActiveClientBattlePokemon()) {
            int nativeIndex = own ? ordinal : count - ordinal - 1;
            if (nativeIndex < index) result += extraHeight(slot.getBattlePokemon(), compact);
            ordinal++;
        }
        return result;
    }

    static int sideExtraHeight(boolean own) {
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return 0;
        var player = MinecraftClient.getInstance().player;
        ClientBattleSide local = BattleUiState.leftSide(battle, player == null ? null : player.getUuid());
        if (local == null) return 0;
        var side = own ? local : (local == battle.getSide1() ? battle.getSide2() : battle.getSide1());
        boolean compact = battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
        int result = 0;
        for (var slot : side.getActiveClientBattlePokemon()) result += extraHeight(slot.getBattlePokemon(), compact);
        return result;
    }

    static int trainerTopInset(boolean own) {
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return 0;
        var player = MinecraftClient.getInstance().player;
        ClientBattleSide local = BattleUiState.leftSide(battle, player == null ? null : player.getUuid());
        if (local == null) return 0;
        var side = own ? local : (local == battle.getSide1() ? battle.getSide2() : battle.getSide1());
        for (var slot : side.getActiveClientBattlePokemon()) {
            var pokemon = slot.getBattlePokemon();
            if (pokemon != null && !BattleUiState.trainerName(pokemon.getUuid()).isBlank()) return 10;
        }
        return 0;
    }

    public static void render(DrawContext context, ActiveClientBattlePokemon active, boolean own,
                              int index, boolean compact, float opacity) {
        var pokemon = active.getBattlePokemon();
        var stages = stages(pokemon);
        if (stages.isEmpty()) return;
        int actorIndex = (Character.digit(active.getActorShowdownId().charAt(1), 10) - 1) / 2;
        int actorOffset = (own ? actorIndex : active.getFormat().getBattleType().getActorsPerSide() - 1 - actorIndex) * 10;
        int y = BattleOverlay.VERTICAL_INSET + index * (compact ? BattleOverlay.COMPACT_VERTICAL_SPACING : BattleOverlay.VERTICAL_SPACING)
                + actorOffset + top(compact, pokemon.getStatus() != null);
        int x = Math.round(active.getXDisplacement()) + (own ? (compact ? 26 : 38) : 5);
        var renderer = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < stages.size(); i++) {
            var stage = stages.get(i);
            var position = position(i, !own);
            int bx = x + position.x(), by = y + position.y();
            BattleUiSkin.drawMovePpBackground(context, bx, by, BADGE_WIDTH, BADGE_HEIGHT, opacity);
            BattleUiSkin.drawScaledRegionAlpha(context, stage.value() > 0 ? INCREASE : DECREASE,
                    bx + 2, by + 2, 6, 5, 0, 0, 8, 6, 8, 6, opacity);
            Text label = Text.translatable("text.tropimon_ui_battle.stat_short." + stage.id())
                    .append((stage.value() > 0 ? "+" : "") + stage.value())
                    .styled(style -> style.withBold(true));
            float scale = Math.min(0.80F, 21F / Math.max(1, renderer.getWidth(label)));
            int color = ((int) (Math.clamp(opacity, 0, 1) * 255) << 24)
                    | 0xF4F7F8;
            if ((color >>> 24) < 4) continue;
            context.getMatrices().push();
            try {
                context.getMatrices().translate(bx + 9, by + (BADGE_HEIGHT - 8 * scale) / 2, 0);
                context.getMatrices().scale(scale, scale, 1);
                context.drawText(renderer, label, 0, 0, color, true);
            } finally { context.getMatrices().pop(); }
        }
    }

    public static void renderTrainer(DrawContext context, ActiveClientBattlePokemon active, boolean own,
                                     int index, boolean compact, float opacity) {
        if (!trainerVisible(active, own, index, compact)) return;
        var pokemon = active.getBattlePokemon();
        if (pokemon == null) return;
        String trainer = BattleUiState.trainerName(pokemon.getUuid());
        if (trainer.isBlank()) return;
        int actorIndex = (Character.digit(active.getActorShowdownId().charAt(1), 10) - 1) / 2;
        int actorOffset = (own ? actorIndex : active.getFormat().getBattleType().getActorsPerSide() - 1 - actorIndex) * 10;
        int baseY = BattleOverlay.VERTICAL_INSET + index * (compact ? BattleOverlay.COMPACT_VERTICAL_SPACING : BattleOverlay.VERTICAL_SPACING)
                + actorOffset;
        int x = Math.round(active.getXDisplacement()) + (own ? (compact ? 26 : 38) : 5);
        var renderer = MinecraftClient.getInstance().textRenderer;
        String fitted = renderer.trimToWidth(trainer, BAR_WIDTH);
        int alpha = (int) (Math.clamp(opacity, 0.0F, 1.0F) * 255.0F);
        if (alpha < 4) return;
        int color = alpha << 24 | (own ? 0x55D5DE : 0xFF7580);
        context.drawTextWithShadow(renderer, fitted,
                x + (BAR_WIDTH - renderer.getWidth(fitted)) / 2, Math.max(1, baseY - 10), color);
    }

    static boolean trainerVisible(ActiveClientBattlePokemon active, boolean own, int index, boolean compact) {
        if (!compact || active == null || active.getActor().getSide() == null) return true;
        Iterable<ActiveClientBattlePokemon> slots = active.getActor().getSide().getActiveClientBattlePokemon();
        int count = 0;
        for (var ignored : slots) count++;
        int first = Integer.MAX_VALUE;
        int ordinal = 0;
        for (var slot : slots) {
            int nativeIndex = own ? ordinal : count - ordinal - 1;
            if (slot.getActor() == active.getActor()) first = Math.min(first, nativeIndex);
            ordinal++;
        }
        return first == Integer.MAX_VALUE || index == first;
    }
}
