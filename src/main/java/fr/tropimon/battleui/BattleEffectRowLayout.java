package fr.tropimon.battleui;

import java.util.ArrayList;
import java.util.List;

/** Pixel positions shared by the visible icon/counter and its hover target. */
final class BattleEffectRowLayout {
    private BattleEffectRowLayout() {
    }

    static List<Position> calculate(int count, int anchorX, int startY, int maxRowWidth,
                                    int tileWidth, int tileHeight, int gap, Alignment alignment) {
        if (count <= 0) return List.of();
        int width = Math.max(1, Math.min(tileWidth, maxRowWidth));
        int spacing = Math.max(0, gap);
        int columns = Math.max(1, (Math.max(width, maxRowWidth) + spacing) / (width + spacing));
        List<Position> result = new ArrayList<>(count);
        for (int start = 0, row = 0; start < count; start += columns, row++) {
            int rowCount = Math.min(columns, count - start);
            int rowWidth = rowCount * width + (rowCount - 1) * spacing;
            int left = switch (alignment) {
                case LEFT -> anchorX;
                case CENTER -> anchorX - rowWidth / 2;
                case RIGHT -> anchorX - rowWidth;
            };
            for (int column = 0; column < rowCount; column++) {
                int visualColumn = alignment == Alignment.RIGHT ? rowCount - column - 1 : column;
                result.add(new Position(left + visualColumn * (width + spacing),
                        startY + row * (tileHeight + spacing), width, tileHeight));
            }
        }
        return List.copyOf(result);
    }

    static int requiredHeight(int count, int maxRowWidth, int tileWidth, int tileHeight, int gap) {
        if (count <= 0) return 0;
        int width = Math.max(1, Math.min(tileWidth, maxRowWidth));
        int spacing = Math.max(0, gap);
        int columns = Math.max(1, (Math.max(width, maxRowWidth) + spacing) / (width + spacing));
        int rows = (count + columns - 1) / columns;
        return rows * tileHeight + Math.max(0, rows - 1) * spacing;
    }

    enum Alignment { LEFT, CENTER, RIGHT }

    record Position(int x, int y, int width, int height) {
    }
}
