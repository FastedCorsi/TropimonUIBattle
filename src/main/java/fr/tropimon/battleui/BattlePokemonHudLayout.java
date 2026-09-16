package fr.tropimon.battleui;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Independent, normalized anchors for the two native active-Pokemon HUD stacks. */
final class BattlePokemonHudLayout {
    static final int HANDLE_SIZE = 14;
    private final Side own = new Side(0, 0);
    private final Side opponent = new Side(1, 0);
    private boolean dragging, draggingOwn;
    private int viewportWidth, viewportHeight;
    private double grabX, grabY;

    record Placement(int x, int y, int width, int height, boolean own) {
        int handleX() { return own ? x + width : x - HANDLE_SIZE; }
        int handleY() { return y; }
        boolean onHandle(double mouseX, double mouseY) {
            return mouseX >= handleX() && mouseX < handleX() + HANDLE_SIZE
                    && mouseY >= handleY() && mouseY < handleY() + HANDLE_SIZE;
        }
    }

    private static final class Side {
        double anchorX, anchorY;
        boolean customized, dirty;

        Side(double anchorX, double anchorY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    Placement layout(boolean isOwn, int screenWidth, int screenHeight, int width, int height) {
        int defaultX = isOwn ? 12 : screenWidth - 12 - width;
        return layout(isOwn, screenWidth, screenHeight, width, height, defaultX, 10);
    }

    Placement layout(boolean isOwn, int screenWidth, int screenHeight, int width, int height,
                     int requestedDefaultX, int requestedDefaultY) {
        return layout(isOwn, screenWidth, screenHeight, width, height,
                requestedDefaultX, requestedDefaultY, 0, 0);
    }

    Placement layout(boolean isOwn, int screenWidth, int screenHeight, int width, int height,
                     int requestedDefaultX, int requestedDefaultY, int topInset, int bottomInset) {
        if (viewportWidth != screenWidth || viewportHeight != screenHeight) {
            dragging = false;
            viewportWidth = screenWidth;
            viewportHeight = screenHeight;
        }
        Side side = side(isOwn);
        int availableX = Math.max(0, screenWidth - width);
        int minX = !isOwn && availableX >= HANDLE_SIZE ? HANDLE_SIZE : 0;
        int maxX = isOwn && availableX >= HANDLE_SIZE ? availableX - HANDLE_SIZE : availableX;
        int absoluteMaxY = Math.max(0, screenHeight - height);
        int minY = Math.min(Math.max(0, topInset), absoluteMaxY);
        int maxY = Math.max(minY, screenHeight - height - Math.max(0, bottomInset));
        int defaultX = Math.clamp(requestedDefaultX, minX, maxX);
        int defaultY = Math.clamp(requestedDefaultY, minY, maxY);
        int x = side.customized
                ? minX + (int) Math.round((maxX - minX) * side.anchorX)
                : defaultX;
        int y = side.customized ? minY + (int) Math.round((maxY - minY) * side.anchorY) : defaultY;
        return new Placement(x, y, width, height, isOwn);
    }

    boolean begin(boolean isOwn, Placement placement, double x, double y, int button) {
        if (button != 0 || !placement.onHandle(x, y)) return false;
        draggingOwn = isOwn;
        dragging = true;
        grabX = x - placement.x;
        grabY = y - placement.y;
        return true;
    }

    boolean drag(double x, double y, int screenWidth, int screenHeight, int width, int height) {
        return drag(x, y, screenWidth, screenHeight, width, height, 0, 0);
    }

    boolean drag(double x, double y, int screenWidth, int screenHeight, int width, int height,
                 int topInset, int bottomInset) {
        if (!dragging) return false;
        if (screenWidth != viewportWidth || screenHeight != viewportHeight) {
            dragging = false;
            return true;
        }
        Side side = side(draggingOwn);
        int availableX = Math.max(0, screenWidth - width);
        int minX = !draggingOwn && availableX >= HANDLE_SIZE ? HANDLE_SIZE : 0;
        int maxX = draggingOwn && availableX >= HANDLE_SIZE ? availableX - HANDLE_SIZE : availableX;
        int absoluteMaxY = Math.max(0, screenHeight - height);
        int minY = Math.min(Math.max(0, topInset), absoluteMaxY);
        int maxY = Math.max(minY, screenHeight - height - Math.max(0, bottomInset));
        side.anchorX = Math.clamp((x - grabX - minX) / Math.max(1, maxX - minX), 0, 1);
        side.anchorY = Math.clamp((y - grabY - minY) / Math.max(1, maxY - minY), 0, 1);
        side.customized = side.dirty = true;
        return true;
    }

    boolean dragging() { return dragging; }
    boolean draggingOwn() { return draggingOwn; }

    boolean finish() {
        dragging = false;
        boolean changed = own.dirty || opponent.dirty;
        own.dirty = opponent.dirty = false;
        return changed;
    }

    void reset(boolean isOwn) {
        Side side = side(isOwn);
        side.customized = false;
        side.anchorX = isOwn ? 0 : 1;
        side.anchorY = 0;
        side.dirty = true;
    }

    void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        loadSide(properties, "own", own);
        loadSide(properties, "opponent", opponent);
    }

    private static void loadSide(Properties properties, String prefix, Side side) {
        try {
            if (!Boolean.parseBoolean(properties.getProperty(prefix + ".customized"))) return;
            double x = Double.parseDouble(properties.getProperty(prefix + ".anchorX"));
            double y = Double.parseDouble(properties.getProperty(prefix + ".anchorY"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) return;
            side.anchorX = x;
            side.anchorY = y;
            side.customized = true;
        } catch (IllegalArgumentException | NullPointerException ignored) { }
    }

    void save(Path file) throws IOException {
        Properties properties = new Properties();
        saveSide(properties, "own", own);
        saveSide(properties, "opponent", opponent);
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), "pokemon-hud-position-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) { properties.store(writer, "By FastedCorsi"); }
            try { Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staged); }
    }

    private static void saveSide(Properties properties, String prefix, Side side) {
        properties.setProperty(prefix + ".customized", Boolean.toString(side.customized));
        properties.setProperty(prefix + ".anchorX", Double.toString(side.anchorX));
        properties.setProperty(prefix + ".anchorY", Double.toString(side.anchorY));
    }

    private Side side(boolean isOwn) { return isOwn ? own : opponent; }
}
