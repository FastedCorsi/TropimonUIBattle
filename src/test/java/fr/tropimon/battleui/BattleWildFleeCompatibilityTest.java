package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleWildFleeCompatibilityTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/fr/tropimon/battleui/mixin/BattleGeneralActionSelectionMixin.java");

    @Test
    void legacyFleeWorkaroundCannotInterceptCobblemon18NativeCallback() throws IOException {
        String source = Files.readString(MIXIN);
        assertTrue(source.contains("lambda$2$1(Lcom/cobblemon/mod/common/client/gui/battle/subscreen/\" +"));
        assertTrue(source.contains("\"BattleGeneralActionSelection;)Lkotlin/Unit;\""));
        assertTrue(source.contains("cancellable = true, require = 0"));
        assertFalse(source.contains("method = \"lambda$2$1\","));
    }
}
