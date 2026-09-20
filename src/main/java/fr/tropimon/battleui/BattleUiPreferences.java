package fr.tropimon.battleui;

import java.io.IOException;
import java.nio.file.*;
import java.util.EnumSet;
import java.util.Properties;

/** Local presentation preferences, independent of battle observations and calculations. */
final class BattleUiPreferences {
    enum Detail { TYPES, WEAKNESSES, STATS, ITEM, ABILITY, ABILITY_DESCRIPTION, HISTORY, BOOSTS, EFFECTS, MOVES }
    enum Palette {
        DEFAULT(0, 0, 0), SLATE(0x20252D, 0x79D7E8, 0xFF9BA8),
        OCEAN(0x102735, 0x80DEEA, 0xFFB48A), FOREST(0x192C25, 0xA5D6A7, 0xF4B8D0),
        PLUM(0x2B2236, 0xB9C6FF, 0xFFB5CC);
        final int background, player, opponent;
        Palette(int background, int player, int opponent) {
            this.background = background; this.player = player; this.opponent = opponent;
        }
    }
    private static Path path;
    private static int chatPercent = 100, tooltipPercent = 100;
    private static Palette palette = Palette.DEFAULT;
    private static boolean rowBackgrounds = true;
    private static final EnumSet<Detail> details = EnumSet.allOf(Detail.class);
    private static long revision;

    private BattleUiPreferences() { }
    static float chatScale() { return chatPercent / 100.0F; }
    static float tooltipScale() { return tooltipPercent / 100.0F; }
    static int chatPercent() { return chatPercent; }
    static int tooltipPercent() { return tooltipPercent; }
    static Palette palette() { return palette; }
    static boolean rowBackgrounds() { return rowBackgrounds; }
    static boolean show(Detail detail) { return details.contains(detail); }
    static long revision() { return revision; }
    static int lineHeight(float scale) { return Math.max(7, (int) Math.ceil(10 * scale)); }
    static int wrapWidth(int pixels, float scale) { return Math.max(1, (int) Math.floor(pixels / scale)); }
    static int textCoordinate(double pixels, float scale) { return (int) Math.floor(pixels / scale); }

    static void cycleChatSize() { chatPercent = chatPercent == 100 ? 70 : chatPercent + 10; changed(); }
    static void cycleTooltipSize() { tooltipPercent = tooltipPercent == 100 ? 70 : tooltipPercent + 10; changed(); }
    static void cyclePalette() { palette = Palette.values()[(palette.ordinal() + 1) % Palette.values().length]; changed(); }
    static void toggleRows() { rowBackgrounds = !rowBackgrounds; changed(); }
    static void toggle(Detail detail) { if (!details.remove(detail)) details.add(detail); changed(); }
    static void reset() { defaults(); changed(); }
    private static void defaults() {
        chatPercent = tooltipPercent = 100; palette = Palette.DEFAULT; rowBackgrounds = true;
        details.clear(); details.addAll(EnumSet.allOf(Detail.class));
    }
    private static void changed() { revision++; save(); }

    static void load(Path file) {
        path = file;
        defaults();
        var properties = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
                chatPercent = percent(properties.getProperty("chatPercent"));
                tooltipPercent = percent(properties.getProperty("tooltipPercent"));
                try { palette = Palette.valueOf(properties.getProperty("palette", "DEFAULT")); }
                catch (IllegalArgumentException ignored) { palette = Palette.DEFAULT; }
                rowBackgrounds = !"false".equals(properties.getProperty("rowBackgrounds"));
                for (Detail detail : Detail.values())
                    if ("false".equals(properties.getProperty("show." + detail.name()))) details.remove(detail);
            }
        } catch (IOException | RuntimeException ignored) {
            defaults();
            TropimonUIBattleClient.LOGGER.warn("Could not read UI Battle display preferences; using defaults.");
        }
        revision++;
    }
    private static int percent(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 70 && parsed <= 100 && parsed % 10 == 0 ? parsed : 100;
        } catch (NumberFormatException ignored) { return 100; }
    }
    private static void save() {
        if (path == null) return;
        var properties = new Properties();
        properties.setProperty("chatPercent", Integer.toString(chatPercent));
        properties.setProperty("tooltipPercent", Integer.toString(tooltipPercent));
        properties.setProperty("palette", palette.name());
        properties.setProperty("rowBackgrounds", Boolean.toString(rowBackgrounds));
        for (Detail detail : Detail.values()) properties.setProperty("show." + detail.name(), Boolean.toString(show(detail)));
        try {
            Files.createDirectories(path.getParent());
            Path staged = Files.createTempFile(path.getParent(), "display-", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(staged)) { properties.store(writer, "By FastedCorsi"); }
                try { Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(staged); }
        } catch (IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save UI Battle display preferences.");
        }
    }
}
