package fr.tropimon.battleui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonBattleEffectsTest {
    private final UUID pokemon = UUID.randomUUID();

    @AfterEach
    void reset() {
        PokemonBattleEffects.reset();
    }

    @Test
    void tracksStartsCountersAndEnds() {
        PokemonBattleEffects.accept(pokemon, "cobblemon.battle.start.confusion", new Object[0], 3);
        PokemonBattleEffects.accept(pokemon, "cobblemon.battle.start.perish", new Object[]{"x", 2}, 3);
        var effects = PokemonBattleEffects.snapshot(pokemon, 3);
        assertEquals("confusion", effects.get(0).id());
        assertEquals(2, effects.get(1).counter());

        PokemonBattleEffects.accept(pokemon, "cobblemon.battle.end.confusion", new Object[0], 4);
        assertEquals(1, PokemonBattleEffects.snapshot(pokemon, 4).size());
    }

    @Test
    void expiresSingleTurnEffectsAndClearsEffectsOnSwitch() {
        PokemonBattleEffects.accept(pokemon, "cobblemon.battle.singleturn.protect", new Object[0], 5);
        assertEquals(1, PokemonBattleEffects.snapshot(pokemon, 5).size());
        assertTrue(PokemonBattleEffects.snapshot(pokemon, 6).isEmpty());

        PokemonBattleEffects.accept(pokemon, "cobblemon.battle.start.taunt", new Object[0], 6);
        PokemonBattleEffects.retainActive(Set.of());
        assertTrue(PokemonBattleEffects.snapshot(pokemon, 6).isEmpty());
    }

    @Test
    void exposesReliableSleepToxicAndDurationCounters() {
        PokemonBattleEffects.synchronizeStatus(pokemon, "slp", 2);
        assertEquals("1/3", PokemonBattleEffects.snapshot(pokemon, 2).getFirst().counterText());
        PokemonBattleEffects.accept(pokemon, "cobblemon.status.sleep.is", new Object[0], 3);
        assertEquals("2/3", PokemonBattleEffects.snapshot(pokemon, 3).getFirst().counterText());

        PokemonBattleEffects.synchronizeStatus(pokemon, "tox", 4);
        var toxic = PokemonBattleEffects.snapshot(pokemon, 4).stream()
                .filter(effect -> effect.id().equals("toxic")).findFirst().orElseThrow();
        assertEquals("1", toxic.counterText());
        PokemonBattleEffects.accept(pokemon, "cobblemon.status.poison.hurt", new Object[0], 5);
        toxic = PokemonBattleEffects.snapshot(pokemon, 5).stream()
                .filter(effect -> effect.id().equals("toxic")).findFirst().orElseThrow();
        assertEquals("2", toxic.counterText());
    }

    @Test
    void firstSleepAndToxicTicksCountOnTheApplicationTurnExactlyOnceInBothModes() {
        for (boolean spectating : new boolean[]{false, true}) {
            PokemonBattleEffects.reset();
            PokemonBattleEffects.beginMessageBatch(spectating);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.sleep.apply", new Object[0], 6);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.sleep.is", new Object[0], 6);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.sleep.is", new Object[0], 6);
            assertEquals("2/3", PokemonBattleEffects.snapshot(pokemon, 6).getFirst().counterText());

            PokemonBattleEffects.accept(pokemon, "cobblemon.status.sleep.cure", new Object[0], 6);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.poisonbadly.apply", new Object[0], 6);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.poison.hurt", new Object[0], 6);
            PokemonBattleEffects.accept(pokemon, "cobblemon.status.poison.hurt", new Object[0], 6);
            assertEquals("2", PokemonBattleEffects.snapshot(pokemon, 6).getFirst().counterText());
        }
    }

    @Test
    void tracksProtectChain() {
        PokemonBattleEffects.acceptMove(pokemon, "protect", 7);
        PokemonBattleEffects.acceptMove(pokemon, "detect", 8);

        var effects = PokemonBattleEffects.snapshot(pokemon, 8);
        assertTrue(effects.stream().anyMatch(effect -> effect.id().equals("protectchain") &&
                effect.counter() == 2 && effect.counterText().equals("2")));
    }

    @Test
    void consumesSingleMoveEffectsWhenThePokemonActsAgain() {
        for (String effect : Set.of("destinybond", "glaiverush", "grudge", "rage")) {
            PokemonBattleEffects.reset();
            PokemonBattleEffects.accept(pokemon, "cobblemon.battle.singlemove." + effect, new Object[0], 4);
            assertTrue(PokemonBattleEffects.active(pokemon, effect, 4));
            assertTrue(PokemonBattleEffects.active(pokemon, effect, 8),
                    "the effect is tied to the next move rather than an estimated turn");

            PokemonBattleEffects.acceptMove(pokemon, "tackle", 8);
            assertTrue(PokemonBattleEffects.snapshot(pokemon, 8).stream()
                    .noneMatch(value -> value.id().equals(effect)));
        }
    }

    @Test
    void tracksEncoreForParticipantsAndSpectatorsWithoutMixingTheirSessions() {
        for (boolean spectating : new boolean[]{false, true}) {
            PokemonBattleEffects.reset();
            PokemonBattleEffects.beginMessageBatch(spectating);
            PokemonBattleEffects.accept(pokemon, "cobblemon.battle.start.encore",
                    new Object[]{"target", "Last Move"}, 4);

            var encore = PokemonBattleEffects.snapshot(pokemon, 5).getFirst();
            assertEquals("encore", encore.id());
            assertEquals("Last Move", encore.detail());
            assertEquals("2", encore.counterText());
        }

        PokemonBattleEffects.beginMessageBatch(false);
        assertTrue(PokemonBattleEffects.snapshot(pokemon, 5).isEmpty());
    }
}
