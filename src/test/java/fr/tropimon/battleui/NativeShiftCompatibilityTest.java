package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NativeShiftCompatibilityTest {
    @Test void cobblemonExposesItsNativeTripleShiftActionAndButton() throws Exception {
        Class<?> selection = Class.forName(
                "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection");
        Class<?> button = Class.forName(
                "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleShiftButton");
        Class<?> response = Class.forName("com.cobblemon.mod.common.battles.ShiftActionResponse");
        assertEquals(button, selection.getMethod("getShiftButton").getReturnType());
        assertNotNull(response.getDeclaredConstructor());
    }

    @Test void nativeSpecialButtonsKeepTheirTransformedRenderAndHoverMethods() throws Exception {
        Class<?> gimmick = Class.forName(
                "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGimmickButton");
        assertNotNull(gimmick.getMethod("render", net.minecraft.client.util.math.MatrixStack.class,
                int.class, int.class, float.class));
        assertNotNull(gimmick.getMethod("isHovered", double.class, double.class));
        Class<?> shift = Class.forName(
                "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleShiftButton");
        assertNotNull(shift.getMethod("render", net.minecraft.client.gui.DrawContext.class,
                int.class, int.class, float.class));
        assertNotNull(shift.getMethod("isHovered", double.class, double.class));
    }

    @Test void cobblemonStillExposesAllFourAuthoritativeBattlePacketHooks() throws Exception {
        Class<?> client = net.minecraft.client.MinecraftClient.class;
        assertEquals(java.util.List.class,
                com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket.class
                        .getMethod("getMessages").getReturnType());
        assertNotNull(com.cobblemon.mod.common.client.net.battle.BattleHealthChangeHandler.class
                .getMethod("handle", com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket.class,
                        client));
        assertNotNull(com.cobblemon.mod.common.client.net.battle.BattleFaintHandler.class
                .getMethod("handle", com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket.class,
                        client));
        assertNotNull(com.cobblemon.mod.common.client.net.battle.BattleReplacePokemonHandler.class
                .getMethod("handle", com.cobblemon.mod.common.net.messages.client.battle.BattleReplacePokemonPacket.class,
                        client));
    }
}
