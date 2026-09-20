package fr.tropimon.battleui;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** A shared, resolution-independent anchor for the native action and move menus. */
public final class BattleActionLayout {
    // Includes Cobblemon's two native columns and the optional third action tile (for example Calc).
    static final int WIDTH = 284, HEIGHT = 102;
    static final int NATIVE_ACTION_WIDTH = 192, MOVE_WIDTH = 208;
    static final int ACTION_FIRST_X = 12, ACTION_SECOND_X = 105, ACTION_FIRST_Y_FROM_BOTTOM = 85;
    // MoveTile coordinates produced by Cobblemon: the first column inherits the
    // selection x (20), while the second is offset to x=125.
    static final int MOVE_FIRST_X = 20, MOVE_SECOND_X = 125, MOVE_FIRST_Y_FROM_BOTTOM = 84;
    // The general action rows end 74 px below the movable origin. The taller bound is
    // only needed by the move screen's Back, Shift and gimmick controls.
    static final int GENERAL_ACTION_HEIGHT = 74;
    static final int HANDLE_WIDTH = 18, HANDLE_HEIGHT = 18;
    static final int GENERAL_HANDLE_OFFSET_X = 261, NATIVE_HANDLE_OFFSET_X = 169,
            MOVE_HANDLE_OFFSET_X = 190, HANDLE_OFFSET_Y = 0;
    // Cobblemon anchors Back below the move grid. Once the grid is aligned with the
    // general actions, that old position can cover the lower-left move at the screen edge.
    // The free header strip keeps Back clickable without moving any attack tile.
    static final int MOVE_BACK_WIDTH = 29, MOVE_BACK_HEIGHT = 17;
    static final int MOVE_BACK_OFFSET_X = MOVE_HANDLE_OFFSET_X - MOVE_BACK_WIDTH - 2;
    static final int MOVE_BACK_OFFSET_Y = 0;
    static final int NATIVE_MOVE_BACK_OFFSET_X = 0, NATIVE_MOVE_BACK_OFFSET_Y = 82;
    private double anchorX, anchorY = 1;
    private boolean customized, dragging, dirty;
    private int viewportWidth, viewportHeight;
    private double grabX, grabY;
    private Placement placement = new Placement(0, 0, 0, 0);

    record Placement(int x, int y, int dx, int dy) {
        int handleX(int offsetX) { return x + offsetX; }
        int handleY() { return y + HANDLE_OFFSET_Y; }
        boolean onHandle(double mx, double my, int offsetX) {
            return mx >= handleX(offsetX) && mx < handleX(offsetX) + HANDLE_WIDTH
                    && my >= handleY() && my < handleY() + HANDLE_HEIGHT;
        }
        double nativeX(double screenX) { return screenX - dx; }
        double nativeY(double screenY) { return screenY - dy; }
    }

    static int moveTileExtraX(Placement actionGrid, Placement moveControls, float nativeTileX) {
        // Move cards are wider than action cards. Align the first column with Fight,
        // then retain Cobblemon's native 105 px move stride so both cards keep a gap.
        return actionGrid.dx - moveControls.dx + ACTION_FIRST_X - MOVE_FIRST_X;
    }

    static int moveTileExtraY(Placement actionGrid, Placement moveControls) {
        return actionGrid.dy - moveControls.dy
                + MOVE_FIRST_Y_FROM_BOTTOM - ACTION_FIRST_Y_FROM_BOTTOM;
    }

    static int sharedContentWidth(boolean optionalCalc) {
        return optionalCalc ? WIDTH : Math.max(NATIVE_ACTION_WIDTH, MOVE_WIDTH);
    }

    static int moveBackExtraX() { return MOVE_BACK_OFFSET_X - NATIVE_MOVE_BACK_OFFSET_X; }
    static int moveBackExtraY() { return MOVE_BACK_OFFSET_Y - NATIVE_MOVE_BACK_OFFSET_Y; }

