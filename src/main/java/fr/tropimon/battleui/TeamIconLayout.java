package fr.tropimon.battleui;

/** Three disjoint rows: animated model without a background, health, then native status. */
record TeamIconLayout(int portraitX, int portraitY, int portraitSize,
                      int hpY, int hpHeight, int statusY, int statusHeight) {
    static final int HEALTH_HEIGHT = 9;
    static final int STATUS_HEIGHT = 9;
    static int heightFor(int width) { return portraitSizeFor(width) + 1 + HEALTH_HEIGHT + 1 + STATUS_HEIGHT; }
    static int portraitSizeFor(int width) {
        int size = (int) Math.floor(width * 0.90F);
        // Equal pixel margins keep the larger portrait centred at every GUI scale.
        if (((width - size) & 1) != 0) size--;
        return Math.max(1, size);
    }

    static TeamIconLayout of(int x, int y, int width, int height, int portraitSize) {
        int rowSize = Math.max(1, Math.min(portraitSizeFor(width), height - (2 + HEALTH_HEIGHT + STATUS_HEIGHT)));
        int modelSize = Math.min(rowSize, Math.max(1, portraitSize));
        int healthY = y + rowSize + 1;
        return new TeamIconLayout(x + (width - modelSize) / 2, y, modelSize,
                healthY, HEALTH_HEIGHT, healthY + HEALTH_HEIGHT + 1, STATUS_HEIGHT);
    }
}
