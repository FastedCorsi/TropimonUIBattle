package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Independent calculation state: no cross-mod reflection, configuration, or service calls. */
final class TropimonDamageCalcBridge {
    private static final Map<Pair, Context> CONTEXTS = new LinkedHashMap<>();
    private static UUID battleId;
    private static Boolean randomMode;
    private static long resources = -1;

    static Optional<DamageEstimate> analyze(MoveTemplate move) {
        var targets = BattleUiState.activeOpponentTargets();
        return analyze(move, targets.isEmpty() ? null : targets.getFirst(), null);
    }
    static Optional<DamageEstimate> analyze(MoveTemplate move, BattleUiState.ActiveTargetView target) {
        return analyze(move, target, null);
    }
    static Optional<DamageEstimate> analyze(MoveTemplate move, BattleUiState.ActiveTargetView target, UUID attacker) {
        MinecraftClient client = MinecraftClient.getInstance();
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (move == null || battle == null || client.world == null || !client.isOnThread()
                || move.getDamageCategory().getName().equalsIgnoreCase("status")) return Optional.empty();
        boolean randomBattle = BattleCalculationInputs.random(battle);
        if (!battle.getBattleId().equals(battleId) || resources != UiResourceEpoch.current()
                || randomMode == null || randomMode != randomBattle) {
            CONTEXTS.clear();
            DamageCalculationCache.clearShared();
            if (resources != UiResourceEpoch.current()) BattleCalcDex.invalidateCobblemonData();
            battleId = battle.getBattleId();
            randomMode = randomBattle;
            resources = UiResourceEpoch.current();
        }
        if (attacker == null) {
            var member = BattleUiState.activeOwnMember();
            if (member == null) return Optional.empty();
            attacker = member.uuid();
        }
        if (target == null) {
            var targets = BattleUiState.activeOpponentTargets();
            if (targets.isEmpty()) return Optional.empty();
            target = targets.getFirst();
        }
        var own = BattleUiState.activeBattlePokemon(attacker);
        var opponent = BattleUiState.activeBattlePokemon(target.uuid());
        if (own == null || opponent == null) return Optional.empty();
        Pair key = new Pair(attacker, target.uuid());
        Context context = CONTEXTS.get(key);
        if (context == null) {
            if (CONTEXTS.size() >= 24) CONTEXTS.remove(CONTEXTS.keySet().iterator().next());
            context = new Context();
            CONTEXTS.put(key, context);
        }
        DamageCalcState state = context.state;
        state.attacker = BattleCalculationInputs.refresh(state.attacker, own, true, move);
        state.defender = BattleCalculationInputs.refresh(state.defender, opponent,
                BattleUiState.canUsePrivateData(opponent), null);
        if (state.attacker == null || state.defender == null) return Optional.empty();
        if (randomBattle) {
            BattleRandomSets.applyInference(state.attacker);
            BattleRandomSets.applyInference(state.defender);
        } else {
            // Normal battles use only the Tropimon usage API. This is a local copy of
            // the suggestion client, not a runtime dependency on Damage Calc.
            String usageFormat = TropimonRankedUsageService.formatForPokemonPerSide(
                    battle.getBattleFormat().getBattleType().getPokemonPerSide());
            TropimonRankedUsageService.INSTANCE.enrichOpponent(state.attacker, usageFormat);
            TropimonRankedUsageService.INSTANCE.enrichOpponent(state.defender, usageFormat);
            applyEstimatedStats(state.attacker);
            applyEstimatedStats(state.defender);
            // Never substitute the Random Battle 85-EV/neutral profile while the
            // normal-battle API profile is loading, missing or incomplete.
            if (!normalStatsReady(state.attacker) || !normalStatsReady(state.defender))
                return Optional.empty();
        }
        boolean neutralizingGas = BattleMoveDynamics.neutralizingGasActive();
        state.attacker.ability = BattleMoveDynamics.abilityUnderGas(state.attacker.ability,
                state.attacker.item, neutralizingGas);
        state.defender.ability = BattleMoveDynamics.abilityUnderGas(state.defender.ability,
                state.defender.item, neutralizingGas);
        BattleCalculationInputs.refreshField(state.field, battle, own, opponent, move);
        int slot = -1;
        for (int i = 0; i < 4; i++) if (state.attacker.moveAt(i) != null
                && state.attacker.moveAt(i).id().equals(BattleCalcDex.normalize(move.getName()))) { slot = i; break; }
        if (slot < 0) return Optional.empty();
        // Actual immutable value snapshots validate the cache, not tick/log revision.
        DamageResult result = state.calculateMove(true, slot);
        if (result == null) return Optional.empty();
        MoveData effective = DamageCalculator.resolveMove(state.attacker, state.defender,
                DamageCalcState.effectiveMove(state.attacker, slot), state.field, new ArrayList<>());
        EstimatedProfile estimatedProfile = estimatedProfile(state.defender, effective);
        CachedEstimate cached = context.estimates.get(slot);
        if (cached != null && cached.result.equals(result) && cached.target.equals(target.name())
                && java.util.Objects.equals(cached.estimate.estimatedProfile(), estimatedProfile))
            return Optional.of(cached.estimate);
        var notes = new ArrayList<String>();
        PokeType type = DamageCalculator.effectiveMoveType(state.attacker, effective, state.field, notes);
        double multiplier = DamageCalculator.typeEffectiveness(effective, type, state.defender, state.attacker, state.field, notes);
        int power = DamageCalculator.effectivePower(state.attacker, state.defender, effective, type,
                state.field, state.field.attackerSide, state.field.defenderSide, notes, new ArrayList<>());
        DamageEstimate estimate = new DamageEstimate(result.minPercent(), result.maxPercent(), multiplier,
                result.koChance(), result.notes(), !result.warnings().isEmpty(),
                estimatedProfile, unknownItem(state.attacker) || unknownItem(state.defender), power,
                type.name().toLowerCase(java.util.Locale.ROOT), target.name());
        context.estimates.put(slot, new CachedEstimate(result, target.name(), estimate));
        return Optional.of(estimate);
    }
    static boolean available() { return true; }
    static void reset() {
        CONTEXTS.clear();
        battleId = null;
        randomMode = null;
        DamageCalculationCache.clearShared();
    }
    private record Pair(UUID attacker, UUID target) { }
    private static final class Context {
        final DamageCalcState state = new DamageCalcState();
        final Map<Integer, CachedEstimate> estimates = new LinkedHashMap<>();
    }
    private record CachedEstimate(DamageResult result, String target, DamageEstimate estimate) { }
    private TropimonDamageCalcBridge() { }

