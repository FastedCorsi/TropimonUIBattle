package fr.tropimon.battleui;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BattleSkinPresentationTest {
    @Test void historyBackgroundDrawsOnlyOneSolidFillWithoutWatermarkOrFrame() throws Exception {
        var calls = new java.util.ArrayList<String>();
        var constants = new java.util.ArrayList<Object>();
        try (var stream = BattleUiSkin.class.getResourceAsStream("BattleUiSkin.class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("drawTropimonLightPanel")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) { constants.add(value); }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            calls.add(name + descriptor);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(List.of("historyBackground()I", "fill(IIIII)V"), calls);
        assertTrue(constants.isEmpty(), "The day/night palette supplies the single fill color dynamically");
    }

    @Test void historyDrawsItsFrameOnlyOnceAndHasNoOversizedShadow() throws Exception {
        int[] frameCalls = {0};
        try (var stream = BattleUiRenderer.class.getResourceAsStream("BattleUiRenderer.class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("renderHistoryWindow")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) {
                            assertNotEquals(Integer.valueOf(0x9910161A), value, "Do not restore the overflowing shadow rectangle");
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (name.equals("drawTropimonFrame")) frameCalls[0]++;
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, frameCalls[0]);
    }

    @Test void badgesReuseTheNativePpStripAndTurnButtonHasNativeHoverFrames() throws Exception {
        var cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElseThrow();
        var overlayPath = cobblemon.findPath("assets/cobblemon/" + BattleUiSkin.COBBLEMON_MOVE_OVERLAY.getPath()).orElseThrow();
        try (var input = Files.newInputStream(overlayPath)) {
            var image = javax.imageio.ImageIO.read(input);
            assertEquals(92, image.getWidth()); assertEquals(24, image.getHeight());
            assertEquals(0xFF2F2F2F, image.getRGB(46, 13));
            assertEquals(0xFF2F2F2F, image.getRGB(70, 20));
            assertEquals(0, image.getRGB(42, 13) >>> 24, "Native slanted PP edge is preserved");
        }
        var buttonPath = cobblemon.findPath("assets/cobblemon/" + BattleUiSkin.COBBLEMON_BUTTON.getPath()).orElseThrow();
        try (var input = Files.newInputStream(buttonPath)) {
            var image = javax.imageio.ImageIO.read(input);
            assertEquals(92, image.getWidth()); assertEquals(48, image.getHeight());
            assertNotEquals(image.getRGB(20, 10), image.getRGB(20, 34));
        }
    }

    @Test void battleDragAndMouseReleaseHooksAreActuallyApplied() {
        assertTrue(java.util.Arrays.stream(com.cobblemon.mod.common.client.gui.battle.BattleGUI.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$drag")));
        assertTrue(java.util.Arrays.stream(com.cobblemon.mod.common.client.gui.battle.BattleGUI.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$click")));
        assertTrue(java.util.Arrays.stream(net.minecraft.client.Mouse.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("tropimonBattleUi$release")));
        var releases = java.util.Arrays.stream(net.minecraft.client.Mouse.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("tropimonBattleUi$release")).toList();
        assertEquals(1, releases.size(), "A single release hook must finish every movable HUD block");
        assertTrue(java.util.Arrays.stream(BattleTeamHudPanel.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("finish")),
                "the team rows must expose the same release endpoint as the other movable HUD blocks");
    }

    @Test void secondClickCoordinatorFinishesEveryMovableHudBlock() throws Exception {
        var owners = new java.util.HashSet<String>();
        try (var stream = BattleSkinPresentationTest.class
                .getResourceAsStream("/fr/tropimon/battleui/mixin/BattleGuiMixin.class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                             String signature, String[] exceptions) {
                    if (!name.contains("tropimonBattleUi$click")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                              String descriptor, boolean isInterface) {
                            if (name.equals("finish") || name.equals("finishHistoryInteraction")) owners.add(owner);
                        }
                    };
                }
            }, 0);
        }
        assertTrue(owners.contains("fr/tropimon/battleui/BattleActionPanel"));
        assertTrue(owners.contains("fr/tropimon/battleui/BattlePokemonHudPanel"));
        assertTrue(owners.contains("fr/tropimon/battleui/BattleTeamHudPanel"));
        assertTrue(owners.contains("fr/tropimon/battleui/BattleUiRenderer"));
    }

    @Test void removedTeamBackgroundIsNotPackagedButHistoryFrameRemains() {
        var root = Path.of("src/main/resources/assets/tropimon_ui_battle/textures/gui/skin");
        assertFalse(Files.exists(root.resolve("team_emblem.png")));
        assertFalse(Files.exists(root.resolve("team_emblem.png.mcmeta")));
        assertTrue(Files.isRegularFile(root.resolve("history_frame.png")));
        assertEquals("tropimon_ui_battle", BattleUiSkin.TROPIMON_NAVIGATOR_FRAME.getNamespace());
    }

    @Test void everyStatusUsesItsOwnNativeStripAndNativeLocalizedAbbreviation() throws Exception {
        var cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElseThrow();
        var language = JsonParser.parseString(Files.readString(cobblemon.findPath("assets/cobblemon/lang/en_us.json")
                .orElseThrow())).getAsJsonObject();
        for (String status : List.of("brn","par","psn","tox","slp","frz","fnt")) {
            var label = BattleUiSkin.statusLabel(status);
            var content = assertInstanceOf(TranslatableTextContent.class, label.getContent());
            assertEquals("cobblemon.ui.status." + status, content.getKey());
            assertTrue(label.getStyle().isBold());
            assertTrue(language.has(content.getKey()));
            assertTrue(language.get(content.getKey()).getAsString().length() <= 3);
            var texture = BattleUiSkin.statusTexture(status);
            assertEquals("cobblemon", texture.getNamespace());
            assertTrue(cobblemon.findPath("assets/cobblemon/" + texture.getPath()).isPresent());
        }
        assertNull(BattleUiSkin.statusTexture(""));
        assertEquals("", BattleUiSkin.statusLabel("").getString());
    }
}
