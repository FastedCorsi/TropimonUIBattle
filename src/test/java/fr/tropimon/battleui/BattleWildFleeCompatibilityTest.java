package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleWildFleeCompatibilityTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/fr/tropimon/battleui/mixin/BattleGeneralActionSelectionMixin.java");

    @Test
    void exactCallbacksShareTheWildOnlyActionAndKeepPvpConfirmation() throws IOException {
        String source = Files.readString(MIXIN);
        assertTrue(source.contains("method = \"lambda$2$1\""));
        assertTrue(source.contains("cancellable = true, require = 1"));
        assertTrue(source.contains("@Surrogate"));
        assertTrue(source.contains("!battle.isPvW() || request.getResponse() != null"));
        assertTrue(source.contains("selectAction(request, new ForfeitActionResponse())"));
        assertTrue(source.contains("selectAction(remaining, PassActionResponse.INSTANCE)"));
        assertTrue(source.contains("remainingSlots-- > 0"));
        assertFalse(source.contains("FleeAttemptActionResponse"));
    }

    @Test
    void supportedCobblemonRunCallbackHasItsTransformedHook() {
        var methods = Arrays.asList(BattleGeneralActionSelection.class.getDeclaredMethods());
        var callback = methods.stream().filter(method -> method.getName().equals("lambda$2$1"))
                .findFirst().orElseThrow();
        int capturedArguments = callback.getParameterCount();
        assertTrue(capturedArguments == 1 || capturedArguments == 3,
                "Review the Run callback if Cobblemon changes its captured arguments");
        String hook = "tropimonBattleUi$runImmediately";
        assertTrue(methods.stream().anyMatch(method -> method.getName().contains(hook)
                && method.getParameterCount() == capturedArguments + 1));
        // Presence of a merged handler alone is not proof of successful injection.
        // Invoke the real callback without a GUI: the expected null dereference
        // must originate in our shared action, not the untouched native callback.
        callback.setAccessible(true);
        var failure = assertThrows(InvocationTargetException.class,
                () -> callback.invoke(null, new Object[capturedArguments]));
        assertTrue(Arrays.stream(failure.getCause().getStackTrace()).anyMatch(frame ->
                frame.getMethodName().contains("tropimonBattleUi$fleeWildBattle")));
    }
}
