package fr.tropimon.battleui;

import java.util.List;
import java.util.Locale;

/** The first participant/subject owns the line, not whichever player is mentioned as its target. */
public enum BattleLogSide {
    NEUTRAL, PLAYER, OPPONENT;

    static BattleLogSide resolve(String subject, String message, List<String> ownNames, List<String> opponentNames) {
        BattleLogSide primary = firstReference(subject, ownNames, opponentNames);
        return primary != NEUTRAL ? primary : firstReference(message, ownNames, opponentNames);
    }

    private static BattleLogSide firstReference(String text, List<String> own, List<String> opponents) {
        if (text == null || text.isBlank()) return NEUTRAL;
        int player = firstName(text, own);
        int opponent = firstName(text, opponents);
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : List.of("the opposing ", "opposing ", "adverse", "sauvage", "wild ")) {
            int index = lower.indexOf(marker);
            if (index >= 0) opponent = Math.min(opponent, index);
        }
        if (player == opponent) return NEUTRAL;
        return player < opponent ? PLAYER : OPPONENT;
    }

    private static int firstName(String text, List<String> names) {
        int first = Integer.MAX_VALUE;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            var matcher = BattleUiState.namePattern(name).matcher(text);
            if (matcher.find()) first = Math.min(first, matcher.start());
        }
        return first;
    }

    int background() { return this == PLAYER ? 0x12257888 : this == OPPONENT ? 0x12973838 : 0; }
    int accent() { return this == PLAYER ? BattleLogTextFormatter.PLAYER_COLOR :
            this == OPPONENT ? BattleLogTextFormatter.OPPONENT_COLOR : 0; }
}
