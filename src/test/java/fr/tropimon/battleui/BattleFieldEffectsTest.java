package fr.tropimon.battleui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleFieldEffectsTest {
    @Test
    void magicRoomAndTrickRoomCoexistSoItemSuppressionIsNotLost() {
        BattleFieldEffects.accept("cobblemon.battle.fieldstart.magicroom", 1);
        BattleFieldEffects.accept("cobblemon.battle.fieldstart.trickroom", 2);
        BattleFieldEffects.accept("cobblemon.battle.fieldstart.wonderroom", 3);
        assertTrue(BattleFieldEffects.active("magicroom"));
        assertTrue(BattleFieldEffects.active("trickroom"));
        assertTrue(BattleFieldEffects.active("wonderroom"));
    }
    @AfterEach
    void reset() {
        BattleFieldEffects.reset();
    }

    @Test
    void tracksWeatherRemainingTurnsAndRemoval() {
        BattleFieldEffects.accept("cobblemon.battle.weather.raindance.start", 3);
        var rain = BattleFieldEffects.snapshot(5).getFirst();
        assertEquals("raindance", rain.id());
        assertEquals(3, rain.minimumRemainingTurns());
        assertEquals(6, rain.maximumRemainingTurns());
        assertEquals(5, rain.standardDuration());
        assertEquals(8, rain.extendedDuration());
        assertEquals("3–6", rain.counter().getString());

        BattleFieldEffects.accept("cobblemon.battle.weather.raindance.end", 6);
        assertTrue(BattleFieldEffects.snapshot(6).isEmpty());
    }

    @Test
    void confirmsExtenderWhenWeatherSurvivesPastNormalDuration() {
        BattleFieldEffects.accept("cobblemon.battle.weather.sunnyday.start", 1);
        var sun = BattleFieldEffects.snapshot(6).getFirst();
        assertEquals(3, sun.minimumRemainingTurns());
        assertEquals(3, sun.maximumRemainingTurns());
    }

    @Test
    void rainSunAndSandAllUseFiveToEightTurnDurations() {
        for (String weather : new String[]{"raindance", "sunnyday", "sandstorm"}) {
            BattleFieldEffects.reset();
            BattleFieldEffects.accept("cobblemon.battle.weather." + weather + ".start", 1);
            var effect = BattleFieldEffects.snapshot(1).getFirst();
            assertEquals(5, effect.minimumRemainingTurns(), weather);
            assertEquals(8, effect.maximumRemainingTurns(), weather);
        }
    }

    @Test
    void doesNotInventTurnCountersForPersistentHazards() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.stealthrock", 2);
        var rocks = BattleFieldEffects.snapshot(10).getFirst();
        assertEquals(0, rocks.minimumRemainingTurns());
        assertEquals(0, rocks.maximumRemainingTurns());
    }

    @Test
    void countsHazardLayers() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 1);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 2);
        var spikes = BattleFieldEffects.snapshot(2).getFirst();
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, spikes.side());
        assertEquals("opponent.spikes", spikes.id());
        assertEquals(2, spikes.layers());
        assertTrue(spikes.layered());
        assertEquals("×2", spikes.counter().getString());
    }

    @Test
    void semanticHazardEventsUseTheRequestedSideAndRespectNativeLayerLimits() {
        BattleFieldEffects.startHazard("toxic_spikes", BattleFieldEffects.EffectSide.PLAYER_FIELD, 2);
        BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD, 2);
        BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD, 3);

        var toxicSpikes = BattleFieldEffects.snapshot(3).getFirst();
        assertEquals("ally.toxicspikes", toxicSpikes.id());
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, toxicSpikes.side());
        assertEquals(2, toxicSpikes.layers());
    }

    @Test
    void explicitAndAbilityHazardSignalsArePairedOnceInEitherOrder() {
        BattleFieldEffects.beginMessageBatch();
        try {
            BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD, 4);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.toxicspikes", 4);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(1, BattleFieldEffects.snapshot(4).getFirst().layers());

        BattleFieldEffects.reset();
        BattleFieldEffects.beginMessageBatch();
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.toxicspikes", 4);
            BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.PLAYER_FIELD, 4);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(1, BattleFieldEffects.snapshot(4).getFirst().layers());
    }

    @Test
    void twoRealToxicDebrisActivationsRemainTwoLayersWhenOneHasAnExplicitEcho() {
        BattleFieldEffects.beginMessageBatch();
        try {
            BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 5);
            BattleFieldEffects.startHazard("toxicspikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 5);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.toxicspikes", 5);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(2, BattleFieldEffects.snapshot(5).getFirst().layers());
    }

    @Test
    void moveAndExplicitEchoStayPairedAcrossSeparatedPacketsForTheWholeTurn() {
        BattleFieldEffects.beginMessageBatch();
        try {
            BattleFieldEffects.startHazardFromMove("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 6);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        BattleFieldEffects.beginMessageBatch();
        BattleFieldEffects.endMessageBatch();
        BattleFieldEffects.beginMessageBatch();
        BattleFieldEffects.endMessageBatch();
        BattleFieldEffects.beginMessageBatch();
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 6);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(1, BattleFieldEffects.snapshot(6).getFirst().layers());
    }

    @Test
    void participantAndSpectatorHazardProvenanceNeverMerge() {
        BattleFieldEffects.beginMessageBatch(false);
        try {
            BattleFieldEffects.startHazardFromMove("spikes", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 6);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        BattleFieldEffects.beginMessageBatch(true);
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 6);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        assertEquals(2, BattleFieldEffects.snapshot(6).getFirst().layers());
    }

    @Test
    void tracksScreensVeilAndTailwindOnTheirActualSides() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.lightscreen", 4);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.reflect", 4);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.auroraveil", 4);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.tailwind", 4);

        var effects = BattleFieldEffects.snapshot(4);
        assertEquals(4, effects.size());
        assertEquals("ally.lightscreen", effects.get(0).id());
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, effects.get(0).side());
        assertEquals("5–8", effects.get(0).counter().getString());
        assertEquals("opponent.reflect", effects.get(1).id());
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, effects.get(1).side());
        assertEquals("ally.auroraveil", effects.get(2).id());
        assertEquals("opponent.tailwind", effects.get(3).id());
        assertEquals("4", effects.get(3).counter().getString());

        BattleFieldEffects.accept("cobblemon.battle.sideend.ally.lightscreen", 5);
        assertEquals(3, BattleFieldEffects.snapshot(5).size());
    }

    @Test
    void poisonTypeAbsorptionRemovesToxicSpikesForParticipantAndSpectatorMessages() {
        for (boolean spectating : new boolean[]{false, true}) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.toxicspikes", 3);
            BattleFieldEffects.endMessageBatch();

            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.sideend.toxicspikes", 4,
                    BattleFieldEffects.EffectSide.PLAYER_FIELD);
            BattleFieldEffects.endMessageBatch();
            assertFalse(BattleFieldEffects.activeOnSide("toxicspikes",
                    BattleFieldEffects.EffectSide.PLAYER_FIELD));
        }
    }

    @Test
    void unresolvedGenericAbsorptionRemovesOnlyAnUnambiguousToxicSpikesSide() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.toxicspikes", 3);
        BattleFieldEffects.accept("cobblemon.battle.sideend.toxicspikes", 4,
                BattleFieldEffects.EffectSide.FIELD);
        assertFalse(BattleFieldEffects.active("toxicspikes"));

        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.toxicspikes", 5);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.toxicspikes", 5);
        BattleFieldEffects.accept("cobblemon.battle.sideend.toxicspikes", 6,
                BattleFieldEffects.EffectSide.FIELD);
        assertTrue(BattleFieldEffects.activeOnSide("toxicspikes",
                BattleFieldEffects.EffectSide.PLAYER_FIELD));
        assertTrue(BattleFieldEffects.activeOnSide("toxicspikes",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD));
    }

    @Test
    void genericTailwindUsesTheObservedMoveSideInParticipantAndSpectatorModes() {
        for (boolean spectating : new boolean[]{false, true}) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.rememberSideEffectMove("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7);
            BattleFieldEffects.endMessageBatch();

            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.tailwind", 7,
                    BattleFieldEffects.EffectSide.FIELD);
            BattleFieldEffects.endMessageBatch();

            assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD));
            assertEquals(1, BattleFieldEffects.snapshot(7).size());
        }
    }

    @Test
    void participantSideEvidenceNeverLeaksIntoSpectatorDetection() {
        BattleFieldEffects.beginMessageBatch(false);
        BattleFieldEffects.rememberSideEffectMove("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7);
        BattleFieldEffects.endMessageBatch();

        BattleFieldEffects.beginMessageBatch(true);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.tailwind", 7,
                BattleFieldEffects.EffectSide.FIELD);
        BattleFieldEffects.endMessageBatch();

        assertFalse(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD));
    }

    @Test
    void spectatorInferredTailwindExpiresBeforeItsFifthTurn() {
        BattleFieldEffects.beginMessageBatch(true);
        try {
            assertTrue(BattleFieldEffects.startSideEffectFromMove("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD, 3).changed());
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals("1", BattleFieldEffects.snapshot(6).getFirst().counter().getString());
        assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 6));
        assertFalse(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7));
        assertTrue(BattleFieldEffects.snapshot(7).isEmpty());
    }

    @Test
    void inferredScreenKeepsItsFiveToEightTurnUncertaintyThenExpires() {
        BattleFieldEffects.beginMessageBatch(true);
        try {
            assertTrue(BattleFieldEffects.startSideEffectFromMove("reflect",
                    BattleFieldEffects.EffectSide.PLAYER_FIELD, 3).changed());
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        var uncertain = BattleFieldEffects.snapshot(8).getFirst();
        assertEquals(0, uncertain.minimumRemainingTurns());
        assertEquals(3, uncertain.maximumRemainingTurns());
        assertEquals("0–3", uncertain.counter().getString());
        assertFalse(BattleFieldEffects.activeOnSide("reflect",
                BattleFieldEffects.EffectSide.PLAYER_FIELD, 8));
        assertTrue(BattleFieldEffects.snapshot(11).isEmpty());
    }

    @Test
    void spectatorSideStartEchoKeepsLocalExpiryAndDoesNotRestartTailwind() {
        BattleFieldEffects.beginMessageBatch(true);
        try {
            BattleFieldEffects.startSideEffectFromMove("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD, 3);
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.tailwind", 3);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals(1, BattleFieldEffects.snapshot(3).size());
        assertEquals("1", BattleFieldEffects.snapshot(6).getFirst().counter().getString());
        assertFalse(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7));
        assertTrue(BattleFieldEffects.snapshot(7).isEmpty());
    }

    @Test
    void delayedSpectatorSideStartEchoDoesNotRestartTheCounter() {
        BattleFieldEffects.beginMessageBatch(true);
        try {
            BattleFieldEffects.startSideEffectFromMove("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD, 3);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
        BattleFieldEffects.beginMessageBatch(true);
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.tailwind", 4);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals("3", BattleFieldEffects.snapshot(4).getFirst().counter().getString());
        assertTrue(BattleFieldEffects.snapshot(7).isEmpty());
    }

    @Test
    void boundedParticipantEffectCannotRemainForeverWhenItsSideEndIsMissing() {
        BattleFieldEffects.beginMessageBatch(false);
        try {
            BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.tailwind", 3);
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertEquals("1", BattleFieldEffects.snapshot(6).getFirst().counter().getString());
        assertTrue(BattleFieldEffects.snapshot(7).isEmpty());
    }

    @Test
    void bothSpectatorSidesCanInferTheSameConditionOnTheSameTurn() {
        BattleFieldEffects.beginMessageBatch(true);
        try {
            assertTrue(BattleFieldEffects.startSideEffectFromMove("tailwind",
                    BattleFieldEffects.EffectSide.PLAYER_FIELD, 5).changed());
            assertTrue(BattleFieldEffects.startSideEffectFromMove("tailwind",
                    BattleFieldEffects.EffectSide.OPPONENT_FIELD, 5).changed());
        } finally {
            BattleFieldEffects.endMessageBatch();
        }

        assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.PLAYER_FIELD, 5));
        assertTrue(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 5));
        assertEquals(2, BattleFieldEffects.snapshot(5).size());
    }

    @Test
    void courtChangeSwapsEverySideEffectWithoutChangingLayersOrCounters() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.spikes", 2);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.spikes", 3);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.reflect", 3);

        BattleFieldEffects.accept("cobblemon.battle.activate.courtchange", 4);
        var effects = BattleFieldEffects.snapshot(4);

        assertEquals("opponent.spikes", effects.get(0).id());
        assertEquals(BattleFieldEffects.EffectSide.OPPONENT_FIELD, effects.get(0).side());
        assertEquals(2, effects.get(0).layers());
        assertEquals("ally.reflect", effects.get(1).id());
        assertEquals(BattleFieldEffects.EffectSide.PLAYER_FIELD, effects.get(1).side());
    }

    @Test
    void replacesMutuallyExclusiveWeatherAndTerrain() {
        BattleFieldEffects.accept("cobblemon.battle.weather.raindance.start", 1);
        BattleFieldEffects.accept("cobblemon.battle.weather.sunnyday.start", 2);
        assertEquals(1, BattleFieldEffects.snapshot(2).size());
        assertEquals("sunnyday", BattleFieldEffects.snapshot(2).getFirst().id());

        BattleFieldEffects.accept("cobblemon.battle.fieldstart.electricterrain", 2);
        BattleFieldEffects.accept("cobblemon.battle.fieldstart.grassyterrain", 3);
        assertEquals(2, BattleFieldEffects.snapshot(3).size());
        assertEquals("grassyterrain", BattleFieldEffects.snapshot(3).get(1).id());
    }

    @Test
    void keepsSlotEffectsAcrossSwitchesAndOnTheTargetedSide() {
        BattleFieldEffects.startSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, 7);
        BattleFieldEffects.startSlotEffect("future_sight", BattleFieldEffects.EffectSide.OPPONENT_FIELD, 7);
        var effects = BattleFieldEffects.snapshot(8);
        assertEquals("ally.wish", effects.get(0).id());
        assertEquals("1", effects.get(0).counter().getString());
        assertEquals("opponent.futuresight", effects.get(1).id());
        assertEquals("2", effects.get(1).counter().getString());

        BattleFieldEffects.endSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD);
        assertEquals(1, BattleFieldEffects.snapshot(8).size());
    }

    @Test
    void delayedAttacksAreTrackedPerSlotAndResolveIndependently() {
        for (boolean spectating : new boolean[]{false, true}) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.startSlotEffect("futuresight", BattleFieldEffects.EffectSide.OPPONENT_FIELD, "p1a", 3);
            BattleFieldEffects.startSlotEffect("futuresight", BattleFieldEffects.EffectSide.OPPONENT_FIELD, "p1b", 3);
            assertEquals(2, BattleFieldEffects.snapshot(3).size());

            BattleFieldEffects.endSlotEffect("futuresight", BattleFieldEffects.EffectSide.OPPONENT_FIELD, "p1a");
            assertEquals(1, BattleFieldEffects.snapshot(4).size());
            BattleFieldEffects.endSlotEffect("futuresight", BattleFieldEffects.EffectSide.OPPONENT_FIELD, "p1b");
            assertTrue(BattleFieldEffects.snapshot(4).isEmpty());
        }
    }

    @Test
    void wishesAreTrackedPerSlotInDoublesAndTriplesForBothObservationModes() {
        for (boolean spectating : new boolean[]{false, true}) {
            BattleFieldEffects.reset();
            BattleFieldEffects.beginMessageBatch(spectating);
            BattleFieldEffects.startSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2a", 3);
            BattleFieldEffects.startSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2b", 3);
            BattleFieldEffects.startSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2c", 3);
            BattleFieldEffects.endMessageBatch();
            assertEquals(3, BattleFieldEffects.snapshot(3).size());

            BattleFieldEffects.endSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2b");
            assertEquals(2, BattleFieldEffects.snapshot(4).size());
            BattleFieldEffects.endSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2a");
            assertEquals(1, BattleFieldEffects.snapshot(4).size());
            BattleFieldEffects.endSlotEffect("wish", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2c");
            assertTrue(BattleFieldEffects.snapshot(4).isEmpty());
        }
    }

    @Test
    void sideEffectAndHazardProvenanceIsPurgedOnTurnChange() {
        BattleFieldEffects.beginMessageBatch(true);
        BattleFieldEffects.rememberSideEffectMove("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 4);
        BattleFieldEffects.startHazardFromMove("spikes",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD, 4);
        BattleFieldEffects.endMessageBatch();

        BattleFieldEffects.beginMessageBatch(true);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.tailwind", 5,
                BattleFieldEffects.EffectSide.FIELD);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 5);
        BattleFieldEffects.endMessageBatch();

        assertFalse(BattleFieldEffects.activeOnSide("tailwind",
                BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        assertEquals(2, BattleFieldEffects.snapshot(5).stream()
                .filter(effect -> effect.id().equals("opponent.spikes")).findFirst().orElseThrow().layers());
    }

    @Test
    void semanticTargetConfirmationReplacesTheGenericDelayedAttackWithoutDuplication() {
        BattleFieldEffects.startSlotEffect("future_sight", BattleFieldEffects.EffectSide.PLAYER_FIELD, 6);
        BattleFieldEffects.startSlotEffect("futuresight", BattleFieldEffects.EffectSide.PLAYER_FIELD, "p2c", 6);

        assertEquals(1, BattleFieldEffects.snapshot(6).size());
        assertEquals("ally.futuresight", BattleFieldEffects.snapshot(6).getFirst().id());
    }

    @Test
    void iconCountersReserveMultiplicationForHazardLayersWithoutLosingUncertainty() {
        BattleFieldEffects.accept("cobblemon.battle.weather.raindance.start", 3);
        assertEquals("3–6", BattleFieldEffects.snapshot(5).getFirst().iconCounter().getString());
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 3);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.spikes", 4);
        assertEquals("×2", BattleFieldEffects.snapshot(5).get(1).iconCounter().getString());
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.tailwind", 5);
        assertEquals("4", BattleFieldEffects.snapshot(5).get(2).iconCounter().getString());
    }

    @Test
    void allTimedFieldEffectsShowOnlyNumbersAndKeepTheirRemainingRange() {
        for (String id : new String[]{"raindance", "sunnyday", "sandstorm", "snow", "hail",
                "electricterrain", "grassyterrain", "mistyterrain", "psychicterrain",
                "lightscreen", "reflect", "auroraveil", "tailwind", "trickroom", "magicroom",
                "wonderroom", "gravity", "safeguard", "mist", "luckychant", "wish", "futuresight"}) {
            var effect = new BattleFieldEffects.EffectView(id, 2, 5, 5, 8, 1, false,
                    BattleFieldEffects.EffectSide.FIELD);
            assertEquals("2–5", effect.iconCounter().getString(), id);
            var last = new BattleFieldEffects.EffectView(id, 1, 1, 5, 8, 1, false,
                    BattleFieldEffects.EffectSide.FIELD);
            assertEquals("1", last.iconCounter().getString(), id);
        }
    }

    @Test
    void singleLayerHazardsDisplayOneButDynamicWeatherDoesNotInventADuration() {
        BattleFieldEffects.accept("cobblemon.battle.sidestart.opponent.stealthrock", 1);
        BattleFieldEffects.accept("cobblemon.battle.sidestart.ally.stickyweb", 1);
        BattleFieldEffects.accept("cobblemon.battle.weather.primordialsea.start", 1);
        var effects = BattleFieldEffects.snapshot(8);
        assertEquals("×1", effects.get(0).iconCounter().getString());
        assertEquals("×1", effects.get(1).iconCounter().getString());
        assertEquals("", effects.get(2).iconCounter().getString());
    }
}
