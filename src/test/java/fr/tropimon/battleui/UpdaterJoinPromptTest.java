package fr.tropimon.battleui;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.thread.ThreadExecutor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UpdaterJoinPromptTest {
    @Test void commandOpensAfterChatHasFinishedClosing() throws Exception {
        var screen = new AtomicReference<>("chat");
        var executor = new ThreadExecutor<Runnable>("Updater command test") {
            @Override protected Runnable createTask(Runnable task) { return task; }
            @Override protected boolean canExecute(Runnable task) { return true; }
            @Override protected Thread getThread() { return Thread.currentThread(); }
        };
        executor.send(() -> screen.set("updater"));
        screen.set(null); // ChatScreen.keyPressed closes chat after sending the command.
        assertNull(screen.get());
        assertTrue(executor.runTask());
        assertEquals("updater", screen.get());
        String source = Files.readString(Path.of("src/main/java/fr/tropimon/battleui/TropimonSelfUpdater.java"));
        assertTrue(source.contains("client.send(() -> client.setScreen(new UpdateScreen"));
    }

    @Test void joiningWithoutConsentOffersPermissionButDecliningDoesNotNag() {
        assertTrue(TropimonSelfUpdater.automaticPromptNeeded(false, false, false));
        assertFalse(TropimonSelfUpdater.automaticPromptNeeded(true, false, false));
        assertFalse(TropimonSelfUpdater.automaticPromptNeeded(false, true, false));
        assertFalse(TropimonSelfUpdater.automaticPromptNeeded(true, true, false));
        assertTrue(TropimonSelfUpdater.automaticPromptNeeded(true, true, true));
    }

    @Test void offerWaitsForTheWorldAndDoesNotInterruptCombatOrLoading() {
        assertTrue(TropimonSelfUpdater.canShowPrompt(null, true, false, false));
        assertFalse(TropimonSelfUpdater.canShowPrompt(null, false, false, false));
        assertFalse(TropimonSelfUpdater.canShowPrompt(null, true, true, false));
        assertFalse(TropimonSelfUpdater.canShowPrompt(null, true, false, true));
    }

    @Test void pauseMenuIsAllowedButOtherModsAndTitleAreNotReplaced() {
        assertTrue(TropimonSelfUpdater.canShowPrompt(new GameMenuScreen(true), true, false, false));
        assertFalse(TropimonSelfUpdater.canShowPrompt(new TitleScreen(), true, false, false));
        Screen otherMod = new Screen(Text.literal("Other mod consent")) {};
        assertFalse(TropimonSelfUpdater.canShowPrompt(otherMod, true, false, false));
    }

    @Test void connectionWiringRemainsBoundedAndDoesNotDownloadOrBypassConsent() throws Exception {
        String source = Files.readString(Path.of("src/main/java/fr/tropimon/battleui/TropimonSelfUpdater.java"));
        assertTrue(source.contains("ClientPlayConnectionEvents.JOIN.register"));
        assertFalse(source.contains("ClientLifecycleEvents.CLIENT_STARTED"));
        assertFalse(source.contains("ClientTickEvents"));
        assertTrue(source.contains("if (checksAllowed && !recentlyChecked()) check();"));
        String prompt = source.substring(source.indexOf("private static void promptAfterJoin"),
                source.indexOf("static boolean checksConsented"));
        assertTrue(prompt.contains("client.getNetworkHandler() != connection"));
        assertTrue(prompt.contains("retries > 0"));
        assertFalse(prompt.contains("approve("));
        assertFalse(prompt.contains("download("));
        assertFalse(prompt.contains("settings("));
    }
}
