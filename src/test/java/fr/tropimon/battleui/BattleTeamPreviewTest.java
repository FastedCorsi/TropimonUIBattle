package fr.tropimon.battleui;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.item.components.PokemonItemComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class BattleTeamPreviewTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }
    @AfterEach void reset() { BattleTeamPreview.reset(); }

    @Test void extractsTheRealCobblemonPokemonItemComponent() {
        Identifier species = Identifier.of("cobblemon", "preview_fixture");
        PokemonItemComponent component = new PokemonItemComponent(
                species, Set.of("shiny"), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(CobblemonItemComponents.POKEMON_ITEM, component);

        PokemonItemComponent extracted = BattleTeamPreview.pokemonItemComponent(stack);

        assertNotNull(extracted);
        assertEquals(species, extracted.getSpecies());
        assertEquals(Set.of("shiny"), extracted.getAspects());
        assertNull(BattleTeamPreview.pokemonItemComponent(ItemStack.EMPTY));
    }

    @Test void recognizesSupportedPreviewTitlesInEnglishAndFrench() {
        assertTrue(BattleTeamPreview.recognizedTitle("Select your lead Pokemon"));
        assertTrue(BattleTeamPreview.recognizedTitle("Select 2 Pokemon for doubles"));
        assertTrue(BattleTeamPreview.recognizedTitle("Sélection de l'équipe"));
        assertFalse(BattleTeamPreview.recognizedTitle("Large Chest"));
    }

    @Test void singlesColumnsContainSixPlayerAndSixOpponentSlots() {
        assertEquals(List.of(0, 9, 18, 27, 36, 45), java.util.stream.IntStream.range(0, 54)
                .filter(slot -> BattleTeamPreview.playerSlot(slot, 54, false)).boxed().toList());
        assertEquals(List.of(8, 17, 26, 35, 44, 53), java.util.stream.IntStream.range(0, 54)
                .filter(slot -> BattleTeamPreview.opponentSlot(slot, 54, false)).boxed().toList());
        assertFalse(BattleTeamPreview.playerSlot(54, 54, false));
        assertFalse(BattleTeamPreview.opponentSlot(-1, 54, false));
    }

    @Test void doublesColumnsContainThreeRowsOfTwoPokemonPerSide() {
        assertEquals(List.of(19, 20, 28, 29, 37, 38), java.util.stream.IntStream.range(0, 54)
                .filter(slot -> BattleTeamPreview.playerSlot(slot, 54, true)).boxed().toList());
        assertEquals(List.of(24, 25, 33, 34, 42, 43), java.util.stream.IntStream.range(0, 54)
                .filter(slot -> BattleTeamPreview.opponentSlot(slot, 54, true)).boxed().toList());
    }

    @Test void capturedPreviewIsConsumedOnceAndExpires() {
        Object screen = new Object();
        BattleTeamPreview.Snapshot snapshot = new BattleTeamPreview.Snapshot(
                List.of(preview(0, "Garchomp")), List.of(preview(8, "Rotom")));
        BattleTeamPreview.remember(screen, snapshot, 1_000L);
        assertEquals(snapshot, BattleTeamPreview.consume(2_000L));
        assertTrue(BattleTeamPreview.consume(2_001L).empty());

        BattleTeamPreview.remember(screen, snapshot, 10_000L);
        assertTrue(BattleTeamPreview.consume(610_001L).empty());
    }

    @Test void repeatedCaptureKeepsAlreadyLoadedSideFromSameScreen() {
        Object screen = new Object();
        BattleTeamPreview.PreviewMember own = preview(0, "Garchomp");
        BattleTeamPreview.PreviewMember foe = preview(8, "Rotom");
        BattleTeamPreview.remember(screen, new BattleTeamPreview.Snapshot(List.of(own), List.of()), 1L);
        BattleTeamPreview.remember(screen, new BattleTeamPreview.Snapshot(List.of(), List.of(foe)), 2L);
        BattleTeamPreview.Snapshot result = BattleTeamPreview.consume(3L);
        assertEquals(List.of(own), result.player());
        assertEquals(List.of(foe), result.opponent());
    }

    @Test void duplicateSpeciesResolveOnePlaceholderAtATimeWithoutDuplicatingActualPokemon() {
        BattleTeamPreview.PreviewMember first = preview(8, "Ditto");
        BattleTeamPreview.PreviewMember second = preview(17, "Ditto");
        BattleTeamPreview.Snapshot snapshot = new BattleTeamPreview.Snapshot(
                List.of(), List.of(first, second));
        TeamMemberView actualA = observed("Ditto");

        List<TeamMemberView> firstPass = snapshot.merge(false, List.of(actualA), List.of());
        assertEquals(List.of(actualA.uuid(), second.view().uuid()),
                firstPass.stream().map(TeamMemberView::uuid).toList());

        TeamMemberView actualB = observed("Ditto");
        List<TeamMemberView> secondPass = snapshot.merge(false, List.of(actualB, actualA), firstPass);
        assertEquals(List.of(actualA.uuid(), actualB.uuid()),
                secondPass.stream().map(TeamMemberView::uuid).toList());
        assertEquals(2, new HashSet<>(secondPass.stream().map(TeamMemberView::uuid).toList()).size());
    }

    @Test void unmatchedPublicAppearanceIsNeverDiscarded() {
        BattleTeamPreview.PreviewMember preview = preview(8, "Garchomp");
        BattleTeamPreview.Snapshot snapshot = new BattleTeamPreview.Snapshot(List.of(), List.of(preview));
        TeamMemberView unexpected = observed("Zoroark");
        List<TeamMemberView> merged = snapshot.merge(false, List.of(unexpected), List.of());
        assertEquals(2, merged.size());
        assertEquals(unexpected.uuid(), merged.getLast().uuid());
    }

    private static BattleTeamPreview.PreviewMember preview(int slot, String species) {
        Identifier id = Identifier.of("cobblemon", species.toLowerCase(java.util.Locale.ROOT));
        TeamMemberView view = new TeamMemberView(UUID.randomUUID(), species, 0, 100.0F, "",
                false, false, ItemStack.EMPTY, null, List.of(), null, List.of(), List.of(), true);
        return new BattleTeamPreview.PreviewMember(slot, id, "", view);
    }

    private static TeamMemberView observed(String species) {
        return new TeamMemberView(UUID.randomUUID(), species, 100, 100.0F, "", false, true,
                ItemStack.EMPTY, null, List.of(new TypeView("normal", Text.literal("Normal"))),
                null, List.of(), List.of(), true);
    }
}
