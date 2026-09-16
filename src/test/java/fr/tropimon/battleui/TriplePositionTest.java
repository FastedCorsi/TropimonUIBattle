package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TriplePositionTest {
    @Test void opposingSlotsAreMirroredIntoViewerLeftCentreRight() {
        assertEquals("L", TriplePosition.label(1, false));
        assertEquals("C", TriplePosition.label(2, false));
        assertEquals("R", TriplePosition.label(3, false));
        assertEquals("R", TriplePosition.label(1, true));
        assertEquals("C", TriplePosition.label(2, true));
        assertEquals("L", TriplePosition.label(3, true));
    }
}
