package fr.tropimon.battleui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.util.Language;

enum PokeType {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND, FLYING,
    PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY, NONE;

    static PokeType byName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        String normalized = name.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (PokeType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return NONE;
    }

    String displayName() {
        if (this == NONE) {
            return "-";
        }
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

enum DamageCategory {
    PHYSICAL, SPECIAL, STATUS
}

enum Stat {
    HP, ATK, DEF, SPA, SPD, SPE
}

enum Weather {
    NONE("None"), SUN("Sun"), HARSH_SUN("Harsh Sun"), RAIN("Rain"), HEAVY_RAIN("Heavy Rain"),
    SAND("Sand"), SNOW("Snow"), STRONG_WINDS("Strong Winds");

    final String label;

    Weather(String label) {
        this.label = label;
    }
}

enum Terrain {
    NONE("None"), ELECTRIC("Electric"), GRASSY("Grassy"), MISTY("Misty"), PSYCHIC("Psychic");

    final String label;

    Terrain(String label) {
        this.label = label;
    }
}

enum StatusCondition {
    NONE("Statut"), BURN("BRN"), POISON("PSN"), PARALYSIS("PAR"), SLEEP("SLP"), FREEZE("FRZ");

    final String label;

    StatusCondition(String label) {
        this.label = label;
    }
}

record SpeciesData(String id, String name, PokeType primaryType, PokeType secondaryType, EnumMap<Stat, Integer> baseStats,
                   boolean notFullyEvolved, String texturePath, String cobblemonSpeciesId, List<String> aspects,
                   double weightKg) {
    SpeciesData(String id, String name, PokeType primaryType, PokeType secondaryType, EnumMap<Stat, Integer> baseStats,
                boolean notFullyEvolved, String texturePath) {
        this(id, name, primaryType, secondaryType, baseStats, notFullyEvolved, texturePath, id, List.of(), 0.0);
    }

    SpeciesData(String id, String name, PokeType primaryType, PokeType secondaryType, EnumMap<Stat, Integer> baseStats,
                boolean notFullyEvolved, String texturePath, String cobblemonSpeciesId, List<String> aspects) {
        this(id, name, primaryType, secondaryType, baseStats, notFullyEvolved, texturePath,
                cobblemonSpeciesId, aspects, 0.0);
    }

    List<PokeType> types() {
        ArrayList<PokeType> types = new ArrayList<>();
        if (primaryType != PokeType.NONE) {
            types.add(primaryType);
        }
        if (secondaryType != PokeType.NONE && secondaryType != primaryType) {
            types.add(secondaryType);
        }
        return types;
    }
}

record MoveData(String id, String name, PokeType type, DamageCategory category, int basePower, boolean spreadMove,
                boolean contact, Set<String> flags, int priority) {
    MoveData(String id, String name, PokeType type, DamageCategory category, int basePower, boolean spreadMove,
             boolean contact) {
        this(id, name, type, category, basePower, spreadMove, contact,
                contact ? Set.of("contact") : Set.of(), 0);
    }

    MoveData(String id, String name, PokeType type, DamageCategory category, int basePower, boolean spreadMove,
             boolean contact, Set<String> flags) {
        this(id, name, type, category, basePower, spreadMove, contact, flags, 0);
    }

    MoveData {
        LinkedHashSet<String> normalizedFlags = new LinkedHashSet<>();
        if (flags != null) {
            for (String flag : flags) {
                String normalized = BattleCalcDex.normalize(flag);
                if (!normalized.isBlank()) normalizedFlags.add(normalized);
            }
        }
        if (contact) normalizedFlags.add("contact");
        flags = Set.copyOf(normalizedFlags);
        contact = normalizedFlags.contains("contact");
    }

    boolean hasFlag(String flag) {
        return flags.contains(BattleCalcDex.normalize(flag));
    }
}

record NatureData(String id, String name, Stat plus, Stat minus) {
    double modifier(Stat stat) {
        if (stat == Stat.HP) {
            return 1.0;
        }
        if (stat == plus) {
            return 1.1;
        }
        if (stat == minus) {
            return 0.9;
        }
        return 1.0;
    }
}

final class PokemonSet {
    SpeciesData species;
    String battleId = "";
    String battleName = "";
    boolean battleFormObserved;
    int level = 100;
    String item = "None";
    String ability = "None";
    NatureData nature = BattleCalcDex.nature("serious");
    boolean itemKnown = true;
    boolean abilityKnown = true;
    boolean natureKnown = true;
    boolean statsKnown = true;
    boolean movesKnown = true;
    PokeType teraType = PokeType.NONE;
    boolean terastallized;
    StatusCondition status = StatusCondition.NONE;
    int currentHp = -1;
    int observedMaxHp = -1;
    float hpObservation;
    final List<Integer> movePp = new ArrayList<>();
    List<String> runtimeEffects = List.of();
    final EnumMap<Stat, Integer> evs = new EnumMap<>(Stat.class);
    final EnumMap<Stat, Integer> ivs = new EnumMap<>(Stat.class);
    final EnumMap<Stat, Integer> boosts = new EnumMap<>(Stat.class);
    final List<MoveData> moves = new ArrayList<>();
    final Map<String, Double> rankedMoveUsage = new LinkedHashMap<>();
    final Set<String> observedMoveIds = new LinkedHashSet<>();
    final Set<String> manualMoveIds = new LinkedHashSet<>();
    final Set<String> suppressedMoveIds = new LinkedHashSet<>();
    String rankedProfileKey = "";
    boolean rankedItemSuggested;
    boolean rankedAbilitySuggested;
    boolean rankedNatureSuggested;
    boolean rankedEvsSuggested;
    final boolean[] zMoves = new boolean[4];
    boolean battleHistoryKnown;
    int timesHit;
    int faintedAllies;
    String lastMoveId = "";
    int consecutiveMoveUses;
    int echoedVoiceChain;
    boolean defenseCurlUsed;
    boolean switchedInThisTurn;
    boolean allyFaintedPreviousTurn;
    boolean lastMoveFailed;
    boolean flashFireActive;
    boolean paradoxBoostActive;
    int turnsActive = -1;
    int lastDamageTaken;
    DamageCategory lastDamageCategory = DamageCategory.STATUS;

    PokemonSet(SpeciesData species) {
        this.species = species;
        for (Stat stat : Stat.values()) {
            evs.put(stat, 0);
            ivs.put(stat, 31);
            boosts.put(stat, 0);
        }
        ability = BattleCalcDex.defaultAbility(species);
        moves.addAll(BattleCalcDex.defaultMoves(species));
    }

    PokemonSet copy() {
        PokemonSet copy = new PokemonSet(species);
        copy.battleId = battleId;
        copy.battleName = battleName;
        copy.battleFormObserved = battleFormObserved;
        copy.level = level;
        copy.item = item;
        copy.ability = ability;
        copy.nature = nature;
        copy.itemKnown = itemKnown;
        copy.abilityKnown = abilityKnown;
        copy.natureKnown = natureKnown;
        copy.statsKnown = statsKnown;
        copy.movesKnown = movesKnown;
        copy.teraType = teraType;
        copy.terastallized = terastallized;
        copy.status = status;
        copy.currentHp = currentHp;
        copy.observedMaxHp = observedMaxHp;
        copy.hpObservation = hpObservation;
        copy.movePp.addAll(movePp);
        copy.runtimeEffects = List.copyOf(runtimeEffects);
        copy.evs.clear();
        copy.evs.putAll(evs);
        copy.ivs.clear();
        copy.ivs.putAll(ivs);
        copy.boosts.clear();
        copy.boosts.putAll(boosts);
        copy.moves.clear();
        copy.moves.addAll(moves);
        copy.rankedMoveUsage.clear();
        copy.rankedMoveUsage.putAll(rankedMoveUsage);
        copy.observedMoveIds.clear();
        copy.observedMoveIds.addAll(observedMoveIds);
        copy.manualMoveIds.clear();
        copy.manualMoveIds.addAll(manualMoveIds);
        copy.suppressedMoveIds.clear();
        copy.suppressedMoveIds.addAll(suppressedMoveIds);
        copy.rankedProfileKey = rankedProfileKey;
        copy.rankedItemSuggested = rankedItemSuggested;
        copy.rankedAbilitySuggested = rankedAbilitySuggested;
        copy.rankedNatureSuggested = rankedNatureSuggested;
        copy.rankedEvsSuggested = rankedEvsSuggested;
        System.arraycopy(zMoves, 0, copy.zMoves, 0, zMoves.length);
        copy.battleHistoryKnown = battleHistoryKnown;
        copy.timesHit = timesHit;
        copy.faintedAllies = faintedAllies;
        copy.lastMoveId = lastMoveId;
        copy.consecutiveMoveUses = consecutiveMoveUses;
        copy.echoedVoiceChain = echoedVoiceChain;
        copy.defenseCurlUsed = defenseCurlUsed;
        copy.switchedInThisTurn = switchedInThisTurn;
        copy.allyFaintedPreviousTurn = allyFaintedPreviousTurn;
        copy.lastMoveFailed = lastMoveFailed;
        copy.flashFireActive = flashFireActive;
        copy.paradoxBoostActive = paradoxBoostActive;
        copy.turnsActive = turnsActive;
        copy.lastDamageTaken = lastDamageTaken;
        copy.lastDamageCategory = lastDamageCategory;
        return copy;
    }

    MoveData moveAt(int slot) {
        return slot >= 0 && slot < moves.size() ? moves.get(slot) : null;
    }

    void setMove(int slot, MoveData move) {
        if (slot < 0) {
            return;
        }
        while (moves.size() <= slot) {
            moves.add(null);
        }
        MoveData previous = moves.get(slot);
        if (previous != null && (move == null || !previous.id().equals(move.id()))) {
            manualMoveIds.remove(previous.id());
            if (rankedMoveUsage.containsKey(previous.id()) && !observedMoveIds.contains(previous.id())) {
                suppressedMoveIds.add(previous.id());
            }
        }
        moves.set(slot, move);
        movesKnown = true;
        if (move != null) {
            manualMoveIds.add(move.id());
            suppressedMoveIds.remove(move.id());
        }
        if (slot < zMoves.length) {
            zMoves[slot] = false;
        }
    }

    void deleteMove(int slot) {
        if (slot >= 0 && slot < moves.size()) {
            MoveData removed = moves.get(slot);
            if (removed != null) {
                observedMoveIds.remove(removed.id());
                manualMoveIds.remove(removed.id());
                suppressedMoveIds.add(removed.id());
            }
            moves.set(slot, null);
        }
        if (slot >= 0 && slot < zMoves.length) {
            zMoves[slot] = false;
        }
    }

    boolean zMoveAt(int slot) {
        return slot >= 0 && slot < zMoves.length && zMoves[slot];
    }

    void toggleZMove(int slot) {
        if (slot >= 0 && slot < zMoves.length && moveAt(slot) != null) {
            zMoves[slot] = !zMoves[slot];
        }
    }

    void clearBattleContext() {
        battleId = "";
        battleName = "";
        currentHp = -1;
        observedMaxHp = -1;
        status = StatusCondition.NONE;
        terastallized = false;
        teraType = PokeType.NONE;
        boosts.replaceAll((stat, value) -> 0);
        battleHistoryKnown = false;
        timesHit = 0;
        faintedAllies = 0;
        lastMoveId = "";
        consecutiveMoveUses = 0;
        echoedVoiceChain = 0;
        defenseCurlUsed = false;
        switchedInThisTurn = false;
        allyFaintedPreviousTurn = false;
        lastMoveFailed = false;
        flashFireActive = false;
        paradoxBoostActive = false;
        turnsActive = -1;
        lastDamageTaken = 0;
        lastDamageCategory = DamageCategory.STATUS;
    }

    int maxHp() {
        return observedMaxHp > 0 ? observedMaxHp : calculatedMaxHp();
    }

    int calculatedMaxHp() {
        if (species != null && species.baseStats().getOrDefault(Stat.HP, 0) == 1) return 1;
        return DamageCalculator.stat(this, Stat.HP, false);
    }

    int visibleHp() {
        return currentHp >= 0 ? Math.min(currentHp, maxHp()) : maxHp();
    }

    String evSummary() {
        return evs.get(Stat.HP) + " HP / " + evs.get(Stat.ATK) + " Atk / " + evs.get(Stat.DEF) + " Def / "
                + evs.get(Stat.SPA) + " SpA / " + evs.get(Stat.SPD) + " SpD / " + evs.get(Stat.SPE) + " Spe";
    }

    List<PokeType> defensiveTypes() {
        if (terastallized && teraType != PokeType.NONE) {
            return List.of(teraType);
        }
        return species.types();
    }
}

final class FieldState {
    Weather weather = Weather.NONE;
    Terrain terrain = Terrain.NONE;
    boolean doubles;
    boolean alliedTarget;
    boolean criticalHit;
    boolean trickRoom;
    boolean wonderRoom;
    boolean gravity;
    boolean helpingHand;
    boolean friendGuard;

    final SideConditions attackerSide = new SideConditions();
    final SideConditions defenderSide = new SideConditions();

    // Legacy global side conditions are kept for direct calculator tests and older callers.
    boolean reflect;
    boolean lightScreen;
    boolean auroraVeil;
    boolean tailwind;

    SideConditions legacySideConditions() {
        SideConditions side = new SideConditions();
        side.reflect = reflect;
        side.lightScreen = lightScreen;
        side.auroraVeil = auroraVeil;
        side.tailwind = tailwind;
        return side;
    }

    void swapSides() {
        attackerSide.swapWith(defenderSide);
    }
}

final class SideConditions {
    boolean reflect;
    boolean lightScreen;
    boolean auroraVeil;
    boolean tailwind;
    boolean helpingHand;
    boolean friendGuard;
    boolean wideGuard;
    boolean quickGuard;
    boolean matBlock;
    boolean craftyShield;
    String partnerAbility = "None";
    List<String> partnerAbilities = List.of();
    String partnerName = "";
    int spreadTargets = 2;

    boolean hasAny() {
        return reflect || lightScreen || auroraVeil || tailwind || helpingHand || friendGuard || wideGuard
                || quickGuard || matBlock || craftyShield
                || !partnerAbilities.isEmpty() || !"none".equals(BattleCalcDex.normalize(partnerAbility));
    }

    String summary() {
        ArrayList<String> parts = new ArrayList<>();
        if (reflect) parts.add("Reflect");
        if (lightScreen) parts.add("LS");
        if (auroraVeil) parts.add("Veil");
        if (tailwind) parts.add("Tailwind");
        if (helpingHand) parts.add("HH");
        if (friendGuard) parts.add("FG");
        if (wideGuard) parts.add("Wide Guard");
        if (quickGuard) parts.add("Quick Guard");
        if (matBlock) parts.add("Mat Block");
        if (craftyShield) parts.add("Crafty Shield");
        if (!partnerAbilities.isEmpty()) parts.add("Partners: " + String.join(", ", partnerAbilities));
        else if (!"none".equals(BattleCalcDex.normalize(partnerAbility))) parts.add("Partner: " + partnerAbility);
        if (spreadTargets != 2) parts.add("Targets: " + spreadTargets);
        return String.join(" / ", parts);
    }

    void swapWith(SideConditions other) {
        boolean previousReflect = reflect;
        boolean previousLightScreen = lightScreen;
        boolean previousAuroraVeil = auroraVeil;
        boolean previousTailwind = tailwind;
        boolean previousHelpingHand = helpingHand;
        boolean previousFriendGuard = friendGuard;
        boolean previousWideGuard = wideGuard;
        boolean previousQuickGuard = quickGuard;
        boolean previousMatBlock = matBlock;
        boolean previousCraftyShield = craftyShield;
        String previousPartnerAbility = partnerAbility;
        List<String> previousPartnerAbilities = partnerAbilities;
        String previousPartnerName = partnerName;
        int previousSpreadTargets = spreadTargets;

        reflect = other.reflect;
        lightScreen = other.lightScreen;
        auroraVeil = other.auroraVeil;
        tailwind = other.tailwind;
        helpingHand = other.helpingHand;
        friendGuard = other.friendGuard;
        wideGuard = other.wideGuard;
        quickGuard = other.quickGuard;
        matBlock = other.matBlock;
        craftyShield = other.craftyShield;
        partnerAbility = other.partnerAbility;
        partnerAbilities = other.partnerAbilities;
        partnerName = other.partnerName;
        spreadTargets = other.spreadTargets;

        other.reflect = previousReflect;
        other.lightScreen = previousLightScreen;
        other.auroraVeil = previousAuroraVeil;
        other.tailwind = previousTailwind;
        other.helpingHand = previousHelpingHand;
        other.friendGuard = previousFriendGuard;
        other.wideGuard = previousWideGuard;
        other.quickGuard = previousQuickGuard;
        other.matBlock = previousMatBlock;
        other.craftyShield = previousCraftyShield;
        other.partnerAbility = previousPartnerAbility;
        other.partnerAbilities = previousPartnerAbilities;
        other.partnerName = previousPartnerName;
        other.spreadTargets = previousSpreadTargets;
    }
}

record CalcWarning(String key, List<String> arguments) {
    CalcWarning {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    static CalcWarning of(String key, String... arguments) {
        return new CalcWarning(key, arguments == null ? List.of() : List.of(arguments));
    }
}

record DamageResult(
        MoveData move,
        int minDamage,
        int maxDamage,
        double minPercent,
        double maxPercent,
        List<Integer> rolls,
        int entryDamage,
        String koChance,
        String showdownLine,
        String shortLine,
        List<String> notes,
        List<CalcWarning> warnings
) {
    static DamageResult zero(MoveData move, String reason) {
        return zero(move, reason, List.of());
    }

    static DamageResult zero(MoveData move, String reason, List<CalcWarning> warnings) {
        return new DamageResult(move, 0, 0, 0, 0, List.of(), 0, "no damage", move.name() + ": 0 damage -- " + reason,
                move.name() + " 0% | " + reason, List.of(reason), List.copyOf(warnings));
    }
}

final class DamageCalcState {
    private static final Stat[] STATS = Stat.values();
    PokemonSet attacker;
    PokemonSet defender;
    FieldState field = new FieldState();
    private final DamageCalculationCache damageCache = new DamageCalculationCache();

    DamageCalcState() { }
    DamageCalcState(PokemonSet attacker, PokemonSet defender, FieldState field) {
        this.attacker = attacker; this.defender = defender; this.field = field;
    }
    DamageResult calculateMove(boolean fromAttacker, int slot) {
        if (slot < 0 || slot >= 4) {
            return null;
        }
        prepareCalculations();
        return calculatePreparedMove(fromAttacker, slot);
    }

    // One validation per UI frame; callers must not mutate state between preparation and these reads.
    void prepareCalculations() { damageCache.prepare(this); }

    DamageResult calculatePreparedMove(boolean fromAttacker, int slot) {
        return damageCache.calculatePrepared(this, fromAttacker, slot);
    }

    int preparedStat(boolean fromAttacker, Stat stat) { return damageCache.statPrepared(this, fromAttacker, stat); }

    long calculationFingerprint() {
        long hash = pokemonFingerprint(attacker);
        hash = mix(hash, pokemonFingerprint(defender));
        hash = mix(hash, field.weather.ordinal());
        hash = mix(hash, field.terrain.ordinal());
        hash = mix(hash, field.doubles ? 1 : 0);
        hash = mix(hash, field.alliedTarget ? 1 : 0);
        hash = mix(hash, field.criticalHit ? 1 : 0);
        hash = mix(hash, field.trickRoom ? 1 : 0);
        hash = mix(hash, field.wonderRoom ? 1 : 0);
        hash = mix(hash, field.gravity ? 1 : 0);
        hash = mix(hash, field.helpingHand ? 1 : 0);
        hash = mix(hash, field.friendGuard ? 1 : 0);
        hash = mix(hash, field.reflect ? 1 : 0);
        hash = mix(hash, field.lightScreen ? 1 : 0);
        hash = mix(hash, field.auroraVeil ? 1 : 0);
        hash = mix(hash, field.tailwind ? 1 : 0);
        hash = mix(hash, sideFingerprint(field.attackerSide));
        return mix(hash, sideFingerprint(field.defenderSide));
    }

    static long pokemonFingerprint(PokemonSet pokemon) {
        long hash = pokemon.species.hashCode();
        hash = mix(hash, pokemon.battleId.hashCode());
        hash = mix(hash, pokemon.level);
        hash = mix(hash, pokemon.item.hashCode());
        hash = mix(hash, pokemon.ability.hashCode());
        hash = mix(hash, pokemon.nature.hashCode());
        hash = mix(hash, pokemon.itemKnown ? 1 : 0);
        hash = mix(hash, pokemon.abilityKnown ? 1 : 0);
        hash = mix(hash, pokemon.natureKnown ? 1 : 0);
        hash = mix(hash, pokemon.statsKnown ? 1 : 0);
        hash = mix(hash, pokemon.movesKnown ? 1 : 0);
        hash = mix(hash, pokemon.teraType.ordinal());
        hash = mix(hash, pokemon.terastallized ? 1 : 0);
        hash = mix(hash, pokemon.status.ordinal());
        hash = mix(hash, pokemon.currentHp);
        hash = mix(hash, pokemon.observedMaxHp);
        for (Stat stat : STATS) {
            hash = mix(hash, pokemon.evs.get(stat));
            hash = mix(hash, pokemon.ivs.get(stat));
            hash = mix(hash, pokemon.boosts.get(stat));
        }
        for (int slot = 0; slot < 4; slot++) {
            MoveData move = pokemon.moveAt(slot);
            hash = mix(hash, move == null ? 0 : move.hashCode());
            hash = mix(hash, pokemon.zMoveAt(slot) ? 1 : 0);
        }
        hash = mix(hash, pokemon.battleHistoryKnown ? 1 : 0);
        hash = mix(hash, pokemon.timesHit);
        hash = mix(hash, pokemon.faintedAllies);
        hash = mix(hash, pokemon.lastMoveId.hashCode());
        hash = mix(hash, pokemon.consecutiveMoveUses);
        hash = mix(hash, pokemon.echoedVoiceChain);
        hash = mix(hash, pokemon.defenseCurlUsed ? 1 : 0);
        hash = mix(hash, pokemon.switchedInThisTurn ? 1 : 0);
        hash = mix(hash, pokemon.allyFaintedPreviousTurn ? 1 : 0);
        hash = mix(hash, pokemon.lastMoveFailed ? 1 : 0);
        hash = mix(hash, pokemon.flashFireActive ? 1 : 0);
        hash = mix(hash, pokemon.paradoxBoostActive ? 1 : 0);
        hash = mix(hash, pokemon.turnsActive);
        hash = mix(hash, pokemon.lastDamageTaken);
        hash = mix(hash, pokemon.lastDamageCategory.ordinal());
        return hash;
    }

    private static long sideFingerprint(SideConditions side) {
        int bits = 0;
        if (side.reflect) bits |= 1;
        if (side.lightScreen) bits |= 2;
        if (side.auroraVeil) bits |= 4;
        if (side.tailwind) bits |= 8;
        if (side.helpingHand) bits |= 16;
        if (side.friendGuard) bits |= 32;
        if (side.wideGuard) bits |= 64;
        if (side.quickGuard) bits |= 128;
        if (side.matBlock) bits |= 256;
        if (side.craftyShield) bits |= 512;
        long hash = bits;
        hash = mix(hash, side.partnerAbility.hashCode());
        hash = mix(hash, side.partnerAbilities.hashCode());
        return mix(hash, side.spreadTargets);
    }

    private static long mix(long hash, long value) {
        return hash * 31L + value;
    }

    static MoveData displayMove(PokemonSet source, int slot) {
        MoveData move = source.moveAt(slot);
        if (move == null || !source.zMoveAt(slot)) {
            return move;
        }
        if (move.category() == DamageCategory.STATUS) {
            return new MoveData("z" + move.id(), zStatusMoveName(move), move.type(), move.category(),
                    0, false, false, Set.of("zmove"), move.priority());
        }
        return new MoveData("z" + move.id(), zDamagingMoveName(move.type()), move.type(), move.category(),
                zMovePower(move.basePower()), false, false, Set.of("zmove"), move.priority());
    }

    static MoveData effectiveMove(PokemonSet source, int slot) {
        MoveData move = source.moveAt(slot);
        if (move == null || !source.zMoveAt(slot) || move.category() == DamageCategory.STATUS) {
            return move;
        }
        return new MoveData("z" + move.id(), zDamagingMoveName(move.type()), move.type(), move.category(),
                zMovePower(move.basePower()), false, false, Set.of("zmove"), move.priority());
    }

    private static String zStatusMoveName(MoveData move) {
        String key = "cobblemon.move.z" + move.id();
        Language language = Language.getInstance();
        if (language.hasTranslation(key)) {
            String translated = language.get(key);
            if (!translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return move.name() + " Z";
    }

    private static String zDamagingMoveName(PokeType type) {
        String id = switch (type) {
            case NORMAL -> "breakneckblitz";
            case FIRE -> "infernooverdrive";
            case WATER -> "hydrovortex";
            case ELECTRIC -> "gigavolthavoc";
            case GRASS -> "bloomdoom";
            case ICE -> "subzeroslammer";
            case FIGHTING -> "alloutpummeling";
            case POISON -> "aciddownpour";
            case GROUND -> "tectonicrage";
            case FLYING -> "supersonicskystrike";
            case PSYCHIC -> "shatteredpsyche";
            case BUG -> "savagespinout";
            case ROCK -> "continentalcrush";
            case GHOST -> "neverendingnightmare";
            case DRAGON -> "devastatingdrake";
            case DARK -> "blackholeeclipse";
            case STEEL -> "corkscrewcrash";
            case FAIRY -> "twinkletackle";
            case NONE -> "breakneckblitz";
        };
        String key = "cobblemon.move." + id;
        Language language = Language.getInstance();
        if (language.hasTranslation(key)) {
            String translated = language.get(key);
            if (!translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return switch (type) {
            case NORMAL -> "Breakneck Blitz";
            case FIRE -> "Inferno Overdrive";
            case WATER -> "Hydro Vortex";
            case ELECTRIC -> "Gigavolt Havoc";
            case GRASS -> "Bloom Doom";
            case ICE -> "Subzero Slammer";
            case FIGHTING -> "All-Out Pummeling";
            case POISON -> "Acid Downpour";
            case GROUND -> "Tectonic Rage";
            case FLYING -> "Supersonic Skystrike";
            case PSYCHIC -> "Shattered Psyche";
            case BUG -> "Savage Spin-Out";
            case ROCK -> "Continental Crush";
            case GHOST -> "Never-Ending Nightmare";
            case DRAGON -> "Devastating Drake";
            case DARK -> "Black Hole Eclipse";
            case STEEL -> "Corkscrew Crash";
            case FAIRY -> "Twinkle Tackle";
            case NONE -> "Breakneck Blitz";
        };
    }

    private static int zMovePower(int basePower) {
        if (basePower <= 55) return 100;
        if (basePower <= 65) return 120;
        if (basePower <= 75) return 140;
        if (basePower <= 85) return 160;
        if (basePower <= 95) return 175;
        if (basePower <= 100) return 180;
        if (basePower <= 110) return 185;
        if (basePower <= 120) return 190;
        if (basePower <= 130) return 195;
        return 200;
    }

}
