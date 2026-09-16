package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveTarget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Public battle-context adjustments shown around Cobblemon's native move tiles. */
final class BattleMoveDynamics {
    private BattleMoveDynamics() {
    }

    static Analysis analyze(MoveTemplate move, InBattleMove battleMove) {
        return analyze(move, battleMove, null);
    }

    static Analysis analyze(MoveTemplate move, InBattleMove battleMove, UUID attackerId) {
        if (move == null) return Analysis.EMPTY;
        TeamMemberView attacker = BattleUiState.ownMember(attackerId);
        List<BattleUiState.ActiveTargetView> targets = BattleUiState.activeOpponentTargets();
        Accuracy accuracy = accuracy(move, attacker, targets.isEmpty() ? null : targets.getFirst());
        Text restriction = restriction(move, battleMove, attacker);
        return new Analysis(targetLabel(move.getTarget()), spread(move.getTarget()), accuracy, restriction,
                battleMove != null && battleMove.mustBeUsed());
    }

    static Accuracy accuracyAgainst(MoveTemplate move, UUID attackerId,
                                    BattleUiState.ActiveTargetView target) {
        return move == null ? new Accuracy(-1.0D, 100.0D, true)
                : accuracy(move, BattleUiState.ownMember(attackerId), target);
    }

    private static Accuracy accuracy(MoveTemplate move, TeamMemberView attacker,
                                     BattleUiState.ActiveTargetView target) {
        double base = move.getAccuracy();
        String id = normalize(move.getName());
        List<TeamMemberView> allies = BattleUiState.activeOwnMembers();
        List<BattleUiState.ActiveTargetView> opponents = BattleUiState.activeOpponentTargets();
        boolean neutralizingGas = neutralizingGasActive(allies, opponents);
        String attackerItem = item(attacker);
        String targetItem = item(target);
        String attackerAbility = abilityUnderGas(ability(attacker == null ? null : attacker.ability()),
                attackerItem, neutralizingGas);
        String targetAbility = abilityUnderGas(ability(target == null ? null : target.ability()),
                targetItem, neutralizingGas);
        boolean weatherSuppressed = weatherSuppressed(allies, opponents, neutralizingGas);
        int victoryStars = activeAlliedAbilityCount(allies, attacker, "victorystar", neutralizingGas);
        if (base < 0 || attackerAbility.equals("noguard") || targetAbility.equals("noguard") ||
                (id.equals("toxic") && hasType(attacker, "poison"))) {
            return new Accuracy(base, 100.0D, true);
        }
        if ((id.equals("thunder") || id.equals("hurricane")) && rain() && !weatherSuppressed) {
            return new Accuracy(base, 100.0D, false);
        }
        if ((id.equals("thunder") || id.equals("hurricane")) && sun() && !weatherSuppressed) {
            return new Accuracy(base, 50.0D, false);
        }
        if (id.equals("blizzard") && snow() && !weatherSuppressed) return new Accuracy(base, 100.0D, false);

        int accuracyStage = stage(attacker == null ? List.of() : attacker.statStages(), "accuracy", "acc");
        int evasionStage = stage(target == null ? List.of() : target.statStages(), "evasion", "eva");
        if (attackerAbility.equals("keeneye")) evasionStage = Math.min(0, evasionStage);
        boolean gravity = BattleFieldEffects.active("gravity");
        double modifier = accuracyStageModifier(accuracyStage, evasionStage, gravity);
        if (attackerAbility.equals("compoundeyes")) modifier *= 1.3D;
        if (attackerAbility.equals("hustle") && move.getDamageCategory().getName().equalsIgnoreCase("physical")) {
            modifier *= 0.8D;
        }
        modifier *= victoryStarModifier(victoryStars);
        if (attackerItem.equals("widelens")) modifier *= 1.1D;
        if (!weatherSuppressed && targetAbility.equals("sandveil") && BattleFieldEffects.active("sandstorm")) modifier *= 0.8D;
        if (!weatherSuppressed && targetAbility.equals("snowcloak") && snow()) modifier *= 0.8D;
        if (targetAbility.equals("tangledfeet") && target != null &&
                PokemonBattleEffects.active(target.uuid(), "confusion", BattleUiState.turn())) modifier *= 0.5D;
        if (target != null && Set.of("brightpowder", "laxincense").contains(targetItem)) {
            modifier *= 0.9D;
        }
        double effective = base * modifier;
        if (targetAbility.equals("wonderskin") && move.getDamageCategory().getName().equalsIgnoreCase("status")) {
            effective = Math.min(50.0D, effective);
        }
        return new Accuracy(base, Math.max(0.0D, Math.min(100.0D, effective)), false);
    }

