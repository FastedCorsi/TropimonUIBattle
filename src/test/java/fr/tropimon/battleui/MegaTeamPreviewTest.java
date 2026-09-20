package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MegaTeamPreviewTest {
    @Test void nativePartyAndStarterPromptAreNoLongerCancelled() {
        assertFalse(Arrays.stream(PartyOverlay.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$hideNativeSidebar")));
    }

    @Test void realMegaShowdownColumnsAreHiddenWithoutClearingTheirState() throws Exception {
        assumeTrue(FabricLoader.getInstance().isModLoaded("mega_showdown"),
                "Run with -Pverification_mods_dir containing Mega Showdown and Architectury");
        Class<?> widget = Class.forName(
                "com.github.yajatkaul.mega_showdown.client.battle.hud.TeamPreviewWidget");
        assertTrue(Arrays.stream(widget.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$hideSideTeamPreview")));
        var render = widget.getDeclaredMethod("renderWidget", DrawContext.class, int.class, int.class, float.class);
        render.setAccessible(true);
        var partyField = widget.getDeclaredField("party");
        partyField.setAccessible(true);
        var enabled = Class.forName("com.github.yajatkaul.mega_showdown.config.MegaShowdownConfig")
                .getField("showBattleHUD");
        boolean previousEnabled = enabled.getBoolean(null);
        try {
            enabled.setBoolean(null, true);
            for (boolean left : new boolean[]{true, false}) {
                Object column = widget.getConstructor(int.class, int.class, boolean.class).newInstance(0, 0, left);
                @SuppressWarnings("unchecked") List<Object> party = (List<Object>) partyField.get(column);
                // A nonempty column would dereference the drawing context without our hook.
                party.add(null);
                assertDoesNotThrow(() -> render.invoke(column, null, 0, 0, 0f));
                assertEquals(1, party.size(), "Only rendering is hidden; battle memory must not be cleared");
                assertTrue(enabled.getBoolean(null), "Do not disable Mega Showdown globally");
            }
        } finally {
            enabled.setBoolean(null, previousEnabled);
        }
    }
}
