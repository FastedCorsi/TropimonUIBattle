package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public battle-state warnings that may alter protection or redirection. */
final class BattleMoveWarnings {
    private BattleMoveWarnings() { }

    static List<Text> forTarget(MoveTemplate template, UUID attacker, BattleUiState.ActiveTargetView target) {
        if (template == null || target == null) return List.of();
        var move = BattleCalculationInputs.move(template);
        var targetSide = BattleUiState.effectSide(target.uuid());
        var attackerSide = BattleUiState.effectSide(attacker);
        boolean allied = attackerSide == targetSide;
        var field = new FieldState();
        field.alliedTarget = allied;
        var conditions = new SideConditions();
        conditions.wideGuard = BattleCalculationHistory.guard(targetSide, BattleCalculationHistory.Guard.WIDE);
        conditions.quickGuard = BattleCalculationHistory.guard(targetSide, BattleCalculationHistory.Guard.QUICK);
        conditions.matBlock = BattleCalculationHistory.guard(targetSide, BattleCalculationHistory.Guard.MAT_BLOCK);
        conditions.craftyShield = BattleCalculationHistory.guard(targetSide, BattleCalculationHistory.Guard.CRAFTY);
        String blocked = BattleProtectionRules.blockedBy(move, field, conditions, effectivePriority(move, attacker));
        List<Text> warnings = new ArrayList<>();
        if (!blocked.isBlank()) warnings.add(Text.translatable("text.tropimon_ui_battle.blocked_by_guard", blocked));
        if (allied || move.spreadMove()) return List.copyOf(warnings);

        var redirect = BattleCalculationHistory.redirect(targetSide);
        if (redirect != null && !redirect.pokemon().equals(target.uuid())) {
            TeamMemberView member = BattleUiState.member(redirect.pokemon());
            String name = member == null ? "?" : member.name();
            warnings.add(Text.translatable("text.tropimon_ui_battle.redirect_move", name,
                    displayMove(redirect.move())));
        }

        String type = BattleCalcDex.normalize(template.getElementalType().getName());
        String redirectAbility = type.equals("electric") ? "lightningrod" : type.equals("water") ? "stormdrain" : "";
        if (!redirectAbility.isBlank()) {
            for (TeamMemberView member : activeOnSide(targetSide)) {
                if (member.uuid().equals(target.uuid())) continue;
                AbilityView known = BattleUiState.currentAbility(member.uuid(), member.ability());
                if (known != null && !known.suppressed() && BattleCalcDex.normalize(known.id()).equals(redirectAbility)) {
                    warnings.add(Text.translatable("text.tropimon_ui_battle.redirect_ability", member.name(),
                            known.name()));
                    continue;
                }
                for (AbilityView possible : BattleUiState.opponentKnowledge(member.uuid()).possibleAbilities()) {
                    if (BattleCalcDex.normalize(possible.id()).equals(redirectAbility)) {
                        warnings.add(Text.translatable("text.tropimon_ui_battle.redirect_ability_possible",
                                member.name(), possible.name()));
                        break;
                    }
                }
            }
        }
        return List.copyOf(warnings);
    }

    private static List<TeamMemberView> activeOnSide(BattleFieldEffects.EffectSide side) {
        List<TeamMemberView> result = new ArrayList<>();
        for (TeamMemberView member : BattleUiState.ownTeam())
            if (member.active() && !member.fainted() && BattleUiState.effectSide(member.uuid()) == side) result.add(member);
        for (TeamMemberView member : BattleUiState.opponentTeam())
            if (member.active() && !member.fainted() && BattleUiState.effectSide(member.uuid()) == side) result.add(member);
        return result;
    }

    private static Text displayMove(String id) {
        var move = com.cobblemon.mod.common.api.moves.Moves.getByName(id);
        return move == null ? Text.literal(id) : move.getDisplayName();
    }

    private static int effectivePriority(MoveData move, UUID attacker) {
        int priority = move.priority();
        TeamMemberView member = BattleUiState.member(attacker);
        AbilityView ability = member == null ? null : BattleUiState.currentAbility(attacker, member.ability());
        String id = ability == null || ability.suppressed() ? "" : BattleCalcDex.normalize(ability.id());
        if (id.equals("prankster") && move.category() == DamageCategory.STATUS) priority++;
        if (id.equals("triage") && move.hasFlag("heal")) priority += 3;
        if (id.equals("galewings") && move.type() == PokeType.FLYING && member.hpPercent() >= 100.0F) priority++;
        return priority;
    }
}
