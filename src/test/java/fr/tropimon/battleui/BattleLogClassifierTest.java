package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleLogClassifierTest {
    @Test
    void recognizesTranslatedTurnMessages() {
        assertEquals(BattleLogEntryType.TURN,
                BattleLogClassifier.classify("cobblemon.battle.turn", "Le tour 12 commence"));
        assertEquals(12,
                BattleLogClassifier.findTurn("cobblemon.battle.turn", "Le tour 12 commence", new Object[]{12}));
    }

    @Test
    void recognizesCoreBattleCategories() {
        assertEquals(BattleLogEntryType.MOVE, BattleLogClassifier.classify("cobblemon.battle.move", "Pikachu utilise Tonnerre"));
        assertEquals(BattleLogEntryType.DAMAGE, BattleLogClassifier.classify("cobblemon.battle.damage", "Coup critique"));
        assertEquals(BattleLogEntryType.HEAL, BattleLogClassifier.classify("cobblemon.battle.heal", "Dracaufeu récupère des PV"));
        assertEquals(BattleLogEntryType.FIELD, BattleLogClassifier.classify("cobblemon.battle.weather.raindance.start", "La pluie commence"));
        assertEquals(BattleLogEntryType.ITEM, BattleLogClassifier.classify("cobblemon.battle.enditem.knockoff", "Pikachu lost its Light Ball"));
        assertEquals(BattleLogEntryType.STAT_UP, BattleLogClassifier.classify("cobblemon.battle.boost.sharp", "Attack rose sharply"));
        assertEquals(BattleLogEntryType.STAT_DOWN, BattleLogClassifier.classify("cobblemon.battle.unboost.slight", "Defense fell"));
        assertEquals(BattleLogEntryType.SWITCH, BattleLogClassifier.classify("cobblemon.battle.switch.other", "Alex sent out Garchomp"));
        assertEquals(BattleLogEntryType.TIMER, BattleLogClassifier.classify("", "Alex has 90 seconds left"));
        assertEquals(BattleLogEntryType.ABILITY,
                BattleLogClassifier.classify("cobblemon.battle.ability.generic", "Giratina's Levitate activated"));
    }

    @Test
    void formatsElapsedBattleTime() {
        assertEquals("00:09", BattleUiRenderer.formatDuration(Duration.ofSeconds(9)));
        assertEquals("03:07", BattleUiRenderer.formatDuration(Duration.ofSeconds(187)));
        assertEquals("1:02:03", BattleUiRenderer.formatDuration(Duration.ofSeconds(3723)));
    }
}
