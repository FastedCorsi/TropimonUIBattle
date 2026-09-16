package fr.tropimon.battleui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UUID-based observations owned by UI Battle; never reads another calculator's state. */
final class BattleCalculationHistory {
    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Map<UUID, Integer> FAINTS = new HashMap<>();
    private static final Map<UUID, BattleFieldEffects.EffectSide> SIDES = new HashMap<>();
    private static final BattleAppearanceTracker<History> APPEARANCES = new BattleAppearanceTracker<>();
    private static final Map<Object, UUID> SLOTS = new java.util.IdentityHashMap<>();
    private static final Map<BattleFieldEffects.EffectSide, Map<Guard, Integer>> GUARDS = new HashMap<>();
    private static final Map<BattleFieldEffects.EffectSide, Redirect> REDIRECTS = new HashMap<>();
    private static final Map<BattleFieldEffects.EffectSide, Echo> ECHOES = new HashMap<>();
    private static UUID lastAttacker, lastDamaged;
    private static DamageCategory lastCategory = DamageCategory.STATUS;
    private static int pendingHits, turn, moveSequence;
    private static boolean directDamagePending;

    static void reset() {
        HISTORY.clear(); FAINTS.clear(); SIDES.clear(); APPEARANCES.clear(); SLOTS.clear(); GUARDS.clear();
        REDIRECTS.clear();
        ECHOES.clear();
        lastAttacker = lastDamaged = null; lastCategory = DamageCategory.STATUS;
        pendingHits = turn = moveSequence = 0; directDamagePending = false;
    }
    static void observe(Object slot, UUID id, BattleFieldEffects.EffectSide side) {
        SIDES.put(id, side);
        if (id.equals(SLOTS.put(slot, id))) return;
        APPEARANCES.observe(slot, id, () -> HISTORY.containsKey(id) ? HISTORY.get(id).copy() : null);
        History history = HISTORY.computeIfAbsent(id, ignored -> new History());
        history.entered = Math.max(0, turn);
        history.lastMove = ""; history.consecutive = 0;
        history.defenseCurl = history.failed = history.flashFire = history.paradox = false;
    }
    static void leave(Object slot) { SLOTS.remove(slot); APPEARANCES.leave(slot); }
    static void reveal(Object slot, UUID apparent, UUID actual) {
        var appearance = APPEARANCES.reveal(slot, apparent, actual);
        if (appearance == null) return;
        History disguised = HISTORY.remove(apparent);
        if (appearance.before() != null) HISTORY.put(apparent, appearance.before());
        if (disguised != null) {
            History real = HISTORY.get(actual);
            // Lifetime hits belong to the real Pokémon; only the hits accumulated
            // during this appearance move off the decoy's pre-entry baseline.
            disguised.hits = (real == null ? 0 : real.hits) + Math.max(0,
                    disguised.hits - (appearance.before() == null ? 0 : appearance.before().hits));
            HISTORY.put(actual, disguised);
        }
        SLOTS.put(slot, actual);
        APPEARANCES.begin(slot, actual, disguised == null ? null : disguised.copy());
        SIDES.put(actual, SIDES.get(apparent));
        Integer fainted = FAINTS.remove(apparent);
        if (fainted != null) FAINTS.put(actual, fainted);
        if (apparent.equals(lastAttacker)) lastAttacker = actual;
        if (apparent.equals(lastDamaged)) lastDamaged = actual;
    }
    static void move(UUID id, String move, String category, int currentTurn) {
        if (id == null) return;
        advanceTurn(currentTurn);
        History h = HISTORY.computeIfAbsent(id, ignored -> new History());
        String normalized = BattleCalcDex.normalize(move);
        boolean same = normalized.equals(h.lastMove);
        h.consecutive = same ? h.consecutive + 1 : 1;
        if (normalized.equals("echoedvoice")) {
            var side = SIDES.getOrDefault(id, BattleFieldEffects.EffectSide.FIELD);
            Echo echo = ECHOES.get(side);
            if (echo == null || echo.turn != turn) ECHOES.put(side,
                    new Echo(turn, echo != null && echo.turn == turn - 1 ? Math.min(5, echo.count + 1) : 1));
        }
        h.lastMove = normalized; h.failed = false;
        if (normalized.equals("defensecurl")) h.defenseCurl = true;
        Guard guard = Guard.fromMove(normalized);
        if (guard != null) markGuard(SIDES.getOrDefault(id, BattleFieldEffects.EffectSide.FIELD), guard);
        if (normalized.equals("followme") || normalized.equals("ragepowder")) {
            REDIRECTS.put(SIDES.getOrDefault(id, BattleFieldEffects.EffectSide.FIELD),
                    new Redirect(id, normalized, turn));
        }
        lastAttacker = id; lastCategory = DamageCategory.valueOf(category.toUpperCase(java.util.Locale.ROOT));
        moveSequence++;
        directDamagePending = lastCategory != DamageCategory.STATUS;
        pendingHits = 0; lastDamaged = null;
    }
    static void accept(UUID id, String key, Object[] args, int currentTurn) {
        advanceTurn(currentTurn);
        if (key == null) return;
        if (key.endsWith("hit_count") || key.endsWith("hit_count_singular")) {
            History h = HISTORY.get(lastDamaged);
            int hits = key.endsWith("hit_count_singular") ? 1 : number(args, 0, 1);
            if (h != null) h.hits += Math.max(0, hits - pendingHits);
            pendingHits = 0; lastDamaged = null;
            directDamagePending = false;
        }
        if (key.contains(".fail") || key.contains(".miss") || key.endsWith(".immune") || key.contains(".cant.")) {
            History attacker = HISTORY.get(lastAttacker);
            if (attacker != null) {
                attacker.failed = true;
                if (Set.of("furycutter", "rollout", "iceball").contains(attacker.lastMove)) attacker.consecutive = 0;
            }
            directDamagePending = false;
        }
        if (breaksDirectDamageContext(key)) directDamagePending = false;
        if (id == null) return;
        History h = HISTORY.computeIfAbsent(id, ignored -> new History());
        if (key.equals("cobblemon.battle.damage_dealt")) {
            // Cobblemon announces residual/contact/recoil causes before their
            // generic damage packet. Only a live damaging-move context may feed
            // Counter/Mirror Coat and hit-history inputs, and never self-damage.
            if (!directDamagePending || lastAttacker == null || id.equals(lastAttacker)) return;
            int damage = Math.max(0, number(args, 1, 0));
            h.damage = h.damageTurn == turn && h.damageSequence == moveSequence ? h.damage + damage : damage;
            h.damageTurn = turn; h.damageSource = lastAttacker; h.damageCategory = lastCategory;
            h.damageSequence = moveSequence;
            h.hits++; pendingHits = id.equals(lastDamaged) ? pendingHits + 1 : 1; lastDamaged = id;
        }
        if (key.endsWith(".faint") || key.endsWith(".fainted")) faint(id, turn);
        if (key.contains("flashfire")) h.flashFire = !key.contains(".end.");
        if (key.contains("protosynthesis") || key.contains("quarkdrive")) h.paradox = !key.contains(".end.") && !key.endsWith(".end");
        Guard guard = Guard.fromEvent(key);
        if (guard != null) markGuard(SIDES.getOrDefault(id, BattleFieldEffects.EffectSide.FIELD), guard);
        if (key.endsWith("followme") || key.endsWith("ragepowder")) {
            String move = key.endsWith("followme") ? "followme" : "ragepowder";
            REDIRECTS.put(SIDES.getOrDefault(id, BattleFieldEffects.EffectSide.FIELD), new Redirect(id, move, turn));
        }
    }
    static void apply(UUID id, PokemonSet pokemon) {
        History h = HISTORY.get(id);
        pokemon.battleHistoryKnown = h != null;
        pokemon.timesHit = h == null ? 0 : h.hits;
        pokemon.lastMoveId = h == null ? "" : h.lastMove;
        pokemon.consecutiveMoveUses = h == null ? 0 : h.consecutive;
        Echo echo = ECHOES.get(SIDES.get(id));
        pokemon.echoedVoiceChain = echo != null && echo.turn >= turn - 1 ? echo.count : 0;
        pokemon.defenseCurlUsed = h != null && h.defenseCurl;
        pokemon.switchedInThisTurn = h != null && h.entered == turn;
        pokemon.lastMoveFailed = h != null && h.failed;
        pokemon.flashFireActive = h != null && h.flashFire;
        pokemon.paradoxBoostActive = h != null && h.paradox;
        pokemon.turnsActive = h == null ? -1 : Math.max(0, turn - h.entered);
        pokemon.lastDamageTaken = h == null || h.damageTurn != turn ? 0 : h.damage;
        pokemon.lastDamageCategory = h == null || h.damageTurn != turn ? DamageCategory.STATUS : h.damageCategory;
        pokemon.faintedAllies = 0; pokemon.allyFaintedPreviousTurn = false;
        for (var faint : FAINTS.entrySet()) if (!id.equals(faint.getKey()) && SIDES.get(id) == SIDES.get(faint.getKey())) {
            pokemon.faintedAllies++;
            if (faint.getValue() == turn - 1) pokemon.allyFaintedPreviousTurn = true;
        }
    }
    static boolean guard(BattleFieldEffects.EffectSide side, Guard guard) {
        return GUARDS.getOrDefault(side, Map.of()).getOrDefault(guard, -1) == turn;
    }
    static boolean wideGuard(BattleFieldEffects.EffectSide side) { return guard(side, Guard.WIDE); }
    static Redirect redirect(BattleFieldEffects.EffectSide side) {
        Redirect value = REDIRECTS.get(side);
        return value != null && value.turn == turn ? value : null;
    }
    static void faint(UUID id, int currentTurn) {
        if (id == null) return;
        advanceTurn(currentTurn);
        FAINTS.putIfAbsent(id, turn);
    }
    private static void markGuard(BattleFieldEffects.EffectSide side, Guard guard) {
        GUARDS.computeIfAbsent(side, ignored -> new java.util.EnumMap<>(Guard.class)).put(guard, turn);
    }
    private static void advanceTurn(int value) {
        if (turn == value) return;
        turn = value;
        lastAttacker = lastDamaged = null;
        lastCategory = DamageCategory.STATUS;
        pendingHits = 0;
        directDamagePending = false;
    }

