package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.nio.file.Path;

/** Moves each complete native HP stack, including trainer labels, stages and side effects. */
public final class BattlePokemonHudPanel {
    // Cobblemon drawBattleTile(context, x, y, delta, ...).
    public static final int DRAW_TILE_X_ARGUMENT = 1;
    public static final int DRAW_TILE_Y_ARGUMENT = 2;
    private static final BattlePokemonHudLayout LAYOUT = new BattlePokemonHudLayout();
    private static final LastValueCache<InsetKey, Insets> OWN_INSETS = new LastValueCache<>();
    private static final LastValueCache<InsetKey, Insets> OPPONENT_INSETS = new LastValueCache<>();
    private static final LastValueCache<EffectCountKey, EffectCounts> EFFECT_COUNTS = new LastValueCache<>();
    private static Path settings;

    private BattlePokemonHudPanel() { }

    public static int offsetX(boolean own, boolean compact) {
        var placement = placement(own, compact);
        int nativeX = own ? BattleOverlay.HORIZONTAL_INSET
                : screenWidth() - BattleOverlay.HORIZONTAL_INSET - placement.width();
        return placement.x() - nativeX;
    }

    public static int offsetY(boolean own, boolean compact) {
        return placement(own, compact).y() - BattleOverlay.VERTICAL_INSET;
    }

    public static void renderHandles(DrawContext context, int mouseX, int mouseY) {
        if (!active()) return;
        boolean compact = compact();
        renderHandle(context, placement(true, compact), mouseX, mouseY);
        renderHandle(context, placement(false, compact), mouseX, mouseY);
    }

    private static void renderHandle(DrawContext context, BattlePokemonHudLayout.Placement placement,
                                     int mouseX, int mouseY) {
        boolean hovered = placement.onHandle(mouseX, mouseY)
                && !BattleUiRenderer.historyCoversPointer(mouseX, mouseY);
        drawMoveHandle(context, placement.handleX(), placement.handleY(), hovered,
                "text.tropimon_ui_battle.pokemon_hud.move_hint", mouseX, mouseY);
    }

    static void drawMoveHandle(DrawContext context, int x, int y, boolean hovered,
                               String hintKey, int mouseX, int mouseY) {
        BattleUiSkin.drawCobblemonButton(context, x, y, BattlePokemonHudLayout.HANDLE_SIZE,
                BattlePokemonHudLayout.HANDLE_SIZE, hovered);
        int arrow = BattleUiTheme.night() ? 0xFF80D8FF : 0xFFFFFFFF;
        int shade = BattleUiTheme.night() ? 0xFF164A6A : 0xFF40515C;
        drawDirectionGlyph(context, x + 7, y + 7, shade);
        drawDirectionGlyph(context, x + 6, y + 6, arrow);
        if (hovered) context.drawTooltip(MinecraftClient.getInstance().textRenderer,
                Text.translatable(hintKey), mouseX, mouseY);
    }

    private static void drawDirectionGlyph(DrawContext context, int cx, int cy, int color) {
        context.fill(cx, cy - 4, cx + 1, cy - 1, color);
        context.fill(cx - 1, cy - 3, cx + 2, cy - 2, color);
        context.fill(cx, cy + 2, cx + 1, cy + 5, color);
        context.fill(cx - 1, cy + 3, cx + 2, cy + 4, color);
        context.fill(cx - 4, cy, cx - 1, cy + 1, color);
        context.fill(cx - 3, cy - 1, cx - 2, cy + 2, color);
        context.fill(cx + 2, cy, cx + 5, cy + 1, color);
        context.fill(cx + 3, cy - 1, cx + 4, cy + 2, color);
    }

    public static boolean click(double x, double y, int button) {
        if (!active() || BattleUiRenderer.historyCoversPointer(x, y)) return false;
        if (LAYOUT.dragging()) {
            if (button == 0) finish();
            return true;
        }
        boolean compact = compact();
        for (boolean own : new boolean[]{true, false}) {
            var placement = placement(own, compact);
            if (button == 1 && placement.onHandle(x, y)) {
                LAYOUT.reset(own);
                finish();
                return true;
            }
            if (LAYOUT.begin(own, placement, x, y, button)) return true;
        }
        return false;
    }

    public static boolean drag(double x, double y, int button) {
        if (button != 0 || !LAYOUT.dragging()) return false;
        if (!active()) {
            finish();
            return true;
        }
        boolean compact = compact();
        var dimensions = dimensions(LAYOUT.draggingOwn(), compact);
        var insets = insets(LAYOUT.draggingOwn(), compact);
        var window = MinecraftClient.getInstance().getWindow();
        return LAYOUT.drag(x, y, window.getScaledWidth(), window.getScaledHeight(),
                dimensions.width(), dimensions.height(), insets.top(), insets.bottom());
    }

