package fr.tropimon.battleui;

import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Uses Cobblemon's own slot and adjacency rules for doubles, triples and multi battles. */
final class BattleMultiTargeting {
    private BattleMultiTargeting() { }

    static List<ActiveClientBattlePokemon> affected(ActiveClientBattlePokemon attacker, MoveTarget target) {
        if (attacker == null || target == null) return List.of();
        List<Targetable> values = target.getTargetList().invoke(attacker);
        if (values == null) values = attacker.getMultiTargetList(target);
        return live(values);
    }

    static List<ActiveClientBattlePokemon> all(ActiveClientBattlePokemon attacker) {
        if (attacker == null) return List.of();
        var values = new ArrayList<Targetable>();
        for (Targetable value : attacker.getAllActivePokemon()) values.add(value);
        return live(values);
    }

    static List<ActiveClientBattlePokemon> live(List<? extends Targetable> values) {
        if (values == null || values.isEmpty()) return List.of();
        var result = new ArrayList<ActiveClientBattlePokemon>(values.size());
        var seen = new HashSet<UUID>();
        for (Targetable value : values) {
            if (!(value instanceof ActiveClientBattlePokemon active) || active.getBattlePokemon() == null
                    || active.getBattlePokemon().getHpValue() <= 0 || !seen.add(active.getBattlePokemon().getUuid())) continue;
            result.add(active);
        }
        return List.copyOf(result);
    }

    static int spreadTargetCount(ActiveClientBattlePokemon attacker, MoveTarget target) {
        return BattleMoveDynamics.spread(target) ? Math.max(1, affected(attacker, target).size()) : 1;
    }

    static int spreadTargetCount(MoveTarget target, int liveTargets) {
        return BattleMoveDynamics.spread(target) ? Math.max(1, liveTargets) : 1;
    }
}
