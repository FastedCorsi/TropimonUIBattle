package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.battle.subscreen.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Client-thread presentation only: native battle actions still handle all decisions. */
public final class BattleActionPanel {
    private static final BattleActionLayout LAYOUT = new BattleActionLayout();
    private static Path settings;
    private BattleActionPanel() { }

    public static boolean movable(Object selection) {
        return selection instanceof BattleGeneralActionSelection || selection instanceof BattleMoveSelection;
    }

    static BattleActionLayout.Placement placement() {
        var window = MinecraftClient.getInstance().getWindow();
        return LAYOUT.layout(window.getScaledWidth(), window.getScaledHeight(), contentWidth(), contentHeight());
    }

    public static boolean active() {
        return BattleUiRenderer.replacesNativeHistory()
                && MinecraftClient.getInstance().currentScreen instanceof BattleGUI gui
                && movable(gui.getCurrentActionSelection());
    }

    private static Object currentSelection() {
        return MinecraftClient.getInstance().currentScreen instanceof BattleGUI gui
                ? gui.getCurrentActionSelection() : null;
    }

    private static int handleOffsetX() {
        if (currentSelection() instanceof BattleMoveSelection) return BattleActionLayout.MOVE_HANDLE_OFFSET_X;
        return hasOptionalCalc() ? BattleActionLayout.GENERAL_HANDLE_OFFSET_X
                : BattleActionLayout.NATIVE_HANDLE_OFFSET_X;
    }

    private static int contentWidth() {
        // Actions and attacks must use one identical horizontal anchor. Otherwise a
        // right-edge placement changes x when Cobblemon opens or closes the move menu.
        return BattleActionLayout.sharedContentWidth(hasOptionalCalc());
    }

    private static boolean hasOptionalCalc() {
        return FabricLoader.getInstance().isModLoaded("tropimon_damage_calc");
    }

    private static int contentHeight() {
        return currentSelection() instanceof BattleGeneralActionSelection
                ? BattleActionLayout.GENERAL_ACTION_HEIGHT : BattleActionLayout.HEIGHT;
    }

    public static int offsetX() { return placement().dx(); }
    public static int offsetY() { return placement().dy(); }

    private static BattleActionLayout.Placement actionGridPlacement() {
        var window = MinecraftClient.getInstance().getWindow();
        int width = BattleActionLayout.sharedContentWidth(hasOptionalCalc());
        return LAYOUT.layout(window.getScaledWidth(), window.getScaledHeight(), width,
                BattleActionLayout.GENERAL_ACTION_HEIGHT);
    }

    public static int moveTileExtraX(float nativeTileX) {
        var grid = actionGridPlacement();
        var controls = placement();
        return BattleActionLayout.moveTileExtraX(grid, controls, nativeTileX);
    }

    public static int moveTileExtraY() {
        var grid = actionGridPlacement();
        var controls = placement();
        return BattleActionLayout.moveTileExtraY(grid, controls);
    }

    public static int moveTileOffsetX(float nativeTileX) { return offsetX() + moveTileExtraX(nativeTileX); }
    public static int moveTilesOffsetY() { return offsetY() + moveTileExtraY(); }
    public static boolean moveMenuActive() {
        return active() && currentSelection() instanceof BattleMoveSelection;
    }
    public static int moveBackExtraX() { return BattleActionLayout.moveBackExtraX(); }
    public static int moveBackExtraY() { return BattleActionLayout.moveBackExtraY(); }

    public static BattleActionLayout.Control gimmickPlacement(Object button) {
        if (!moveMenuActive() || !(currentSelection() instanceof BattleMoveSelection moves)) return null;
        int index = moves.getGimmickButtons().indexOf(button);
        if (index < 0) return null;
        return BattleActionLayout.gimmick(index, moves.getGimmickButtons().size(),
                MinecraftClient.getInstance().getWindow().getScaledHeight());
    }

    public static float shiftX() { return BattleActionLayout.shift(0).x(); }
    public static float shiftY() {
        return BattleActionLayout.shift(MinecraftClient.getInstance().getWindow().getScaledHeight()).y();
    }

    public static void redrawNightGeneralActions(DrawContext context, Object selection, int mouseX, int mouseY) {
        if (!BattleUiTheme.night() || !(selection instanceof BattleGeneralActionSelection general)) return;
        float opacity = (float) CobblemonClient.INSTANCE.getBattleOverlay().getOpacityRatio();
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textColor = Math.round(255.0F * opacity) << 24 | 0xFFFFFF;
        context.getMatrices().push();
        try {
            // Draw above Cobblemon's original grey plate while retaining each native action icon and accent.
            context.getMatrices().translate(0, 0, 2);
            for (var tile : general.getTiles()) {
                boolean hovered = tile.isHovered(mouseX, mouseY);
                BattleUiSkin.drawCobblemonButton(context, tile.getX(), tile.getY(), 90, 26, hovered, opacity);
                // Keep Cobblemon's complete bevel so the icon tray is part of the button, not pasted onto it.
                BattleUiSkin.drawIntegratedActionIcon(context, tile.getResource(), tile.getX(), tile.getY(),
                        hovered, opacity);
                context.drawText(renderer, tile.getText(), tile.getX() + 6, tile.getY() + 8, textColor, true);
            }
        } finally { context.getMatrices().pop(); }
    }