    // Dedicated header strip: gimmicks on the left, Shift, Back, then drag handle.
    // Compress only the optional gimmick row if a server offers many at once.
    public record Control(float x, float y, float scale) {
        public double nativeX(double pointer, float original) { return original + (pointer - x) / scale; }
        public double nativeY(double pointer, float original) { return original + (pointer - y) / scale; }
    }
    static Control gimmick(int index, int count, int screenHeight) {
        float stride = Math.min(22.0F, 116.0F / Math.max(1, count));
        float scale = Math.min(1.0F, (stride - 2.0F) / 18.0F);
        return new Control(9 + index * stride, screenHeight - 104, scale);
    }
    static Control shift(int screenHeight) { return new Control(9 + 120, screenHeight - 104, 1); }

    Placement layout(int width, int height) {
        return layout(width, height, WIDTH, HEIGHT);
    }

    Placement layout(int width, int height, int contentHeight) {
        return layout(width, height, WIDTH, contentHeight);
    }

    Placement layout(int width, int height, int contentWidth, int contentHeight) {
        if (viewportWidth != width || viewportHeight != height) dragging = false;
        viewportWidth = width;
        viewportHeight = height;
        int boundedWidth = Math.clamp(contentWidth, HANDLE_WIDTH, WIDTH);
        int boundedHeight = Math.clamp(contentHeight, HANDLE_HEIGHT, HEIGHT);
        int edgeMaxX = Math.max(0, width - boundedWidth), edgeMaxY = Math.max(0, height - boundedHeight);
        int defaultMaxX = Math.max(0, width - boundedWidth - 2), defaultMaxY = Math.max(0, height - HEIGHT - 2);
        int x = customized ? (int) Math.round(edgeMaxX * anchorX) : Math.min(9, defaultMaxX);
        int y = customized ? (int) Math.round(edgeMaxY * anchorY) : defaultMaxY;
        placement = new Placement(x, y, x - 9, y - (height - 104));
        return placement;
    }

    boolean begin(double x, double y, int button, int handleOffsetX) {
        if (button != 0 || !placement.onHandle(x, y, handleOffsetX)) return false;
        dragging = true;
        grabX = x - placement.x;
        grabY = y - placement.y;
        return true;
    }

    boolean drag(double x, double y, int width, int height) {
        return drag(x, y, width, height, WIDTH, HEIGHT);
    }

    boolean drag(double x, double y, int width, int height, int contentHeight) {
        return drag(x, y, width, height, WIDTH, contentHeight);
    }

    boolean drag(double x, double y, int width, int height, int contentWidth, int contentHeight) {
        if (!dragging) return false;
        if (width != viewportWidth || height != viewportHeight) { dragging = false; return true; }
        int boundedWidth = Math.clamp(contentWidth, HANDLE_WIDTH, WIDTH);
        int boundedHeight = Math.clamp(contentHeight, HANDLE_HEIGHT, HEIGHT);
        int availableX = Math.max(0, width - boundedWidth), availableY = Math.max(0, height - boundedHeight);
        anchorX = Math.clamp((x - grabX) / Math.max(1, availableX), 0, 1);
        anchorY = Math.clamp((y - grabY) / Math.max(1, availableY), 0, 1);
        customized = dirty = true;
        layout(width, height, boundedWidth, boundedHeight);
        return true;
    }

    boolean dragging() { return dragging; }
    boolean finish() { dragging = false; boolean changed = dirty; dirty = false; return changed; }
    void reset() { customized = false; anchorX = 0; anchorY = 1; dragging = false; dirty = true; }

    void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        try {
            double x = Double.parseDouble(properties.getProperty("anchorX"));
            double y = Double.parseDouble(properties.getProperty("anchorY"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) return;
            anchorX = x; anchorY = y;
            customized = Boolean.parseBoolean(properties.getProperty("customized"));
        } catch (IllegalArgumentException | NullPointerException ignored) { }
    }

    void save(Path file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("customized", Boolean.toString(customized));
        properties.setProperty("anchorX", Double.toString(anchorX));
        properties.setProperty("anchorY", Double.toString(anchorY));
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), "action-position-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) { properties.store(writer, "By FastedCorsi"); }
            try { Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staged); }
    }
}
