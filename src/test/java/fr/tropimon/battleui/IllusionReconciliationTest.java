package fr.tropimon.battleui;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IllusionReconciliationTest {
    private final List<UUID> touched = new ArrayList<>();

    @BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void normalAndHisuianZoroarkDoNotKeepDamageOrStatusOnThePreviouslySeenDecoy() throws Exception {
        for (String species : List.of("Zoroark", "Zoroark-Hisui")) {
            UUID decoy = id(), real = id();
            Map<UUID, TeamMemberView> team = map("OPPONENT_TEAM");
            Map<UUID, LinkedHashMap<String, MoveView>> moves = map("REVEALED_MOVES");
            team.put(decoy, member(decoy, "Weezing-Galar", 83.5F, "brn", false));
            moves.put(decoy, new LinkedHashMap<>(Map.of("toxic", move("toxic").spendPp(5))));
            var before = BattleUiState.opponentMemory(decoy);
            team.put(decoy, member(decoy, "Weezing-Galar", 24F, "psn", true));
            moves.get(decoy).put("toxic", moves.get(decoy).get("toxic").spendPp(2));
            moves.get(decoy).put("nightdaze", move("nightdaze").spendPp(1));
            team.put(real, member(real, species, 24F, "psn", true));
            moves.put(real, new LinkedHashMap<>(Map.of("toxic", move("toxic").spendPp(1))));
            PokemonBattleEffects.accept(decoy, "cobblemon.battle.start.confusion", new Object[0], 4);

            BattleUiState.reconcileIllusion(decoy, real, before);

            assertEquals(83.5F, team.get(decoy).hpPercent());
            assertEquals("brn", team.get(decoy).status());
            assertFalse(team.get(decoy).active());
            assertEquals(24F, team.get(real).hpPercent());
            assertEquals(species, team.get(real).name());
            assertEquals(5, moves.get(decoy).get("toxic").ppUsed());
            assertFalse(moves.get(decoy).containsKey("nightdaze"));
            assertEquals(3, moves.get(real).get("toxic").ppUsed());
            assertEquals(1, moves.get(real).get("nightdaze").ppUsed());
            assertFalse(PokemonBattleEffects.active(decoy, "confusion", 4));
            assertTrue(PokemonBattleEffects.active(real, "confusion", 4));
        }
    }

    @Test void anUnseenDecoyDoesNotLeaveAPhantomDamagedOrFaintedTeamCard() throws Exception {
        UUID decoy = id(), real = id();
        Map<UUID, TeamMemberView> team = map("OPPONENT_TEAM");
        var before = BattleUiState.opponentMemory(decoy);
        team.put(decoy, member(decoy, "Dragonite", 0F, "fnt", true));
        BattleUiState.reconcileIllusion(decoy, real, before);
        assertFalse(team.containsKey(decoy));
        assertFalse(team.containsKey(real)); // The native replacement DTO creates the actual card, never a guessed copy.
    }

    @Test void sameUuidReplacementDoesNotEraseKnownHealthOrPp() throws Exception {
        UUID pokemon = id();
        Map<UUID, TeamMemberView> team = map("OPPONENT_TEAM");
        team.put(pokemon, member(pokemon, "Zoroark-Hisui", 0.04F, "", true));
        var before = BattleUiState.opponentMemory(pokemon);
        BattleUiState.reconcileIllusion(pokemon, pokemon, before);
        assertEquals(0.04F, team.get(pokemon).hpPercent());
        assertTrue(team.get(pokemon).active());
    }

    @Test void illusionTransfersObservedStagesAndConfirmedCuresWithoutPollutingTheDecoy() throws Exception {
        UUID decoy = id(), real = id();
        Map<UUID, TeamMemberView> team = map("OPPONENT_TEAM");
        team.put(decoy, member(decoy, "Decoy", 100F, "par", true));
        var before = BattleUiState.opponentMemory(decoy);

        var stagesField = BattleUiState.class.getDeclaredField("OBSERVED_STAT_STAGES");
        stagesField.setAccessible(true);
        var stages = (BattleStatStageTracker) stagesField.get(null);
        stages.change(decoy, "spa", 2);
        var curesField = BattleUiState.class.getDeclaredField("CONFIRMED_STATUS_CURES");
        curesField.setAccessible(true);
        @SuppressWarnings("unchecked") var cures = (Set<UUID>) curesField.get(null);
        cures.add(decoy);

        BattleUiState.reconcileIllusion(decoy, real, before);

        assertTrue(stages.stages(decoy).isEmpty());
        assertEquals(2, stages.stages(real).get("spa"));
        assertEquals("par", BattleUiState.resolvedStatus(decoy, "par"));
        assertEquals("", BattleUiState.resolvedStatus(real, "par"));
    }

    @Test void nativeCobblemonIdentityHooksAreActuallyAppliedByFabric() {
        var slotMethods = Arrays.stream(com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        var replacementMethods = Arrays.stream(com.cobblemon.mod.common.client.net.battle.BattleReplacePokemonHandler.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        assertTrue(slotMethods.stream().anyMatch(name -> name.contains("tropimonUiBattle$rememberAppearance")));
        assertTrue(replacementMethods.stream().anyMatch(name -> name.contains("tropimonUiBattle$revealIllusion")));
        assertTrue(replacementMethods.stream().anyMatch(name -> name.contains("tropimonUiBattle$refreshIdentity")));
    }

    @AfterEach void cleanup() throws Exception {
        for (var field : BattleUiState.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> values) touched.stream().filter(values::containsKey).forEach(values::remove);
            if (value instanceof Set<?> values) touched.stream().filter(values::contains).forEach(values::remove);
        }
        PokemonBattleEffects.reset();
    }

    private UUID id() { UUID result = UUID.randomUUID(); touched.add(result); return result; }
    @SuppressWarnings("unchecked") private static <T> Map<UUID, T> map(String name) throws Exception {
        var field = BattleUiState.class.getDeclaredField(name); field.setAccessible(true);
        return (Map<UUID, T>) field.get(null);
    }
    private static TeamMemberView member(UUID uuid, String name, float hp, String status, boolean active) {
        return new TeamMemberView(uuid, name, 100, hp, status, hp <= 0, active, ItemStack.EMPTY, null,
                List.of(), null, List.of(), List.of(), true);
    }
    private static MoveView move(String id) {
        return new MoveView(id, Text.literal(id), Text.empty(), "dark", 16, 16, 0, true);
    }
}
