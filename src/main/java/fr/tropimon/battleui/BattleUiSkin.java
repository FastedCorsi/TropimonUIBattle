package fr.tropimon.battleui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

final class BattleUiSkin {
    static final Identifier COBBLEMON_LOG = Identifier.of("cobblemon", "textures/gui/battle/battle_log_expanded.png");
    static final Identifier COBBLEMON_MOVE_OVERLAY = Identifier.of("cobblemon", "textures/gui/battle/battle_move_overlay.png");
    static final Identifier COBBLEMON_BUTTON = Identifier.of("cobblemon", "textures/gui/battle/battle_move.png");
    static final Identifier COBBLEMON_SWITCH_BUTTON = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_switch.png");
    static final Identifier TROPIMON_NAVIGATOR_FRAME = Identifier.of("tropimon_ui_battle",
            "textures/gui/skin/history_frame.png");

    private static final int TROPIMON_FRAME_WIDTH = 345;
    private static final int TROPIMON_FRAME_HEIGHT = 205;
    private static final int TROPIMON_FRAME_SLICE = 17;

    private BattleUiSkin() {
    }

    static void drawScaled(DrawContext context, Identifier texture, int x, int y, int width, int height,
                           int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) return;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(width / (float) textureWidth, height / (float) textureHeight, 1.0F);
        context.drawTexture(texture, 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
        context.getMatrices().pop();
    }

    static void drawScaledRegion(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                 int u, int v, int regionWidth, int regionHeight,
                                 int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) return;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(width / (float) regionWidth, height / (float) regionHeight, 1.0F);
        context.drawTexture(texture, 0, 0, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        context.getMatrices().pop();
    }

