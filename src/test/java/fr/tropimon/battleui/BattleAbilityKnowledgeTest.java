package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.api.abilities.CommonAbility;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.*;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleAbilityKnowledgeTest {
    private static List<AbilityTemplate> original;
    private final List<UUID> touched = new ArrayList<>();

    @BeforeAll static void prepare() {
        DamageCacheParityTest.bootstrap();
        original = List.copyOf(Abilities.all());
        Abilities.INSTANCE.receiveSyncPacket$common(List.of(ability("overgrow"), ability("chlorophyll"),
                ability("leafguard"), ability("poisonheal"), ability("waterabsorb"), ability("magicbounce"), ability("trace")));
    }
    @AfterAll static void restore() { Abilities.INSTANCE.receiveSyncPacket$common(original); }

    @Test void hiddenAbilitiesArePublicPossibilitiesEvenThoughGenerationRejectsThem() {
        var hidden = new HiddenAbility(Abilities.get("chlorophyll"));
        assertFalse(hidden.isSatisfiedBy(Set.of()));
        var possible = BattleUiState.possibleAbilities(List.of(new CommonAbility(Abilities.get("overgrow")), hidden), Set.of());
        assertEquals(List.of("overgrow", "chlorophyll"), possible.stream().map(AbilityView::id).toList());
        assertFalse(possible.getFirst().hidden());
        assertTrue(possible.getLast().hidden());
        assertTrue(possible.getLast().withSuppressed(true).hidden());
        assertNull(BattleUiState.currentAbility(UUID.randomUUID(), null), "possibilities do not reveal a real ability");
    }

    @Test void duplicateAbilitiesAndDifferentFormPoolsRemainDistinct() {
        var template = Abilities.get("chlorophyll");
        for (boolean hiddenFirst : List.of(true, false)) {
            var pool = hiddenFirst ? List.of(new HiddenAbility(template), new CommonAbility(template))
                    : List.of(new CommonAbility(template), new HiddenAbility(template));
            var possible = BattleUiState.possibleAbilities(new ArrayList<>(pool), Set.of());
            assertEquals(1, possible.size());
            assertFalse(possible.getFirst().hidden(), "not exclusively a hidden ability in this form");
        }
        var changedForm = BattleUiState.possibleAbilities(List.of(new CommonAbility(Abilities.get("leafguard"))), Set.of());
        assertEquals(List.of("leafguard"), changedForm.stream().map(AbilityView::id).toList());
    }

    @Test void revealsSurviveSwitchesSuppressionAndAnUnknownFallback() throws Exception {
        UUID id = addOpponent("FixtureA");
        BattleUiState.rememberRevealedAbility("cobblemon.battle.heal.poisonheal", new Object[]{Text.literal("FixtureA")});
        assertEquals("poisonheal", BattleUiState.currentAbility(id, null).id());
        assertEquals("poisonheal", BattleUiState.knownOpponent(id).ability().id());
        BattleUiState.rememberRevealedAbility("cobblemon.battle.endability", new Object[]{Text.literal("FixtureA")});
        assertTrue(BattleUiState.currentAbility(id, null).suppressed());
        var leave = BattleUiState.class.getDeclaredMethod("clearSwitchedOutDynamicTypes", Set.class);
        leave.setAccessible(true);
        leave.invoke(null, Set.of(id));
        leave.invoke(null, Set.of());
        var known = BattleUiState.currentAbility(id, null);
        assertEquals("poisonheal", known.id());
        assertFalse(known.suppressed());
        BattleUiState.rememberRevealedAbility("cobblemon.battle.damage.roughskin", new Object[]{Text.literal("FixtureA")});
        assertEquals("poisonheal", BattleUiState.currentAbility(id, null).id(), "a victim does not own Rough Skin");
    }

    @Test void explicitTraceRevealsBothOwnersWithoutFakingAnActivation() throws Exception {
        UUID tracer = addOpponent("FixtureA"), source = addOpponent("FixtureB");
        BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.trace", new Object[]{
                Text.literal("FixtureA"), Text.literal("FixtureB"), Text.translatable("cobblemon.ability.magicbounce")});
        assertEquals("magicbounce", BattleUiState.currentAbility(tracer, null).id());
        assertEquals("magicbounce", BattleUiState.currentAbility(source, null).id());
        assertFalse(map("FREE_NEXT_MOVE_USE").containsKey(source));
        assertFalse(map("FREE_NEXT_MOVE_USE").containsKey(tracer));
        assertEquals("magicbounce", BattleUiState.revealedAbility("cobblemon.battle.ability.magicbounce",
                new Object[]{Text.literal("FixtureA"), Text.translatable("cobblemon.ability.trace")}).getName());
    }

    @Test void aNewBattleResetsKnowledgeButTheSameBattleDoesNot() throws Exception {
        var begin = BattleUiState.class.getDeclaredMethod("beginIfNeeded", ClientBattle.class);
        begin.setAccessible(true);
        var battle = new ClientBattle(UUID.randomUUID(), new BattleFormat());
        begin.invoke(null, battle);
        UUID id = addOpponent("FixtureA");
        BattleUiState.rememberRevealedAbility("cobblemon.battle.heal.waterabsorb", new Object[]{Text.literal("FixtureA")});
        begin.invoke(null, battle);
        assertEquals("waterabsorb", BattleUiState.currentAbility(id, null).id());
        begin.invoke(null, new ClientBattle(UUID.randomUUID(), new BattleFormat()));
        assertNull(BattleUiState.currentAbility(id, null));
        assertNull(BattleUiState.knownOpponent(id));
    }

    @AfterEach void cleanup() throws Exception {
        for (var field : BattleUiState.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> values) touched.stream().filter(values::containsKey).forEach(values::remove);
            if (value instanceof Set<?> values) touched.stream().filter(values::contains).forEach(values::remove);
        }
    }

    private UUID addOpponent(String name) throws Exception {
        UUID id = UUID.randomUUID(); touched.add(id);
        map("OPPONENT_TEAM").put(id, new TeamMemberView(id, name, 100, 100F, "", false, true,
                ItemStack.EMPTY, null, List.of(), null, List.of(), List.of(), true));
        return id;
    }
    @SuppressWarnings("unchecked") private static <T> Map<UUID, T> map(String name) throws Exception {
        var field = BattleUiState.class.getDeclaredField(name); field.setAccessible(true);
        return (Map<UUID, T>) field.get(null);
    }
    private static AbilityTemplate ability(String id) {
        return new AbilityTemplate(id, (template, forced, priority) -> null,
                "cobblemon.ability." + id, "cobblemon.ability." + id + ".desc");
    }
}
