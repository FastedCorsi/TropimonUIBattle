package fr.tropimon.battleui;

/** Cobblemon 1.7: name y+2, category icon x+48/y+14.5, PP centred x+75/y+14. */
record MoveBadgeLayout(int x, int y, int width, int height) {
    static MoveBadgeLayout inside(float tileX, float tileY) {
        // Stay within the lower-left pocket, never in the 5px gap between the two rows.
        return new MoveBadgeLayout(Math.round(tileX) + 17, Math.round(tileY) + 13, 30, 10);
    }
}