    public static boolean dragging() { return LAYOUT.dragging(); }

    public static void finish() {
        if (!LAYOUT.finish() || settings == null) return;
        try { LAYOUT.save(settings); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save the active Pokemon HUD positions.");
        }
    }

    static void load(Path file) {
        settings = file;
        try { LAYOUT.load(file); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not load the active Pokemon HUD positions; using defaults.");
        }
    }

    private static boolean active() {
        return MinecraftClient.getInstance().currentScreen instanceof BattleGUI
                && CobblemonClient.INSTANCE.getBattle() != null;
    }

    private static boolean compact() {
        var battle = CobblemonClient.INSTANCE.getBattle();
        return battle != null && battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
    }

    private static BattlePokemonHudLayout.Placement placement(boolean own, boolean compact) {
        var window = MinecraftClient.getInstance().getWindow();
        var dimensions = dimensions(own, compact);
        var insets = insets(own, compact);
        return LAYOUT.layout(own, window.getScaledWidth(), window.getScaledHeight(),
                dimensions.width(), dimensions.height(),
                own ? 12 : window.getScaledWidth() - 12 - dimensions.width(), 10,
                insets.top(), insets.bottom());
    }

    private static Insets insets(boolean own, boolean compact) {
        var client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int ownTeamSize = Math.max(BattleUiState.ownTeam().size(), 6);
        int opponentSlots = Math.max(1, BattleUiState.opponentSlotCount());
        int top = BattleStatBadges.trainerTopInset(own);
        var battle = CobblemonClient.INSTANCE.getBattle();
        var countKey = new EffectCountKey(battle == null ? null : battle.getBattleId(), BattleUiState.turn(),
                BattleUiState.logRevision());
        EffectCounts counts = EFFECT_COUNTS.get(countKey, BattlePokemonHudPanel::countEffects);
        int count = own ? counts.own() : counts.opponent();
        var key = new InsetKey(screenWidth, screenHeight, compact, ownTeamSize, opponentSlots, count, top);
        return (own ? OWN_INSETS : OPPONENT_INSETS).get(key,
                () -> calculateInsets(compact, screenWidth, screenHeight, ownTeamSize, opponentSlots, count, top));
    }

    private static EffectCounts countEffects() {
        int own = 0, opponent = 0;
        for (var effect : BattleUiState.effects()) {
            if (effect.side() == BattleFieldEffects.EffectSide.PLAYER_FIELD) own++;
            else if (effect.side() == BattleFieldEffects.EffectSide.OPPONENT_FIELD) opponent++;
        }
        return new EffectCounts(own, opponent);
    }

    private static Insets calculateInsets(boolean compact, int screenWidth, int screenHeight,
                                           int ownTeamSize, int opponentSlots, int count, int top) {
        BattleUiLayout ui = BattleUiLayout.calculate(screenWidth, screenHeight, ownTeamSize, opponentSlots);
        int bottom = 0;
        if (count > 0) {
            var metrics = BattleEffectPresentation.metrics(screenWidth, screenHeight);
            int inset = ui.sideEffectsInset(metrics.tileWidth(), compact);
            bottom = 2 + BattleEffectRowLayout.requiredHeight(count, ui.sideEffectsWidth(inset),
                    metrics.tileWidth(), metrics.tileHeight(), metrics.gap());
        }
        return new Insets(top, bottom);
    }

    private static Dimensions dimensions(boolean own, boolean compact) {
        int cards = 1, actors = 1, slotsPerActor = 1;
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != null) {
            var type = battle.getBattleFormat().getBattleType();
            cards = Math.max(1, type.getPokemonPerSide());
            actors = Math.max(1, type.getActorsPerSide());
            slotsPerActor = Math.max(1, type.getSlotsPerActor());
        }
        int width = (compact ? BattleOverlay.COMPACT_TILE_WIDTH : BattleOverlay.TILE_WIDTH)
                + (slotsPerActor - 1) * BattleOverlay.HORIZONTAL_SPACING;
        int spacing = compact ? BattleOverlay.COMPACT_VERTICAL_SPACING : BattleOverlay.VERTICAL_SPACING;
        int tileHeight = compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT;
        int height = (cards - 1) * spacing + (actors - 1) * 10 + tileHeight
                + BattleStatBadges.sideExtraHeight(own);
        return new Dimensions(width, Math.max(tileHeight, height));
    }

    private static int screenWidth() { return MinecraftClient.getInstance().getWindow().getScaledWidth(); }

    private record Dimensions(int width, int height) { }
    private record Insets(int top, int bottom) { }
    private record InsetKey(int screenWidth, int screenHeight, boolean compact, int ownTeamSize,
                            int opponentSlots, int effectCount, int trainerTop) { }
    private record EffectCountKey(java.util.UUID battleId, int turn, long logRevision) { }
    private record EffectCounts(int own, int opponent) { }
}
