package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class PartySidebarTest {
    @Test void sidebarSuppressionIsActuallyAppliedToCobblemon() {
        assertTrue(Arrays.stream(PartyOverlay.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$hideNativeSidebar")),
                "The real transformed overlay must contain the cancellation hook");
    }

    @Test void cancellationPointPreservesStarterPromptAndPrecedesAllSidebarDrawing() throws Exception {
        var calls = new ArrayList<String>();
        try (var stream = PartyOverlay.class.getResourceAsStream("PartyOverlay.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("render")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                              String descriptor, boolean isInterface) {
                            calls.add(name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        int cut = calls.indexOf("getWindow");
        assertTrue(cut > 0);
        assertEquals(cut, calls.lastIndexOf("getWindow"));
        assertTrue(calls.indexOf("getPromptStarter") >= 0 && calls.indexOf("getPromptStarter") < cut);
        assertTrue(calls.indexOf("blitk$default") > cut);
        assertTrue(calls.indexOf("drawPosablePortrait$default") > cut);
    }
}