    private static boolean breaksDirectDamageContext(String key) {
        return key.startsWith("cobblemon.battle.damage.")
                || key.startsWith("cobblemon.status.")
                || key.startsWith("cobblemon.battle.heal.")
                || key.contains(".weather.");
    }
    private record Echo(int turn, int count) { }
    enum Guard {
        WIDE("wideguard"), QUICK("quickguard"), MAT_BLOCK("matblock"), CRAFTY("craftyshield");
        private final String id;
        Guard(String id) { this.id = id; }
        static Guard fromMove(String move) {
            for (Guard guard : values()) if (guard.id.equals(move)) return guard;
            return null;
        }
        static Guard fromEvent(String key) {
            if (key == null) return null;
            for (Guard guard : values()) if (key.endsWith(guard.id)) return guard;
            return null;
        }
    }
    record Redirect(UUID pokemon, String move, int turn) { }
    private static int number(Object[] args, int index, int fallback) {
        if (index >= args.length) return fallback;
        if (args[index] instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(args[index])); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static final class History {
        int hits, consecutive, entered, damage, damageTurn = -1, damageSequence = -1;
        String lastMove = "";
        UUID damageSource;
        DamageCategory damageCategory = DamageCategory.STATUS;
        boolean defenseCurl, failed, flashFire, paradox;
        History copy() {
            History h = new History();
            h.hits = hits; h.consecutive = consecutive; h.entered = entered;
            h.damage = damage; h.damageTurn = damageTurn; h.damageSequence = damageSequence;
            h.lastMove = lastMove; h.damageSource = damageSource;
            h.damageCategory = damageCategory; h.defenseCurl = defenseCurl; h.failed = failed; h.flashFire = flashFire; h.paradox = paradox;
            return h;
        }
    }
    private BattleCalculationHistory() { }
}
