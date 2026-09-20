package fr.tropimon.battleui;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BattleUiPreferencesTest {
    @TempDir Path temporary;
    @AfterEach void reset() { BattleUiPreferences.load(temporary.resolve("defaults.properties")); }

    @Test void tooltipFiltersHideOnlySelectedPresentationAndKeepHealthAndStatus() {
        DamageCacheParityTest.bootstrap();
        BattleUiPreferences.load(temporary.resolve("filters.properties"));
        var member = new TeamMemberView(java.util.UUID.randomUUID(), "Fixture", 50, 65, "brn", false, true,
                net.minecraft.item.ItemStack.EMPTY, null,
                java.util.List.of(new TypeView("water", net.minecraft.text.Text.literal("Fixture type"))),
                new AbilityView("torrent", net.minecraft.text.Text.literal("Fixture ability"),
                        net.minecraft.text.Text.literal("Fixture description")), java.util.List.of(), java.util.List.of(), true);
        var knowledge = OpponentKnowledgeView.empty();
        var before = BattleUiRenderer.teamTooltipRows(member, false, knowledge, SpeedRangeView.unknown(),
                BattleStatsView.UNKNOWN, java.util.List.of());
        assertTrue(before.stream().anyMatch(r -> r.text().getString().equals("Fixture description")));
        BattleUiPreferences.toggle(BattleUiPreferences.Detail.ABILITY_DESCRIPTION);
        var withoutDescription = BattleUiRenderer.teamTooltipRows(member, false, knowledge, SpeedRangeView.unknown(),
                BattleStatsView.UNKNOWN, java.util.List.of());
        assertEquals(before.size() - 1, withoutDescription.size());
        assertTrue(withoutDescription.stream().anyMatch(r -> r.text().getString().contains("Fixture type")));
        for (var detail : BattleUiPreferences.Detail.values())
            if (BattleUiPreferences.show(detail)) BattleUiPreferences.toggle(detail);
        var minimal = BattleUiRenderer.teamTooltipRows(member, false, knowledge, SpeedRangeView.unknown(),
                BattleStatsView.UNKNOWN, java.util.List.of());
        assertEquals(2, minimal.size());
        assertEquals(before.subList(0, 2), minimal);
        assertEquals("brn", member.status());
        assertEquals(65, member.hpPercent());
    }

    @Test void settingsSurviveReloadWithoutDroppingUnrelatedDetailSelections() throws Exception {
        Path file = temporary.resolve("display.properties");
        BattleUiPreferences.load(file);
        BattleUiPreferences.cycleChatSize();
        BattleUiPreferences.cycleTooltipSize();
        BattleUiPreferences.cyclePalette();
        BattleUiPreferences.toggleRows();
        BattleUiPreferences.toggle(BattleUiPreferences.Detail.TYPES);
        BattleUiPreferences.toggle(BattleUiPreferences.Detail.ABILITY_DESCRIPTION);
        long before = BattleUiPreferences.revision();
        BattleUiPreferences.load(file);
        assertTrue(BattleUiPreferences.revision() > before);
        assertEquals(70, BattleUiPreferences.chatPercent());
        assertEquals(70, BattleUiPreferences.tooltipPercent());
        assertEquals(BattleUiPreferences.Palette.SLATE, BattleUiPreferences.palette());
        assertFalse(BattleUiPreferences.rowBackgrounds());
        assertFalse(BattleUiPreferences.show(BattleUiPreferences.Detail.TYPES));
        assertFalse(BattleUiPreferences.show(BattleUiPreferences.Detail.ABILITY_DESCRIPTION));
        assertTrue(BattleUiPreferences.show(BattleUiPreferences.Detail.ABILITY));
        assertTrue(BattleUiPreferences.show(BattleUiPreferences.Detail.WEAKNESSES));
    }

    @Test void invalidSettingsRecoverAndMissingKeysKeepExistingPresentation() throws Exception {
        Path file = temporary.resolve("display.properties");
        Files.writeString(file, "chatPercent=0\ntooltipPercent=NaN\npalette=invalid\nshow.TYPES=false\n");
        BattleUiPreferences.load(file);
        assertEquals(100, BattleUiPreferences.chatPercent());
        assertEquals(100, BattleUiPreferences.tooltipPercent());
        assertEquals(BattleUiPreferences.Palette.DEFAULT, BattleUiPreferences.palette());
        assertTrue(BattleUiPreferences.show(BattleUiPreferences.Detail.MOVES));
        assertFalse(BattleUiPreferences.show(BattleUiPreferences.Detail.TYPES));
        BattleUiPreferences.reset();
        assertTrue(BattleUiPreferences.show(BattleUiPreferences.Detail.TYPES));
    }

    @Test void smallerTextFitsMoreContentAndHoverCoordinatesUseTheSameScale() {
        for (float scale : new float[]{0.7F, 0.8F, 0.9F, 1.0F}) {
            int wrap = BattleUiPreferences.wrapWidth(200, scale);
            assertTrue(wrap * scale <= 200.01F);
            assertTrue((wrap + 1) * scale > 200);
            assertEquals(40, BattleUiPreferences.textCoordinate(40.25 * scale, scale));
            assertTrue(BattleUiPreferences.lineHeight(scale) >= 9 * scale);
        }
        assertTrue(BattleUiPreferences.wrapWidth(200, .7F) > BattleUiPreferences.wrapWidth(200, 1));
    }
}
