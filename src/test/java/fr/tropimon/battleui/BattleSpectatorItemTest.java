package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.*;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleSpectatorItemTest {
    private static List<MoveTemplate> originalMoves;
    private static List<AbilityTemplate> originalAbilities;

    @BeforeAll static void bootstrap() {
        DamageCacheParityTest.bootstrap();
        for (String name : List.of("focus_sash", "focus_band", "air_balloon", "leftovers", "sitrus_berry", "lum_berry", "rocky_helmet")) {
            var id = Identifier.of("cobblemon", name);
            if (!Registries.ITEM.containsId(id)) Registry.register(Registries.ITEM, id, new Item(new Item.Settings()));
        }
        originalMoves = List.copyOf(Moves.all());
        originalAbilities = List.copyOf(Abilities.all());
        Moves.INSTANCE.receiveSyncPacket$common(List.of(
                TestMoveTemplates.create("surf", 0, ElementalTypes.WATER,
                        DamageCategories.INSTANCE.getSPECIAL(), 90, MoveTarget.allAdjacent, 100, 15, 0, 1, new Double[0]),
                TestMoveTemplates.create("trick", 0, ElementalTypes.PSYCHIC,
                        DamageCategories.INSTANCE.getSTATUS(), 0, MoveTarget.normal, 100, 10, 0, 1, new Double[0]),
                TestMoveTemplates.create("guardswap", 0, ElementalTypes.PSYCHIC,
                        DamageCategories.INSTANCE.getSTATUS(), 0, MoveTarget.normal, 100, 10, 0, 1, new Double[0]),
                TestMoveTemplates.create("powerswap", 0, ElementalTypes.PSYCHIC,
                        DamageCategories.INSTANCE.getSTATUS(), 0, MoveTarget.normal, 100, 10, 0, 1, new Double[0]),
                TestMoveTemplates.create("heartswap", 0, ElementalTypes.PSYCHIC,
                        DamageCategories.INSTANCE.getSTATUS(), 0, MoveTarget.normal, 100, 10, 0, 1, new Double[0]),
                TestMoveTemplates.create("batonpass", 0, ElementalTypes.NORMAL,
                        DamageCategories.INSTANCE.getSTATUS(), 0, MoveTarget.self, 0, 40, 0, 1, new Double[0]),
                sideEffect("tailwind"), sideEffect("reflect"), sideEffect("lightscreen"),
                sideEffect("auroraveil"), sideEffect("safeguard"), sideEffect("mist"),
                sideEffect("luckychant"),
                hazard("spikes"), hazard("toxicspikes"), hazard("stealthrock"), hazard("stickyweb"),
                hazardAttack("ceaselessedge"), hazardAttack("stoneaxe"),
                removalAttack("rapidspin", MoveTarget.normal),
                removalAttack("mortalspin", MoveTarget.allAdjacentFoes),
                removalStatus("defog", MoveTarget.normal),
                removalStatus("tidyup", MoveTarget.self),
                removalAttack("gmaxwindrage", MoveTarget.allAdjacentFoes),
                removalAttack("brickbreak", MoveTarget.normal),
                removalAttack("psychicfangs", MoveTarget.normal),
                removalAttack("ragingbull", MoveTarget.normal),
                removalAttack("icespinner", MoveTarget.normal),
                removalAttack("steelroller", MoveTarget.normal)));
        Abilities.INSTANCE.receiveSyncPacket$common(List.of(
                new AbilityTemplate("pressure", (t, f, p) -> null,
                        "cobblemon.ability.pressure", "cobblemon.ability.pressure.desc"),
                new AbilityTemplate("toxicdebris", (t, f, p) -> null,
                        "cobblemon.ability.toxicdebris", "cobblemon.ability.toxicdebris.desc"),
                new AbilityTemplate("magicbounce", (t, f, p) -> null,
                        "cobblemon.ability.magicbounce", "cobblemon.ability.magicbounce.desc"),
                new AbilityTemplate("screencleaner", (t, f, p) -> null,
                        "cobblemon.ability.screencleaner", "cobblemon.ability.screencleaner.desc")));
    }

    @AfterAll static void restoreRegistries() {
        Moves.INSTANCE.receiveSyncPacket$common(originalMoves);
        Abilities.INSTANCE.receiveSyncPacket$common(originalAbilities);
    }

    @BeforeEach void beginSpectating() throws Exception { begin(true); }
    @AfterEach void cleanup() throws Exception {
        CobblemonClient.INSTANCE.setBattle(null);
        begin(false);
    }

    @Test void spectatorOrientationMatchesCobblemonAndPrivatePartiesAreNeverRead() throws Exception {
        var battle = begin(true);
        var right = actor(battle, true, "RightTrainer");
        var left = actor(battle, false, "LeftTrainer");
        // A null sentinel would fail immediately if a spectator enumerated private party entries.
        right.setPokemon(Collections.singletonList(null));
        left.setPokemon(Collections.singletonList(null));
        assertSame(battle.getSide2(), BattleUiState.leftSide(battle, UUID.randomUUID()));
        BattleUiState.scanTeams(battle, UUID.randomUUID());
        assertEquals("LeftTrainer", BattleUiState.ownSideName());
        assertEquals("RightTrainer", BattleUiState.opponentSideName());
        assertTrue(BattleUiState.ownTeam().isEmpty());
        assertTrue(BattleUiState.opponentTeam().isEmpty());
        battle.setSpectating(false);
        assertSame(battle.getSide1(), BattleUiState.leftSide(battle, right.getUuid()));
        assertSame(battle.getSide2(), BattleUiState.leftSide(battle, left.getUuid()));
        assertNull(BattleUiState.leftSide(battle, UUID.randomUUID()));
    }

    @Test void publicTeamsStaySeparatedAndSnapshotsDoNotFreezeOrReallocateUnnecessarily() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        assertEquals(List.of(left), BattleUiState.ownTeam().stream().map(TeamMemberView::uuid).toList());
        assertEquals(List.of(right), BattleUiState.opponentTeam().stream().map(TeamMemberView::uuid).toList());
        assertFalse(BattleUiState.isOpponentMember(left));
        assertTrue(BattleUiState.isOpponentMember(right));
        assertTrue(BattleUiState.publicInformationOnly(left));
        assertSame(BattleUiState.ownTeam(), BattleUiState.ownTeam());
        assertSame(BattleUiState.opponentTeam(), BattleUiState.opponentTeam());
        var before = BattleUiState.ownTeam();
        team().put(left, team().get(left).withHealth(0.04F));
        assertNotSame(before, BattleUiState.ownTeam());
        assertEquals(0.04F, BattleUiState.ownTeam().getFirst().hpPercent());
        assertEquals(100F, BattleUiState.opponentTeam().getFirst().hpPercent());
    }

    @Test void authoritativeFaintStateUpdatesEitherSpectatorSideWithoutAHealthPacket() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);

        BattleUiState.markKnownFainted(left, false);
        BattleUiState.markKnownFainted(right, true);

        assertEquals(0.0F, BattleUiState.ownTeam().getFirst().hpPercent());
        assertTrue(BattleUiState.ownTeam().getFirst().fainted());
        assertEquals(0.0F, BattleUiState.opponentTeam().getFirst().hpPercent());
        assertTrue(BattleUiState.opponentTeam().getFirst().fainted());
    }

    @Test void repeatedFaintSignalsStayIdempotent() throws Exception {
        UUID left = member("LeftMon", true);

        BattleUiState.markKnownFainted(left, false);
        TeamMemberView first = team().get(left);
        BattleUiState.markKnownFainted(left, false);

        assertSame(first, team().get(left));
        assertTrue(first.fainted());
        @SuppressWarnings("unchecked") Set<UUID> confirmed = (Set<UUID>) get("CONFIRMED_FAINTS");
        assertEquals(Set.of(left), confirmed,
                "the confirmed KO must survive stale public HP scans during the faint animation");
    }

    @Test void sashAndBalloonWithoutItemArgumentsAreRememberedAsGoneOnBothSides() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("enditem.focussash", "LeftMon");
        event("enditem.airballoon", "RightMon");
        assertGone(left, "focus_sash", ItemEventKind.CONSUMED);
        assertGone(right, "air_balloon", ItemEventKind.DESTROYED);
        team().put(left, team().get(left).withActive(false));
        team().put(right, team().get(right).withActive(false));
        assertGone(left, "focus_sash", ItemEventKind.CONSUMED);
        assertGone(right, "air_balloon", ItemEventKind.DESTROYED);
        assertTrue(BattleUiState.currentItem(left, stack("focus_sash")).isEmpty());
        assertTrue(BattleUiState.currentItem(right, stack("air_balloon")).isEmpty());
    }

    @Test void knockOffTargetsTheVictimNotTheAttackerAndKeepsItsOldItem() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("item.airballoon", "LeftMon", item("air_balloon"));
        event("enditem.knockoff", "LeftMon", item("air_balloon"), Text.literal("RightMon"));
        assertGone(left, "air_balloon", ItemEventKind.KNOCKED_OFF);
        assertFalse(BattleUiState.opponentItemKnownAbsent(right));
        assertTrue(BattleUiState.opponentKnowledge(right).itemHistory().isEmpty());
    }

    @Test void focusBandIsNotConsumedAndUnrelatedDamageCannotGiveVictimAnItem() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("enditem.focusband", "LeftMon");
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
        assertEquals(stack("focus_band").getItem(), BattleUiState.currentItem(left, ItemStack.EMPTY).getItem());
        event("damage.rockyhelmet", "RightMon", item("rocky_helmet"));
        assertTrue(BattleUiState.currentItem(right, ItemStack.EMPTY).isEmpty());
        assertTrue(BattleUiState.opponentKnowledge(right).itemHistory().isEmpty());
    }

    @Test void eatenItemCanReturnAndTransfersUpdateBothOwners() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("enditem.eat", "LeftMon", item("sitrus_berry"));
        assertGone(left, "sitrus_berry", ItemEventKind.CONSUMED);
        event("item.recycle", "LeftMon", item("sitrus_berry"));
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
        assertEquals(stack("sitrus_berry").getItem(), BattleUiState.currentItem(left, ItemStack.EMPTY).getItem());
        event("item.thief", "RightMon", item("sitrus_berry"), Text.literal("LeftMon"));
        assertGone(left, "sitrus_berry", ItemEventKind.STOLEN);
        assertEquals(stack("sitrus_berry").getItem(), BattleUiState.currentItem(right, ItemStack.EMPTY).getItem());
        event("item.bestow", "LeftMon", item("sitrus_berry"), Text.literal("RightMon"));
        assertGone(right, "sitrus_berry", ItemEventKind.GIVEN);
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
    }

    @Test void participantOwnItemIsAlsoRemovedEvenIfThePartySnapshotIsStale() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnMon").withItem(stack("focus_sash"))));
        event("enditem.focussash", "OwnMon");
        assertGone(own, "focus_sash", ItemEventKind.CONSUMED);
        assertTrue(BattleUiState.ownTeam().getFirst().heldItem().isEmpty());
        assertTrue(BattleUiState.currentItem(own, stack("focus_sash")).isEmpty());
    }

    @Test void lumBerryCuresTheConsumerDespiteAStaleSnapshotInSpectatorAndParticipantModes() throws Exception {
        UUID spectated = member("BerryMon", true);
        team().put(spectated, team().get(spectated).withStatus("par"));
        PokemonBattleEffects.accept(spectated, "cobblemon.battle.start.confusion", new Object[0], 4);
        event("enditem.eat", "BerryMon", item("lum_berry"));
        assertEquals("", team().get(spectated).status());
        assertEquals("", BattleUiState.visibleStatus(spectated, "par"),
                "a stale Cobblemon snapshot must not restore the cured status");
        assertFalse(PokemonBattleEffects.active(spectated, "confusion", 4));

        begin(false);
        UUID own = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnBerryMon").withStatus("brn")));
        event("enditem.eat", "OwnBerryMon", item("lum_berry"));
        assertEquals("", BattleUiState.ownTeam().getFirst().status());
        assertEquals("", BattleUiState.visibleStatus(own, "brn"));
    }

    @Test void lumBerryKeyWithoutAnItemArgumentStillCuresItsConsumer() throws Exception {
        UUID pokemon = member("KeyBerryMon", true);
        team().put(pokemon, team().get(pokemon).withStatus("tox"));
        event("enditem.lumberry", "KeyBerryMon");
        assertEquals("", team().get(pokemon).status());
        assertEquals("", BattleUiState.visibleStatus(pokemon, "tox"));
    }

    @Test void bothSpectatedSidesRememberMovesAndPressureUsesTheOpposingCamp() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.generic", new Object[]{
                Text.literal("RightMon"), Text.translatable("cobblemon.ability.pressure")});
        var move = BattleUiState.class.getDeclaredMethod("rememberRevealedMove", String.class, Object[].class);
        move.setAccessible(true);
        move.invoke(null, "cobblemon.battle.used_move", new Object[]{Text.literal("LeftMon"), Text.translatable("cobblemon.move.surf")});
        move.invoke(null, "cobblemon.battle.used_move", new Object[]{Text.literal("RightMon"), Text.translatable("cobblemon.move.surf")});
        MoveView leftMove = team().get(left).knownMoves().getFirst();
        MoveView rightMove = team().get(right).knownMoves().getFirst();
        assertEquals(2, leftMove.ppUsed());
        assertEquals(13, leftMove.minCurrentPp());
        assertEquals(22, leftMove.currentPp());
        assertEquals(1, rightMove.ppUsed());
        assertEquals(14, rightMove.minCurrentPp());
        assertEquals(23, rightMove.currentPp());
        assertEquals("pressure", BattleUiState.currentAbility(right, null).id());
    }

    @Test void spiteSubtractsItsAnnouncedAmountFromBothOpponentPpBounds() throws Exception {
        UUID right = member("RightMon", false);
        var useMove = BattleUiState.class.getDeclaredMethod("rememberRevealedMove", String.class, Object[].class);
        useMove.setAccessible(true);
        useMove.invoke(null, "cobblemon.battle.used_move",
                new Object[]{Text.literal("RightMon"), Text.translatable("cobblemon.move.surf")});

        var changePp = BattleUiState.class.getDeclaredMethod("rememberPpChange", String.class, Object[].class);
        changePp.setAccessible(true);
        changePp.invoke(null, "cobblemon.battle.activate.spite",
                new Object[]{Text.literal("RightMon"), Text.translatable("cobblemon.move.surf"), 4});

        MoveView surf = team().get(right).knownMoves().getFirst();
        assertEquals(10, surf.minCurrentPp());
        assertEquals(19, surf.currentPp());
        assertEquals(15, surf.minMaxPp());
        assertEquals(24, surf.maxPp());
        assertEquals(5, surf.ppUsed());
    }

    @Test void spectatedLeftAndRightKeepDistinctFieldSidesForScreensAndHazards() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, BattleUiState.effectSide(left));
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, BattleUiState.effectSide(right));
    }

    @Test void publicToxicDebrisAnnouncementsCreateTheRightLayersOnBothSpectatorSides() throws Exception {
        member("LeftGlimmora", true);
        member("RightGlimmoraA", false);
        member("RightGlimmoraB", false);

        BattleFieldEffects.beginMessageBatch(true);
        publicAbility("RightGlimmoraA", "Toxic Debris");
        var leftHazard = BattleUiState.effects().getFirst();
        assertEquals("ally.toxicspikes", leftHazard.id());
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, leftHazard.side());
        assertEquals(1, leftHazard.layers());

        // A spread physical attack can trigger two holders in one public batch.
        publicAbility("RightGlimmoraB", "Toxic Debris");
        assertEquals(2, BattleUiState.effects().getFirst().layers());

        publicAbility("LeftGlimmora", "Toxic Debris");
        BattleFieldEffects.endMessageBatch();
        var hazards = BattleUiState.effects();
        assertEquals(2, hazards.size());
        assertEquals("opponent.toxicspikes", hazards.get(1).id());
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, hazards.get(1).side());
    }

    @Test void magicBouncePlacesEveryEntryHazardOnTheOppositeSpectatorSideWithoutEchoDuplicates() throws Exception {
        member("LeftBouncer", true);
        member("RightBouncer", false);
        for (String hazard : List.of("spikes", "toxicspikes", "stealthrock", "stickyweb")) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch();
            try {
                BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.magicbounce", new Object[]{
                        Text.literal("LeftBouncer"), Text.translatable("cobblemon.move." + hazard)});
                BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent." + hazard, 0);
            } finally {
                BattleFieldEffects.endMessageBatch();
            }
            var reflected = BattleUiState.effects().getFirst();
            assertEquals("opponent." + hazard, reflected.id());
            assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, reflected.side());
            assertEquals(1, reflected.layers(), hazard);
        }

        BattleFieldEffects.reset();
        BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.magicbounce", new Object[]{
                Text.literal("RightBouncer"), Text.translatable("cobblemon.move.toxicspikes")});
        assertEquals("ally.toxicspikes", BattleUiState.effects().getFirst().id());
    }

    @Test void targetedHazardMovesResolveBothSpectatorSidesAndPairTheirExplicitEcho() throws Exception {
        member("LeftSetter", true);
        member("RightTarget", false);
        for (String hazard : List.of("spikes", "toxicspikes", "stealthrock", "stickyweb")) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch();
            try {
                slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                        Text.translatable("cobblemon.move." + hazard), Text.literal("RightTarget"));
                BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent." + hazard, 0);
            } finally {
                BattleFieldEffects.endMessageBatch();
            }
            var placed = BattleUiState.effects();
            assertEquals(1, placed.size(), hazard);
            assertEquals("opponent." + hazard, placed.getFirst().id(), hazard);
            assertEquals(1, placed.getFirst().layers(), hazard);
        }

        BattleFieldEffects.reset();
        BattleFieldEffects.beginMessageBatch();
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("RightTarget"),
                    Text.translatable("cobblemon.move.stealthrock"), Text.literal("LeftSetter"));
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals("ally.stealthrock", BattleUiState.effects().getFirst().id());
    }

    @Test void targetedHazardsAlsoResolveBothParticipantSidesInMultiBattles() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnLead")));
        team().put(opponent, view(opponent, "OpponentLead"));
        BattleFieldEffects.beginMessageBatch();
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("OwnLead"),
                    Text.translatable("cobblemon.move.stealthrock"), Text.literal("OpponentLead"));
            slotEvent("cobblemon.battle.used_move_on", Text.literal("OpponentLead"),
                    Text.translatable("cobblemon.move.stickyweb"), Text.literal("OwnLead"));
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertTrue(BattleFieldEffects.activeOnSide("stealthrock", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("stickyweb", BattleFieldEffects.EffectSide.PLAYER_FIELD));
    }

    @Test void moveInferredHazardsFollowMagicBounceAndFailedMovesLeaveNoFalseHazard() throws Exception {
        member("LeftSetter", true);
        member("RightBouncer", false);
        BattleFieldEffects.beginMessageBatch();
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.spikes"), Text.literal("RightBouncer"));
            BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.magicbounce", new Object[]{
                    Text.literal("RightBouncer"), Text.translatable("cobblemon.move.spikes")});
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.spikes", 0);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        var reflected = BattleUiState.effects();
        assertEquals(1, reflected.size());
        assertEquals("ally.spikes", reflected.getFirst().id());
        assertEquals(1, reflected.getFirst().layers());

        BattleFieldEffects.reset();
        BattleFieldEffects.beginMessageBatch();
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.stealthrock"), Text.literal("RightBouncer"));
            slotEvent("cobblemon.battle.fail");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertTrue(BattleUiState.effects().isEmpty());
    }

    @Test void magicCoatPublicEffectReflectsHazardsAndDefogWithoutPretendingItIsMagicBounce() throws Exception {
        member("LeftSetter", true);
        UUID right = member("RightCoater", false);
        set("turn", 4);
        PokemonBattleEffects.accept(right, "cobblemon.battle.singleturn.magiccoat",
                new Object[]{Text.literal("RightCoater")}, 4);

        publicMove("LeftSetter", "spikes", "RightCoater");
        assertTrue(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertFalse(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD));

        seedSideEffects("LeftSetter", List.of("reflect"));
        seedSideEffects("RightCoater", List.of("reflect"));
        publicMove("LeftSetter", "defog", "RightCoater");
        assertFalse(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        assertEquals("magiccoat", PokemonBattleEffects.snapshot(right, 4).getFirst().id());
    }

    @Test void damagingHazardMovesUseTheirRealHazardType() throws Exception {
        member("LeftSetter", true);
        member("RightTarget", false);
        BattleFieldEffects.beginMessageBatch();
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.ceaselessedge"), Text.literal("RightTarget"));
            slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.stoneaxe"), Text.literal("RightTarget"));
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertTrue(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("stealthrock", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
    }

    @Test void ceaselessEdgeAndItsDelayedExplicitEchoCountOnceWhileSpectating() throws Exception {
        member("LeftSetter", true);
        member("RightTarget", false);
        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.ceaselessedge"), Text.literal("RightTarget"));
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        BattleFieldEffects.beginMessageBatch(true);
        BattleFieldEffects.endMessageBatch();
        BattleFieldEffects.beginMessageBatch(true);
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 0);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(1, BattleUiState.effects().getFirst().layers());
    }

    @Test void ceaselessEdgeAndItsDelayedExplicitEchoCountOnceForAParticipant() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID(), opponent = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnSetter")));
        team().put(opponent, view(opponent, "OpponentTarget"));
        BattleFieldEffects.beginMessageBatch(false);
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal("OwnSetter"),
                    Text.translatable("cobblemon.move.ceaselessedge"), Text.literal("OpponentTarget"));
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        BattleFieldEffects.beginMessageBatch(false);
        BattleFieldEffects.endMessageBatch();
        BattleFieldEffects.beginMessageBatch(false);
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 0);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(1, BattleUiState.effects().getFirst().layers());
    }

    @Test void illusionRestoresTheLeftDecoyAndTransfersConsumedItemsOnlyToItsRealIdentity() throws Exception {
        UUID fake = member("Decoy", true), real = UUID.randomUUID();
        var before = BattleUiState.opponentMemory(fake);
        event("enditem.focussash", "Decoy");
        BattleUiState.reconcileIllusion(fake, real, before);
        assertFalse(BattleUiState.opponentItemKnownAbsent(fake));
        assertTrue(BattleUiState.opponentKnowledge(fake).itemHistory().isEmpty());
        team().put(real, view(real, "Zoroark-Hisui"));
        assertGone(real, "focus_sash", ItemEventKind.CONSUMED);
        assertFalse(BattleUiState.isOpponentMember(real));
        assertEquals(2, BattleUiState.ownTeam().size());
        assertTrue(BattleUiState.opponentTeam().isEmpty());
    }

    @Test void newBattleClearsBothSidesAndTheirItemKnowledge() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("enditem.focussash", "LeftMon");
        event("enditem.airballoon", "RightMon");
        begin(true);
        assertTrue(BattleUiState.ownTeam().isEmpty());
        assertTrue(BattleUiState.opponentTeam().isEmpty());
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
        assertFalse(BattleUiState.opponentItemKnownAbsent(right));
    }

    @Test void mirroredSpeciesUseTrainerIdentityAndAmbiguousMessagesDoNotInventAnOwner() throws Exception {
        UUID left = member("MirrorMon", true), right = member("MirrorMon", false);
        @SuppressWarnings("unchecked") Set<String> leftNames = (Set<String>) get("OWN_ACTOR_NAMES");
        @SuppressWarnings("unchecked") Set<String> rightNames = (Set<String>) get("OPPONENT_ACTOR_NAMES");
        leftNames.add("LeftTrainer"); rightNames.add("RightTrainer");
        event("enditem.focussash", "MirrorMon");
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
        assertFalse(BattleUiState.opponentItemKnownAbsent(right));
        event("enditem.focussash", "LeftTrainer's MirrorMon");
        event("enditem.airballoon", "RightTrainer's MirrorMon");
        assertGone(left, "focus_sash", ItemEventKind.CONSUMED);
        assertGone(right, "air_balloon", ItemEventKind.DESTROYED);
        var side = BattleUiState.class.getDeclaredMethod("logSide", Object[].class, String.class, BattleLogEntryType.class);
        side.setAccessible(true);
        assertEquals(BattleLogSide.PLAYER, side.invoke(null, new Object[]{Text.literal("LeftTrainer's MirrorMon")}, "", BattleLogEntryType.ITEM));
        assertEquals(BattleLogSide.OPPONENT, side.invoke(null, new Object[]{Text.literal("RightTrainer's MirrorMon")}, "", BattleLogEntryType.ITEM));
    }

    @Test void trickUpdatesBothCurrentItemsWithoutDiscardingEarlierRevelations() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("item.generic", "LeftMon", item("focus_sash"));
        event("item.generic", "RightMon", item("leftovers"));
        event("item.trick", "LeftMon", item("leftovers"));
        event("item.trick", "RightMon", item("focus_sash"));
        assertEquals(stack("leftovers").getItem(), BattleUiState.currentItem(left, ItemStack.EMPTY).getItem());
        assertEquals(stack("focus_sash").getItem(), BattleUiState.currentItem(right, ItemStack.EMPTY).getItem());
        assertEquals(2, BattleUiState.opponentKnowledge(left).itemHistory().size());
        assertEquals(ItemEventKind.SWAPPED, BattleUiState.opponentKnowledge(right).itemHistory().getLast().kind());
    }

    @Test void leftoversEffectKeyRevealsTheItemEvenWhenItsArgumentIsLiteral() throws Exception {
        for (boolean spectate : new boolean[]{false, true}) {
            begin(spectate);
            UUID owner = member("Terracruel", false);
            BattleUiState.rememberItemState("cobblemon.battle.heal.leftovers", "",
                    new Object[]{Text.literal("Terracruel"), Text.literal("Leftovers")});
            assertEquals(stack("leftovers").getItem(),
                    BattleUiState.currentItem(owner, ItemStack.EMPTY).getItem());
            assertEquals(ItemEventKind.REVEALED,
                    BattleUiState.opponentKnowledge(owner).itemHistory().getLast().kind());
        }
    }

    @Test void trickWithAnEmptySlotClearsTheOldOwnerAndTheSecondTrickReversesBothSides() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        event("item.generic", "LeftMon", item("focus_sash"));

        trick("LeftMon", "RightMon");
        event("item.trick", "RightMon", item("focus_sash"));
        assertTrue(BattleUiState.currentItem(left, stack("focus_sash")).isEmpty());
        assertTrue(BattleUiState.opponentItemKnownAbsent(left));
        assertEquals(stack("focus_sash").getItem(), BattleUiState.currentItem(right, ItemStack.EMPTY).getItem());
        assertFalse(BattleUiState.opponentItemKnownAbsent(right));

        trick("RightMon", "LeftMon");
        event("item.trick", "LeftMon", item("focus_sash"));
        assertEquals(stack("focus_sash").getItem(), BattleUiState.currentItem(left, ItemStack.EMPTY).getItem());
        assertFalse(BattleUiState.opponentItemKnownAbsent(left));
        assertTrue(BattleUiState.currentItem(right, stack("focus_sash")).isEmpty());
        assertTrue(BattleUiState.opponentItemKnownAbsent(right));
        assertEquals(ItemEventKind.SWAPPED, BattleUiState.opponentKnowledge(right).itemHistory().getLast().kind());
    }

    @Test void participantTrickAlsoClearsThePlayersStalePartyItemWhenTheOpponentWasEmpty() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnMon").withItem(stack("focus_sash"))));
        UUID opponent = member("RightMon", false);

        trick("OwnMon", "RightMon");
        event("item.trick", "RightMon", item("focus_sash"));

        assertTrue(BattleUiState.currentItem(own, stack("focus_sash")).isEmpty());
        assertTrue(BattleUiState.ownTeam().getFirst().heldItem().isEmpty());
        assertTrue(BattleUiState.opponentItemKnownAbsent(own));
        assertEquals(stack("focus_sash").getItem(), BattleUiState.currentItem(opponent, ItemStack.EMPTY).getItem());
    }

    @Test void genericSpectatorSideConditionsUseTheAnnouncedTrainerSideWithoutDuplicates() throws Exception {
        ClientBattle battle = begin(true);
        actor(battle, true, "RightTrainer");
        actor(battle, false, "LeftTrainer");
        BattleUiState.scanTeams(battle, UUID.randomUUID());

        var left = BattleUiState.effectSideFromArgument(Text.literal("LeftTrainer"));
        var right = BattleUiState.effectSideFromArgument(Text.literal("RightTrainer"));
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, left);
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, right);
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD,
                BattleUiState.effectSideFromArgument("LeftTrainer"));
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD,
                BattleUiState.effectSideFromArgument("RightTrainer"));
        for (String effect : List.of("tailwind", "reflect", "lightscreen", "auroraveil",
                "safeguard", "mist", "luckychant")) {
            BattleFieldEffects.accept("cobblemon.battle.sidestart." + effect, 3, right);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent." + effect, 3);
            assertTrue(BattleFieldEffects.activeOnSide(effect,
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD), effect);
            assertFalse(BattleFieldEffects.activeOnSide(effect,
                    BattleFieldEffects.EffectSide.PLAYER_FIELD), effect);
            assertEquals(1, BattleFieldEffects.snapshot(3).size(), effect + " must not be duplicated");
            BattleFieldEffects.accept("cobblemon.battle.sideend." + effect, 4, right);
            assertFalse(BattleFieldEffects.active(effect), effect);

            BattleFieldEffects.accept("cobblemon.battle.sidestart." + effect, 5, left);
            assertTrue(BattleFieldEffects.activeOnSide(effect,
                    BattleFieldEffects.EffectSide.PLAYER_FIELD), effect);
            BattleFieldEffects.accept("cobblemon.battle.sideend." + effect, 6, left);
            assertFalse(BattleFieldEffects.active(effect), effect);
        }
    }

    @Test void sideConditionMovesActivateWithoutASeparateSideStartWhileSpectating() throws Exception {
        for (String effect : List.of("tailwind", "reflect", "lightscreen", "auroraveil",
                "safeguard", "mist", "luckychant")) {
            begin(true);
            member("LeftSetter", true);
            member("RightSetter", false);
            BattleFieldEffects.beginMessageBatch(true);
            try {
                slotEvent("cobblemon.battle.used_move", Text.literal("RightSetter"),
                        Text.translatable("cobblemon.move." + effect));
            } finally {
                BattleFieldEffects.endMessageBatch();
            }

            assertTrue(BattleFieldEffects.activeOnSide(effect,
                            BattleFieldEffects.EffectSide.OPPONENT_FIELD),
                    effect + " must be inferred from its public move-use event");
            assertEquals(1, BattleFieldEffects.snapshot(3).size(), effect);
        }
    }

    @Test void participantSideConditionsStillUseTheOfficialSideStartWithoutDuplicates() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnSetter")));
        set("turn", 3);
        BattleFieldEffects.beginMessageBatch(false);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("OwnSetter"),
                    Text.translatable("cobblemon.move.tailwind"));
            assertFalse(BattleFieldEffects.active("tailwind"));
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.tailwind", 3);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertEquals(1, BattleFieldEffects.snapshot(3).size());
    }

    @Test void failedSideConditionMoveRollsBackOnlyItsOwnSpectatorInference() throws Exception {
        begin(true);
        member("LeftSetter", true);
        set("turn", 4);

        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.tailwind"));
            assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                    BattleFieldEffects.EffectSide.PLAYER_FIELD));
            slotEvent("cobblemon.battle.fail");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertFalse(BattleFieldEffects.active("tailwind"));
    }

    @Test void failedRepeatedSideConditionCannotRemoveTheAlreadyActiveEffect() throws Exception {
        begin(true);
        member("LeftFirst", true);
        member("LeftSecond", true);
        set("turn", 6);

        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftFirst"),
                    Text.translatable("cobblemon.move.tailwind"));
            usedMove("LeftFirst", "tailwind", "LeftFirst");
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSecond"),
                    Text.translatable("cobblemon.move.tailwind"));
            usedMove("LeftSecond", "tailwind", "LeftSecond");
            slotEvent("cobblemon.battle.fail");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertEquals(1, BattleFieldEffects.snapshot(6).size());
    }

    @Test void spectatorScreenRecastAfterItsStandardDurationStartsANewCounter() throws Exception {
        begin(true);
        member("LeftSetter", true);
        set("turn", 3);
        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.reflect"));
            usedMove("LeftSetter", "reflect", "LeftSetter");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        set("turn", 8);
        assertEquals("0–3", BattleFieldEffects.snapshot(8).getFirst().counter().getString());
        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.reflect"));
            usedMove("LeftSetter", "reflect", "LeftSetter");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals("5–8", BattleFieldEffects.snapshot(8).getFirst().counter().getString());
        assertTrue(BattleFieldEffects.activeOnSide("reflect",
                BattleFieldEffects.EffectSide.PLAYER_FIELD, 8));
    }

    @Test void failedSpectatorScreenRecastRestoresThePreviousUncertainState() throws Exception {
        begin(true);
        member("LeftSetter", true);
        set("turn", 3);
        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.reflect"));
            usedMove("LeftSetter", "reflect", "LeftSetter");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        set("turn", 8);
        BattleFieldEffects.beginMessageBatch(true);
        try {
            slotEvent("cobblemon.battle.used_move", Text.literal("LeftSetter"),
                    Text.translatable("cobblemon.move.reflect"));
            usedMove("LeftSetter", "reflect", "LeftSetter");
            slotEvent("cobblemon.battle.fail");
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals("0–3", BattleFieldEffects.snapshot(8).getFirst().counter().getString());
        assertFalse(BattleFieldEffects.activeOnSide("reflect",
                BattleFieldEffects.EffectSide.PLAYER_FIELD, 8));
    }

    @Test void spectatorSpinMovesRemoveOnlyTheirOwnHazardsFromPublicMoveUse() throws Exception {
        for (String move : List.of("rapidspin", "mortalspin")) {
            begin(true);
            member("LeftSpinner", true);
            member("RightSetter", false);
            seedHazards("RightSetter", "LeftSpinner");
            seedHazards("LeftSpinner", "RightSetter");

            publicMove("LeftSpinner", move, "RightSetter");
            if (move.equals("mortalspin")) {
                slotEvent("cobblemon.battle.damage_dealt", Text.literal("RightSetter"), 20);
            }

            for (String hazard : List.of("spikes", "toxicspikes", "stealthrock", "stickyweb")) {
                assertFalse(BattleFieldEffects.activeOnSide(hazard,
                        BattleFieldEffects.EffectSide.PLAYER_FIELD), move + ": " + hazard);
                assertTrue(BattleFieldEffects.activeOnSide(hazard,
                        BattleFieldEffects.EffectSide.OPPONENT_FIELD), move + ": opposing " + hazard);
            }
        }
    }

    @Test void mortalSpinWaitsForAnySuccessfulTargetInDoubles() throws Exception {
        begin(true);
        member("LeftSpinner", true);
        member("RightImmune", false);
        member("RightHit", false);
        seedHazards("RightImmune", "LeftSpinner");

        publicMove("LeftSpinner", "mortalspin", "RightImmune");
        assertTrue(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        slotEvent("cobblemon.battle.immune", Text.literal("RightImmune"));
        assertTrue(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD),
                "one immune target must not decide the spread move");
        slotEvent("cobblemon.battle.damage_dealt", Text.literal("RightHit"), 20);
        assertFalse(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD),
                "one successful target is enough for Mortal Spin to clean its side");
    }

    @Test void spectatorDefogRemovesBothHazardFieldsAndOnlyTheTargetsBarriers() throws Exception {
        begin(true);
        member("LeftDefogger", true);
        member("RightTarget", false);
        seedHazards("LeftDefogger", "RightTarget");
        seedHazards("RightTarget", "LeftDefogger");
        seedSideEffects("LeftDefogger", List.of("reflect", "lightscreen", "auroraveil", "safeguard", "mist"));
        seedSideEffects("RightTarget", List.of("reflect", "lightscreen", "auroraveil", "safeguard", "mist"));

        publicMove("LeftDefogger", "defog", "RightTarget");

        for (String hazard : List.of("spikes", "toxicspikes", "stealthrock", "stickyweb")) {
            assertFalse(BattleFieldEffects.active(hazard), hazard);
        }
        for (String effect : List.of("reflect", "lightscreen", "auroraveil", "safeguard", "mist")) {
            assertTrue(BattleFieldEffects.activeOnSide(effect,
                    BattleFieldEffects.EffectSide.PLAYER_FIELD), "Defog must preserve the user's " + effect);
            assertFalse(BattleFieldEffects.activeOnSide(effect,
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD), "Defog must clear the target's " + effect);
        }
    }

    @Test void spectatorTidyUpAndGmaxWindRageFollowTheirDistinctSideRules() throws Exception {
        begin(true);
        member("LeftCleaner", true);
        member("RightTarget", false);
        seedHazards("LeftCleaner", "RightTarget");
        seedHazards("RightTarget", "LeftCleaner");
        seedSideEffects("LeftCleaner", List.of("reflect"));
        seedSideEffects("RightTarget", List.of("reflect"));

        publicMove("LeftCleaner", "tidyup", "LeftCleaner");
        assertFalse(BattleFieldEffects.active("spikes"));
        assertFalse(BattleFieldEffects.active("toxicspikes"));
        assertFalse(BattleFieldEffects.active("stealthrock"));
        assertFalse(BattleFieldEffects.active("stickyweb"));
        assertTrue(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.OPPONENT_FIELD));

        seedHazards("LeftCleaner", "RightTarget");
        seedHazards("RightTarget", "LeftCleaner");
        publicMove("LeftCleaner", "gmaxwindrage", "RightTarget");
        slotEvent("cobblemon.battle.damage_dealt", Text.literal("RightTarget"), 20);
        assertFalse(BattleFieldEffects.active("spikes"));
        assertFalse(BattleFieldEffects.active("toxicspikes"));
        assertFalse(BattleFieldEffects.active("stealthrock"));
        assertFalse(BattleFieldEffects.active("stickyweb"));
        assertTrue(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertFalse(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
    }

    @Test void spectatorBarrierBreakingAttacksAndScreenCleanerUsePublicAnnouncements() throws Exception {
        for (String move : List.of("brickbreak", "psychicfangs", "ragingbull")) {
            begin(true);
            member("LeftBreaker", true);
            member("RightTarget", false);
            seedSideEffects("RightTarget", List.of("reflect", "lightscreen", "auroraveil", "safeguard"));
            publicMove("LeftBreaker", move, "RightTarget");
            for (String screen : List.of("reflect", "lightscreen", "auroraveil")) {
                assertFalse(BattleFieldEffects.activeOnSide(screen,
                        BattleFieldEffects.EffectSide.OPPONENT_FIELD), move + ": " + screen);
            }
            assertTrue(BattleFieldEffects.activeOnSide("safeguard",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD), move + " must not clear Safeguard");
        }

        begin(true);
        member("LeftCleaner", true);
        member("RightTarget", false);
        seedSideEffects("LeftCleaner", List.of("reflect", "lightscreen", "auroraveil"));
        seedSideEffects("RightTarget", List.of("reflect", "lightscreen", "auroraveil"));
        BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.generic", new Object[]{
                Text.literal("LeftCleaner"), Text.translatable("cobblemon.ability.screencleaner")});
        assertFalse(BattleFieldEffects.active("reflect"));
        assertFalse(BattleFieldEffects.active("lightscreen"));
        assertFalse(BattleFieldEffects.active("auroraveil"));
    }

    @Test void blockedMissedOrImmuneRemovalMovesRestoreExactlyWhatTheyRemoved() throws Exception {
        for (String move : List.of("rapidspin", "defog", "brickbreak")) {
            for (String blocked : List.of("cobblemon.battle.fail", "cobblemon.battle.missed",
                    "cobblemon.battle.immune", "cobblemon.battle.activate.protect")) {
                begin(true);
                member("LeftCleaner", true);
                member("RightTarget", false);
                seedHazards("RightTarget", "LeftCleaner");
                seedSideEffects("RightTarget", List.of("reflect"));
                publicMove("LeftCleaner", move, "RightTarget");
                slotEvent(blocked, Text.literal("RightTarget"));
                assertTrue(BattleFieldEffects.activeOnSide("spikes",
                        BattleFieldEffects.EffectSide.PLAYER_FIELD), move + " / " + blocked);
                assertTrue(BattleFieldEffects.activeOnSide("reflect",
                        BattleFieldEffects.EffectSide.OPPONENT_FIELD), move + " / " + blocked);
            }
        }
    }

    @Test void participantRemovalMovesWaitForAuthoritativeSideEndMessages() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID(), opponent = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnDefogger")));
        team().put(opponent, view(opponent, "OpponentTarget"));
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.spikes", 3);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.reflect", 3);

        publicMove("OwnDefogger", "defog", "OpponentTarget");

        assertTrue(BattleFieldEffects.activeOnSide("spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("reflect", BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        BattleFieldEffects.accept("cobblemon.battle.sideend.ally.spikes", 3);
        BattleFieldEffects.accept("cobblemon.battle.sideend.opponent.reflect", 3);
        assertFalse(BattleFieldEffects.active("spikes"));
        assertFalse(BattleFieldEffects.active("reflect"));
    }

    @Test void nativeSlotSwitchAbsorbsSpectatorToxicSpikesWithoutSideEnd() throws Exception {
        ClientBattle battle = begin(true);
        CobblemonClient.INSTANCE.setBattle(battle);
        ClientBattleActor leftActor = actor(battle, false, "LeftTrainer");
        ActiveClientBattlePokemon leftSlot = new ActiveClientBattlePokemon(leftActor, null);
        leftActor.getActivePokemon().add(leftSlot);
        UUID poison = member("LeftPoison", true);
        member("RightSetter", false);
        team().put(poison, team().get(poison).withTypes(List.of(new TypeView("poison", Text.literal("Poison")))));
        publicMove("RightSetter", "toxicspikes", "LeftPoison");
        assertTrue(BattleFieldEffects.activeOnSide("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD));

        ClientBattlePokemon incoming = new ClientBattlePokemon(poison, Text.literal("LeftPoison"),
                new PokemonProperties(), Set.of(), 1F, 1F, false, null, new HashMap<>());
        incoming.setActor(leftActor);
        leftSlot.setBattlePokemon(incoming);

        assertFalse(BattleFieldEffects.active("toxicspikes"));
    }

    @Test void airbornePoisonSwitchInDoesNotAbsorbSpectatorToxicSpikes() throws Exception {
        begin(true);
        UUID poison = member("LeftFlyingPoison", true);
        member("RightSetter", false);
        team().put(poison, team().get(poison).withTypes(List.of(
                new TypeView("poison", Text.literal("Poison")),
                new TypeView("flying", Text.literal("Flying")))));
        publicMove("RightSetter", "toxicspikes", "LeftFlyingPoison");

        BattleUiState.absorbToxicSpikesOnEntry(poison, BattleFieldEffects.EffectSide.PLAYER_FIELD);

        assertTrue(BattleFieldEffects.activeOnSide("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD));
    }

    @Test void terrainClearingMovesUseTheBattleWideFieldEndSeenBySpectators() throws Exception {
        begin(true);
        member("LeftCleaner", true);
        member("RightTarget", false);
        for (String move : List.of("icespinner", "steelroller", "defog")) {
            BattleFieldEffects.accept("cobblemon.battle.fieldstart.electricterrain", 3);
            publicMove("LeftCleaner", move, "RightTarget");
            assertTrue(BattleFieldEffects.active("electricterrain"), move + " waits for the successful FieldEnd");
            BattleFieldEffects.accept("cobblemon.battle.fieldend.electricterrain", 3);
            assertFalse(BattleFieldEffects.active("electricterrain"), move);
        }
    }

    @Test void semanticFutureSightMessagesTrackAndEndTheParticipantTargetSide() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID(), opponent = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnCaster")));
        team().put(opponent, view(opponent, "OpponentTarget"));

        slotEvent("cobblemon.battle.start.futuresight", Text.literal("OwnCaster"));
        assertTrue(BattleFieldEffects.activeOnSide("futuresight",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        slotEvent("cobblemon.battle.end.futuresight", Text.literal("OpponentTarget"));
        assertFalse(BattleFieldEffects.active("futuresight"));
    }

    @Test void futureSightStartEchoCannotLeaveASecondEffectAfterTheHit() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID(), opponent = UUID.randomUUID();
        set("ownTeam", List.of(view(own, "OwnCaster")));
        team().put(opponent, view(opponent, "OpponentTarget"));

        BattleFieldEffects.startSlotEffect("futuresight",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, "p2a", 3);
        slotEvent("cobblemon.battle.start.futuresight", Text.literal("OwnCaster"));
        assertEquals(1, BattleFieldEffects.snapshot(3).size());

        slotEvent("cobblemon.battle.end.futuresight", Text.literal("OpponentTarget"));
        assertFalse(BattleFieldEffects.active("futuresight"));
    }

    @Test void semanticFutureSightMessagesTrackAndEndEitherSpectatorSide() throws Exception {
        begin(true);
        member("LeftCaster", true);
        member("RightCaster", false);
        UUID left = member("LeftTarget", true);
        UUID right = member("RightTarget", false);

        slotEvent("cobblemon.battle.start.futuresight", Text.literal("LeftCaster"));
        slotEvent("cobblemon.battle.start.doomdesire", Text.literal("RightCaster"));
        assertTrue(BattleFieldEffects.activeOnSide("futuresight",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("doomdesire",
                BattleFieldEffects.EffectSide.PLAYER_FIELD));
        slotEvent("cobblemon.battle.end.futuresight", Text.literal("RightTarget"));
        slotEvent("cobblemon.battle.end.doomdesire", Text.literal("LeftTarget"));
        assertFalse(BattleFieldEffects.active("futuresight"));
        assertFalse(BattleFieldEffects.active("doomdesire"));
    }

    @Test void exactDefensiveStatsRemainPrivateToTheLocalParticipant() throws Exception {
        begin(false);
        UUID own = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        Map<UUID, BattleStatsView> stats = (Map<UUID, BattleStatsView>) get("OWN_BATTLE_STATS");
        stats.put(own, new BattleStatsView(361, 236, 251, 299, 216, 328));
        assertEquals(new BattleStatsView(361, 236, 251, 299, 216, 328), BattleUiState.ownBattleStats(own));

        begin(true);
        stats = (Map<UUID, BattleStatsView>) get("OWN_BATTLE_STATS");
        stats.put(own, new BattleStatsView(361, 236, 251, 299, 216, 328));
        assertSame(BattleStatsView.UNKNOWN, BattleUiState.ownBattleStats(own));
    }

    @Test void exactBoostsAndTargetlessSwapMessagesUseThePublicMoveTarget() throws Exception {
        UUID left = member("LeftMon", true), right = member("RightMon", false);
        var tracker = (BattleStatStageTracker) get("OBSERVED_STAT_STAGES");
        tracker.change(left, "atk", -2);
        statEvent("cobblemon.battle.setboost.bellydrum", Text.literal("LeftMon"));
        assertEquals(6, tracker.stages(left).get("atk"));

        tracker.change(left, "def", 2);
        tracker.change(right, "def", -1);
        usedMove("LeftMon", "guardswap", "RightMon");
        statEvent("cobblemon.battle.swapboost.guardswap", Text.literal("LeftMon"));
        assertEquals(-1, tracker.stages(left).get("def"));
        assertEquals(2, tracker.stages(right).get("def"));
    }

    @Test void directPressureCostsExtraOnlyAgainstAnOpponentInParticipantAndSpectatorBattles() throws Exception {
        var ppCost = BattleUiState.class.getDeclaredMethod("ppCost", Object[].class, MoveTemplate.class);
        ppCost.setAccessible(true);
        MoveTemplate trick = Moves.getByName("trick");
        for (boolean spectate : new boolean[]{false, true}) {
            begin(spectate);
            UUID user;
            UUID ally;
            UUID foe;
            if (spectate) {
                user = member("LeftUser", true);
                ally = member("LeftPressure", true);
                foe = member("RightPressure", false);
            } else {
                user = UUID.randomUUID(); ally = UUID.randomUUID(); foe = UUID.randomUUID();
                set("ownTeam", List.of(view(user, "OwnUser"), view(ally, "OwnPressure")));
                team().put(foe, view(foe, "RightPressure"));
            }
            String userName = spectate ? "LeftUser" : "OwnUser";
            String allyName = spectate ? "LeftPressure" : "OwnPressure";
            BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.generic", new Object[]{
                    Text.literal(allyName), Text.translatable("cobblemon.ability.pressure")});
            BattleUiState.rememberRevealedAbility("cobblemon.battle.ability.generic", new Object[]{
                    Text.literal("RightPressure"), Text.translatable("cobblemon.ability.pressure")});

            assertEquals(1, ppCost.invoke(null, new Object[]{
                    Text.literal(userName), Text.translatable("cobblemon.move.trick"), Text.literal(allyName)}, trick));
            assertEquals(2, ppCost.invoke(null, new Object[]{
                    Text.literal(userName), Text.translatable("cobblemon.move.trick"), Text.literal("RightPressure")}, trick));
            assertTrue(BattleUiState.opposingPokemon(user, foe));
            assertFalse(BattleUiState.opposingPokemon(user, ally));
        }
    }

    @Test void publicGenericItemMessagesAndRockyHelmetRevealTheActualHolder() throws Exception {
        for (boolean spectate : new boolean[]{false, true}) {
            begin(spectate);
            UUID holder = member("HelmetHolder", false);
            UUID victim;
            if (spectate) {
                victim = member("ContactVictim", true);
            } else {
                victim = UUID.randomUUID();
                set("ownTeam", List.of(view(victim, "ContactVictim")));
            }

            BattleUiState.rememberItemState("cobblemon.battle.damage.rockyhelmet", "", new Object[]{
                    Text.literal("ContactVictim"), Text.literal("HelmetHolder")});
            assertEquals(stack("rocky_helmet").getItem(),
                    BattleUiState.currentItem(holder, ItemStack.EMPTY).getItem());
            assertTrue(BattleUiState.currentItem(victim, ItemStack.EMPTY).isEmpty());

            BattleUiState.rememberItemState("cobblemon.battle.heal.item", "", new Object[]{
                    Text.literal("ContactVictim"), item("sitrus_berry")});
            assertEquals(stack("sitrus_berry").getItem(),
                    BattleUiState.currentItem(victim, ItemStack.EMPTY).getItem());
            BattleUiState.rememberItemState("cobblemon.battle.damage.item", "", new Object[]{
                    Text.literal("HelmetHolder"), item("air_balloon")});
            assertEquals(stack("air_balloon").getItem(),
                    BattleUiState.currentItem(holder, ItemStack.EMPTY).getItem());
        }
    }

    @Test void allPublicProtectionSignalsCancelMoveInferredHazardsInBothModes() throws Exception {
        for (boolean spectate : new boolean[]{false, true}) {
            for (String blocked : List.of("cobblemon.battle.activate.protect",
                    "cobblemon.battle.activate.wideguard", "cobblemon.battle.activate.quickguard",
                    "cobblemon.battle.activate.craftyshield", "cobblemon.battle.activate.matblock",
                    "cobblemon.battle.block.aromaveil", "cobblemon.battle.immune")) {
                begin(spectate);
                String setter;
                String target;
                if (spectate) {
                    member("LeftSetter", true); member("RightTarget", false);
                    setter = "LeftSetter"; target = "RightTarget";
                } else {
                    UUID own = UUID.randomUUID(), opponent = UUID.randomUUID();
                    set("ownTeam", List.of(view(own, "OwnSetter")));
                    team().put(opponent, view(opponent, "OpponentTarget"));
                    setter = "OwnSetter"; target = "OpponentTarget";
                }
                BattleFieldEffects.beginMessageBatch(spectate);
                slotEvent("cobblemon.battle.used_move_on", Text.literal(setter),
                        Text.translatable("cobblemon.move.ceaselessedge"), Text.literal(target));
                slotEvent(blocked, Text.literal(target));
                BattleFieldEffects.endMessageBatch();
                assertTrue(BattleUiState.effects().isEmpty(), blocked);
            }
        }
    }

    @Test void blockedConcurrentMoveCannotRemoveAnotherMovesAppliedHazardOrBatonPass() throws Exception {
        for (boolean spectate : new boolean[]{false, true}) {
            for (int activePerSide : new int[]{2, 3}) {
                begin(spectate);
                String first = spectate ? "LeftFirst" : "OwnFirst";
                String target = "RightTarget";
                UUID batonUser;
                List<String> laterUsers = new ArrayList<>();
                if (spectate) {
                    batonUser = member(first, true);
                    for (int index = 2; index <= activePerSide; index++) {
                        String name = "Left" + index;
                        member(name, true);
                        laterUsers.add(name);
                    }
                    member(target, false);
                } else {
                    batonUser = UUID.randomUUID();
                    List<TeamMemberView> own = new ArrayList<>();
                    own.add(view(batonUser, first));
                    for (int index = 2; index <= activePerSide; index++) {
                        String name = "Own" + index;
                        own.add(view(UUID.randomUUID(), name));
                        laterUsers.add(name);
                    }
                    set("ownTeam", List.copyOf(own));
                    UUID targetId = UUID.randomUUID();
                    team().put(targetId, view(targetId, target));
                }
                set("turn", 8);
                BattleFieldEffects.startHazard("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7);
                BattleFieldEffects.startHazard("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7);
                BattleFieldEffects.beginMessageBatch(spectate);
                slotEvent("cobblemon.battle.used_move_on", Text.literal(first),
                        Text.translatable("cobblemon.move.ceaselessedge"), Text.literal(target));
                for (String later : laterUsers) {
                    slotEvent("cobblemon.battle.used_move_on", Text.literal(later),
                            Text.translatable("cobblemon.move.ceaselessedge"), Text.literal(target));
                }
                slotEvent("cobblemon.battle.missed");
                BattleFieldEffects.endMessageBatch();
                assertEquals(3, BattleUiState.effects().stream().filter(effect -> effect.id().endsWith("spikes"))
                        .findFirst().orElseThrow().layers(),
                        "a failed capped move in " + activePerSide + "v" + activePerSide
                                + " must not remove another battler's layer");

                slotEvent("cobblemon.battle.used_move", Text.literal(first),
                        Text.translatable("cobblemon.move.batonpass"));
                usedMove(first, "batonpass", first);
                for (String later : laterUsers) {
                    slotEvent("cobblemon.battle.used_move_on", Text.literal(later),
                            Text.translatable("cobblemon.move.surf"), Text.literal(target));
                }
                slotEvent("cobblemon.battle.missed");
                var consume = BattleUiState.class.getDeclaredMethod("consumePendingBatonPass", UUID.class);
                consume.setAccessible(true);
                assertTrue((boolean) consume.invoke(null, batonUser),
                        "another battler's failed move in " + activePerSide + "v" + activePerSide
                                + " must not cancel Baton Pass");
            }
        }
    }

    @Test void ghostCurseBelongsToItsTargetAndResolvedStateHasOneSharedSource() throws Exception {
        for (boolean spectate : new boolean[]{false, true}) {
            begin(spectate);
            UUID caster;
            UUID target;
            if (spectate) {
                caster = member("LeftGhost", true); target = member("RightTarget", false);
            } else {
                caster = UUID.randomUUID(); target = UUID.randomUUID();
                set("ownTeam", List.of(view(caster, "OwnGhost")));
                team().put(target, view(target, "RightTarget"));
            }
            String casterName = spectate ? "LeftGhost" : "OwnGhost";
            Object[] args = {Text.literal(casterName), Text.literal("RightTarget")};
            UUID subject = BattleUiState.pokemonEffectSubject("cobblemon.battle.start.curse", args);
            assertEquals(target, subject);
            PokemonBattleEffects.accept(subject, "cobblemon.battle.start.curse", args, 4);
            assertTrue(PokemonBattleEffects.active(target, "curse", 4));
            assertFalse(PokemonBattleEffects.active(caster, "curse", 4));

            var tracker = (BattleStatStageTracker) get("OBSERVED_STAT_STAGES");
            tracker.change(target, "def", -2);
            assertEquals(-2, BattleUiState.resolvedStatStageValues(target, Map.of()).get("def"));
            assertEquals("par", BattleUiState.resolvedStatus(target, "par"));
        }
    }

    @Test void temporaryTypesRestoreOnTheBenchAndBatonPassMarkersAreDatedAndConsumed() throws Exception {
        UUID pokemon = member("SoakedMon", false);
        TypeView water = new TypeView("water", Text.literal("Water"));
        TypeView fire = new TypeView("fire", Text.literal("Fire"));
        team().put(pokemon, team().get(pokemon).withTypes(List.of(water)));
        var dynamic = BattleUiState.class.getDeclaredMethod("acceptDynamicPokemonState", String.class, Object[].class);
        dynamic.setAccessible(true);
        dynamic.invoke(null, "cobblemon.battle.start.typechange", new Object[]{
                Text.literal("SoakedMon"), Text.translatable("cobblemon.type.fire")});
        assertEquals(List.of(fire).stream().map(type -> type.id().toLowerCase(Locale.ROOT)).toList(),
                team().get(pokemon).types().stream().map(type -> type.id().toLowerCase(Locale.ROOT)).toList());
        @SuppressWarnings("unchecked") Set<UUID> active = (Set<UUID>) get("PREVIOUSLY_ACTIVE");
        active.add(pokemon);
        var clear = BattleUiState.class.getDeclaredMethod("clearSwitchedOutDynamicTypes", Set.class);
        clear.setAccessible(true);
        clear.invoke(null, Set.of());
        assertEquals("water", team().get(pokemon).types().getFirst().id());

        set("turn", 8);
        var remember = BattleUiState.class.getDeclaredMethod("rememberUsedMoveEffects", String.class, Object[].class);
        remember.setAccessible(true);
        remember.invoke(null, "cobblemon.battle.used_move", new Object[]{
                Text.literal("SoakedMon"), Text.translatable("cobblemon.move.batonpass")});
        var consume = BattleUiState.class.getDeclaredMethod("consumePendingBatonPass", UUID.class);
        consume.setAccessible(true);
        assertTrue((boolean) consume.invoke(null, pokemon));
        assertFalse((boolean) consume.invoke(null, pokemon));
        remember.invoke(null, "cobblemon.battle.used_move", new Object[]{
                Text.literal("SoakedMon"), Text.translatable("cobblemon.move.batonpass")});
        set("turn", 9);
        assertFalse((boolean) consume.invoke(null, pokemon));
    }

    private static void assertGone(UUID id, String item, ItemEventKind kind) {
        assertTrue(BattleUiState.opponentItemKnownAbsent(id));
        assertTrue(BattleUiState.currentItem(id, ItemStack.EMPTY).isEmpty());
        var history = BattleUiState.opponentKnowledge(id).itemHistory();
        assertFalse(history.isEmpty());
        assertEquals(kind, history.getLast().kind());
        assertEquals(stack(item).getItem(), history.getLast().item().getItem());
    }
    private static ClientBattle begin(boolean spectate) throws Exception {
        var battle = new ClientBattle(UUID.randomUUID(), new BattleFormat()); battle.setSpectating(spectate);
        var begin = BattleUiState.class.getDeclaredMethod("beginIfNeeded", ClientBattle.class);
        begin.setAccessible(true); begin.invoke(null, battle); return battle;
    }
    private static ClientBattleActor actor(ClientBattle battle, boolean right, String name) {
        var actor = new ClientBattleActor(right ? "p1" : "p2", Text.literal(name), UUID.randomUUID(), ActorType.PLAYER);
        var side = right ? battle.getSide1() : battle.getSide2();
        actor.setSide(side); side.getActors().add(actor); return actor;
    }
    private static UUID member(String name, boolean left) throws Exception {
        UUID id = UUID.randomUUID(); team().put(id, view(id, name));
        if (left) { @SuppressWarnings("unchecked") Set<UUID> ids = (Set<UUID>) get("SPECTATED_LEFT"); ids.add(id); }
        return id;
    }
    private static TeamMemberView view(UUID id, String name) {
        return new TeamMemberView(id, name, 100, 100F, "", false, true, ItemStack.EMPTY, null,
                List.of(), null, List.of(), List.of(), true);
    }
    private static void event(String key, String subject, Object... rest) {
        Object[] args = new Object[rest.length + 1]; args[0] = Text.literal(subject);
        System.arraycopy(rest, 0, args, 1, rest.length);
        BattleUiState.rememberItemState("cobblemon.battle." + key, "", args);
    }
    private static void publicAbility(String owner, String abilityLabel) {
        Text message = Text.translatable("cobblemon.battle.ability.generic", Text.literal(owner), abilityLabel);
        var content = (TranslatableTextContent) message.getContent();
        assertInstanceOf(String.class, content.getArgs()[1],
                "Cobblemon broadcasts the ability label as a raw String");
        BattleUiState.rememberRevealedAbility(content.getKey(), content.getArgs());
    }
    private static void trick(String user, String target) throws Exception {
        var method = BattleUiState.class.getDeclaredMethod("rememberItemTransferContext", String.class, Object[].class);
        method.setAccessible(true);
        method.invoke(null, "cobblemon.battle.used_move_on", new Object[]{
                Text.literal(user), Text.translatable("cobblemon.move.trick"), Text.literal(target)});
    }
    private static Text item(String id) { return Text.translatable("item.cobblemon." + id); }
    private static MoveTemplate hazard(String id) {
        return TestMoveTemplates.create(id, 0, ElementalTypes.WATER, DamageCategories.INSTANCE.getSTATUS(),
                0, MoveTarget.foeSide, 0, 20, 0, 1, new Double[0]);
    }
    private static MoveTemplate sideEffect(String id) {
        return TestMoveTemplates.create(id, 0, ElementalTypes.FLYING, DamageCategories.INSTANCE.getSTATUS(),
                0, MoveTarget.self, 0, 20, 0, 1, new Double[0]);
    }
    private static MoveTemplate hazardAttack(String id) {
        return TestMoveTemplates.create(id, 0, ElementalTypes.WATER, DamageCategories.INSTANCE.getPHYSICAL(),
                65, MoveTarget.normal, 100, 15, 0, 1, new Double[0]);
    }
    private static MoveTemplate removalAttack(String id, MoveTarget target) {
        return TestMoveTemplates.create(id, 0, ElementalTypes.NORMAL, DamageCategories.INSTANCE.getPHYSICAL(),
                50, target, 100, 15, 0, 1, new Double[0]);
    }
    private static MoveTemplate removalStatus(String id, MoveTarget target) {
        return TestMoveTemplates.create(id, 0, ElementalTypes.NORMAL, DamageCategories.INSTANCE.getSTATUS(),
                0, target, 100, 15, 0, 1, new Double[0]);
    }
    private static void slotEvent(String key, Object... args) throws Exception {
        var method = BattleUiState.class.getDeclaredMethod("rememberSlotEffects", String.class, Object[].class);
        method.setAccessible(true);
        method.invoke(null, key, args);
    }
    private static void publicMove(String user, String move, String target) throws Exception {
        BattleFieldEffects.beginMessageBatch(BattleUiState.spectating());
        try {
            slotEvent("cobblemon.battle.used_move_on", Text.literal(user),
                    Text.translatable("cobblemon.move." + move), Text.literal(target));
            usedMove(user, move, target);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
    }
    private static void seedHazards(String setter, String target) throws Exception {
        for (String hazard : List.of("spikes", "toxicspikes", "stealthrock", "stickyweb")) {
            publicMove(setter, hazard, target);
        }
    }
    private static void seedSideEffects(String setter, List<String> effects) throws Exception {
        for (String effect : effects) publicMove(setter, effect, setter);
    }
    private static void usedMove(String user, String move, String target) throws Exception {
        var method = BattleUiState.class.getDeclaredMethod("rememberUsedMoveEffects", String.class, Object[].class);
        method.setAccessible(true);
        method.invoke(null, "cobblemon.battle.used_move_on", new Object[]{
                Text.literal(user), Text.translatable("cobblemon.move." + move), Text.literal(target)});
    }
    private static void statEvent(String key, Object... args) throws Exception {
        var method = BattleUiState.class.getDeclaredMethod("rememberStatStages", String.class, Object[].class);
        method.setAccessible(true);
        method.invoke(null, key, args);
    }
    private static ItemStack stack(String id) { return Registries.ITEM.get(Identifier.of("cobblemon", id)).getDefaultStack(); }
    @SuppressWarnings("unchecked") private static Map<UUID, TeamMemberView> team() throws Exception {
        return (Map<UUID, TeamMemberView>) get("OPPONENT_TEAM");
    }
    private static Object get(String name) throws Exception {
        var field = BattleUiState.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void set(String name, Object value) throws Exception {
        var field = BattleUiState.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
