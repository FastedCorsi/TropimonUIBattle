package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.Text;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.MoveTarget;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchMatchupRendererTest {
    @AfterEach void clearBattleState() throws Exception {
        teraTypes().clear();
        PokemonBattleEffects.reset();
    }

    @Test void defensiveLabelsDistinguishImmunityResistanceNeutralityWeaknessAndUnknown() {
        assertLabel(0, "immune", "0");
        assertLabel(0.5, "resists", "0.5");
        assertLabel(1, "neutral", "1");
        assertLabel(2, "weak", "2");
        assertLabel(Double.NaN, "unknown");
    }

    @Test void panelIsCenteredAboveTheWholeParty() {
        var position = SwitchMatchupRenderer.panelPosition(40, 240, 180, 270, 90, 420, 260, 6);
        assertEquals(6, position.x());
        assertEquals(68, position.y());
    }

    @Test void panelStaysInsideTheScreenAtTheTopEdge() {
        var position = SwitchMatchupRenderer.panelPosition(100, 300, 45, 270, 90, 420, 260, 6);
        assertEquals(65, position.x());
        assertEquals(6, position.y());
    }

    @Test void oversizedPanelIsClampedOnSmallGuiScales() {
        assertEquals(108, SwitchMatchupRenderer.maximumPanelHeight(120, 6));
        var position = SwitchMatchupRenderer.panelPosition(0, 80, 20, 108, 108, 120, 120, 6);
        assertTrue(position.x() >= 0 && position.x() + 108 <= 120);
        assertTrue(position.y() >= 0 && position.y() + 108 <= 120);
    }

    @Test void moveNamesAreBoldAndColoredByTheirType() {
        MoveTemplate move = TestMoveTemplates.create("fixture", 0, ElementalTypes.FIRE,
                DamageCategories.INSTANCE.getSPECIAL(), 80, MoveTarget.normal, 100, 15, 0, 1, new Double[0]);
        var text = SwitchMatchupRenderer.emphasizedMoveName(move);
        assertTrue(text.getStyle().isBold());
        assertEquals(MoveTooltipRenderer.typeColor("fire") & 0xFFFFFF, text.getStyle().getColor().getRgb());
    }

    @Test void targetCacheKeyChangesWhenAnOtherwiseIdenticalTargetTerastallizesStellar() throws Exception {
        UUID id = UUID.randomUUID();
        var target = new BattleUiState.ActiveTargetView(id, "Target",
                List.of(new TypeView("water", Text.literal("Water"))), null);
        var before = SwitchMatchupRenderer.targetKey(target, null);

        teraTypes().put(id, "stellar");

        assertNotEquals(before, SwitchMatchupRenderer.targetKey(target, null));
    }

    @Test void targetCacheKeyChangesAcrossTheFullHpThresholdUsedByTeraShell() {
        UUID id = UUID.randomUUID();
        var full = new BattleUiState.ActiveTargetView(id, "Target",
                List.of(new TypeView("normal", Text.literal("Normal"))),
                new AbilityView("terashell", Text.literal("Tera Shell"), Text.empty()),
                "", "", List.of(), 100.0F);
        var damaged = new BattleUiState.ActiveTargetView(id, "Target", full.types(), full.ability(),
                "", "", List.of(), 99.9F);

        assertNotEquals(SwitchMatchupRenderer.targetKey(full, null),
                SwitchMatchupRenderer.targetKey(damaged, null));
    }

    @Test void activeAbilityKeyChangesWhenCloudNineIsReplacedByNeutralizingGas() {
        TeamMemberView cloudNine = member(UUID.randomUUID(), "cloudnine", true);
        TeamMemberView neutralizingGas = member(UUID.randomUUID(), "neutralizinggas", false);
        var before = SwitchMatchupRenderer.activeAbilityKeys(List.of(cloudNine, neutralizingGas));
        var after = SwitchMatchupRenderer.activeAbilityKeys(List.of(
                cloudNine.withActive(false), neutralizingGas.withActive(true)));

        assertNotEquals(before, after);
        assertEquals("cloudnine", before.getFirst().ability());
        assertEquals("neutralizinggas", after.getFirst().ability());
    }

    private static void assertLabel(double multiplier, String suffix, Object... args) {
        var text = SwitchMatchupRenderer.defenseLabel(multiplier);
        var content = (TranslatableTextContent) text.getContent();
        assertEquals("text.tropimon_ui_battle.switch_matchup." + suffix, content.getKey());
        assertEquals(java.util.List.of(args), java.util.List.of(content.getArgs()));
    }

    private static TeamMemberView member(UUID id, String ability, boolean active) {
        return new TeamMemberView(id, ability, 100, 100, "", false, active, ItemStack.EMPTY, null,
                List.of(new TypeView("normal", Text.literal("Normal"))),
                new AbilityView(ability, Text.literal(ability), Text.empty()), List.of(), List.of(), true);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, String> teraTypes() throws Exception {
        Field field = BattleUiState.class.getDeclaredField("TERA_TYPES");
        field.setAccessible(true);
        return (Map<UUID, String>) field.get(null);
    }
}
