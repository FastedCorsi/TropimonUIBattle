package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Path;
import java.util.List;

/** Independent movable anchors for the two compact team rows rendered above the battle. */
public final class BattleTeamHudPanel {
    private static final int TRAINER_LABEL_SPACE = 12;
    private static final BattlePokemonHudLayout LAYOUT = new BattlePokemonHudLayout();
    private static Path settings;

    private BattleTeamHudPanel() { }

    static int offsetX(boolean own, BattleUiLayout ui) {
        BattlePokemonHudLayout.Placement placement = placement(own, ui);
        return placement.x() - (own ? ui.ownTeamX() : ui.opponentTeamX());
    }

    static int offsetY(boolean own, BattleUiLayout ui) {
        return placement(own, ui).y() - ui.teamTop();
    }

    public static void renderHandles(DrawContext context, int mouseX, int mouseY) {
        if (!active()) return;
        BattleUiLayout ui = currentLayout();
        for (boolean own : new boolean[]{true, false}) {
            BattlePokemonHudLayout.Placement placement = placement(own, ui);
            boolean hovered = placement.onHandle(mouseX, mouseY)
                    && !BattleUiRenderer.historyCoversPointer(mouseX, mouseY);
            BattlePokemonHudPanel.drawMoveHandle(context, placement.handleX(), placement.handleY(), hovered,
                    "text.tropimon_ui_battle.team_hud.move_hint", mouseX, mouseY);
        }
    }

    public static boolean click(double x, double y, int button) {
        if (!active() || BattleUiRenderer.historyCoversPointer(x, y)) return false;
        // A second left click always releases a gesture if the platform missed
        // the original mouse-up event. This prevents the team row following the
        // pointer indefinitely while keeping normal click-and-drag behaviour.
        if (LAYOUT.dragging()) {
            if (button == 0) finish();
            return true;
        }
        BattleUiLayout ui = currentLayout();
        for (boolean own : new boolean[]{true, false}) {
            BattlePokemonHudLayout.Placement placement = placement(own, ui);
            if (button == 1 && placement.onHandle(x, y)) {
                LAYOUT.reset(own);
                finish();
                return true;
            }
            if (LAYOUT.begin(own, placement, x, y, button)) return true;
        }
        return false;
    }

    public static boolean drag(double x, double y, int button) {
        if (button != 0 || !LAYOUT.dragging()) return false;
        if (!active()) { finish(); return true; }
        BattleUiLayout ui = currentLayout();
        Dimensions dimensions = dimensions(LAYOUT.draggingOwn(), ui);
        return LAYOUT.drag(x, y, ui.screenWidth(), ui.screenHeight(), dimensions.width(), dimensions.height());
    }

    public static boolean dragging() { return LAYOUT.dragging(); }

    public static void finish() {
        if (!LAYOUT.finish() || settings == null) return;
        try { LAYOUT.save(settings); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save the team-row positions.");
        }
    }

    static void load(Path file) {
        settings = file;
        try { LAYOUT.load(file); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not load the team-row positions; using defaults.");
        }
    }

    private static BattlePokemonHudLayout.Placement placement(boolean own, BattleUiLayout ui) {
        Dimensions dimensions = dimensions(own, ui);
        return LAYOUT.layout(own, ui.screenWidth(), ui.screenHeight(), dimensions.width(), dimensions.height(),
                own ? ui.ownTeamX() : ui.opponentTeamX(), ui.teamTop());
    }

    private static Dimensions dimensions(boolean own, BattleUiLayout ui) {
        List<TeamMemberView> team = hudTeam(own);
        return new Dimensions(own ? ui.ownTeamWidth() : ui.opponentTeamWidth(),
                visibleHeight(team, ui.teamTileHeight()));
    }

    private static List<TeamMemberView> hudTeam(boolean own) {
        return own ? BattleUiState.ownHudTeam() : BattleUiState.opponentHudTeam();
    }

    static int visibleHeight(List<TeamMemberView> team, int tileHeight) {
        boolean statusVisible = team.stream().anyMatch(member -> {
            String status = member.fainted() ? "fnt" : member.status();
            return BattleUiSkin.statusTexture(status) != null;
        });
        boolean trainerGroups = hasTrainerGroups(team);
        // Trainer names are rendered after the complete native tile. Keep that
        // internal status row even when empty, otherwise the label itself would
        // start exactly at the bottom edge and be clipped off-screen.
        int statusSpace = statusVisible || trainerGroups ? 0 : TeamIconLayout.STATUS_HEIGHT + 1;
        return Math.max(1, tileHeight - statusSpace)
                + (trainerGroups ? TRAINER_LABEL_SPACE : 0);
    }

    static boolean hasTrainerGroups(List<TeamMemberView> team) {
        return team.stream().map(member -> BattleUiState.trainerName(member.uuid()))
                .filter(name -> !name.isBlank()).distinct().limit(2).count() >= 2;
    }

    private static BattleUiLayout currentLayout() {
        MinecraftClient client = MinecraftClient.getInstance();
        return layout(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
    }

    static BattleUiLayout layout(int screenWidth, int screenHeight) {
        List<TeamMemberView> own = BattleUiState.ownHudTeam();
        List<TeamMemberView> opponent = BattleUiState.opponentHudTeam();
        return BattleUiLayout.calculate(screenWidth, screenHeight, Math.max(6, own.size()),
                Math.max(Math.max(1, BattleUiState.opponentSlotCount()), opponent.size()));
    }

    private static boolean active() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof BattleGUI && BattleUiState.active() && !client.options.hudHidden;
    }

    private record Dimensions(int width, int height) { }
}
