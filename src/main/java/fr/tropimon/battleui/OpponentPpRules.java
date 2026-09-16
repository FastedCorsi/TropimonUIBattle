package fr.tropimon.battleui;

import java.util.Set;

/** PP ownership rules for moves invoked by another move or battle effect. */
final class OpponentPpRules {
    private static final Set<String> CALLERS = Set.of("assist", "copycat", "mefirst", "metronome",
            "mirrormove", "naturepower", "sleeptalk");
    private OpponentPpRules() { }
    static boolean callsAnotherMove(String move) { return CALLERS.contains(BattleCalcDex.normalize(move)); }
    static boolean repeatsWithoutPp(String eventKey) {
        return "cobblemon.battle.singleturn.instruct".equalsIgnoreCase(eventKey);
    }
}
