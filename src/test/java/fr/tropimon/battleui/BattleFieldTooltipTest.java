package fr.tropimon.battleui;

import net.minecraft.client.font.TextHandler;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleFieldTooltipTest {
    @Test
    void everyDurationVariantUsesSeparateTitleAndDescriptionWithoutControlGlyphs() {
        for (var effect : List.of(
                new BattleFieldEffects.EffectView("snow", 5, 8, 5, 8, 1, false, BattleFieldEffects.EffectSide.FIELD),
                new BattleFieldEffects.EffectView("trickroom", 3, 3, 5, 5, 1, false, BattleFieldEffects.EffectSide.FIELD),
                new BattleFieldEffects.EffectView("primordialsea", 0, 0, 0, 0, 1, false, BattleFieldEffects.EffectSide.FIELD),
                new BattleFieldEffects.EffectView("ally.spikes", 0, 0, 0, 0, 3, true, BattleFieldEffects.EffectSide.PLAYER_FIELD))) {
            var lines = effect.tooltipLines();
            assertEquals(2, lines.size());
            assertTrue(lines.getFirst().getStyle().isBold());
            assertFalse(lines.getLast().getStyle().isBold(), "Description must not inherit the title's bold style");
            for (var line : lines) assertFalse(line.getString().codePoints().anyMatch(Character::isISOControl));
        }
    }

    @Test
    void minecraftWrappingSplitsLongDescriptionAndExplicitNewlinesBeforeDrawing() {
        var handler = new TextHandler((codePoint, style) -> 1.0F);
        var text = Text.empty().append(Text.literal("Snow").styled(style -> style.withBold(true)))
                .append("\n5 to 8 turns remaining depending on the item or ability that created the effect.");
        var lines = handler.wrapLines(text, 30, Style.EMPTY);
        assertEquals("Snow", lines.getFirst().getString());
        assertTrue(lines.size() > 2);
        for (var line : lines) {
            assertTrue(handler.getWidth(line) <= 30);
            assertFalse(line.getString().contains("\n"));
        }
    }
}
