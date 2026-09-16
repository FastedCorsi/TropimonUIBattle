package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveBadgeLayoutTest {
    @Test void allMultipliersStayBelowTheNameBeforeThePpAndInsideTheirOwnRow() {
        for (float x : new float[]{0, 10, 115, 350.5F}) {
            for (float y : new float[]{0, 100, 129, 300.5F}) {
                var box = MoveBadgeLayout.inside(x, y);
                assertTrue(box.x() >= Math.round(x) + 17);
                assertTrue(box.x() + box.width() <= Math.round(x) + 48);
                assertTrue(box.y() >= Math.round(y) + 13);
                assertTrue(box.y() + box.height() <= Math.round(y) + 24);
                assertEquals(10, box.height());
            }
        }
    }

    @Test void multipliersUseExactlyTheNativePpFontAndWeight() {
        for (String label : new String[]{"×0", "×0.25", "×0.5", "×1", "×2", "×4", "×?", "×…"}) {
            var text = MoveTooltipRenderer.ppStyledLabel(label);
            assertEquals(label, text.getString());
            assertEquals(com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE(), text.getStyle().getFont());
            assertTrue(text.getStyle().isBold());
            assertFalse(text.getStyle().isItalic());
        }
    }
}
