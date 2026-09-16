package fr.tropimon.battleui;

/** Converts Showdown slot digits to the left-to-right position seen by the viewer. */
final class TriplePosition {
    private TriplePosition() { }
    static String label(int showdownDigit, boolean opponent) {
        int digit = Math.max(1, Math.min(3, showdownDigit));
        int visual = opponent ? 4 - digit : digit;
        return visual == 1 ? "L" : visual == 2 ? "C" : "R";
    }
}
