package fr.tropimon.battleui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Only observations made during the disguise belong to the revealed Pokémon. */
final class BattleIllusionMoves {
    private BattleIllusionMoves() { }

    static LinkedHashMap<String, MoveView> transfer(Map<String, MoveView> before,
            Map<String, MoveView> during, Map<String, MoveView> real) {
        var result = new LinkedHashMap<>(real);
        during.forEach((id, current) -> {
            MoveView previous = before.get(id);
            if (Objects.equals(previous, current)) return;
            MoveView known = result.get(id);
            if (current.origin() == MoveOrigin.CALLED) {
                if (known == null) result.put(id, current);
                return;
            }
            if (current.origin() == MoveOrigin.COPIED || current.origin() == MoveOrigin.TRANSFORMED) {
                result.put(id, current);
                return;
            }
            int oldPp = previous == null || previous.currentPp() < 0 ? current.maxPp() : previous.currentPp();
            int used = Math.max(0, current.ppUsed() - (previous == null ? 0 : previous.ppUsed()));
            int netSpent = oldPp - current.currentPp();
            if (known == null || known.origin() == MoveOrigin.CALLED) {
                known = current.atFullPp();
            }
            if (used == 0 && current.currentPp() == 0 && oldPp > 0) {
                known = known.exhaustPp(); // Grudge exhausts the real move, not just the decoy's previous remainder.
            } else {
                known = known.spendPp(Math.max(used, netSpent));
                if (used > netSpent) known = known.restorePp(used - netSpent);
            }
            result.put(id, known);
        });
        return result;
    }
}