    private static boolean unknownItem(PokemonSet pokemon) {
        return !pokemon.itemKnown && !pokemon.rankedItemSuggested;
    }

    /** Normal-battle estimate: perfect IVs plus only a completed Tropimon API spread. */
    static void applyEstimatedStats(PokemonSet pokemon) {
        if (pokemon == null || pokemon.statsKnown) return;
        for (Stat stat : Stat.values()) pokemon.ivs.put(stat, 31);
        if (pokemon.rankedEvsSuggested) {
            int hpEv = inferHpEv31(pokemon);
            if (hpEv >= 0) pokemon.evs.put(Stat.HP, hpEv);
        }
    }

    static boolean normalStatsReady(PokemonSet pokemon) {
        return pokemon != null && (pokemon.natureKnown || pokemon.rankedNatureSuggested)
                && (pokemon.statsKnown || pokemon.rankedEvsSuggested);
    }

    static int inferHpEv31(PokemonSet pokemon) {
        if (pokemon == null || pokemon.statsKnown || pokemon.species == null || pokemon.observedMaxHp <= 1) return -1;
        Integer base = pokemon.species.baseStats().get(Stat.HP);
        if (base == null || base <= 1) return -1;
        int preferred = Math.max(0, Math.min(252, pokemon.evs.getOrDefault(Stat.HP, 0)));
        int otherEvs = 0;
        for (Stat stat : Stat.values()) if (stat != Stat.HP) otherEvs += Math.max(0, pokemon.evs.getOrDefault(stat, 0));
        int legalMaximum = Math.min(252, Math.max(0, 510 - otherEvs));
        int best = -1, bestDistance = Integer.MAX_VALUE;
        for (int ev = 0; ev <= legalMaximum; ev += 4) {
            int hp = (int) Math.floor(((2 * base + 31 + Math.floor(ev / 4.0)) * pokemon.level) / 100.0)
                    + pokemon.level + 10;
            if (hp != pokemon.observedMaxHp) continue;
            int distance = Math.abs(ev - preferred);
            if (distance < bestDistance) { best = ev; bestDistance = distance; }
        }
        return best;
    }

    static EstimatedProfile estimatedProfile(PokemonSet defender, MoveData move) {
        if (defender == null || defender.statsKnown || move == null || move.category() == DamageCategory.STATUS)
            return null;
        Stat defense = move.category() == DamageCategory.PHYSICAL ? Stat.DEF : Stat.SPD;
        if (java.util.Set.of("psyshock", "psystrike", "secretsword").contains(move.id())) defense = Stat.DEF;
        String nature = defender.nature == null ? "serious" : defender.nature.id();
        return new EstimatedProfile(nature, defender.evs.getOrDefault(Stat.HP, 0), defense,
                defender.evs.getOrDefault(defense, 0));
    }

    record DamageEstimate(double minPercent, double maxPercent, double effectiveness,
                          String koChance, List<String> notes, boolean approximate,
                          EstimatedProfile estimatedProfile, boolean unknownItemAssumption,
                          int effectivePower, String effectiveType,
                          String targetName) {
        DamageEstimate {
            koChance = koChance == null ? "" : koChance;
            notes = notes == null ? List.of() : List.copyOf(notes);
            targetName = targetName == null ? "" : targetName;
            effectiveType = effectiveType == null ? "" : effectiveType;
            effectivePower = Math.max(0, effectivePower);
        }
    }

    record EstimatedProfile(String natureId, int hpEv, Stat defenseStat, int defenseEv) {
        EstimatedProfile {
            natureId = natureId == null || natureId.isBlank() ? "serious" : natureId;
            hpEv = Math.max(0, Math.min(252, hpEv));
            defenseEv = Math.max(0, Math.min(252, defenseEv));
            defenseStat = defenseStat == Stat.DEF ? Stat.DEF : Stat.SPD;
        }
    }

}
