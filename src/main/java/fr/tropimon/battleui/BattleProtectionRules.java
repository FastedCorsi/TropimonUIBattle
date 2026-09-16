package fr.tropimon.battleui;

import java.util.Set;

/** Shared, side-aware protection rules used by both damage and target previews. */
final class BattleProtectionRules {
    private static final Set<String> BYPASSES_PROTECT = Set.of(
            "feint", "hyperspacefury", "hyperspacehole", "phantomforce", "shadowforce");

    private BattleProtectionRules() { }

    static String blockedBy(MoveData move, FieldState field, SideConditions defendingSide) {
        return blockedBy(move, field, defendingSide, move == null ? 0 : move.priority());
    }

    static String blockedBy(MoveData move, FieldState field, SideConditions defendingSide, int effectivePriority) {
        if (move == null || defendingSide == null || field.alliedTarget || bypasses(move)) return "";
        if (defendingSide.wideGuard && move.spreadMove()) return "Wide Guard";
        if (defendingSide.quickGuard && effectivePriority > 0) return "Quick Guard";
        if (defendingSide.matBlock && move.category() != DamageCategory.STATUS) return "Mat Block";
        if (defendingSide.craftyShield && move.category() == DamageCategory.STATUS) return "Crafty Shield";
        return "";
    }

    private static boolean bypasses(MoveData move) {
        return BYPASSES_PROTECT.contains(BattleCalcDex.normalize(move.id())) || move.hasFlag("breaksprotect");
    }
}