    static void drawScaledRegionAlpha(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                      int u, int v, int regionWidth, int regionHeight,
                                      int textureWidth, int textureHeight, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        try {
            drawScaledRegion(context, texture, x, y, width, height, u, v,
                    regionWidth, regionHeight, textureWidth, textureHeight);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    static void drawMovePpBackground(DrawContext context, int x, int y, int width, int height, float alpha) {
        // Exact lower-right PP strip, including its slanted left edge; no category icon or text.
        drawScaledRegionAlpha(context, COBBLEMON_MOVE_OVERLAY, x, y, width, height,
                42, 13, 50, 11, 92, 24, alpha);
    }

    static void drawIntegratedActionIcon(DrawContext context, Identifier texture, int x, int y,
                                         boolean hovered, float alpha) {
        int v = hovered ? 26 : 0;
        drawScaledRegionAlpha(context, texture, x + 60, y, 30, 26,
                60, v, 30, 26, 90, 52, alpha);
        if (!BattleUiTheme.night()) return;
        int face = BattleUiTheme.nativeButtonFace(hovered, alpha);
        // Only cover the remaining grey pixels before Cobblemon's black angled icon tray.
        context.fill(x + 60, y + 1, x + 65, y + 4, face);
        context.fill(x + 60, y + 4, x + 64, y + 8, face);
        context.fill(x + 60, y + 8, x + 63, y + 14, face);
        context.fill(x + 60, y + 14, x + 62, y + 18, face);
        context.fill(x + 60, y + 18, x + 61, y + 21, face);
    }

    static void drawCobblemonButton(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        drawCobblemonButton(context, x, y, width, height, hovered, 1.0F);
    }

    static void drawCobblemonButton(DrawContext context, int x, int y, int width, int height,
                                     boolean hovered, float alpha) {
        int v = hovered ? 24 : 0;
        float opacity = Math.max(0.0F, Math.min(1.0F, alpha));
        BattleUiTheme.beginNativeTint(opacity);
        try {
            // Preserve the native end caps instead of stretching their corners with the label.
            drawScaledRegion(context, COBBLEMON_BUTTON, x, y, 4, height, 0, v, 4, 24, 92, 48);
            drawScaledRegion(context, COBBLEMON_BUTTON, x + 4, y, width - 8, height, 4, v, 84, 24, 92, 48);
            drawScaledRegion(context, COBBLEMON_BUTTON, x + width - 4, y, 4, height, 88, v, 4, 24, 92, 48);
        } finally { BattleUiTheme.endNativeTint(); }
        if (BattleUiTheme.night()) context.fill(x + 2, y + 2, x + width - 2, y + height - 2,
                BattleUiTheme.nativeButtonOverlay(opacity));
    }

    /**
     * Nine-slices the navigator frame used by Tropimon's caught-Pokémon card.
     * This keeps its cyan corners and red details crisp at both team-card and
     * battle-history sizes instead of distorting the complete 345x205 image.
     */
    static void drawTropimonFrame(DrawContext context, int x, int y, int width, int height, float alpha) {
        if (width < 4 || height < 4) return;
        int borderX = Math.max(2, Math.min(7, width / 5));
        int borderY = Math.max(2, Math.min(7, height / 5));
        int middleWidth = Math.max(0, width - borderX * 2);
        int middleHeight = Math.max(0, height - borderY * 2);
        int sourceMiddleWidth = TROPIMON_FRAME_WIDTH - TROPIMON_FRAME_SLICE * 2;
        int sourceMiddleHeight = TROPIMON_FRAME_HEIGHT - TROPIMON_FRAME_SLICE * 2;

        RenderSystem.enableBlend();
        BattleUiTheme.beginNativeTint(Math.max(0.0F, Math.min(1.0F, alpha)));
        try {
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x, y, borderX, borderY,
                    0, 0, TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x + borderX, y, middleWidth, borderY,
                    TROPIMON_FRAME_SLICE, 0, sourceMiddleWidth, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x + width - borderX, y, borderX, borderY,
                    TROPIMON_FRAME_WIDTH - TROPIMON_FRAME_SLICE, 0,
                    TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);

            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x, y + borderY, borderX, middleHeight,
                    0, TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE, sourceMiddleHeight,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x + width - borderX, y + borderY,
                    borderX, middleHeight, TROPIMON_FRAME_WIDTH - TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE, sourceMiddleHeight,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);

            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x, y + height - borderY,
                    borderX, borderY, 0, TROPIMON_FRAME_HEIGHT - TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x + borderX, y + height - borderY,
                    middleWidth, borderY, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_HEIGHT - TROPIMON_FRAME_SLICE,
                    sourceMiddleWidth, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
            drawScaledRegion(context, TROPIMON_NAVIGATOR_FRAME, x + width - borderX,
                    y + height - borderY, borderX, borderY,
                    TROPIMON_FRAME_WIDTH - TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_HEIGHT - TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_SLICE, TROPIMON_FRAME_SLICE,
                    TROPIMON_FRAME_WIDTH, TROPIMON_FRAME_HEIGHT);
        } finally {
            BattleUiTheme.endNativeTint();
        }
    }

    static void drawTropimonLightPanel(DrawContext context, int x, int y, int width, int height) {
        // One uniform inset fill; the frame is drawn once, after the journal content.
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, BattleUiTheme.historyBackground());
    }

    static Identifier statusTexture(String status) {
        String id = switch (status == null ? "" : status.toLowerCase(java.util.Locale.ROOT)) {
            case "brn" -> "brn";
            case "par" -> "par";
            case "psn" -> "psn";
            case "tox" -> "tox";
            case "slp" -> "slp";
            case "frz" -> "frz";
            case "fnt" -> "fnt";
            default -> "";
        };
        return id.isEmpty() ? null : Identifier.of("cobblemon", "textures/gui/battle/battle_status_" + id + ".png");
    }

    static net.minecraft.text.Text statusLabel(String status) {
        if (statusTexture(status) == null) return net.minecraft.text.Text.empty();
        return net.minecraft.text.Text.translatable("cobblemon.ui.status." + status.toLowerCase(java.util.Locale.ROOT))
                .formatted(net.minecraft.util.Formatting.BOLD);
    }

    static void drawDarkPanel(DrawContext context, int x, int y, int width, int height) {
        drawScaled(context, COBBLEMON_LOG, x, y, width, height, 169, 101);
        context.fill(x + 4, y + 4, x + width - 4, y + height - 4, BattleUiTheme.darkPanelFill());
    }

}
