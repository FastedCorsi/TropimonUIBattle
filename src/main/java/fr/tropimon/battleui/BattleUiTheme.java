package fr.tropimon.battleui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Persisted visual mode; it changes presentation only, never battle state. */
public final class BattleUiTheme {
    private static Path settings;
    private static boolean night;

    private BattleUiTheme() { }

    public static boolean night() { return night; }

    static void toggle() {
        night = !night;
        save();
    }

    static boolean darkHistory() { return night || BattleUiPreferences.palette() != BattleUiPreferences.Palette.DEFAULT; }
    static int historyBackground() {
        var palette = BattleUiPreferences.palette();
        return palette == BattleUiPreferences.Palette.DEFAULT ? (night ? 0xF8071424 : 0xFFEDF0F1)
                : 0xFF000000 | palette.background;
    }
    static int historyHeader() { return night ? 0xFF06101D : 0xFC182026; }
    static int neutralRow() {
        if (BattleUiPreferences.palette() != BattleUiPreferences.Palette.DEFAULT) return 0x28101010;
        return night ? 0xFF0D2235 : 0xFFE0E5E7;
    }
    static int scrollTrack() { return night ? 0xCC10263A : 0x8840515C; }
    static int darkPanelFill() { return night ? 0xF4061424 : 0xF2182026; }
    static int sideRow(BattleLogSide side) {
        if (!BattleUiPreferences.rowBackgrounds()) return 0;
        var palette = BattleUiPreferences.palette();
        if (palette != BattleUiPreferences.Palette.DEFAULT)
            return side == BattleLogSide.NEUTRAL ? 0 : 0x18000000 |
                    (side == BattleLogSide.PLAYER ? palette.player : palette.opponent);
        if (!night) return side.background();
        return side == BattleLogSide.PLAYER ? 0xD0123442
                : side == BattleLogSide.OPPONENT ? 0xD03A1722 : 0;
    }

    static net.minecraft.text.Text historyText(net.minecraft.text.Text text) {
        return darkHistory() ? recolor(text) : text;
    }

    private static net.minecraft.text.MutableText recolor(net.minecraft.text.Text source) {
        var result = source.copyContentOnly().setStyle(recolor(source.getStyle()));
        for (net.minecraft.text.Text sibling : source.getSiblings()) result.append(recolor(sibling));
        return result;
    }

    private static net.minecraft.text.Style recolor(net.minecraft.text.Style style) {
        var color = style.getColor();
        if (color == null) return style;
        int replacement = switch (color.getRgb() & 0xFFFFFF) {
            case 0x17616B -> BattleUiPreferences.palette() == BattleUiPreferences.Palette.DEFAULT
                    ? 0x55D5DE : BattleUiPreferences.palette().player;
            case 0x973838 -> BattleUiPreferences.palette() == BattleUiPreferences.Palette.DEFAULT
                    ? 0xFF7580 : BattleUiPreferences.palette().opponent;
            case 0x29353B, 0x15242B -> 0xE6F2F6;
            case 0x58666D -> 0x91AAB7;
            default -> color.getRgb();
        };
        return style.withColor(replacement);
    }

    public static void beginNativeTint() {
        beginNativeTint(1.0F);
    }

    static void beginNativeTint(float alpha) {
        if (night) RenderSystem.setShaderColor(0.32F, 0.56F, 0.82F, alpha);
        else RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    public static void endNativeTint() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static int nativeButtonOverlay() { return night ? 0x66071424 : 0; }

    static int nativeButtonFace(boolean hovered) {
        return hovered ? 0xFF1B4C70 : 0xFF203A58;
    }

    static int nativeButtonFace(boolean hovered, float opacity) {
        int color = nativeButtonFace(hovered);
        int alpha = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, opacity)));
        return alpha << 24 | color & 0xFFFFFF;
    }

    static int nativeButtonOverlay(float opacity) {
        int overlay = nativeButtonOverlay();
        if (overlay == 0) return 0;
        int alpha = Math.round(((overlay >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, opacity)));
        return (alpha << 24) | (overlay & 0xFFFFFF);
    }

    public static int explicitNativeRgbMask(int kotlinDefaultMask) {
        // blitk$default bits for red, green and blue; clear them so the mixin values are not reset to white.
        return kotlinDefaultMask & ~(2048 | 4096 | 8192);
    }

    static void load(Path file) {
        settings = file;
        if (!Files.isRegularFile(file)) return;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            night = "night".equalsIgnoreCase(properties.getProperty("mode", "day"));
        } catch (IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not read UI Battle theme; using day mode.");
            night = false;
        }
    }

    private static void save() {
        if (settings == null) return;
        try {
            Files.createDirectories(settings.getParent());
            var properties = new Properties();
            properties.setProperty("mode", night ? "night" : "day");
            Path staged = Files.createTempFile(settings.getParent(), "theme-", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(staged)) { properties.store(writer, "By FastedCorsi"); }
                try { Files.move(staged, settings, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staged, settings, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(staged); }
        } catch (IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save UI Battle theme; this session still uses the selected mode.");
        }
    }
}
