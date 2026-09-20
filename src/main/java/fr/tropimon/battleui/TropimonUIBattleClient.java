package fr.tropimon.battleui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TropimonUIBattleClient implements ClientModInitializer {
    public static final String MOD_ID = "tropimon_ui_battle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        TropimonSelfUpdater.start(LOGGER);
        BattleUiRenderer.loadHistoryWindow(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("history-window.properties"));
        BattleActionPanel.load(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("action-position.properties"));
        BattlePokemonHudPanel.load(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("pokemon-hud-position.properties"));
        BattleTeamHudPanel.load(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("team-row-position.properties"));
        BattleUiTheme.load(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("theme.properties"));
        BattleUiPreferences.load(FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID).resolve("display.properties"));
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public net.minecraft.util.Identifier getFabricId() {
                        return net.minecraft.util.Identifier.of(MOD_ID, "ui_caches");
                    }
                    @Override public void reload(net.minecraft.resource.ResourceManager manager) {
                        // No live Minecraft objects are inspected by the reload worker.
                        UiResourceEpoch.invalidate();
                    }
                });
        // Preload live registries/resources on the client thread, before a battle
        // hover could otherwise trigger the first database scan during rendering.
        ClientTickEvents.END_CLIENT_TICK.register(BattleCalcDex::preloadOnClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(BattleUiState::tick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> BattleUiRenderer.renderHud(context));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof BattleGUI) {
                ScreenEvents.remove(screen).register(removed -> {
                    BattleUiRenderer.finishHistoryInteraction();
                    BattleActionPanel.finish();
                    BattlePokemonHudPanel.finish();
                    BattleTeamHudPanel.finish();
                });
                ScreenKeyboardEvents.allowKeyPress(screen).register((current, key, scanCode, modifiers) ->
                        !BattleUiRenderer.handleKeyPress(key) && !finishHudDragOnEscape(key));
            }
        });

        if (FabricLoader.getInstance().isModLoaded("cobblemonextendedbattleui")) {
            LOGGER.warn("Cobblemon Extended Battle UI est chargé en même temps que Tropimon UI Battle. " +
                    "Les deux mods modifient le journal de combat et peuvent se superposer.");
        }
    }

    private static boolean finishHudDragOnEscape(int key) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return false;
        if (BattleActionPanel.dragging()) {
            BattleActionPanel.finish();
            return true;
        }
        if (BattlePokemonHudPanel.dragging()) {
            BattlePokemonHudPanel.finish();
            return true;
        }
        if (BattleTeamHudPanel.dragging()) {
            BattleTeamHudPanel.finish();
            return true;
        }
        return false;
    }
}