    public static void renderHandle(DrawContext context, int mouseX, int mouseY) {
        if (!active()) return;
        var p = placement();
        int handleOffsetX = handleOffsetX();
        boolean hovered = p.onHandle(mouseX, mouseY, handleOffsetX)
                && !BattleUiRenderer.historyCoversPointer(mouseX, mouseY);
        int x = p.handleX(handleOffsetX), y = p.handleY();
        BattleUiSkin.drawCobblemonButton(context, x, y, BattleActionLayout.HANDLE_WIDTH,
                BattleActionLayout.HANDLE_HEIGHT, hovered);
        int arrow = BattleUiTheme.night() ? 0xFF80D8FF : 0xFFFFFFFF;
        int shade = BattleUiTheme.night() ? 0xFF164A6A : 0xFF40515C;
        drawDirectionGlyph(context, x + 9, y + 9, shade);
        drawDirectionGlyph(context, x + 8, y + 8, arrow);
        var renderer = MinecraftClient.getInstance().textRenderer;
        if (hovered) context.drawTooltip(renderer,
                Text.translatable("text.tropimon_ui_battle.actions.move_hint"), mouseX, mouseY);
    }

    private static void drawDirectionGlyph(DrawContext context, int cx, int cy, int color) {
        context.fill(cx, cy - 5, cx + 1, cy - 1, color);
        context.fill(cx - 1, cy - 4, cx + 2, cy - 3, color);
        context.fill(cx, cy + 2, cx + 1, cy + 6, color);
        context.fill(cx - 1, cy + 4, cx + 2, cy + 5, color);
        context.fill(cx - 5, cy, cx - 1, cy + 1, color);
        context.fill(cx - 4, cy - 1, cx - 3, cy + 2, color);
        context.fill(cx + 2, cy, cx + 6, cy + 1, color);
        context.fill(cx + 4, cy - 1, cx + 5, cy + 2, color);
    }

    public static void redrawOptionalCalc(DrawContext context, int mouseX, int mouseY) {
        if (!BattleUiTheme.night() || !hasOptionalCalc()
                || !(currentSelection() instanceof BattleGeneralActionSelection)) return;
        int dx = offsetX(), dy = offsetY();
        int x = 198 + dx, y = MinecraftClient.getInstance().getWindow().getScaledHeight() - 70 + dy;
        boolean hovered = mouseX >= x && mouseX < x + 90 && mouseY >= y && mouseY < y + 26;
        float opacity = (float) CobblemonClient.INSTANCE.getBattleOverlay().getOpacityRatio();
        BattleUiSkin.drawCobblemonButton(context, x, y, 90, 26, hovered, opacity);
        BattleUiSkin.drawIntegratedActionIcon(context, BattleUiSkin.COBBLEMON_SWITCH_BUTTON, x, y,
                hovered, opacity);
        int color = Math.round(255.0F * opacity) << 24 | 0xFFFFFF;
        context.drawText(MinecraftClient.getInstance().textRenderer, "Calc", x + 6, y + 8, color, true);
    }

    public static boolean click(double x, double y, int button) {
        if (!active()) return false;
        var p = placement();
        int handleOffsetX = handleOffsetX();
        if (LAYOUT.dragging()) {
            if (button == 0) finish();
            return true;
        }
        if (button == 1 && p.onHandle(x, y, handleOffsetX)) { LAYOUT.reset(); finish(); return true; }
        return LAYOUT.begin(x, y, button, handleOffsetX);
    }

    public static boolean drag(double x, double y, int button) {
        if (button != 0 || !LAYOUT.dragging()) return false;
        if (!active()) { finish(); return true; }
        var window = MinecraftClient.getInstance().getWindow();
        return LAYOUT.drag(x, y, window.getScaledWidth(), window.getScaledHeight(), contentWidth(), contentHeight());
    }

    public static boolean dragging() { return LAYOUT.dragging(); }
    public static void finish() {
        if (!LAYOUT.finish() || settings == null) return;
        try { LAYOUT.save(settings); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save the UI Battle action position.");
        }
    }

    static void load(Path file) {
        settings = file;
        try { LAYOUT.load(file); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not load the UI Battle action position; using defaults.");
        }
    }
}
