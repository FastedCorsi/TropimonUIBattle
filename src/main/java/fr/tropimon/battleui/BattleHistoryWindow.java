package fr.tropimon.battleui;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** One movable/resizable panel; normalized bounds survive window and GUI-scale changes. */
final class BattleHistoryWindow {
    private static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8, MOVE = 16;
    static final int RESIZE_BORDER = 5;
    private Rect bounds = new Rect(0, 0, 0, 0);
    private Rect dragStart;
    private double startX, startY;
    private double anchorX = 1, anchorY = 1, widthRatio, heightRatio;
    private boolean customized, dirty;
    private int mode, viewportWidth, viewportHeight;

    record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    int width(int screenWidth, int margin, int defaultWidth) {
        int edge = customized ? 0 : margin;
        int available = Math.max(1, screenWidth - edge * 2);
        return clamp(customized ? (int) Math.round(widthRatio * screenWidth) : defaultWidth,
                Math.min(190, available), available);
    }

    Rect layout(int screenWidth, int screenHeight, int margin, int defaultWidth, int defaultHeight) {
        if (screenWidth != viewportWidth || screenHeight != viewportHeight) mode = 0;
        viewportWidth = screenWidth;
        viewportHeight = screenHeight;
        int edge = customized ? 0 : margin;
        int w = width(screenWidth, margin, defaultWidth);
        int availableHeight = Math.max(1, screenHeight - edge * 2);
        int h = clamp(customized ? (int) Math.round(heightRatio * screenHeight) : defaultHeight,
                Math.min(90, availableHeight), availableHeight);
        int x = edge + (int) Math.round((screenWidth - edge * 2 - w) * anchorX);
        int y = edge + (int) Math.round((screenHeight - edge * 2 - h) * anchorY);
        bounds = new Rect(x, y, w, h);
        return bounds;
    }

    boolean begin(double x, double y, int button, int headerHeight) {
        if (button != 0 || !bounds.contains(x, y)) return false;
        mode = edges(x, y);
        if (mode == 0 && y < bounds.y + headerHeight) mode = MOVE;
        if (mode == 0) return false;
        dragStart = bounds;
        startX = x;
        startY = y;
        return true;
    }

    boolean onResizeBorder(double x, double y) { return bounds.contains(x, y) && edges(x, y) != 0; }
    boolean interacting() { return mode != 0; }

    private int edges(double x, double y) {
        int edges = 0;
        if (x < bounds.x + RESIZE_BORDER) edges |= LEFT;
        else if (x >= bounds.x + bounds.width - RESIZE_BORDER) edges |= RIGHT;
        if (y < bounds.y + RESIZE_BORDER) edges |= TOP;
        else if (y >= bounds.y + bounds.height - RESIZE_BORDER) edges |= BOTTOM;
        // A generous bottom-right handle remains easy to grab at high GUI scales.
        if (x >= bounds.x + bounds.width - 11 && y >= bounds.y + bounds.height - 11)
            edges = RIGHT | BOTTOM;
        return edges;
    }

    boolean drag(double x, double y, int screenWidth, int screenHeight, int margin) {
        if (mode == 0) return false;
        if (screenWidth != viewportWidth || screenHeight != viewportHeight) { mode = 0; return true; }
        int edge = 0;
        int dx = (int) Math.round(x - startX), dy = (int) Math.round(y - startY);
        int left = dragStart.x, top = dragStart.y;
        int right = left + dragStart.width, bottom = top + dragStart.height;
        if (mode == MOVE) {
            left = clamp(left + dx, edge, screenWidth - edge - dragStart.width);
            top = clamp(top + dy, edge, screenHeight - edge - dragStart.height);
            right = left + dragStart.width;
            bottom = top + dragStart.height;
        } else {
            int minWidth = Math.min(190, screenWidth - edge * 2);
            int minHeight = Math.min(90, screenHeight - edge * 2);
            if ((mode & LEFT) != 0) left = clamp(left + dx, edge, right - minWidth);
            if ((mode & RIGHT) != 0) right = clamp(right + dx, left + minWidth, screenWidth - edge);
            if ((mode & TOP) != 0) top = clamp(top + dy, edge, bottom - minHeight);
            if ((mode & BOTTOM) != 0) bottom = clamp(bottom + dy, top + minHeight, screenHeight - edge);
        }
        Rect next = new Rect(left, top, right - left, bottom - top);
        if (!next.equals(bounds)) {
            bounds = next;
            customized = true;
            dirty = true;
            widthRatio = bounds.width / (double) screenWidth;
            heightRatio = bounds.height / (double) screenHeight;
            anchorX = bounds.x / (double) Math.max(1, screenWidth - bounds.width);
            anchorY = bounds.y / (double) Math.max(1, screenHeight - bounds.height);
        }
        return true;
    }

    boolean finish() {
        mode = 0;
        boolean changed = dirty;
        dirty = false;
        return changed;
    }

    void reset() {
        customized = false;
        anchorX = anchorY = 1;
        mode = 0;
        dirty = true;
    }

    void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        try {
            if (!Boolean.parseBoolean(properties.getProperty("customized"))) return;
            double x = Double.parseDouble(properties.getProperty("anchorX"));
            double y = Double.parseDouble(properties.getProperty("anchorY"));
            double w = Double.parseDouble(properties.getProperty("widthRatio"));
            double h = Double.parseDouble(properties.getProperty("heightRatio"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w) || !Double.isFinite(h) ||
                    x < 0 || x > 1 || y < 0 || y > 1 || w <= 0 || w > 1 || h <= 0 || h > 1) return;
            anchorX = x; anchorY = y; widthRatio = w; heightRatio = h;
            customized = true;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Invalid local settings fall back to the normal layout, never break a battle.
        }
    }

    void save(Path file) throws IOException {
        var properties = new Properties();
        properties.setProperty("customized", Boolean.toString(customized));
        properties.setProperty("anchorX", Double.toString(anchorX));
        properties.setProperty("anchorY", Double.toString(anchorY));
        properties.setProperty("widthRatio", Double.toString(widthRatio));
        properties.setProperty("heightRatio", Double.toString(heightRatio));
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), "history-window-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) { properties.store(writer, "By FastedCorsi"); }
            try { Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staged); }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
