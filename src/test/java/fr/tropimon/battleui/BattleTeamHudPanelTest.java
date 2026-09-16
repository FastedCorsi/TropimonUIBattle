package fr.tropimon.battleui;

import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleTeamHudPanelTest {
    @BeforeAll
    static void bootstrap() {
        DamageCacheParityTest.bootstrap();
    }

    @AfterEach
    void clearTrainerNames() throws Exception {
        trainerNames().clear();
    }

    @Test
    void healthySingleTrainerCanPutTheHealthPercentageOnTheBottomEdge() throws Exception {
        TeamMemberView member = member("", false);
        trainerNames().put(member.uuid(), "Trainer");
        int tileWidth = 36;
        int tileHeight = TeamIconLayout.heightFor(tileWidth);
        int visible = BattleTeamHudPanel.visibleHeight(List.of(member), tileHeight);
        assertEquals(tileHeight - TeamIconLayout.STATUS_HEIGHT - 1, visible,
                "an empty status row must not reserve invisible space");

        BattlePokemonHudLayout layout = new BattlePokemonHudLayout();
        var start = layout.layout(true, 320, 180, 140, visible, 12, 3);
        layout.begin(true, start, start.handleX() + 2, start.handleY() + 2, 0);
        layout.drag(10_000, 10_000, 320, 180, 140, visible);
        var bottom = layout.layout(true, 320, 180, 140, visible, 12, 3);
        assertEquals(180, bottom.y() + visible);
        var icon = TeamIconLayout.of(0, bottom.y(), tileWidth, tileHeight,
                TeamIconLayout.portraitSizeFor(tileWidth));
        assertEquals(180, icon.hpY() + icon.hpHeight(),
                "the percentage row, not an invisible status slot, reaches the edge");
    }

    @Test
    void visibleStatusAndMultiTrainerLabelReserveOnlyTheirRealPixels() throws Exception {
        TeamMemberView first = member("par", false);
        TeamMemberView second = member("", false);
        trainerNames().put(first.uuid(), "Trainer A");
        trainerNames().put(second.uuid(), "Trainer B");
        assertEquals(62, BattleTeamHudPanel.visibleHeight(List.of(first, second), 50));

        trainerNames().put(second.uuid(), "Trainer A");
        assertEquals(50, BattleTeamHudPanel.visibleHeight(List.of(first, second), 50));
        assertStatusRowReachesBottom(List.of(first, second));

        TeamMemberView fainted = member("", true);
        trainerNames().put(fainted.uuid(), "Trainer A");
        assertEquals(50, BattleTeamHudPanel.visibleHeight(List.of(fainted), 50),
                "the KO badge needs the same complete status row");
        assertStatusRowReachesBottom(List.of(fainted));
    }

    @Test
    void healthyMultiTrainerLabelsRemainVisibleAtTheBottomEdge() throws Exception {
        TeamMemberView first = member("", false);
        TeamMemberView second = member("", false);
        trainerNames().put(first.uuid(), "Trainer A");
        trainerNames().put(second.uuid(), "Trainer B");

        assertEquals(62, BattleTeamHudPanel.visibleHeight(List.of(first, second), 50),
                "the label is rendered after the full 50 px tile and needs all 12 trailing pixels");
    }

    private static void assertStatusRowReachesBottom(List<TeamMemberView> team) {
        int tileWidth = 36;
        int tileHeight = TeamIconLayout.heightFor(tileWidth);
        int visible = BattleTeamHudPanel.visibleHeight(team, tileHeight);
        BattlePokemonHudLayout layout = new BattlePokemonHudLayout();
        var start = layout.layout(true, 320, 180, 140, visible, 12, 3);
        layout.begin(true, start, start.handleX() + 2, start.handleY() + 2, 0);
        layout.drag(10_000, 10_000, 320, 180, 140, visible);
        var bottom = layout.layout(true, 320, 180, 140, visible, 12, 3);
        var icon = TeamIconLayout.of(0, bottom.y(), tileWidth, tileHeight,
                TeamIconLayout.portraitSizeFor(tileWidth));
        assertEquals(180, icon.statusY() + icon.statusHeight());
    }

    private static TeamMemberView member(String status, boolean fainted) {
        return new TeamMemberView(UUID.randomUUID(), "Fixture", 100, fainted ? 0F : 100F,
                status, fainted, false, ItemStack.EMPTY, null, List.of(), null,
                List.of(), List.of(), true);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, String> trainerNames() throws Exception {
        Field field = BattleUiState.class.getDeclaredField("TRAINER_BY_POKEMON");
        field.setAccessible(true);
        return (Map<UUID, String>) field.get(null);
    }
}
