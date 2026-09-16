package fr.tropimon.battleui;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleUiThemeTest {
    @TempDir Path temporary;

    @Test void themePersistsAndNightHistoryRemapsDarkLogColors() throws Exception {
        Path file = temporary.resolve("ui/theme.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "mode=day\n");
        BattleUiTheme.load(file);
        assertFalse(BattleUiTheme.night());
        BattleUiTheme.toggle();
        assertTrue(BattleUiTheme.night());
        assertNotEquals(0, BattleUiTheme.nativeButtonOverlay());
        assertEquals(0x33071424, BattleUiTheme.nativeButtonOverlay(0.5F));
        assertEquals(0x80203A58, BattleUiTheme.nativeButtonFace(false, 0.5F));
        assertEquals(0, BattleUiTheme.explicitNativeRgbMask(2048 | 4096 | 8192));
        assertEquals(16, BattleUiTheme.explicitNativeRgbMask(16 | 2048 | 4096 | 8192));
        assertTrue(Files.readString(file).contains("mode=night"));

        Text recolored = BattleUiTheme.historyText(Text.literal("log")
                .styled(style -> style.withColor(BattleLogTextFormatter.BODY_COLOR)));
        assertEquals(0xE6F2F6, recolored.getStyle().getColor().getRgb());

        Files.writeString(file, "mode=day\n");
        BattleUiTheme.load(file);
        assertFalse(BattleUiTheme.night());
        assertEquals(0, BattleUiTheme.nativeButtonOverlay());
    }

    @Test void generatedThemeIconsAreCompactTransparentSprites() throws Exception {
        for (String name : new String[]{"theme_day.png", "theme_night.png"}) {
            try (var input = getClass().getResourceAsStream(
                    "/assets/tropimon_ui_battle/textures/gui/theme/" + name)) {
                assertNotNull(input, name);
                var image = ImageIO.read(input);
                assertEquals(12, image.getWidth(), name);
                assertEquals(12, image.getHeight(), name);
                assertTrue(image.getColorModel().hasAlpha(), name);
                assertEquals(0, image.getRGB(0, 0) >>> 24, name);
            }
        }
    }
}
