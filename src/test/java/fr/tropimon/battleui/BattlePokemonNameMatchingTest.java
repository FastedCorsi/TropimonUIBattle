package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlePokemonNameMatchingTest {
    @Test
    void doesNotConfuseSpeciesWhoseNamesArePrefixes() {
        assertTrue(BattleUiState.nameMatches("The opposing Mew used Psychic!", "Mew"));
        assertFalse(BattleUiState.nameMatches("The opposing Mewtwo used Psychic!", "Mew"));
    }

    @Test
    void matchesNamesAroundTrainerPrefixesAndPunctuation() {
        assertTrue(BattleUiState.nameMatches("Shinon_'s Taper Taper used Ice Hammer!", "Taper Taper"));
        assertTrue(BattleUiState.nameMatches("Go! Mr. Mime!", "Mr. Mime"));
    }

    @Test
    void recognizesLocalizedWildAndOpposingMarkers() {
        assertTrue(BattleUiState.isOpponentReference("The opposing Garchomp used Earthquake!"));
        assertTrue(BattleUiState.isOpponentReference("A wild Voltorb appeared!"));
        assertTrue(BattleUiState.isOpponentReference("Un Voltorbe sauvage apparaît !"));
    }
}
