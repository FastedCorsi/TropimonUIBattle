package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleActionExtensionPriorityTest {
    private static final int DEFAULT_MIXIN_PRIORITY = 1000;

    @Test void extensionScopeBracketsDefaultPriorityTailRenderers() throws Exception {
        int begin = priority("BattleGuiActionExtensionBeginMixin");
        int coordinates = priority("BattleGuiActionExtensionCoordinatesBeginMixin");
        int restore = priority("BattleGuiActionExtensionCoordinatesEndMixin");
        int end = priority("BattleGuiActionExtensionEndMixin");
        assertEquals(400, begin);
        assertTrue(begin < coordinates && coordinates < DEFAULT_MIXIN_PRIORITY);
        assertTrue(DEFAULT_MIXIN_PRIORITY < restore && restore < end);
        assertEquals(2000, end);
    }

    private static int priority(String simpleName) throws Exception {
        String source = Files.readString(Path.of("src/main/java/fr/tropimon/battleui/mixin/" + simpleName + ".java"));
        var matcher = java.util.regex.Pattern.compile("priority\\s*=\\s*(\\d+)").matcher(source);
        assertTrue(matcher.find(), simpleName);
        return Integer.parseInt(matcher.group(1));
    }
}
