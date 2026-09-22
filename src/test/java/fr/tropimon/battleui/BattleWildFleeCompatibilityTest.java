package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import kotlin.Unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleWildFleeCompatibilityTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/fr/tropimon/battleui/mixin/BattleGeneralActionSelectionMixin.java");

    @Test
    void actualTransformedCallbackCanCancelAndReturnWithoutANullCallbackInfo() throws Exception {
        String target = BattleGeneralActionSelection.class.getName();
        Class.forName(target); // Force Fabric to apply and export the real injections.
        var original = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", target.replace('.', '/') + ".class")))
                .accept(original, 0);
        var fixture = new ClassNode();
        fixture.version = original.version;
        fixture.access = Opcodes.ACC_PUBLIC;
        fixture.name = "fr/tropimon/battleui/TransformedFleeFixture";
        fixture.superName = "java/lang/Object";
        for (var method : original.methods) {
            if (!method.name.equals("lambda$2$1")
                    && !method.name.contains("tropimonBattleUi$runImmediately")
                    && !method.name.contains("tropimonBattleUi$fleeWildBattle")) continue;
            if (method.name.contains("tropimonBattleUi$fleeWildBattle")) {
                // Replace only battle/network side effects. Retain the actual injected
                // callback construction, argument passing and cancellation epilogue.
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(original.name)
                        && call.name.contains("tropimonBattleUi$")) call.owner = fixture.name;
            }
            fixture.methods.add(method);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        fixture.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> probe = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        var callback = Arrays.stream(probe.getDeclaredMethods())
                .filter(method -> method.getName().equals("lambda$2$1")).findFirst().orElseThrow();
        callback.setAccessible(true);
        assertSame(Unit.INSTANCE, callback.invoke(null, new Object[callback.getParameterCount()]));
    }

    @Test
    void exactCallbacksShareTheWildOnlyActionAndKeepPvpConfirmation() throws IOException {
        String source = Files.readString(MIXIN);
        assertTrue(source.contains("method = \"lambda$2$1\""));
        assertTrue(source.contains("cancellable = true, require = 1"));
        assertFalse(source.contains("@Surrogate"));
        assertTrue(source.contains("@Local(argsOnly = true)"));
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
                && method.getParameterCount() == 2));
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