    private static Text restriction(MoveTemplate move, InBattleMove battleMove, TeamMemberView attacker) {
        if (battleMove == null) return Text.empty();
        String moveId = normalize(move.getName());
        String lastMove = attacker == null ? "" : BattleUiState.lastSelectedMove(attacker.uuid());
        if (battleMove.mustBeUsed() && moveId.equals("struggle")) {
            return Text.translatable("text.tropimon_ui_battle.move_forced.struggle");
        }
        if (battleMove.getPp() <= 0) return Text.translatable("text.tropimon_ui_battle.move_blocked.no_pp");
        if (!battleMove.getDisabled() && !battleMove.mustBeUsed()) return Text.empty();
        if (attacker != null) {
            String encoreMove = PokemonBattleEffects.detail(attacker.uuid(), "encore", BattleUiState.turn());
            if (PokemonBattleEffects.active(attacker.uuid(), "encore", BattleUiState.turn())) {
                return Text.translatable("text.tropimon_ui_battle.move_forced.encore",
                        displayMove(encoreMove.isBlank() ? lastMove : encoreMove));
            }
            String disabledMove = PokemonBattleEffects.detail(attacker.uuid(), "disable", BattleUiState.turn());
            if (PokemonBattleEffects.active(attacker.uuid(), "disable", BattleUiState.turn()) &&
                    (disabledMove.isBlank() || normalize(disabledMove).equals(moveId))) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.disable");
            }
            if (PokemonBattleEffects.active(attacker.uuid(), "taunt", BattleUiState.turn()) &&
                    move.getDamageCategory().getName().equalsIgnoreCase("status")) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.taunt");
            }
            if (PokemonBattleEffects.active(attacker.uuid(), "torment", BattleUiState.turn()) &&
                    normalize(lastMove).equals(moveId)) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.torment");
            }
            if (PokemonBattleEffects.active(attacker.uuid(), "healblock", BattleUiState.turn()) &&
                    HEALING_MOVES.contains(moveId)) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.heal_block");
            }
            if (PokemonBattleEffects.active(attacker.uuid(), "throatchop", BattleUiState.turn()) &&
                    SOUND_MOVES.contains(moveId)) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.throat_chop");
            }
            if (BattleUiState.activeOpponentHasEffect("imprison") &&
                    BattleUiState.activeOpponentHasRevealedMove(moveId)) {
                return Text.translatable("text.tropimon_ui_battle.move_blocked.imprison");
            }
        }
        String heldItem = item(attacker);
        if (heldItem.equals("assaultvest") && move.getDamageCategory().getName().equalsIgnoreCase("status")) {
            return Text.translatable("text.tropimon_ui_battle.move_blocked.assault_vest");
        }
        if (heldItem.startsWith("choice") && !lastMove.isBlank() && !normalize(lastMove).equals(moveId)) {
            return Text.translatable("text.tropimon_ui_battle.move_blocked.choice", displayMove(lastMove));
        }
        if (BattleFieldEffects.active("gravity") && GRAVITY_BLOCKED.contains(moveId)) {
            return Text.translatable("text.tropimon_ui_battle.move_blocked.gravity");
        }
        if (battleMove.getDisabled()) return Text.translatable("text.tropimon_ui_battle.move_blocked.generic");
        return Text.empty();
    }

    static Text targetLabel(MoveTarget target) {
        if (target == MoveTarget.self) return Text.translatable("text.tropimon_ui_battle.move_target.self");
        if (target == MoveTarget.allAdjacentFoes) return Text.translatable("text.tropimon_ui_battle.move_target.all_foes");
        if (target == MoveTarget.allAdjacent) return Text.translatable("text.tropimon_ui_battle.move_target.all_adjacent");
        if (target == MoveTarget.all) return Text.translatable("text.tropimon_ui_battle.move_target.all");
        if (target == MoveTarget.allies || target == MoveTarget.allySide || target == MoveTarget.allyTeam) {
            return Text.translatable("text.tropimon_ui_battle.move_target.ally_side");
        }
        if (target == MoveTarget.foeSide) return Text.translatable("text.tropimon_ui_battle.move_target.foe_side");
        if (target == MoveTarget.adjacentAlly) return Text.translatable("text.tropimon_ui_battle.move_target.ally");
        if (target == MoveTarget.adjacentAllyOrSelf) return Text.translatable("text.tropimon_ui_battle.move_target.ally_or_self");
        if (target == MoveTarget.randomNormal) return Text.translatable("text.tropimon_ui_battle.move_target.random");
        if (target == MoveTarget.scripted) return Text.translatable("text.tropimon_ui_battle.move_target.scripted");
        return Text.translatable("text.tropimon_ui_battle.move_target.one");
    }

    static boolean spread(MoveTarget target) {
        return target == MoveTarget.all || target == MoveTarget.allAdjacent || target == MoveTarget.allAdjacentFoes;
    }

    private static int stage(List<StatStageView> stages, String... ids) {
        for (StatStageView stage : stages) {
            for (String id : ids) if (stage.id().equalsIgnoreCase(id)) return stage.stage();
        }
        return 0;
    }

    private static double stageMultiplier(int rawStage) {
        int stage = Math.max(-6, Math.min(6, rawStage));
        return stage >= 0 ? (3.0D + stage) / 3.0D : 3.0D / (3.0D - stage);
    }

    static double accuracyStageModifier(int accuracyStage, int evasionStage, boolean gravity) {
        double modifier = stageMultiplier(accuracyStage - evasionStage);
        return gravity ? modifier * 5.0D / 3.0D : modifier;
    }

    private static boolean hasType(TeamMemberView member, String type) {
        return member != null && member.types().stream().anyMatch(value -> value.id().equalsIgnoreCase(type));
    }

    private static String ability(AbilityView ability) {
        return ability == null || ability.suppressed() ? "" : normalize(ability.id());
    }

    private static String item(TeamMemberView member) {
        if (member == null || member.heldItem().isEmpty()) return "";
        return normalize(net.minecraft.registry.Registries.ITEM.getId(member.heldItem().getItem()).getPath());
    }

    private static String item(BattleUiState.ActiveTargetView target) {
        return target == null ? "" : normalize(target.heldItemId());
    }

    static String abilityUnderGas(String ability, String heldItem, boolean neutralizingGas) {
        String normalized = normalize(ability);
        if (!neutralizingGas || normalize(heldItem).equals("abilityshield") || GAS_IMMUNE.contains(normalized)) {
            return normalized;
        }
        return "";
    }

    static double victoryStarModifier(int activeVictoryStars) {
        return Math.pow(1.1D, Math.max(0, activeVictoryStars));
    }

    static boolean neutralizingGasActive() {
        return neutralizingGasActive(BattleUiState.activeOwnMembers(), BattleUiState.activeOpponentTargets());
    }

    static boolean weatherSuppressed() {
        List<TeamMemberView> allies = BattleUiState.activeOwnMembers();
        List<BattleUiState.ActiveTargetView> opponents = BattleUiState.activeOpponentTargets();
        return weatherSuppressed(allies, opponents, neutralizingGasActive(allies, opponents));
    }

    private static boolean neutralizingGasActive(List<TeamMemberView> allies,
                                                 List<BattleUiState.ActiveTargetView> opponents) {
        return hasActiveAbility(allies, opponents, "neutralizinggas", false);
    }

    private static boolean weatherSuppressed(List<TeamMemberView> allies,
                                             List<BattleUiState.ActiveTargetView> opponents,
                                             boolean neutralizingGas) {
        return hasActiveAbility(allies, opponents, "cloudnine", neutralizingGas)
                || hasActiveAbility(allies, opponents, "airlock", neutralizingGas);
    }

    private static boolean hasActiveAbility(List<TeamMemberView> allies,
                                            List<BattleUiState.ActiveTargetView> opponents,
                                            String expected, boolean neutralizingGas) {
        String normalized = normalize(expected);
        for (TeamMemberView member : allies) {
            if (abilityUnderGas(ability(member.ability()), item(member), neutralizingGas).equals(normalized)) return true;
        }
        for (BattleUiState.ActiveTargetView opponent : opponents) {
            if (abilityUnderGas(ability(opponent.ability()), item(opponent), neutralizingGas).equals(normalized)) return true;
        }
        return false;
    }

    private static int activeAlliedAbilityCount(List<TeamMemberView> allies, TeamMemberView attacker,
                                                String expected, boolean neutralizingGas) {
        String normalized = normalize(expected);
        int count = 0;
        boolean attackerIncluded = false;
        for (TeamMemberView member : allies) {
            if (attacker != null && member.uuid().equals(attacker.uuid())) attackerIncluded = true;
            if (abilityUnderGas(ability(member.ability()), item(member), neutralizingGas).equals(normalized)) count++;
        }
        if (!attackerIncluded && attacker != null
                && abilityUnderGas(ability(attacker.ability()), item(attacker), neutralizingGas).equals(normalized)) count++;
        return count;
    }

    private static Text displayMove(String id) {
        var move = com.cobblemon.mod.common.api.moves.Moves.getByName(normalize(id));
        return move == null ? Text.literal(id) : move.getDisplayName();
    }

    private static boolean rain() {
        return BattleFieldEffects.active("raindance") || BattleFieldEffects.active("primordialsea");
    }

    private static boolean sun() {
        return BattleFieldEffects.active("sunnyday") || BattleFieldEffects.active("desolateland");
    }

    private static boolean snow() {
        return BattleFieldEffects.active("snow") || BattleFieldEffects.active("hail");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    record Analysis(Text target, boolean spread, Accuracy accuracy, Text restriction, boolean forced) {
        private static final Analysis EMPTY = new Analysis(Text.empty(), false,
                new Accuracy(-1.0D, 100.0D, true), Text.empty(), false);
    }

    record Accuracy(double base, double effective, boolean alwaysHits) {
    }

    private static final Set<String> GRAVITY_BLOCKED = Set.of("bounce", "fly", "flyingpress", "highjumpkick",
            "jumpkick", "magnetrise", "skydrop", "splash", "telekinesis");
    private static final Set<String> GAS_IMMUNE = Set.of("neutralizinggas", "multitype", "rkssystem", "schooling",
            "shieldsdown", "stancechange", "comatose", "disguise", "iceface", "gulpmissile", "asoneglastrier",
            "asonespectrier", "zerotohero", "battlebond", "powerconstruct", "zenmode", "terashift");
    private static final Set<String> HEALING_MOVES = Set.of("aquaring", "floralhealing", "healorder", "healpulse",
            "healingwish", "junglehealing", "lifedew", "lunarblessing", "lunarDance", "milkdrink", "moonlight",
            "morningSun", "purify", "recover", "rest", "roost", "shoreup", "slackoff", "softboiled", "strengthsap",
            "synthesis", "wish").stream().map(BattleMoveDynamics::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> SOUND_MOVES = Set.of("alluringvoice", "boomburst", "bugbuzz", "chatter",
            "clangingscales", "clangoroussoul", "confide", "disarmingvoice", "echoedvoice", "eeriespell", "growl",
            "healbell", "hypervoice", "metalSound", "nobleroar", "overdrive", "partingshot", "perishsong", "psychicnoise",
            "relicsong", "roar", "round", "screech", "sing", "snarl", "snore", "sparklingaria", "supersonic",
            "torchsong", "uproar").stream().map(BattleMoveDynamics::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
}
