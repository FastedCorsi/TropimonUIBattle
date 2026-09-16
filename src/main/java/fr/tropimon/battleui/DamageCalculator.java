package fr.tropimon.battleui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DamageCalculator {
    private static final int Q12 = 4096;
    private static final int HALF = 2048;
    private static final int THREE_QUARTERS = 3072;
    private static final int THREE_HALVES = 6144;
    private static final Set<String> ATTACKER_HP_POWER_MOVES = Set.of(
            "eruption", "waterspout", "dragonenergy", "flail", "reversal"
    );
    private static final Set<String> DEFENDER_HP_POWER_MOVES = Set.of(
            "brine", "crushgrip", "wringout", "hardpress"
    );
    private static final Set<String> WEIGHT_POWER_MOVES = Set.of(
            "lowkick", "grassknot", "heavyslam", "heatcrash"
    );
    private static final Set<String> UNSUPPORTED_CONTEXT_MOVES = Set.of(
            "assurance", "payback", "pursuit", "lashout",
            "trumpcard", "return", "frustration",
            "beatup", "bide", "spitup", "magnitude", "present", "fling", "naturalgift", "round",
            "fusionbolt", "fusionflare", "ficklebeam",
            "fishiousrend", "boltbeak"
    );
    private static final Set<String> OHKO_MOVES = Set.of("fissure", "guillotine", "horndrill", "sheercold");
    private static final Set<String> ABILITY_IGNORING_MOVES = Set.of(
            "moongeistbeam", "sunsteelstrike", "photongeyser", "lightthatburnsthesky",
            "menacingmoonrazemaelstrom", "searingsunrazesmash", "gmaxdrumsolo", "gmaxfireball", "gmaxhydrosnipe"
    );
    private static final Set<String> REACTIVE_DAMAGE_MOVES = Set.of("counter", "mirrorcoat", "metalburst", "comeuppance");
    private static final Map<String, String> MEGA_STONE_HOLDERS = Map.ofEntries(
            Map.entry("abomasite", "abomasnow"), Map.entry("absolite", "absol"),
            Map.entry("aerodactylite", "aerodactyl"), Map.entry("aggronite", "aggron"),
            Map.entry("alakazite", "alakazam"), Map.entry("altarianite", "altaria"),
            Map.entry("ampharosite", "ampharos"), Map.entry("audinite", "audino"),
            Map.entry("banettite", "banette"), Map.entry("beedrillite", "beedrill"),
            Map.entry("blastoisinite", "blastoise"), Map.entry("blazikenite", "blaziken"),
            Map.entry("cameruptite", "camerupt"), Map.entry("charizarditex", "charizard"),
            Map.entry("charizarditey", "charizard"), Map.entry("diancite", "diancie"),
            Map.entry("galladite", "gallade"), Map.entry("garchompite", "garchomp"),
            Map.entry("gardevoirite", "gardevoir"), Map.entry("gengarite", "gengar"),
            Map.entry("glalitite", "glalie"), Map.entry("gyaradosite", "gyarados"),
            Map.entry("heracronite", "heracross"), Map.entry("houndoominite", "houndoom"),
            Map.entry("kangaskhanite", "kangaskhan"), Map.entry("latiasite", "latias"),
            Map.entry("latiosite", "latios"), Map.entry("lopunnite", "lopunny"),
            Map.entry("lucarionite", "lucario"), Map.entry("manectite", "manectric"),
            Map.entry("mawilite", "mawile"), Map.entry("medichamite", "medicham"),
            Map.entry("metagrossite", "metagross"), Map.entry("mewtwonitex", "mewtwo"),
            Map.entry("mewtwonitey", "mewtwo"), Map.entry("pidgeotite", "pidgeot"),
            Map.entry("pinsirite", "pinsir"), Map.entry("sablenite", "sableye"),
            Map.entry("salamencite", "salamence"), Map.entry("scizorite", "scizor"),
            Map.entry("sceptilite", "sceptile"), Map.entry("sharpedonite", "sharpedo"),
            Map.entry("slowbronite", "slowbro"), Map.entry("steelixite", "steelix"),
            Map.entry("swampertite", "swampert"), Map.entry("tyranitarite", "tyranitar"),
            Map.entry("venusaurite", "venusaur")
    );
    private static final EnumMap<PokeType, EnumMap<PokeType, Double>> TYPE_CHART = new EnumMap<>(PokeType.class);
    private static final Set<String> BITE_MOVES = Set.of(
            "bite", "crunch", "firefang", "fishiousrend", "hyperfang", "icefang", "jawlock", "poisonfang", "psychicfangs", "thunderfang"
    );
    private static final Set<String> PUNCH_MOVES = Set.of(
            "bulletpunch", "cometpunch", "dizzypunch", "doubleironbash", "drainpunch", "dynamicpunch", "firepunch", "focuspunch",
            "hammerarm", "icehammer", "icepunch", "machpunch", "megapunch", "meteormash", "plasmafists", "poweruppunch",
            "jetpunch", "ragefist", "shadowpunch", "skyuppercut", "surgingstrikes", "thunderpunch", "wickedblow"
    );
    private static final Set<String> CONTACT_MOVES = Set.of(
            "aerialace", "aquajet", "aquatail", "bite", "bodyslam", "boltbeak", "bravebird", "brickbreak", "bulldoze",
            "bulletpunch", "closecombat", "crabhammer", "crosschop", "crunch", "doubleedge", "dragonclaw", "drainpunch",
            "drillpeck", "facade", "fakeout", "firefang", "firepunch", "fishiousrend", "flareblitz", "focuspunch",
            "headbutt", "headsmash", "icefang", "icepunch", "ironhead", "leafblade", "liquidation", "lowkick",
            "machpunch", "megahorn", "nightslash", "playrough", "poisonjab", "poweruppunch", "psychicfangs", "quickattack",
            "razorshell", "sacredsword", "shadowclaw", "shadowpunch", "slash", "stoneedge", "suckerpunch", "tackle",
            "thunderfang", "thunderpunch", "uturn", "volttackle", "waterfall", "wildcharge", "woodhammer", "xscissor", "zenheadbutt"
    );
    private static final Set<String> SLICING_MOVES = Set.of(
            "aerialace", "aircutter", "aquacutter", "behemothblade", "bitterblade", "ceaselessedge", "crosspoison", "cut",
            "furycutter", "kowtowcleave", "leafblade", "nightslash", "psychocut", "razorleaf", "razorshell", "sacredsword",
            "slash", "solarblade", "stoneaxe", "xscissor"
    );
    private static final Set<String> PULSE_MOVES = Set.of(
            "aurasphere", "darkpulse", "dragonpulse", "originpulse", "terrainpulse", "waterpulse"
    );
    private static final Set<String> SOUND_MOVES = Set.of(
            "alluringvoice", "boomburst", "bugbuzz", "clangingscales", "disarmingvoice", "echoedvoice", "hypervoice",
            "overdrive", "psychicnoise", "relicsong", "round", "snarl", "snore", "sparklingaria", "torchsong", "uproar"
    );
    private static final Set<String> BULLET_MOVES = Set.of(
            "acidspray", "aurasphere", "barrage", "bulletseed", "eggbomb", "electroball", "energyball", "focusblast",
            "gyroball", "iceball", "magnetbomb", "mistball", "mudbomb", "octazooka", "pollenpuff", "pyroball",
            "rockblast", "rockwrecker", "seedbomb", "shadowball", "sludgebomb", "weatherball", "zapcannon"
    );
    private static final Set<String> WIND_MOVES = Set.of(
            "aircutter", "bleakwindstorm", "blizzard", "fairywind", "gust", "heatwave", "hurricane", "icywind",
            "petalblizzard", "sandsearstorm", "springtidestorm", "twister", "wildboltstorm"
    );
    private static final Set<String> RECOIL_MOVES = Set.of(
            "bravebird", "chloroblast", "doubleedge", "flareblitz", "headcharge", "headsmash", "highjumpkick", "jumpkick",
            "lightofruin", "submission", "takedown", "volttackle", "wavecrash", "wildcharge", "woodhammer"
    );
    private static final Set<String> SPEED_HALVING_ITEMS = Set.of(
            "ironball", "poweranklet", "powerband", "powerbelt", "powerbracer", "powerlens", "powerweight", "machobrace"
    );
    private static final Map<PokeType, Set<String>> TYPE_BOOST_ITEMS = Map.ofEntries(
            Map.entry(PokeType.NORMAL, Set.of("silkscarf")),
            Map.entry(PokeType.FIRE, Set.of("charcoal", "charcoalstick", "flameplate")),
            Map.entry(PokeType.WATER, Set.of("mysticwater", "splashplate", "seaincense", "waveincense")),
            Map.entry(PokeType.ELECTRIC, Set.of("magnet", "zapplate")),
            Map.entry(PokeType.GRASS, Set.of("miracleseed", "meadowplate", "roseincense")),
            Map.entry(PokeType.ICE, Set.of("nevermeltice", "icicleplate")),
            Map.entry(PokeType.FIGHTING, Set.of("blackbelt", "fistplate")),
            Map.entry(PokeType.POISON, Set.of("poisonbarb", "toxicplate")),
            Map.entry(PokeType.GROUND, Set.of("softsand", "earthplate")),
            Map.entry(PokeType.FLYING, Set.of("sharpbeak", "skyplate")),
            Map.entry(PokeType.PSYCHIC, Set.of("twistedspoon", "mindplate", "oddincense")),
            Map.entry(PokeType.BUG, Set.of("silverpowder", "insectplate")),
            Map.entry(PokeType.ROCK, Set.of("hardstone", "stoneplate", "rockincense")),
            Map.entry(PokeType.GHOST, Set.of("spelltag", "spookyplate")),
            Map.entry(PokeType.DRAGON, Set.of("dragonfang", "dracoplate")),
            Map.entry(PokeType.DARK, Set.of("blackglasses", "dreadplate")),
            Map.entry(PokeType.STEEL, Set.of("metalcoat", "ironplate")),
            Map.entry(PokeType.FAIRY, Set.of("fairyfeather", "pixieplate"))
    );

    static {
        for (PokeType attacking : PokeType.values()) {
            TYPE_CHART.put(attacking, new EnumMap<>(PokeType.class));
        }
        weak(PokeType.NORMAL, PokeType.ROCK, PokeType.STEEL);
        immune(PokeType.NORMAL, PokeType.GHOST);
        strong(PokeType.FIRE, PokeType.GRASS, PokeType.ICE, PokeType.BUG, PokeType.STEEL);
        weak(PokeType.FIRE, PokeType.FIRE, PokeType.WATER, PokeType.ROCK, PokeType.DRAGON);
        strong(PokeType.WATER, PokeType.FIRE, PokeType.GROUND, PokeType.ROCK);
        weak(PokeType.WATER, PokeType.WATER, PokeType.GRASS, PokeType.DRAGON);
        strong(PokeType.ELECTRIC, PokeType.WATER, PokeType.FLYING);
        weak(PokeType.ELECTRIC, PokeType.ELECTRIC, PokeType.GRASS, PokeType.DRAGON);
        immune(PokeType.ELECTRIC, PokeType.GROUND);
        strong(PokeType.GRASS, PokeType.WATER, PokeType.GROUND, PokeType.ROCK);
        weak(PokeType.GRASS, PokeType.FIRE, PokeType.GRASS, PokeType.POISON, PokeType.FLYING, PokeType.BUG, PokeType.DRAGON, PokeType.STEEL);
        strong(PokeType.ICE, PokeType.GRASS, PokeType.GROUND, PokeType.FLYING, PokeType.DRAGON);
        weak(PokeType.ICE, PokeType.FIRE, PokeType.WATER, PokeType.ICE, PokeType.STEEL);
        strong(PokeType.FIGHTING, PokeType.NORMAL, PokeType.ICE, PokeType.ROCK, PokeType.DARK, PokeType.STEEL);
        weak(PokeType.FIGHTING, PokeType.POISON, PokeType.FLYING, PokeType.PSYCHIC, PokeType.BUG, PokeType.FAIRY);
        immune(PokeType.FIGHTING, PokeType.GHOST);
        strong(PokeType.POISON, PokeType.GRASS, PokeType.FAIRY);
        weak(PokeType.POISON, PokeType.POISON, PokeType.GROUND, PokeType.ROCK, PokeType.GHOST);
        immune(PokeType.POISON, PokeType.STEEL);
        strong(PokeType.GROUND, PokeType.FIRE, PokeType.ELECTRIC, PokeType.POISON, PokeType.ROCK, PokeType.STEEL);
        weak(PokeType.GROUND, PokeType.GRASS, PokeType.BUG);
        immune(PokeType.GROUND, PokeType.FLYING);
        strong(PokeType.FLYING, PokeType.GRASS, PokeType.FIGHTING, PokeType.BUG);
        weak(PokeType.FLYING, PokeType.ELECTRIC, PokeType.ROCK, PokeType.STEEL);
        strong(PokeType.PSYCHIC, PokeType.FIGHTING, PokeType.POISON);
        weak(PokeType.PSYCHIC, PokeType.PSYCHIC, PokeType.STEEL);
        immune(PokeType.PSYCHIC, PokeType.DARK);
        strong(PokeType.BUG, PokeType.GRASS, PokeType.PSYCHIC, PokeType.DARK);
        weak(PokeType.BUG, PokeType.FIRE, PokeType.FIGHTING, PokeType.POISON, PokeType.FLYING, PokeType.GHOST, PokeType.STEEL, PokeType.FAIRY);
        strong(PokeType.ROCK, PokeType.FIRE, PokeType.ICE, PokeType.FLYING, PokeType.BUG);
        weak(PokeType.ROCK, PokeType.FIGHTING, PokeType.GROUND, PokeType.STEEL);
        strong(PokeType.GHOST, PokeType.PSYCHIC, PokeType.GHOST);
        weak(PokeType.GHOST, PokeType.DARK);
        immune(PokeType.GHOST, PokeType.NORMAL);
        strong(PokeType.DRAGON, PokeType.DRAGON);
        weak(PokeType.DRAGON, PokeType.STEEL);
        immune(PokeType.DRAGON, PokeType.FAIRY);
        strong(PokeType.DARK, PokeType.PSYCHIC, PokeType.GHOST);
        weak(PokeType.DARK, PokeType.FIGHTING, PokeType.DARK, PokeType.FAIRY);
        strong(PokeType.STEEL, PokeType.ICE, PokeType.ROCK, PokeType.FAIRY);
        weak(PokeType.STEEL, PokeType.FIRE, PokeType.WATER, PokeType.ELECTRIC, PokeType.STEEL);
        strong(PokeType.FAIRY, PokeType.FIGHTING, PokeType.DRAGON, PokeType.DARK);
        weak(PokeType.FAIRY, PokeType.FIRE, PokeType.POISON, PokeType.STEEL);
    }

    private DamageCalculator() {
    }

    static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field) {
        return calculate(attacker, defender, move, field, field.legacySideConditions());
    }

    static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field, SideConditions defenderSide) {
        return calculate(attacker, defender, move, field, new SideConditions(), defenderSide);
    }

    static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field,
                                  SideConditions attackerSide, SideConditions defenderSide) {
        if (move.category() == DamageCategory.STATUS) {
            return DamageResult.zero(move, "status move");
        }
        ArrayList<String> notes = new ArrayList<>();
        ArrayList<CalcWarning> warnings = new ArrayList<>();
        move = resolveMove(attacker, defender, move, field, notes);
        String protection = BattleProtectionRules.blockedBy(move, field, defenderSide,
                effectivePriority(attacker, move));
        if (!protection.isBlank()) return DamageResult.zero(move, protection);

        addContextWarnings(attacker, defender, move, warnings);
        PokeType moveType = effectiveMoveType(attacker, move, field, notes);
        String weatherBlock = strongWeatherBlock(moveType, field.weather);
        if (weatherBlock != null) return DamageResult.zero(move, weatherBlock, warnings);
        String immunity = defensiveImmunity(attacker, defender, move, moveType, field, defenderSide);
        if (immunity != null) {
            return DamageResult.zero(move, immunity, warnings);
        }
        double effectiveness = typeEffectiveness(move, moveType, defender, attacker, field, notes);
        if (effectiveness == 0.0) {
            return DamageResult.zero(move, "immune", warnings);
        }
        if (!ignoresDefensiveAbilities(attacker, move) && hasAbility(defender, "Wonder Guard") && effectiveness <= 1.0) {
            return DamageResult.zero(move, "Wonder Guard immunity", warnings);
        }

        HitProfile hits = hitProfile(attacker, defender, move, field, attackerSide);
        FixedDamage fixedDamage = fixedDamage(attacker, defender, move);
        if (fixedDamage != null) {
            return fixedDamageResult(attacker, defender, move, fixedDamage, hits, warnings);
        }

        int power = effectivePower(attacker, defender, move, moveType, field, attackerSide, defenderSide, notes, warnings);
        if (power <= 0) {
            return DamageResult.zero(move, "no base power", warnings);
        }

        boolean criticalHit = isCriticalHit(attacker, defender, move, field);
        int attackStat = offensiveStat(attacker, defender, move, moveType, field, attackerSide, defenderSide,
                criticalHit, notes);
        int defenseStat = defensiveStat(attacker, defender, move, field, attackerSide, defenderSide,
                criticalHit, notes);
        if (defenseStat <= 0) {
            defenseStat = 1;
        }

        DamageFormula formula = damageFormula(attacker, defender, move, moveType, field, attackerSide,
                defenderSide, effectiveness, criticalHit, notes);

        List<HitCalculation> hitCalculations = hitCalculations(attacker, defender, move, moveType, field,
                attackerSide, defenderSide, power, attackStat, defenseStat, formula, hits);
        DamageDistribution distribution = damageDistribution(move, hits, hitCalculations);
        ArrayList<Integer> rolls = new ArrayList<>();
        for (int roll = 85; roll <= 100; roll++) {
            rolls.add(damageForHits(attacker.level, hitCalculations, roll, hits.minHits()));
        }

        int min = distribution.minDamage();
        int max = distribution.maxDamage();
        if (hits.maxHits() > 1) {
            notes.add(hits.multiAccuracy() && !hits.forcedHits()
                    ? "1-" + hits.maxHits() + " hits"
                    : hits.minHits() == hits.maxHits()
                    ? hits.minHits() + " hits"
                    : hits.minHits() + "-" + hits.maxHits() + " hits");
        }
        int maxHp = defender.maxHp();
        int entryDamage = 0;
        int koHp = Math.max(1, defender.visibleHp() - entryDamage);
        double minPercent = min * 100.0 / maxHp;
        double maxPercent = max * 100.0 / maxHp;
        String koChance = koChance(distribution.probabilities(), koHp);
        if (hits.maxHits() == 1 && max >= koHp && defender.visibleHp() == defender.maxHp()
                && (hasItem(defender, "Focus Sash")
                || hasAbility(defender, "Sturdy") && !ignoresDefensiveAbilities(attacker, move))) {
            koChance = "survives at 1 HP";
        }
        if (entryDamage > 0) {
            koChance += " after " + entryDamage + " entry damage";
        }
        String attackerPrefix = attacker.evs.get(move.category() == DamageCategory.PHYSICAL ? Stat.ATK : Stat.SPA) + " "
                + (move.category() == DamageCategory.PHYSICAL ? "Atk" : "SpA") + " " + attacker.species.name();
        String defenderPrefix = defender.evs.get(Stat.HP) + " HP / "
                + defender.evs.get(move.category() == DamageCategory.PHYSICAL ? Stat.DEF : Stat.SPD) + " "
                + (move.category() == DamageCategory.PHYSICAL ? "Def" : "SpD") + " " + defender.species.name();
        String line = attackerPrefix + " " + move.name() + " vs. " + defenderPrefix + ": "
                + min + "-" + max + " (" + percent(minPercent) + " - " + percent(maxPercent) + "%) -- " + koChance;
        String estimated = warnings.isEmpty() ? "" : " [estimated]";
        String shortLine = move.name() + " " + min + "-" + max + " (" + percent(minPercent) + "-"
                + percent(maxPercent) + "%) | " + koChance + estimated;
        return new DamageResult(move, min, max, minPercent, maxPercent, rolls, entryDamage, koChance,
                line + estimated, shortLine, List.copyOf(notes), List.copyOf(warnings));
    }

    private static DamageFormula damageFormula(PokemonSet attacker, PokemonSet defender, MoveData move,
                                               PokeType moveType, FieldState field,
                                               SideConditions attackerSide, SideConditions defenderSide,
                                               double effectiveness, boolean criticalHit, List<String> notes) {
        int spreadModifier = Q12;
        if (field.doubles && move.spreadMove() && attackerSide.spreadTargets > 1) {
            spreadModifier = THREE_QUARTERS;
            if (!notes.contains("spread x0.75")) notes.add("spread x0.75");
        }
        int criticalModifier = Q12;
        if (criticalHit) {
            criticalModifier = THREE_HALVES;
            if (!notes.contains("crit")) notes.add("crit");
        }
        FinalModifierValues finalModifiers = finalModifierValues(attacker, defender, move, moveType, field,
                attackerSide, defenderSide, effectiveness, criticalHit, notes);
        boolean burned = attacker.status == StatusCondition.BURN && move.category() == DamageCategory.PHYSICAL
                && !move.id().equals("facade") && !hasAbility(attacker, "Guts");
        if (burned && !notes.contains("burn x0.5")) notes.add("burn x0.5");
        return new DamageFormula(spreadModifier, weatherModifier(attacker, defender, move, moveType, field, notes),
                criticalModifier, stabModifier(attacker, moveType, notes), effectiveness, burned,
                finalModifiers.normal(), finalModifiers.firstHit());
    }

    private static int damageForHits(int level, List<HitCalculation> calculations,
                                     int randomRoll, int hitCount) {
        int total = 0;
        for (int hit = 1; hit <= Math.max(1, hitCount); hit++) {
            HitCalculation calculation = calculations.get(hit - 1);
            if (calculation.blocked()) continue;
            total += damageForHit(level, calculation.power(), calculation.attackStat(), calculation.defenseStat(),
                    calculation.formula(), randomRoll, calculation.parentalChild(), calculation.firstDamagingHit());
        }
        return total;
    }

    private static DamageDistribution damageDistribution(MoveData move, HitProfile hits,
                                                          List<HitCalculation> calculations) {
        Map<Integer, Double> combined = new HashMap<>();
        for (Map.Entry<Integer, Double> hitCount : hitCountProbabilities(hits).entrySet()) {
            Map<Integer, Double> totals = Map.of(0, 1.0);
            HashMap<Integer, Double> stopped = new HashMap<>();
            double continuationAccuracy = move.hasFlag("multiaccuracy") && !hits.forcedHits()
                    ? moveAccuracy(move) / 100.0 : 1.0;
            for (int hit = 1; hit <= hitCount.getKey(); hit++) {
                HitCalculation calculation = calculations.get(hit - 1);
                HashMap<Integer, Double> next = new HashMap<>();
                for (Map.Entry<Integer, Double> total : totals.entrySet()) {
                    if (hit > 1 && continuationAccuracy < 1.0) {
                        stopped.merge(total.getKey(), total.getValue() * (1.0 - continuationAccuracy), Double::sum);
                    }
                    if (calculation.blocked()) {
                        next.merge(total.getKey(), total.getValue(), Double::sum);
                        continue;
                    }
                    for (int roll = 85; roll <= 100; roll++) {
                        int damage = damageForHit(calculation.level(), calculation.power(), calculation.attackStat(),
                                calculation.defenseStat(), calculation.formula(), roll,
                                calculation.parentalChild(), calculation.firstDamagingHit());
                        double hitChance = hit > 1 ? continuationAccuracy : 1.0;
                        next.merge(total.getKey() + damage, total.getValue() * hitChance / 16.0, Double::sum);
                    }
                }
                totals = next;
            }
            for (Map.Entry<Integer, Double> stoppedTotal : stopped.entrySet()) {
                combined.merge(stoppedTotal.getKey(), stoppedTotal.getValue() * hitCount.getValue(), Double::sum);
            }
            for (Map.Entry<Integer, Double> total : totals.entrySet()) {
                combined.merge(total.getKey(), total.getValue() * hitCount.getValue(), Double::sum);
            }
        }
        int min = combined.keySet().stream().min(Integer::compareTo).orElse(0);
        int max = combined.keySet().stream().max(Integer::compareTo).orElse(0);
        return new DamageDistribution(Map.copyOf(combined), min, max);
    }

    private static List<HitCalculation> hitCalculations(PokemonSet attacker, PokemonSet defender, MoveData move,
                                                        PokeType firstMoveType, FieldState field,
                                                        SideConditions attackerSide, SideConditions defenderSide,
                                                        int firstPower, int firstAttack, int firstDefense,
                                                        DamageFormula firstFormula, HitProfile profile) {
        PokemonSet simulatedAttacker = attacker.copy();
        PokemonSet simulatedDefender = defender.copy();
        FieldState simulatedField = copyField(field);
        ArrayList<HitCalculation> calculations = new ArrayList<>(profile.maxHits());
        boolean firstDamagingHit = true;
        for (int hit = 1; hit <= profile.maxHits(); hit++) {
            boolean blocked = profile.blockedFirstHit() && hit == 1;
            if (blocked) {
                calculations.add(new HitCalculation(attacker.level, 0, 1, 1, firstFormula,
                        false, false, true));
                continue;
            }

            int power = firstPower;
            int attack = firstAttack;
            int defense = firstDefense;
            DamageFormula formula = firstFormula;
            if (hit > 1) {
                ArrayList<String> scratchNotes = new ArrayList<>();
                PokeType moveType = effectiveMoveType(simulatedAttacker, move, simulatedField, scratchNotes);
                power = progressiveHitPower(simulatedAttacker, simulatedDefender, move, moveType, simulatedField,
                        attackerSide, defenderSide, firstPower, hit);
                boolean critical = isCriticalHit(simulatedAttacker, simulatedDefender, move, simulatedField);
                attack = offensiveStat(simulatedAttacker, simulatedDefender, move, moveType, simulatedField,
                        attackerSide, defenderSide, critical, scratchNotes);
                defense = Math.max(1, defensiveStat(simulatedAttacker, simulatedDefender, move, simulatedField,
                        attackerSide, defenderSide, critical, scratchNotes));
                double effectiveness = typeEffectiveness(move, moveType, simulatedDefender, simulatedAttacker,
                        simulatedField, scratchNotes);
                formula = damageFormula(simulatedAttacker, simulatedDefender, move, moveType, simulatedField,
                        attackerSide, defenderSide, effectiveness, critical, scratchNotes);
            }
            calculations.add(new HitCalculation(attacker.level, power, attack, defense, formula,
                    profile.parentalBond() && hit == 2, firstDamagingHit, false));
            firstDamagingHit = false;
            PokeType hitMoveType = hit == 1 ? firstMoveType
                    : effectiveMoveType(simulatedAttacker, move, simulatedField, new ArrayList<>());
            applyBetweenHitTransitions(simulatedAttacker, simulatedDefender, move, hitMoveType, simulatedField);
        }
        return List.copyOf(calculations);
    }

    private static void applyBetweenHitTransitions(PokemonSet attacker, PokemonSet defender, MoveData move,
                                                   PokeType moveType, FieldState field) {
        if (move.id().equals("knockoff") && knockOffBoostApplies(defender)
                && (ignoresDefensiveAbilities(attacker, move) || !hasAbility(defender, "Sticky Hold"))) {
            defender.item = "None";
        }
        if (move.id().equals("poweruppunch") || move.hasFlag("selfboostatk1")) {
            changeStage(attacker, Stat.ATK, 1);
        }
        // Mold Breaker-style effects only bypass abilities that alter whether or
        // how this hit lands. Post-damage reactions still activate per hit.
        if (hasAbility(defender, "Stamina")) {
            changeStage(defender, Stat.DEF, 1);
        } else if (hasAbility(defender, "Weak Armor") && move.category() == DamageCategory.PHYSICAL) {
            // White Herb activates after the complete move, not between its hits.
            // The temporary simulation therefore keeps every Weak Armor drop so
            // each following hit uses the correct Defense stage. Knock Off has
            // already removed the Herb above, which likewise prevents activation.
            changeStage(defender, Stat.DEF, -1);
            changeStage(defender, Stat.SPE, 2);
        } else if (hasAbility(defender, "Water Compaction") && moveType == PokeType.WATER) {
            changeStage(defender, Stat.DEF, 2);
        } else if (hasAbility(defender, "Seed Sower")) {
            field.terrain = Terrain.GRASSY;
        } else if (hasAbility(defender, "Sand Spit") && field.weather != Weather.HARSH_SUN
                && field.weather != Weather.HEAVY_RAIN && field.weather != Weather.STRONG_WINDS) {
            field.weather = Weather.SAND;
        }
    }

    private static void changeStage(PokemonSet pokemon, Stat stat, int delta) {
        int current = pokemon.boosts.getOrDefault(stat, 0);
        pokemon.boosts.put(stat, Math.max(-6, Math.min(6, current + delta)));
    }

    private static FieldState copyField(FieldState source) {
        FieldState copy = new FieldState();
        copy.weather = source.weather;
        copy.terrain = source.terrain;
        copy.doubles = source.doubles;
        copy.alliedTarget = source.alliedTarget;
        copy.criticalHit = source.criticalHit;
        copy.trickRoom = source.trickRoom;
        copy.wonderRoom = source.wonderRoom;
        copy.gravity = source.gravity;
        copy.helpingHand = source.helpingHand;
        copy.friendGuard = source.friendGuard;
        copy.reflect = source.reflect;
        copy.lightScreen = source.lightScreen;
        copy.auroraVeil = source.auroraVeil;
        copy.tailwind = source.tailwind;
        return copy;
    }

    private static int damageForHit(int level, int power, int attackStat, int defenseStat,
                                    DamageFormula formula, int randomRoll, boolean parentalChild,
                                    boolean firstDamagingHit) {
        long levelFactor = Math.floorDiv(2L * level, 5L) + 2L;
        long scaled = Math.floorDiv(levelFactor * Math.max(1, power) * Math.max(1, attackStat),
                Math.max(1, defenseStat));
        int damage = safeInt(Math.floorDiv(scaled, 50L) + 2L);
        damage = roundQ12(damage, formula.spreadModifier());
        if (parentalChild) damage = roundQ12(damage, 1024);
        damage = roundQ12(damage, formula.weatherModifier());
        damage = roundQ12(damage, formula.criticalModifier());
        damage = safeInt(Math.floorDiv((long) damage * randomRoll, 100L));
        damage = roundQ12(damage, formula.stabModifier());
        damage = safeInt((long) Math.floor(damage * formula.effectiveness()));
        if (formula.burned()) damage = Math.floorDiv(damage, 2);
        damage = roundQ12(Math.max(1, damage), firstDamagingHit
                ? formula.firstHitFinalModifier() : formula.finalModifier());
        return Math.max(1, damage);
    }

    static Map<Integer, Double> hitCountProbabilities(HitProfile hits) {
        if (hits.minHits() == hits.maxHits()) {
            return Map.of(hits.minHits(), 1.0);
        }
        if (hits.minHits() == 2 && hits.maxHits() == 5) {
            return Map.of(2, 0.35, 3, 0.35, 4, 0.15, 5, 0.15);
        }
        if (hits.minHits() == 4 && hits.maxHits() == 5) {
            return Map.of(4, 0.5, 5, 0.5);
        }
        HashMap<Integer, Double> probabilities = new HashMap<>();
        double probability = 1.0 / (hits.maxHits() - hits.minHits() + 1);
        for (int count = hits.minHits(); count <= hits.maxHits(); count++) {
            probabilities.put(count, probability);
        }
        return probabilities;
    }

    private static int progressiveHitPower(PokemonSet attacker, PokemonSet defender, MoveData move,
                                           PokeType moveType, FieldState field,
                                           SideConditions attackerSide, SideConditions defenderSide,
                                           int firstHitPower, int hit) {
        if (hit <= 1) return firstHitPower;
        int rawPower = move.basePower();
        if (move.id().equals("tripleaxel") || move.id().equals("triplekick")) rawPower *= hit;
        MoveData perHit = new MoveData(move.id(), move.name(), move.type(), move.category(),
                Math.max(1, rawPower), move.spreadMove(), move.contact(), move.flags(), move.priority());
        return effectivePower(attacker, defender, perHit, moveType, field, attackerSide, defenderSide,
                new ArrayList<>(), new ArrayList<>());
    }

    static MoveData resolveMove(PokemonSet attacker, PokemonSet defender, MoveData move,
                                FieldState field, List<String> notes) {
        MoveData resolved = withEffectiveCategory(attacker, defender, move, field, notes);
        boolean spread = resolved.spreadMove() || resolved.id().equals("expandingforce")
                && field.terrain == Terrain.PSYCHIC && isGrounded(attacker, field);
        if (spread == resolved.spreadMove()) return resolved;
        notes.add("Expanding Force targets opposing side");
        return new MoveData(resolved.id(), resolved.name(), resolved.type(), resolved.category(),
                resolved.basePower(), true, resolved.contact(), resolved.flags(), resolved.priority());
    }

    private static MoveData withEffectiveCategory(PokemonSet attacker, PokemonSet defender, MoveData move,
                                                   FieldState field, List<String> notes) {
        DamageCategory category = move.category();
        String id = move.id();
        if ((id.equals("terablast") && attacker.terastallized)
                || id.equals("photongeyser") || id.equals("lightthatburnsthesky")) {
            // These moves compare the calculated battle stats (nature + stages),
            // before item, ability and weather stat modifiers are applied.
            int attack = storedStat(attacker, Stat.ATK, false);
            int specialAttack = storedStat(attacker, Stat.SPA, false);
            category = attack > specialAttack ? DamageCategory.PHYSICAL : DamageCategory.SPECIAL;
        } else if (id.equals("shellsidearm")) {
            int attack = storedStat(attacker, Stat.ATK, false);
            int specialAttack = storedStat(attacker, Stat.SPA, false);
            // Shell Side Arm compares the two raw defensive stats. Wonder Room
            // changes which boost channel applies to each one instead of swapping
            // already-boosted totals a second time.
            Stat physicalBoost = field.wonderRoom ? Stat.SPD : Stat.DEF;
            Stat specialBoost = field.wonderRoom ? Stat.DEF : Stat.SPD;
            int defense = storedStatWithBoost(defender, Stat.DEF, physicalBoost, false);
            int specialDefense = storedStatWithBoost(defender, Stat.SPD, specialBoost, false);
            category = (long) attack * specialDefense > (long) specialAttack * defense
                    ? DamageCategory.PHYSICAL : DamageCategory.SPECIAL;
        }
        java.util.HashSet<String> flags = new java.util.HashSet<>(move.flags());
        if (id.equals("shellsidearm")) {
            if (category == DamageCategory.PHYSICAL) flags.add("contact");
            else flags.remove("contact");
        }
        boolean contact = flags.contains("contact");
        if (category == move.category() && contact == move.contact()) {
            return move;
        }
        if (category != move.category()) {
            notes.add(move.name() + " uses " + category.name().toLowerCase(Locale.ROOT) + " damage");
        }
        return new MoveData(move.id(), move.name(), move.type(), category, move.basePower(), move.spreadMove(),
                contact, flags, move.priority());
    }

    private static HitProfile hitProfile(PokemonSet attacker, PokemonSet defender, MoveData move,
                                         FieldState field, SideConditions attackerSide) {
        int min = 1;
        int max = 1;
        for (String flag : move.flags()) {
            if (!flag.startsWith("hits")) continue;
            String value = flag.substring(4);
            int separator = value.indexOf("to");
            try {
                if (separator >= 0) {
                    min = Integer.parseInt(value.substring(0, separator));
                    max = Integer.parseInt(value.substring(separator + 2));
                } else {
                    min = max = Integer.parseInt(value);
                }
            } catch (NumberFormatException ignored) {
                min = max = 1;
            }
            break;
        }
        if (max <= 1 && move.hasFlag("multihit")) {
            min = 2;
            max = 5;
        }
        boolean forcedHits = false;
        if (max > min && hasAbility(attacker, "Skill Link")) {
            min = max;
            forcedHits = true;
        } else if (max > min && hasItem(attacker, "Loaded Dice")) {
            min = Math.min(max, Math.max(min, 4));
            forcedHits = true;
        } else if (move.hasFlag("multiaccuracy") && hasAbility(attacker, "Skill Link")) {
            forcedHits = true;
        } else if (move.hasFlag("multiaccuracy") && hasItem(attacker, "Loaded Dice")) {
            // Loaded Dice turns Population Bomb's ten checks into one check,
            // then selects four through ten hits uniformly.
            if (min == 10 && max == 10) min = 4;
            forcedHits = true;
        }
        boolean parentalBond = min == 1 && max == 1 && hasAbility(attacker, "Parental Bond")
                && !(field.doubles && move.spreadMove() && attackerSide.spreadTargets > 1)
                && !move.hasFlag("noparentalbond") && !move.hasFlag("charge")
                && !move.hasFlag("futuremove") && !move.hasFlag("zmove") && !move.hasFlag("maxmove");
        if (parentalBond) {
            min = max = 2;
        }
        boolean blockedFirstHit = !ignoresDefensiveAbilities(attacker, move)
                && (hasAbility(defender, "Disguise") && !hasAspect(defender, "busted")
                || move.category() == DamageCategory.PHYSICAL && hasAbility(defender, "Ice Face")
                && !hasAspect(defender, "noice"));
        return new HitProfile(Math.max(1, min), Math.max(Math.max(1, min), max), forcedHits,
                move.hasFlag("multiaccuracy"), parentalBond, blockedFirstHit);
    }

    static int moveAccuracy(MoveData move) {
        for (String flag : move.flags()) {
            if (!flag.startsWith("accuracy")) continue;
            try {
                return Math.max(1, Math.min(100, Integer.parseInt(flag.substring("accuracy".length()))));
            } catch (NumberFormatException ignored) {
                return 100;
            }
        }
        return 100;
    }

    static int stat(PokemonSet pokemon, Stat stat, boolean ignoreBoosts) {
        int value = storedStat(pokemon, stat, ignoreBoosts);
        if (stat != Stat.HP) {
            value = itemStatModifier(pokemon, stat, value);
            value = abilityStatModifier(pokemon, stat, value);
        }
        return Math.max(1, value);
    }

    private static int storedStat(PokemonSet pokemon, Stat stat, boolean ignoreBoosts) {
        int base = pokemon.species.baseStats().get(stat);
        int iv = pokemon.ivs.get(stat);
        int ev = pokemon.evs.get(stat);
        int value;
        if (stat == Stat.HP) {
            value = (int) Math.floor(((2 * base + iv + Math.floor(ev / 4.0)) * pokemon.level) / 100.0) + pokemon.level + 10;
        } else {
            value = (int) Math.floor(((2 * base + iv + Math.floor(ev / 4.0)) * pokemon.level) / 100.0) + 5;
            value = (int) Math.floor(value * pokemon.nature.modifier(stat));
            if (!ignoreBoosts) {
                value = applyBoost(value, pokemon.boosts.get(stat));
            }
        }
        return Math.max(1, value);
    }

    private static int storedStatWithBoost(PokemonSet pokemon, Stat rawStat, Stat boostStat,
                                           boolean ignoreBoosts) {
        int value = storedStat(pokemon, rawStat, true);
        if (!ignoreBoosts) value = applyBoost(value, pokemon.boosts.getOrDefault(boostStat, 0));
        return Math.max(1, value);
    }

    static int stat(PokemonSet pokemon, Stat stat, boolean ignoreBoosts, FieldState field) {
        return stat(pokemon, stat, ignoreBoosts, field, new SideConditions());
    }

    static int stat(PokemonSet pokemon, Stat stat, boolean ignoreBoosts, FieldState field, SideConditions side) {
        int value = weatherStatModifier(pokemon, stat, stat(pokemon, stat, ignoreBoosts), field, null);
        return sideStatModifier(stat, value, side, null);
    }

    static int displayedStat(PokemonSet pokemon, Stat stat, FieldState field, SideConditions side) {
        int value = stat(pokemon, stat, false, field, side);
        if (stat == Stat.ATK && pokemon.status == StatusCondition.BURN && !hasAbility(pokemon, "Guts")) {
            value = Math.max(1, (int) Math.floor(value * 0.5));
        }
        return value;
    }

    static double typeEffectiveness(PokeType attackingType, PokemonSet defender, PokemonSet attacker, List<String> notes) {
        return typeEffectiveness(attackingType, defender, attacker, new FieldState(), notes);
    }

    private static double typeEffectiveness(PokeType attackingType, PokemonSet defender, PokemonSet attacker,
                                            FieldState field, List<String> notes) {
        return typeEffectiveness(null, attackingType, defender, attacker, field, notes);
    }

    static double typeEffectiveness(MoveData move, PokeType attackingType, PokemonSet defender,
                                             PokemonSet attacker, FieldState field, List<String> notes) {
        if (attackingType == PokeType.GROUND && !field.gravity
                && (move == null || !move.id().equals("thousandarrows"))
                && hasAbility(defender, "Levitate") && !ignoresDefensiveAbilities(attacker, move)) {
            notes.add("Levitate immunity");
            return 0.0;
        }
        double effectiveness = 1.0;
        for (PokeType defendingType : defender.defensiveTypes()) {
            double factor = TYPE_CHART.get(attackingType).getOrDefault(defendingType, 1.0);
            boolean bypassGhostImmunity = defendingType == PokeType.GHOST
                    && (attackingType == PokeType.NORMAL || attackingType == PokeType.FIGHTING)
                    && (hasAbility(attacker, "Scrappy") || hasAbility(attacker, "Mind's Eye"));
            boolean groundedFlying = attackingType == PokeType.GROUND && defendingType == PokeType.FLYING
                    && (field.gravity || hasItem(defender, "Iron Ball")
                    || move != null && move.id().equals("thousandarrows"));
            if ((factor == 0.0 && hasItem(defender, "Ring Target")) || bypassGhostImmunity || groundedFlying) {
                factor = 1.0;
            }
            if (move != null && move.id().equals("freezedry") && defendingType == PokeType.WATER) {
                factor = 2.0;
            }
            effectiveness *= factor;
        }
        if (field.weather == Weather.STRONG_WINDS && defender.defensiveTypes().contains(PokeType.FLYING)
                && TYPE_CHART.get(attackingType).getOrDefault(PokeType.FLYING, 1.0) > 1.0) {
            effectiveness *= 0.5;
            notes.add("Strong Winds");
        }
        if (move != null && move.id().equals("flyingpress")) {
            effectiveness *= rawTypeEffectiveness(PokeType.FLYING, defender.defensiveTypes());
        }
        if (effectiveness > 0.0 && !ignoresDefensiveAbilities(attacker, move)
                && hasAbility(defender, "Tera Shell") && defender.visibleHp() == defender.maxHp()) {
            effectiveness = 0.5;
            notes.add("Tera Shell");
        }
        if (effectiveness > 1.0) {
            notes.add("super effective x" + trim(effectiveness));
        } else if (effectiveness < 1.0) {
            notes.add("resisted x" + trim(effectiveness));
        }
        return effectiveness;
    }

    static PokeType effectiveMoveType(PokemonSet attacker, MoveData move, FieldState field, List<String> notes) {
        if (hasAbility(attacker, "Normalize")) {
            notes.add("Normalize");
            return PokeType.NORMAL;
        }
        if (move.id().equals("weatherball") && weatherBallUsesWeather(attacker, field.weather)) {
            PokeType type = switch (field.weather) {
                case SUN, HARSH_SUN -> PokeType.FIRE;
                case RAIN, HEAVY_RAIN -> PokeType.WATER;
                case SAND -> PokeType.ROCK;
                case SNOW -> PokeType.ICE;
                case NONE, STRONG_WINDS -> PokeType.NORMAL;
            };
            notes.add("Weather Ball " + type.displayName());
            return type;
        }
        if (move.id().equals("terrainpulse") && isGrounded(attacker, field) && field.terrain != Terrain.NONE) {
            PokeType type = terrainType(field.terrain);
            notes.add("Terrain Pulse " + type.displayName());
            return type;
        }
        if (move.id().equals("terablast") && attacker.terastallized && attacker.teraType != PokeType.NONE) {
            notes.add("Tera Blast " + attacker.teraType.displayName());
            return attacker.teraType;
        }
        if (move.id().equals("revelationdance")) {
            return attacker.terastallized && attacker.teraType != PokeType.NONE
                    ? attacker.teraType : attacker.species.primaryType();
        }
        if (move.id().equals("aurawheel") && hasAspect(attacker, "hangry")) {
            return PokeType.DARK;
        }
        if (move.id().equals("ragingbull")) {
            return attacker.defensiveTypes().contains(PokeType.FIRE) ? PokeType.FIRE
                    : attacker.defensiveTypes().contains(PokeType.WATER) ? PokeType.WATER : PokeType.FIGHTING;
        }
        PokeType itemType = itemMoveType(attacker.item, move.id());
        if (itemType != PokeType.NONE) {
            notes.add(move.name() + " " + itemType.displayName());
            return itemType;
        }
        if (move.type() == PokeType.NORMAL) {
            if (hasAbility(attacker, "Aerilate")) {
                notes.add("Aerilate");
                return PokeType.FLYING;
            }
            if (hasAbility(attacker, "Galvanize")) {
                notes.add("Galvanize");
                return PokeType.ELECTRIC;
            }
            if (hasAbility(attacker, "Pixilate")) {
                notes.add("Pixilate");
                return PokeType.FAIRY;
            }
            if (hasAbility(attacker, "Refrigerate")) {
                notes.add("Refrigerate");
                return PokeType.ICE;
            }
        }
        if (hasAbility(attacker, "Liquid Voice") && hasMoveFlag(move, "sound", SOUND_MOVES)) {
            notes.add("Liquid Voice");
            return PokeType.WATER;
        }
        return move.type();
    }

    static int effectivePower(PokemonSet attacker, PokemonSet defender, MoveData move, PokeType moveType, FieldState field,
                                      SideConditions attackerSide, SideConditions defenderSide, List<String> notes,
                                      List<CalcWarning> warnings) {
        int power = variableBasePower(attacker, defender, move, field, attackerSide, defenderSide, notes, warnings);
        if (move.id().equals("weatherball") && weatherBallUsesWeather(attacker, field.weather)) {
            power *= 2;
            notes.add("Weather Ball power x2");
        }
        if (move.id().equals("terrainpulse") && isGrounded(attacker, field) && field.terrain != Terrain.NONE) {
            power *= 2;
            notes.add("Terrain Pulse power x2");
        }
        if (move.id().equals("steelroller") && field.terrain == Terrain.NONE) {
            notes.add("Steel Roller needs terrain");
            return 0;
        }
        ArrayList<Integer> modifiers = new ArrayList<>();
        if (move.id().equals("knockoff") && knockOffBoostApplies(defender)) {
            modifiers.add(THREE_HALVES);
            notes.add("Knock Off power x1.5");
        }
        if ((move.id().equals("solarbeam") || move.id().equals("solarblade"))
                && (isRain(field.weather) || field.weather == Weather.SAND || field.weather == Weather.SNOW)) {
            modifiers.add(HALF);
            notes.add(move.name() + " weather x0.5");
        }
        if ((move.id().equals("fishiousrend") || move.id().equals("boltbeak"))
                && movesBefore(attacker, defender, field, attackerSide, defenderSide)) {
            power *= 2;
            notes.add(move.name() + " first x2");
        }
        if ((move.id().equals("collisioncourse") || move.id().equals("electrodrift"))
                && typeEffectiveness(move, moveType, defender, attacker, field, new ArrayList<>()) > 1.0) {
            modifiers.add(5461);
            notes.add(move.name() + " super effective boost");
        }
        if (hasAbility(attacker, "Technician") && power <= 60) {
            modifiers.add(THREE_HALVES);
            notes.add("Technician");
        }
        if (hasAbility(attacker, "Flare Boost") && attacker.status == StatusCondition.BURN
                && move.category() == DamageCategory.SPECIAL) {
            modifiers.add(THREE_HALVES);
            notes.add("Flare Boost");
        }
        if (hasAbility(attacker, "Toxic Boost") && attacker.status == StatusCondition.POISON
                && move.category() == DamageCategory.PHYSICAL) {
            modifiers.add(THREE_HALVES);
            notes.add("Toxic Boost");
        }
        if (hasAbility(attacker, "Strong Jaw") && hasMoveFlag(move, "bite", BITE_MOVES)) {
            modifiers.add(THREE_HALVES);
            notes.add("Strong Jaw");
        }
        if (hasAbility(attacker, "Iron Fist") && hasMoveFlag(move, "punch", PUNCH_MOVES)) {
            modifiers.add(4915);
            notes.add("Iron Fist");
        }
        if (hasAbility(attacker, "Sharpness") && hasMoveFlag(move, "slicing", SLICING_MOVES)) {
            modifiers.add(THREE_HALVES);
            notes.add("Sharpness");
        }
        if (hasAbility(attacker, "Mega Launcher") && hasMoveFlag(move, "pulse", PULSE_MOVES)) {
            modifiers.add(THREE_HALVES);
            notes.add("Mega Launcher");
        }
        if (hasAbility(attacker, "Steely Spirit") && moveType == PokeType.STEEL) {
            modifiers.add(THREE_HALVES);
            notes.add("Steely Spirit");
        }
        if (hasAbility(attacker, "Tough Claws") && isContactMove(move)
                && !punchingGloveRemovesContact(attacker, move)) {
            modifiers.add(5325);
            notes.add("Tough Claws");
        }
        if (hasAbility(attacker, "Sheer Force") && move.hasFlag("secondary")) {
            modifiers.add(5325);
            notes.add("Sheer Force");
        }
        if (hasAbility(attacker, "Analytic")
                && movesAfter(attacker, defender, field, attackerSide, defenderSide)) {
            modifiers.add(5325);
            notes.add("Analytic");
        }
        if (hasAbility(attacker, "Punk Rock") && hasMoveFlag(move, "sound", SOUND_MOVES)) {
            modifiers.add(5325);
            notes.add("Punk Rock");
        }
        if (hasAbility(attacker, "Sand Force") && field.weather == Weather.SAND
                && (moveType == PokeType.ROCK || moveType == PokeType.GROUND || moveType == PokeType.STEEL)) {
            modifiers.add(5325);
            notes.add("Sand Force");
        }
        if (hasAbility(attacker, "Reckless") && hasMoveFlag(move, "recoil", RECOIL_MOVES)) {
            modifiers.add(4915);
            notes.add("Reckless");
        }
        if (hasAbility(attacker, "Normalize") || ((hasAbility(attacker, "Aerilate") || hasAbility(attacker, "Galvanize")
                || hasAbility(attacker, "Pixilate") || hasAbility(attacker, "Refrigerate")) && move.type() == PokeType.NORMAL)) {
            modifiers.add(4915);
            notes.add("type ability x1.2");
        }
        if (field.doubles && (field.helpingHand || attackerSide.helpingHand)) {
            modifiers.add(THREE_HALVES);
            notes.add("Helping Hand");
        }
        if (field.terrain == Terrain.GRASSY && isGrounded(defender, field)
                && (move.id().equals("earthquake") || move.id().equals("bulldoze") || move.id().equals("magnitude"))) {
            modifiers.add(HALF);
            notes.add("Grassy Terrain halves " + move.name());
        }
        if (isGrounded(attacker, field) && field.terrain == Terrain.ELECTRIC && moveType == PokeType.ELECTRIC) {
            modifiers.add(5325);
            notes.add("Electric Terrain");
        }
        if (field.terrain == Terrain.ELECTRIC && move.id().equals("risingvoltage") && isGrounded(defender, field)) {
            power *= 2;
            notes.add("Rising Voltage terrain x2");
        }
        if (isGrounded(attacker, field) && field.terrain == Terrain.GRASSY && moveType == PokeType.GRASS) {
            modifiers.add(5325);
            notes.add("Grassy Terrain");
        }
        if (isGrounded(attacker, field) && field.terrain == Terrain.PSYCHIC && moveType == PokeType.PSYCHIC) {
            modifiers.add(5325);
            notes.add("Psychic Terrain");
        }
        if (field.terrain == Terrain.PSYCHIC && move.id().equals("expandingforce") && isGrounded(attacker, field)) {
            modifiers.add(THREE_HALVES);
            notes.add("Expanding Force terrain x1.5");
        }
        if (field.terrain == Terrain.MISTY && move.id().equals("mistyexplosion") && isGrounded(attacker, field)) {
            modifiers.add(THREE_HALVES);
            notes.add("Misty Explosion terrain x1.5");
        }
        if (field.terrain == Terrain.ELECTRIC && move.id().equals("psyblade")) {
            modifiers.add(THREE_HALVES);
            notes.add("Psyblade terrain x1.5");
        }
        if (field.gravity && move.id().equals("gravapple")) {
            modifiers.add(THREE_HALVES);
            notes.add("Grav Apple gravity x1.5");
        }
        if (isGrounded(defender, field) && field.terrain == Terrain.MISTY && moveType == PokeType.DRAGON) {
            modifiers.add(HALF);
            notes.add("Misty Terrain");
        }
        if (hasAbility(defender, "Dry Skin") && moveType == PokeType.FIRE
                && !ignoresDefensiveAbilities(attacker, move)) {
            modifiers.add(5120);
            notes.add("Dry Skin fire weakness");
        }
        int batteries = partnerAbilityCount(attackerSide, "Battery");
        if (field.doubles && move.category() == DamageCategory.SPECIAL && batteries > 0) {
            for (int i = 0; i < batteries; i++) modifiers.add(5325);
            notes.add(batteries == 1 ? "Partner Battery" : "Partner Battery x" + batteries);
        }
        int powerSpots = partnerAbilityCount(attackerSide, "Power Spot");
        if (field.doubles && powerSpots > 0) {
            for (int i = 0; i < powerSpots; i++) modifiers.add(5325);
            notes.add(powerSpots == 1 ? "Partner Power Spot" : "Partner Power Spot x" + powerSpots);
        }
        if (hasAbility(attacker, "Supreme Overlord") && attacker.battleHistoryKnown && attacker.faintedAllies > 0) {
            int allies = Math.min(5, attacker.faintedAllies);
            int[] modifiersByAllies = {Q12, 4506, 4915, 5325, 5734, 6144};
            modifiers.add(modifiersByAllies[allies]);
            notes.add("Supreme Overlord x" + trim(1.0 + 0.1 * allies));
        }
        int aura = auraPowerModifier(attacker, defender, moveType, attackerSide, defenderSide, notes);
        if (aura != Q12) modifiers.add(aura);
        addItemBasePowerModifiers(modifiers, attacker, move, moveType, notes);
        return Math.max(1, roundQ12(power, chainModifiers(modifiers, 41)));
    }

    private static int variableBasePower(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field,
                                         SideConditions attackerSide, SideConditions defenderSide, List<String> notes,
                                         List<CalcWarning> warnings) {
        String id = move.id();
        int power = move.basePower();
        if (id.equals("gyroball")) {
            power = Math.min(150, 1 + 25 * speedStat(defender, field, defenderSide)
                    / Math.max(1, speedStat(attacker, field, attackerSide)));
        } else if (id.equals("electroball")) {
            double ratio = speedStat(attacker, field, attackerSide)
                    / (double) Math.max(1, speedStat(defender, field, defenderSide));
            power = ratio >= 4 ? 150 : ratio >= 3 ? 120 : ratio >= 2 ? 80 : ratio >= 1 ? 60 : 40;
        } else if (id.equals("lowkick") || id.equals("grassknot")) {
            power = weightBasedPower(effectiveWeight(defender, ignoresDefensiveAbilities(attacker, move)));
        } else if (id.equals("heavyslam") || id.equals("heatcrash")) {
            double ratio = effectiveWeight(attacker, false)
                    / Math.max(0.1, effectiveWeight(defender, ignoresDefensiveAbilities(attacker, move)));
            power = ratio >= 5 ? 120 : ratio >= 4 ? 100 : ratio >= 3 ? 80 : ratio >= 2 ? 60 : 40;
        } else if (id.equals("storedpower") || id.equals("powertrip")) {
            int positiveBoosts = 0;
            for (Stat stat : new Stat[]{Stat.ATK, Stat.DEF, Stat.SPA, Stat.SPD, Stat.SPE}) {
                positiveBoosts += Math.max(0, attacker.boosts.get(stat));
            }
            power = 20 + 20 * positiveBoosts;
        } else if (id.equals("punishment")) {
            int positiveBoosts = 0;
            for (Stat stat : new Stat[]{Stat.ATK, Stat.DEF, Stat.SPA, Stat.SPD, Stat.SPE}) {
                positiveBoosts += Math.max(0, defender.boosts.get(stat));
            }
            power = Math.min(200, 60 + 20 * positiveBoosts);
        } else if (id.equals("ragefist") && attacker.battleHistoryKnown) {
            power = Math.min(350, 50 + 50 * attacker.timesHit);
        } else if (id.equals("lastrespects") && attacker.battleHistoryKnown) {
            power = Math.min(300, 50 + 50 * attacker.faintedAllies);
        } else if (id.equals("furycutter") && attacker.battleHistoryKnown) {
            power = Math.min(160, move.basePower() * (1 << Math.min(3, Math.max(0, attacker.consecutiveMoveUses))));
        } else if ((id.equals("rollout") || id.equals("iceball")) && attacker.battleHistoryKnown) {
            power = move.basePower() * (1 << Math.min(4, Math.max(0, attacker.consecutiveMoveUses)));
            if (attacker.defenseCurlUsed) power *= 2;
        } else if (id.equals("echoedvoice") && attacker.battleHistoryKnown) {
            power = Math.min(200, move.basePower() + 40 * Math.max(0, attacker.echoedVoiceChain));
        } else if (id.equals("retaliate") && attacker.battleHistoryKnown && attacker.allyFaintedPreviousTurn) {
            power *= 2;
        } else if ((id.equals("stompingtantrum") || id.equals("temperflare"))
                && attacker.battleHistoryKnown && attacker.lastMoveFailed) {
            power *= 2;
        } else if ((id.equals("avalanche") || id.equals("revenge"))
                && attacker.battleHistoryKnown && attacker.lastDamageTaken > 0) {
            power *= 2;
        } else if (id.equals("payback")
                && movesAfter(attacker, defender, field, attackerSide, defenderSide)) {
            power *= 2;
        } else if (id.equals("watershuriken") && hasAspect(attacker, "ash")) {
            power = 20;
        } else if (id.equals("eruption") || id.equals("waterspout") || id.equals("dragonenergy")) {
            power = Math.max(1, move.basePower() * attacker.visibleHp() / attacker.maxHp());
        } else if (id.equals("flail") || id.equals("reversal")) {
            int ratio = 48 * attacker.visibleHp() / attacker.maxHp();
            power = ratio <= 1 ? 200 : ratio <= 4 ? 150 : ratio <= 9 ? 100 : ratio <= 16 ? 80 : ratio <= 32 ? 40 : 20;
        } else if (id.equals("crushgrip") || id.equals("wringout")) {
            power = Math.max(1, 1 + 120 * defender.visibleHp() / defender.maxHp());
        } else if (id.equals("hardpress")) {
            power = Math.max(1, 100 * defender.visibleHp() / defender.maxHp());
        } else if (id.equals("facade") && (attacker.status == StatusCondition.BURN
                || attacker.status == StatusCondition.POISON || attacker.status == StatusCondition.PARALYSIS)) {
            power *= 2;
        } else if ((id.equals("hex") || id.equals("infernalparade")) && defender.status != StatusCondition.NONE) {
            power *= 2;
        } else if ((id.equals("venoshock") || id.equals("barbbarrage")) && defender.status == StatusCondition.POISON) {
            power *= 2;
        } else if (id.equals("brine") && defender.visibleHp() * 2 <= defender.maxHp()) {
            power *= 2;
        } else if (id.equals("acrobatics") && hasItem(attacker, "None")) {
            power *= 2;
        } else if (id.equals("wakeupslap") && defender.status == StatusCondition.SLEEP) {
            power *= 2;
        } else if (id.equals("smellingsalts") && defender.status == StatusCondition.PARALYSIS) {
            power *= 2;
        }
        if (power != move.basePower()) notes.add(move.name() + " power " + power);
        if (power <= 0) {
            warnings.add(CalcWarning.of("variable_power_fallback"));
            return Math.max(1, move.basePower());
        }
        return power;
    }

    private static int weightBasedPower(double weightKg) {
        if (weightKg < 10) return 20;
        if (weightKg < 25) return 40;
        if (weightKg < 50) return 60;
        if (weightKg < 100) return 80;
        if (weightKg < 200) return 100;
        return 120;
    }

    private static double effectiveWeight(PokemonSet pokemon, boolean ignoreAbility) {
        double weight = Math.max(0.1, pokemon.species.weightKg());
        if (!ignoreAbility && hasAbility(pokemon, "Heavy Metal")) weight *= 2.0;
        if (!ignoreAbility && hasAbility(pokemon, "Light Metal")) weight *= 0.5;
        if (hasItem(pokemon, "Float Stone")) weight *= 0.5;
        return Math.max(0.1, Math.floor(weight * 10.0) / 10.0);
    }

    private static boolean knockOffBoostApplies(PokemonSet defender) {
        String item = normalize(defender.item);
        return !item.isBlank() && !item.equals("none") && !isUnremovableItem(defender, item);
    }

    private static boolean isUnremovableItem(PokemonSet defender, String item) {
        String species = normalize(defender.species.id()) + normalize(defender.species.name())
                + normalize(defender.species.cobblemonSpeciesId());
        if (item.equals("boosterenergy") && defender.paradoxBoostActive) return true;
        if (species.contains("dialgaorigin") && item.equals("adamantcrystal")) return true;
        if (species.contains("palkiaorigin") && item.equals("lustrousglobe")) return true;
        if (species.contains("giratinaorigin") && item.contains("griseous")) return true;
        if (species.contains("arceus") && item.endsWith("plate")) return true;
        if (species.contains("genesect") && item.endsWith("drive")) return true;
        if (species.contains("groudon") && item.equals("redorb")) return true;
        if (species.contains("kyogre") && item.equals("blueorb")) return true;
        if (species.contains("silvally") && item.endsWith("memory")) return true;
        if (item.endsWith("iumz")) return true;
        if (species.contains("zacian") && item.equals("rustedsword")) return true;
        if (species.contains("zamazenta") && item.equals("rustedshield")) return true;
        if (species.contains("ogerponcornerstone") && item.equals("cornerstonemask")) return true;
        if (species.contains("ogerponhearthflame") && item.equals("hearthflamemask")) return true;
        if (species.contains("ogerponwellspring") && item.equals("wellspringmask")) return true;
        if (species.contains("venomiconepilogue") && item.equals("vilevial")) return true;
        String megaHolder = MEGA_STONE_HOLDERS.get(item);
        return megaHolder != null && species.contains(megaHolder);
    }

    private static FixedDamage fixedDamage(PokemonSet attacker, PokemonSet defender, MoveData move) {
        if (OHKO_MOVES.contains(move.id())) {
            return new FixedDamage(defender.visibleHp(), defender.visibleHp(), move.id());
        }
        int damage = switch (move.id()) {
            case "seismictoss", "nightshade" -> attacker.level;
            case "dragonrage" -> 40;
            case "sonicboom" -> 20;
            case "superfang", "ruination", "naturesmadness" -> Math.max(1, defender.visibleHp() / 2);
            case "guardianofalola" -> Math.max(1, (int) Math.floor(defender.visibleHp() * 0.75));
            case "endeavor" -> Math.max(0, defender.visibleHp() - attacker.visibleHp());
            case "finalgambit" -> attacker.visibleHp();
            case "counter" -> attacker.lastDamageCategory == DamageCategory.PHYSICAL
                    ? attacker.lastDamageTaken * 2 : 0;
            case "mirrorcoat" -> attacker.lastDamageCategory == DamageCategory.SPECIAL
                    ? attacker.lastDamageTaken * 2 : 0;
            case "metalburst", "comeuppance" -> (int) Math.floor(attacker.lastDamageTaken * 1.5);
            default -> -1;
        };
        if (damage >= 0) {
            return new FixedDamage(damage, damage, move.id());
        }
        if (move.id().equals("psywave")) {
            return new FixedDamage(Math.max(1, attacker.level / 2), Math.max(1, attacker.level * 3 / 2), move.id());
        }
        return null;
    }

    private static DamageResult fixedDamageResult(PokemonSet attacker, PokemonSet defender, MoveData move,
                                                  FixedDamage fixed, HitProfile hits,
                                                  List<CalcWarning> warnings) {
        if (fixed.maxDamage() <= 0) {
            return DamageResult.zero(move, "condition not met", warnings);
        }
        HashMap<Integer, Double> singleHit = new HashMap<>();
        double probability = 1.0 / (fixed.maxDamage() - fixed.minDamage() + 1);
        for (int damage = fixed.minDamage(); damage <= fixed.maxDamage(); damage++) {
            singleHit.put(damage, probability);
        }
        boolean parentalBond = hits.parentalBond();
        HashMap<Integer, Double> distribution = new HashMap<>();
        if (parentalBond) {
            Map<Integer, Double> firstHit = hits.blockedFirstHit() ? Map.of(0, 1.0) : singleHit;
            int visibleHp = defender.visibleHp();
            for (Map.Entry<Integer, Double> first : firstHit.entrySet()) {
                int firstDamage = Math.min(visibleHp, first.getKey());
                if (firstDamage >= visibleHp) {
                    distribution.merge(visibleHp, first.getValue(), Double::sum);
                    continue;
                }
                PokemonSet afterFirst = defender.copy();
                afterFirst.currentHp = visibleHp - firstDamage;
                FixedDamage secondFixed = fixedDamage(attacker, afterFirst, move);
                if (secondFixed == null || secondFixed.maxDamage() <= 0) {
                    distribution.merge(firstDamage, first.getValue(), Double::sum);
                    continue;
                }
                Map<Integer, Double> secondHit = fixedDamageDistribution(secondFixed);
                for (Map.Entry<Integer, Double> second : secondHit.entrySet()) {
                    int damage = Math.min(visibleHp, firstDamage + second.getKey());
                    distribution.merge(damage, first.getValue() * second.getValue(), Double::sum);
                }
            }
        } else if (hits.blockedFirstHit()) {
            distribution.put(0, 1.0);
        } else {
            for (Map.Entry<Integer, Double> roll : singleHit.entrySet()) {
                distribution.merge(Math.min(defender.visibleHp(), roll.getKey()), roll.getValue(), Double::sum);
            }
        }
        int min = distribution.keySet().stream().min(Integer::compareTo).orElse(0);
        int max = distribution.keySet().stream().max(Integer::compareTo).orElse(0);
        double minPercent = min * 100.0 / defender.maxHp();
        double maxPercent = max * 100.0 / defender.maxHp();
        String koChance = koChance(distribution, defender.visibleHp());
        if ((!parentalBond || hits.blockedFirstHit()) && max >= defender.visibleHp()
                && defender.visibleHp() == defender.maxHp()
                && (hasItem(defender, "Focus Sash")
                || hasAbility(defender, "Sturdy") && !ignoresDefensiveAbilities(attacker, move))) {
            koChance = min >= defender.visibleHp() ? "survives at 1 HP" : "cannot OHKO from full HP";
        }
        String line = attacker.species.name() + " " + move.name() + " vs. " + defender.species.name()
                + ": " + min + "-" + max + " (" + percent(minPercent) + " - " + percent(maxPercent)
                + "%) -- " + koChance;
        ArrayList<Integer> rolls = new ArrayList<>(distribution.keySet());
        rolls.sort(Integer::compareTo);
        if (rolls.size() > 16) rolls.subList(16, rolls.size()).clear();
        ArrayList<String> notes = new ArrayList<>();
        notes.add("fixed damage: " + fixed.reason());
        if (parentalBond) notes.add("Parental Bond: 2 hits");
        if (hits.blockedFirstHit()) notes.add("first hit blocked by " + defender.ability);
        return new DamageResult(move, min, max, minPercent, maxPercent, List.copyOf(rolls), 0, koChance,
                line, move.name() + " " + min + "-" + max + " (" + percent(minPercent) + "-"
                + percent(maxPercent) + "%) | " + koChance, List.copyOf(notes),
                List.copyOf(warnings));
    }

    private static Map<Integer, Double> fixedDamageDistribution(FixedDamage fixed) {
        HashMap<Integer, Double> distribution = new HashMap<>();
        double probability = 1.0 / (fixed.maxDamage() - fixed.minDamage() + 1);
        for (int damage = fixed.minDamage(); damage <= fixed.maxDamage(); damage++) {
            distribution.put(damage, probability);
        }
        return distribution;
    }

    private static void addContextWarnings(PokemonSet attacker, PokemonSet defender, MoveData move, List<CalcWarning> warnings) {
        if (!attacker.itemKnown || !attacker.abilityKnown || !attacker.natureKnown || !attacker.statsKnown) {
            warnings.add(CalcWarning.of("attacker_set_unknown"));
        }
        if (!defender.itemKnown || !defender.abilityKnown || !defender.natureKnown || !defender.statsKnown) {
            warnings.add(CalcWarning.of("target_set_unknown"));
        }
        if (ATTACKER_HP_POWER_MOVES.contains(move.id()) && attacker.currentHp < 0) {
            warnings.add(CalcWarning.of("attacker_hp_unknown"));
        }
        if (DEFENDER_HP_POWER_MOVES.contains(move.id()) && defender.currentHp < 0) {
            warnings.add(CalcWarning.of("target_hp_unknown"));
        }
        if (WEIGHT_POWER_MOVES.contains(move.id())
                && (defender.species.weightKg() <= 0.0
                || ((move.id().equals("heavyslam") || move.id().equals("heatcrash")) && attacker.species.weightKg() <= 0.0))) {
            warnings.add(CalcWarning.of("weight_unavailable"));
        }
        if (UNSUPPORTED_CONTEXT_MOVES.contains(move.id())) {
            warnings.add(CalcWarning.of("turn_context", move.name()));
        }
        if ((move.id().equals("ragefist") || move.id().equals("lastrespects") || move.id().equals("furycutter")
                || move.id().equals("rollout") || move.id().equals("iceball") || move.id().equals("echoedvoice")
                || move.id().equals("retaliate") || move.id().equals("stompingtantrum")
                || move.id().equals("temperflare") || move.id().equals("avalanche")
                || move.id().equals("revenge") || REACTIVE_DAMAGE_MOVES.contains(move.id()))
                && !attacker.battleHistoryKnown) {
            warnings.add(CalcWarning.of("battle_history", move.name()));
        }
        if (hasAbility(attacker, "Stakeout") && !attacker.battleHistoryKnown) warnings.add(CalcWarning.of("stakeout"));
        if (hasAbility(attacker, "Supreme Overlord") && !attacker.battleHistoryKnown) warnings.add(CalcWarning.of("supreme_overlord"));
        if (hasAbility(attacker, "Rivalry")) warnings.add(CalcWarning.of("rivalry"));
        if (hasAbility(attacker, "Download")) warnings.add(CalcWarning.of("download"));
        if (hasAbility(attacker, "Analytic")) warnings.add(CalcWarning.of("move_order"));
        if (hasAbility(attacker, "Slow Start") && attacker.turnsActive < 0) {
            warnings.add(CalcWarning.of("slow_start"));
        }
        if (hasItem(attacker, "Metronome") && !attacker.battleHistoryKnown) {
            warnings.add(CalcWarning.of("metronome"));
        }
        if (hasAbility(attacker, "Flash Fire") && !attacker.battleHistoryKnown) {
            warnings.add(CalcWarning.of("flash_fire"));
        }
    }

    private static PokeType terrainType(Terrain terrain) {
        return switch (terrain) {
            case ELECTRIC -> PokeType.ELECTRIC;
            case GRASSY -> PokeType.GRASS;
            case MISTY -> PokeType.FAIRY;
            case PSYCHIC -> PokeType.PSYCHIC;
            case NONE -> PokeType.NORMAL;
        };
    }

    private static int offensiveStat(PokemonSet attacker, PokemonSet defender, MoveData move, PokeType moveType,
                                     FieldState field,
                                     SideConditions attackerSide, SideConditions defenderSide,
                                     boolean criticalHit, List<String> notes) {
        Stat stat = move.category() == DamageCategory.PHYSICAL ? Stat.ATK : Stat.SPA;
        Stat rawStat = stat;
        Stat boostStat = stat;
        PokemonSet statOwner = attacker;
        if (move.id().equals("bodypress")) {
            stat = Stat.DEF;
            rawStat = Stat.DEF;
            boostStat = field.wonderRoom ? Stat.SPD : Stat.DEF;
            notes.add("Body Press uses Defense");
        } else if (move.id().equals("foulplay")) {
            statOwner = defender;
            stat = Stat.ATK;
            rawStat = Stat.ATK;
            boostStat = Stat.ATK;
            notes.add("Foul Play uses target Attack");
        }
        PokemonSet copy = statOwner.copy();
        if (criticalHit && copy.boosts.getOrDefault(boostStat, 0) < 0) {
            copy.boosts.put(boostStat, 0);
        }
        boolean ignoreBoosts = !ignoresDefensiveAbilities(attacker, move) && hasAbility(defender, "Unaware");
        int value = storedStatWithBoost(copy, rawStat, boostStat, ignoreBoosts);
        // Hustle is applied directly to the selected offensive value before the
        // remaining attack modifiers. This also matters for Foul Play rounding.
        if (move.category() == DamageCategory.PHYSICAL && hasAbility(attacker, "Hustle")) {
            value = roundQ12(value, THREE_HALVES);
            notes.add("Hustle");
        }
        ArrayList<Integer> modifiers = new ArrayList<>();
        boolean physical = move.category() == DamageCategory.PHYSICAL;
        boolean special = move.category() == DamageCategory.SPECIAL;
        if (hasAbility(attacker, "Defeatist") && attacker.visibleHp() * 2 <= attacker.maxHp())
            addModifier(modifiers, HALF, notes, "Defeatist");
        if (physical && slowStartActive(attacker)) addModifier(modifiers, HALF, notes, "Slow Start");
        if (special && hasAbility(attacker, "Solar Power") && isSun(field.weather)
                && !hasItem(attacker, "Utility Umbrella"))
            addModifier(modifiers, THREE_HALVES, notes, "Solar Power");
        if (physical && hasAbility(attacker, "Flower Gift") && isSun(field.weather)
                && !hasItem(attacker, "Utility Umbrella"))
            addModifier(modifiers, THREE_HALVES, notes, "Flower Gift");
        if (physical && hasAbility(attacker, "Gorilla Tactics"))
            addModifier(modifiers, THREE_HALVES, notes, "Gorilla Tactics");
        if (physical && hasAbility(attacker, "Guts") && attacker.status != StatusCondition.NONE)
            addModifier(modifiers, THREE_HALVES, notes, "Guts");
        if (lowHp(attacker)) {
            if (hasAbility(attacker, "Overgrow") && moveType == PokeType.GRASS)
                addModifier(modifiers, THREE_HALVES, notes, "Overgrow");
            if (hasAbility(attacker, "Blaze") && moveType == PokeType.FIRE)
                addModifier(modifiers, THREE_HALVES, notes, "Blaze");
            if (hasAbility(attacker, "Torrent") && moveType == PokeType.WATER)
                addModifier(modifiers, THREE_HALVES, notes, "Torrent");
            if (hasAbility(attacker, "Swarm") && moveType == PokeType.BUG)
                addModifier(modifiers, THREE_HALVES, notes, "Swarm");
        }
        if (field.doubles && special && (hasAbility(attacker, "Plus") || hasAbility(attacker, "Minus"))
                && (hasPartnerAbility(attackerSide, "Plus") || hasPartnerAbility(attackerSide, "Minus")))
            addModifier(modifiers, THREE_HALVES, notes, "Plus/Minus");
        if (hasAbility(attacker, "Flash Fire") && attacker.flashFireActive && moveType == PokeType.FIRE)
            addModifier(modifiers, THREE_HALVES, notes, "Flash Fire");
        if (hasAbility(attacker, "Steelworker") && moveType == PokeType.STEEL)
            addModifier(modifiers, THREE_HALVES, notes, "Steelworker");
        if (hasAbility(attacker, "Rocky Payload") && moveType == PokeType.ROCK)
            addModifier(modifiers, THREE_HALVES, notes, "Rocky Payload");
        if (hasAbility(attacker, "Dragon's Maw") && moveType == PokeType.DRAGON)
            addModifier(modifiers, THREE_HALVES, notes, "Dragon's Maw");
        if (hasAbility(attacker, "Transistor") && moveType == PokeType.ELECTRIC)
            addModifier(modifiers, 5325, notes, "Transistor");
        if (hasAbility(attacker, "Stakeout") && defender.switchedInThisTurn)
            addModifier(modifiers, 8192, notes, "Stakeout");
        if (hasAbility(attacker, "Water Bubble") && moveType == PokeType.WATER)
            addModifier(modifiers, 8192, notes, "Water Bubble");
        if (physical && (hasAbility(attacker, "Huge Power") || hasAbility(attacker, "Pure Power")))
            addModifier(modifiers, 8192, notes, attacker.ability);

        if (field.doubles && physical && isSun(field.weather)
                && !hasItem(attacker, "Utility Umbrella") && hasPartnerAbility(attackerSide, "Flower Gift"))
            addModifier(modifiers, THREE_HALVES, notes, "Partner Flower Gift");
        int steelySpirits = partnerAbilityCount(attackerSide, "Steely Spirit");
        if (field.doubles && moveType == PokeType.STEEL && steelySpirits > 0) {
            for (int i = 0; i < steelySpirits; i++) modifiers.add(THREE_HALVES);
            notes.add(steelySpirits == 1 ? "Partner Steely Spirit" : "Partner Steely Spirit x" + steelySpirits);
        }

        if (!ignoresDefensiveAbilities(attacker, move)) {
            if (hasAbility(defender, "Thick Fat") && (moveType == PokeType.FIRE || moveType == PokeType.ICE))
                addModifier(modifiers, HALF, notes, "Thick Fat");
            if (hasAbility(defender, "Water Bubble") && moveType == PokeType.FIRE)
                addModifier(modifiers, HALF, notes, "Water Bubble fire resist");
            if (hasAbility(defender, "Purifying Salt") && moveType == PokeType.GHOST)
                addModifier(modifiers, HALF, notes, "Purifying Salt");
            if (hasAbility(defender, "Heatproof") && moveType == PokeType.FIRE)
                addModifier(modifiers, HALF, notes, "Heatproof");
        }

        if (physical && !hasAbility(attacker, "Tablets of Ruin")
                && (hasAbility(defender, "Tablets of Ruin") || hasPartnerAbility(attackerSide, "Tablets of Ruin")
                || hasPartnerAbility(defenderSide, "Tablets of Ruin")))
            addModifier(modifiers, THREE_QUARTERS, notes, "Tablets of Ruin");
        if (special && !hasAbility(attacker, "Vessel of Ruin")
                && (hasAbility(defender, "Vessel of Ruin") || hasPartnerAbility(attackerSide, "Vessel of Ruin")
                || hasPartnerAbility(defenderSide, "Vessel of Ruin")))
            addModifier(modifiers, THREE_QUARTERS, notes, "Vessel of Ruin");

        Stat offensiveStat = physical ? Stat.ATK : Stat.SPA;
        if (paradoxBoosts(attacker, field) && highestStat(attacker, field, false) == offensiveStat)
            addModifier(modifiers, 5325, notes, attacker.ability);
        if (physical && hasAbility(attacker, "Orichalcum Pulse") && isSun(field.weather)
                && !hasItem(attacker, "Utility Umbrella"))
            addModifier(modifiers, 5461, notes, "Orichalcum Pulse");
        if (special && hasAbility(attacker, "Hadron Engine") && field.terrain == Terrain.ELECTRIC)
            addModifier(modifiers, 5461, notes, "Hadron Engine");

        if (physical && hasItem(attacker, "Thick Club")
                && (isSpecies(attacker, "cubone") || isSpecies(attacker, "marowak")))
            addModifier(modifiers, 8192, notes, "Thick Club");
        if ((physical || special) && hasItem(attacker, "Light Ball") && isSpecies(attacker, "pikachu"))
            addModifier(modifiers, 8192, notes, "Light Ball");
        if (special && hasItem(attacker, "Deep Sea Tooth") && isSpecies(attacker, "clamperl"))
            addModifier(modifiers, 8192, notes, "Deep Sea Tooth");
        if (physical && hasItem(attacker, "Choice Band"))
            addModifier(modifiers, THREE_HALVES, notes, "Choice Band");
        if (special && hasItem(attacker, "Choice Specs"))
            addModifier(modifiers, THREE_HALVES, notes, "Choice Specs");
        return Math.max(1, roundQ12(value, chainModifiers(modifiers, 410)));
    }

    private static int defensiveStat(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field,
                                     SideConditions attackerSide, SideConditions defenderSide,
                                     boolean criticalHit, List<String> notes) {
        boolean hitsPhysical = move.category() == DamageCategory.PHYSICAL
                || Set.of("psyshock", "psystrike", "secretsword").contains(move.id());
        Stat defenseStat = hitsPhysical ? Stat.DEF : Stat.SPD;
        if (move.category() != DamageCategory.PHYSICAL && hitsPhysical) {
            notes.add(move.name() + " targets Defense");
        }
        Stat rawStat = defenseStat;
        if (field.wonderRoom) {
            rawStat = defenseStat == Stat.DEF ? Stat.SPD : Stat.DEF;
            notes.add("Wonder Room swaps Defense and SpD");
        }
        PokemonSet copy = defender.copy();
        int boost = copy.boosts.getOrDefault(defenseStat, 0);
        if (criticalHit && boost > 0) {
            boost = 0;
        }
        boolean ignoreBoosts = hasAbility(attacker, "Unaware");
        int value = storedStat(copy, rawStat, true);
        if (!ignoreBoosts) value = applyBoost(value, boost);
        // Sand/Snow are direct stat operations. All remaining Defense modifiers
        // are chained first and rounded once, matching the Gen 9 fixed-point order.
        if (!hitsPhysical && field.weather == Weather.SAND
                && copy.defensiveTypes().contains(PokeType.ROCK)) {
            value = roundQ12(value, THREE_HALVES);
            notes.add("Sand rock SpD x1.5");
        }
        if (hitsPhysical && field.weather == Weather.SNOW
                && copy.defensiveTypes().contains(PokeType.ICE)) {
            value = roundQ12(value, THREE_HALVES);
            notes.add("Snow ice Defense x1.5");
        }
        ArrayList<Integer> modifiers = new ArrayList<>();
        boolean ignoreDefensiveAbility = ignoresDefensiveAbilities(attacker, move);
        if (!ignoreDefensiveAbility && hitsPhysical
                && hasAbility(defender, "Marvel Scale") && defender.status != StatusCondition.NONE) {
            addModifier(modifiers, THREE_HALVES, notes, "Marvel Scale");
        } else if (!ignoreDefensiveAbility && !hitsPhysical && isSun(field.weather)
                && !hasItem(defender, "Utility Umbrella") && hasAbility(defender, "Flower Gift")) {
            addModifier(modifiers, THREE_HALVES, notes, "Flower Gift");
        } else if (!ignoreDefensiveAbility && hitsPhysical
                && hasAbility(defender, "Grass Pelt") && field.terrain == Terrain.GRASSY) {
            addModifier(modifiers, THREE_HALVES, notes, "Grass Pelt");
        } else if (!ignoreDefensiveAbility && hitsPhysical && hasAbility(defender, "Fur Coat")) {
            addModifier(modifiers, 8192, notes, "Fur Coat");
        }
        int flowerGifts = field.doubles && !hitsPhysical && isSun(field.weather)
                && !hasItem(defender, "Utility Umbrella")
                ? partnerAbilityCount(defenderSide, "Flower Gift") : 0;
        for (int i = 0; i < flowerGifts; i++) modifiers.add(THREE_HALVES);
        if (flowerGifts > 0) notes.add(flowerGifts == 1 ? "Partner Flower Gift" : "Partner Flower Gift x" + flowerGifts);
        if (hitsPhysical && !hasAbility(defender, "Sword of Ruin")
                && (hasAbility(attacker, "Sword of Ruin")
                || hasPartnerAbility(attackerSide, "Sword of Ruin")
                || hasPartnerAbility(defenderSide, "Sword of Ruin"))) {
            addModifier(modifiers, THREE_QUARTERS, notes, "Sword of Ruin");
        }
        if (!hitsPhysical && !hasAbility(defender, "Beads of Ruin")
                && (hasAbility(attacker, "Beads of Ruin")
                || hasPartnerAbility(attackerSide, "Beads of Ruin")
                || hasPartnerAbility(defenderSide, "Beads of Ruin"))) {
            addModifier(modifiers, THREE_QUARTERS, notes, "Beads of Ruin");
        }
        if (paradoxBoosts(defender, field) && highestStat(defender, field, false) == defenseStat) {
            addModifier(modifiers, 5324, notes, defender.ability);
        }
        if (hasItem(defender, "Eviolite") && defender.species.notFullyEvolved()) {
            addModifier(modifiers, THREE_HALVES, notes, "Eviolite");
        } else if (hasItem(defender, "Assault Vest") && !hitsPhysical) {
            addModifier(modifiers, THREE_HALVES, notes, "Assault Vest");
        } else if (hasItem(defender, "Deep Sea Scale") && isSpecies(defender, "clamperl") && !hitsPhysical) {
            addModifier(modifiers, 8192, notes, "Deep Sea Scale");
        } else if (hasItem(defender, "Metal Powder") && isSpecies(defender, "ditto") && hitsPhysical) {
            addModifier(modifiers, 8192, notes, "Metal Powder");
        }
        return Math.max(1, roundQ12(value, chainModifiers(modifiers, 410)));
    }

    private static int weatherModifier(PokemonSet attacker, PokemonSet defender, MoveData move,
                                       PokeType moveType, FieldState field, List<String> notes) {
        if (isSun(field.weather)) {
            if (move.id().equals("hydrosteam") && moveType == PokeType.WATER
                    && !hasItem(attacker, "Utility Umbrella")) {
                notes.add("Sun Hydro Steam x1.5");
                return THREE_HALVES;
            }
            if (hasItem(defender, "Utility Umbrella")) {
                notes.add("Utility Umbrella");
                return Q12;
            }
            if (moveType == PokeType.FIRE) {
                notes.add("Sun fire x1.5");
                return THREE_HALVES;
            }
            if (moveType == PokeType.WATER) {
                notes.add("Sun water x0.5");
                return HALF;
            }
        }
        if (isRain(field.weather)) {
            if (hasItem(defender, "Utility Umbrella")) {
                notes.add("Utility Umbrella");
                return Q12;
            }
            if (moveType == PokeType.WATER) {
                notes.add("Rain water x1.5");
                return THREE_HALVES;
            }
            if (moveType == PokeType.FIRE) {
                notes.add("Rain fire x0.5");
                return HALF;
            }
        }
        return Q12;
    }

    private static FinalModifierValues finalModifierValues(PokemonSet attacker, PokemonSet defender, MoveData move,
                                                           PokeType moveType, FieldState field,
                                                           SideConditions attackerSide,
                                                           SideConditions defenderSide, double effectiveness,
                                                           boolean criticalHit, List<String> notes) {
        ArrayList<Integer> modifiers = new ArrayList<>();
        boolean breaksScreens = Set.of("brickbreak", "psychicfangs", "ragingbull").contains(move.id());
        if (!field.alliedTarget && !criticalHit && !breaksScreens && !hasAbility(attacker, "Infiltrator")) {
            if (defenderSide.auroraVeil || defenderSide.reflect && move.category() == DamageCategory.PHYSICAL
                    || defenderSide.lightScreen && move.category() == DamageCategory.SPECIAL) {
                modifiers.add(field.doubles ? 2732 : HALF);
                notes.add(defenderSide.auroraVeil ? "Aurora Veil"
                        : move.category() == DamageCategory.PHYSICAL ? "Reflect" : "Light Screen");
            }
        }
        if (hasAbility(attacker, "Neuroforce") && effectiveness > 1.0) {
            addModifier(modifiers, 5120, notes, "Neuroforce");
        } else if (criticalHit && hasAbility(attacker, "Sniper")) {
            addModifier(modifiers, THREE_HALVES, notes, "Sniper");
        } else if (hasAbility(attacker, "Tinted Lens") && effectiveness < 1.0) {
            addModifier(modifiers, 8192, notes, "Tinted Lens");
        }

        int multiscaleInsertion = modifiers.size();
        boolean ignoresAbilities = ignoresDefensiveAbilities(attacker, move);
        if (!ignoresAbilities) {
            if (hasAbility(defender, "Fluffy") && isContactMove(move)
                    && !hasAbility(attacker, "Long Reach") && !punchingGloveRemovesContact(attacker, move)) {
                addModifier(modifiers, HALF, notes, "Fluffy contact resist");
            } else if (hasAbility(defender, "Punk Rock") && hasMoveFlag(move, "sound", SOUND_MOVES)) {
                addModifier(modifiers, HALF, notes, "Punk Rock resist");
            } else if (hasAbility(defender, "Ice Scales") && move.category() == DamageCategory.SPECIAL) {
                addModifier(modifiers, HALF, notes, "Ice Scales");
            }
        }
        if (effectiveness > 1.0 && (hasAbility(defender, "Prism Armor")
                || !ignoresAbilities && (hasAbility(defender, "Filter") || hasAbility(defender, "Solid Rock")))) {
            addModifier(modifiers, THREE_QUARTERS, notes, defender.ability);
        }
        int friendGuards = partnerAbilityCount(defenderSide, "Friend Guard");
        if (field.friendGuard || defenderSide.friendGuard) friendGuards = Math.max(1, friendGuards);
        if (field.doubles && friendGuards > 0) {
            for (int i = 0; i < friendGuards; i++) modifiers.add(THREE_QUARTERS);
            notes.add(friendGuards == 1 ? "Friend Guard" : "Friend Guard x" + friendGuards);
        }
        if (!ignoresAbilities && hasAbility(defender, "Fluffy") && moveType == PokeType.FIRE) {
            addModifier(modifiers, 8192, notes, "Fluffy fire weakness");
        }

        if (hasItem(attacker, "Expert Belt") && effectiveness > 1.0)
            addModifier(modifiers, 4915, notes, "Expert Belt");
        else if (hasItem(attacker, "Life Orb")) addModifier(modifiers, 5324, notes, "Life Orb");
        else if (hasItem(attacker, "Metronome") && attacker.battleHistoryKnown
                && attacker.consecutiveMoveUses > 0 && normalize(attacker.lastMoveId).equals(move.id())) {
            int uses = Math.min(5, attacker.consecutiveMoveUses);
            modifiers.add(uses < 5 ? Q12 + 819 * uses : 8192);
            notes.add("Metronome x" + trim(1.0 + 0.2 * uses));
        }

        ArrayList<Integer> firstHitModifiers = new ArrayList<>(modifiers);
        boolean fullHp = defender.visibleHp() == defender.maxHp();
        if (fullHp && (hasAbility(defender, "Shadow Shield")
                || hasAbility(defender, "Multiscale") && !ignoresAbilities)) {
            firstHitModifiers.add(multiscaleInsertion, HALF);
            if (!notes.contains(defender.ability)) notes.add(defender.ability);
        }
        if (isResistBerry(defender.item, moveType) && (effectiveness > 1.0 || moveType == PokeType.NORMAL)
                && !hasUnnerve(attacker, attackerSide)) {
            int berry = hasAbility(defender, "Ripen") ? 1024 : HALF;
            addModifier(firstHitModifiers, berry, notes, defender.item + (berry == 1024 ? " + Ripen" : ""));
        }
        return new FinalModifierValues(chainModifiers(modifiers, 41), chainModifiers(firstHitModifiers, 41));
    }

    private static boolean hasPartnerAbility(SideConditions side, String expected) {
        return partnerAbilityCount(side, expected) > 0;
    }

    private static int partnerAbilityCount(SideConditions side, String expected) {
        if (side == null) return 0;
        String normalized = BattleCalcDex.normalize(expected);
        if (!side.partnerAbilities.isEmpty()) {
            int count = 0;
            for (String ability : side.partnerAbilities) {
                if (BattleCalcDex.normalize(ability).equals(normalized)) count++;
            }
            return count;
        }
        return BattleCalcDex.normalize(side.partnerAbility).equals(normalized) ? 1 : 0;
    }

    private static int auraPowerModifier(PokemonSet attacker, PokemonSet defender, PokeType moveType,
                                         SideConditions attackerSide, SideConditions defenderSide,
                                         List<String> notes) {
        boolean matchingAura = moveType == PokeType.DARK
                && (hasAbility(attacker, "Dark Aura") || hasAbility(defender, "Dark Aura")
                || hasPartnerAbility(attackerSide, "Dark Aura") || hasPartnerAbility(defenderSide, "Dark Aura"))
                || moveType == PokeType.FAIRY
                && (hasAbility(attacker, "Fairy Aura") || hasAbility(defender, "Fairy Aura")
                || hasPartnerAbility(attackerSide, "Fairy Aura") || hasPartnerAbility(defenderSide, "Fairy Aura"));
        if (matchingAura) {
            boolean auraBreak = hasAbility(attacker, "Aura Break") || hasAbility(defender, "Aura Break")
                    || hasPartnerAbility(attackerSide, "Aura Break") || hasPartnerAbility(defenderSide, "Aura Break");
            notes.add(auraBreak ? "Aura Break" : moveType == PokeType.DARK ? "Dark Aura" : "Fairy Aura");
            return auraBreak ? THREE_QUARTERS : 5448;
        }
        return Q12;
    }

    private static int stabModifier(PokemonSet attacker, PokeType moveType, List<String> notes) {
        boolean originalStab = attacker.species.types().contains(moveType);
        boolean teraStab = attacker.terastallized && attacker.teraType == moveType;
        boolean proteanStab = !attacker.terastallized && moveType != PokeType.NONE
                && (hasAbility(attacker, "Protean") || hasAbility(attacker, "Libero"));
        if (proteanStab) {
            notes.add(hasAbility(attacker, "Libero") ? "Libero STAB" : "Protean STAB");
            return THREE_HALVES;
        }
        if (hasAbility(attacker, "Adaptability") && originalStab && teraStab) {
            notes.add("Adaptability Tera STAB");
            return 9216;
        }
        if (hasAbility(attacker, "Adaptability") && (originalStab || teraStab)) {
            notes.add("Adaptability STAB");
            return 8192;
        }
        if (originalStab && teraStab) {
            notes.add("Tera STAB x2");
            return 8192;
        }
        if (originalStab || teraStab) {
            notes.add("STAB");
            return THREE_HALVES;
        }
        return Q12;
    }

    private static int itemStatModifier(PokemonSet pokemon, Stat stat, int value) {
        if (hasItem(pokemon, "Choice Band") && stat == Stat.ATK) {
            return roundQ12(value, THREE_HALVES);
        }
        if (hasItem(pokemon, "Choice Specs") && stat == Stat.SPA) {
            return roundQ12(value, THREE_HALVES);
        }
        if (hasItem(pokemon, "Choice Scarf") && stat == Stat.SPE) {
            return roundQ12(value, THREE_HALVES);
        }
        if (hasItem(pokemon, "Assault Vest") && stat == Stat.SPD) {
            return roundQ12(value, THREE_HALVES);
        }
        if (hasItem(pokemon, "Eviolite") && pokemon.species.notFullyEvolved() && (stat == Stat.DEF || stat == Stat.SPD)) {
            return roundQ12(value, THREE_HALVES);
        }
        if (hasItem(pokemon, "Light Ball") && isSpecies(pokemon, "pikachu") && (stat == Stat.ATK || stat == Stat.SPA)) {
            return value * 2;
        }
        if (hasItem(pokemon, "Thick Club") && (isSpecies(pokemon, "cubone") || isSpecies(pokemon, "marowak")) && stat == Stat.ATK) {
            return value * 2;
        }
        if (hasItem(pokemon, "Deep Sea Tooth") && isSpecies(pokemon, "clamperl") && stat == Stat.SPA) {
            return value * 2;
        }
        if (hasItem(pokemon, "Deep Sea Scale") && isSpecies(pokemon, "clamperl") && stat == Stat.SPD) {
            return value * 2;
        }
        if (hasItem(pokemon, "Metal Powder") && isSpecies(pokemon, "ditto") && stat == Stat.DEF) {
            return value * 2;
        }
        if (hasItem(pokemon, "Quick Powder") && isSpecies(pokemon, "ditto") && stat == Stat.SPE) {
            return value * 2;
        }
        if (stat == Stat.SPE && SPEED_HALVING_ITEMS.contains(normalize(pokemon.item))) {
            return Math.max(1, roundQ12(value, HALF));
        }
        return value;
    }

    private static void addItemBasePowerModifiers(List<Integer> modifiers, PokemonSet attacker, MoveData move,
                                                  PokeType moveType, List<String> notes) {
        String item = normalize(attacker.item);
        if (move.category() == DamageCategory.PHYSICAL && item.equals("muscleband")) {
            addModifier(modifiers, 4505, notes, "Muscle Band");
        }
        if (move.category() == DamageCategory.SPECIAL && item.equals("wiseglasses")) {
            addModifier(modifiers, 4505, notes, "Wise Glasses");
        }
        if (item.equals("punchingglove") && hasMoveFlag(move, "punch", PUNCH_MOVES)) {
            addModifier(modifiers, 4506, notes, "Punching Glove");
        }
        if (TYPE_BOOST_ITEMS.getOrDefault(moveType, Set.of()).contains(item)) {
            addModifier(modifiers, 4915, notes, attacker.item);
        }
        if (item.equals(normalize(moveType.displayName() + " Gem"))) {
            addModifier(modifiers, 5325, notes, attacker.item);
        }
        if ((isSpecies(attacker, "latios") || isSpecies(attacker, "latias")) && item.equals("souldew")
                && (moveType == PokeType.PSYCHIC || moveType == PokeType.DRAGON)) {
            addModifier(modifiers, 4915, notes, "Soul Dew");
        }
        if (isLegendaryOrbBoost(attacker, item, moveType)) {
            addModifier(modifiers, 4915, notes, attacker.item);
        }
        if ((item.equals("hearthflamemask") || item.equals("cornerstonemask") || item.equals("wellspringmask"))
                && normalize(attacker.species.cobblemonSpeciesId()).equals("ogerpon")) {
            addModifier(modifiers, 4915, notes, attacker.item);
        }
    }

    private static boolean isLegendaryOrbBoost(PokemonSet attacker, String item, PokeType moveType) {
        if (isSpecies(attacker, "dialga") && (item.equals("adamantorb") || item.equals("adamantcrystal"))
                && (moveType == PokeType.STEEL || moveType == PokeType.DRAGON)) {
            return true;
        }
        if (isSpecies(attacker, "palkia") && (item.equals("lustrousorb") || item.equals("lustrousglobe"))
                && (moveType == PokeType.WATER || moveType == PokeType.DRAGON)) {
            return true;
        }
        return isSpecies(attacker, "giratina") && (item.equals("griseousorb") || item.equals("griseouscore"))
                && (moveType == PokeType.GHOST || moveType == PokeType.DRAGON);
    }

    private static int abilityStatModifier(PokemonSet pokemon, Stat stat, int value) {
        if ((hasAbility(pokemon, "Huge Power") || hasAbility(pokemon, "Pure Power")) && stat == Stat.ATK) {
            return value * 2;
        }
        return value;
    }

    private static int weatherStatModifier(PokemonSet pokemon, Stat stat, int value, FieldState field, List<String> notes) {
        if (field == null || stat == Stat.HP) {
            return value;
        }
        boolean umbrella = hasItem(pokemon, "Utility Umbrella");
        if (isSun(field.weather)) {
            if (!umbrella && stat == Stat.SPE && hasAbility(pokemon, "Chlorophyll")) {
                value = multiplyStat(value, 2.0, notes, "Chlorophyll");
            }
            if (!umbrella && stat == Stat.SPA && hasAbility(pokemon, "Solar Power")) {
                value = multiplyStat(value, 1.5, notes, "Solar Power");
            }
            if (!umbrella && stat == Stat.ATK && hasAbility(pokemon, "Orichalcum Pulse")) {
                value = multiplyStat(value, 4.0 / 3.0, notes, "Orichalcum Pulse");
            }
            if (!umbrella && (stat == Stat.ATK || stat == Stat.SPD) && hasAbility(pokemon, "Flower Gift")) {
                value = multiplyStat(value, 1.5, notes, "Flower Gift");
            }
            if (hasAbility(pokemon, "Protosynthesis") && stat == highestStat(pokemon, field, false)) {
                value = multiplyStat(value, stat == Stat.SPE ? 1.5 : 1.3, notes, "Protosynthesis");
            }
        }
        if (!umbrella && isRain(field.weather) && stat == Stat.SPE && hasAbility(pokemon, "Swift Swim")) {
            value = multiplyStat(value, 2.0, notes, "Swift Swim");
        }
        if (field.weather == Weather.SAND) {
            if (stat == Stat.SPE && hasAbility(pokemon, "Sand Rush")) {
                value = multiplyStat(value, 2.0, notes, "Sand Rush");
            }
            if (stat == Stat.SPD && pokemon.defensiveTypes().contains(PokeType.ROCK)) {
                value = multiplyStat(value, 1.5, notes, "Sand rock SpD x1.5");
            }
        }
        if (field.weather == Weather.SNOW) {
            if (stat == Stat.SPE && hasAbility(pokemon, "Slush Rush")) {
                value = multiplyStat(value, 2.0, notes, "Slush Rush");
            }
            if (stat == Stat.DEF && pokemon.defensiveTypes().contains(PokeType.ICE)) {
                value = multiplyStat(value, 1.5, notes, "Snow ice Defense x1.5");
            }
        }
        if (field.terrain == Terrain.ELECTRIC) {
            if (stat == Stat.SPE && hasAbility(pokemon, "Surge Surfer")) {
                value = multiplyStat(value, 2.0, notes, "Surge Surfer");
            }
            if (stat == Stat.SPA && hasAbility(pokemon, "Hadron Engine")) {
                value = multiplyStat(value, 4.0 / 3.0, notes, "Hadron Engine");
            }
            if (hasAbility(pokemon, "Quark Drive") && stat == highestStat(pokemon, field, false)) {
                value = multiplyStat(value, stat == Stat.SPE ? 1.5 : 1.3, notes, "Quark Drive");
            }
        }
        if ((pokemon.paradoxBoostActive || hasItem(pokemon, "Booster Energy")) && !isSun(field.weather)
                && hasAbility(pokemon, "Protosynthesis") && stat == highestStat(pokemon, field, false)) {
            value = multiplyStat(value, stat == Stat.SPE ? 1.5 : 1.3, notes, "Protosynthesis (Booster Energy)");
        }
        if ((pokemon.paradoxBoostActive || hasItem(pokemon, "Booster Energy")) && field.terrain != Terrain.ELECTRIC
                && hasAbility(pokemon, "Quark Drive") && stat == highestStat(pokemon, field, false)) {
            value = multiplyStat(value, stat == Stat.SPE ? 1.5 : 1.3, notes, "Quark Drive (Booster Energy)");
        }
        if (stat == Stat.SPE && pokemon.status == StatusCondition.PARALYSIS && !hasAbility(pokemon, "Quick Feet")) {
            value = multiplyStat(value, 0.5, notes, "Paralysis Speed x0.5");
        } else if (pokemon.status != StatusCondition.NONE && stat == Stat.SPE && hasAbility(pokemon, "Quick Feet")) {
            value = multiplyStat(value, 1.5, notes, "Quick Feet");
        }
        if (stat == Stat.SPE && slowStartActive(pokemon)) {
            value = multiplyStat(value, 0.5, notes, "Slow Start");
        }
        return Math.max(1, value);
    }

    private static int multiplyStat(int value, double modifier, List<String> notes, String note) {
        if (notes != null && !notes.contains(note)) {
            notes.add(note);
        }
        return roundQ12(value, q12(modifier));
    }

    private static Stat highestStat(PokemonSet pokemon, FieldState field, boolean includeWeatherBoosts) {
        Stat best = Stat.ATK;
        int bestValue = -1;
        for (Stat candidate : new Stat[]{Stat.ATK, Stat.DEF, Stat.SPA, Stat.SPD, Stat.SPE}) {
            Stat rawCandidate = candidate;
            if (field != null && field.wonderRoom && candidate == Stat.DEF) rawCandidate = Stat.SPD;
            else if (field != null && field.wonderRoom && candidate == Stat.SPD) rawCandidate = Stat.DEF;
            int value = storedStat(pokemon, rawCandidate, true);
            if (includeWeatherBoosts) {
                value = weatherStatModifier(pokemon, candidate, value, field, null);
            }
            if (value > bestValue) {
                bestValue = value;
                best = candidate;
            }
        }
        return best;
    }

    private static int speedStat(PokemonSet pokemon, FieldState field, SideConditions side) {
        int value = weatherStatModifier(pokemon, Stat.SPE, stat(pokemon, Stat.SPE, false), field, null);
        return sideStatModifier(Stat.SPE, value, side, null);
    }

    private static boolean movesBefore(PokemonSet attacker, PokemonSet defender, FieldState field,
                                       SideConditions attackerSide, SideConditions defenderSide) {
        int attackerSpeed = speedStat(attacker, field, attackerSide);
        int defenderSpeed = speedStat(defender, field, defenderSide);
        return field.trickRoom ? attackerSpeed < defenderSpeed : attackerSpeed > defenderSpeed;
    }

    private static boolean movesAfter(PokemonSet attacker, PokemonSet defender, FieldState field,
                                      SideConditions attackerSide, SideConditions defenderSide) {
        int attackerSpeed = speedStat(attacker, field, attackerSide);
        int defenderSpeed = speedStat(defender, field, defenderSide);
        return field.trickRoom ? attackerSpeed > defenderSpeed : attackerSpeed < defenderSpeed;
    }

    private static int sideStatModifier(Stat stat, int value, SideConditions side, List<String> notes) {
        if (side != null && side.tailwind && stat == Stat.SPE) {
            value = multiplyStat(value, 2.0, notes, "Tailwind");
        }
        return value;
    }

    private static int applyBoost(int value, int boost) {
        if (boost > 0) {
            return (int) Math.floor(value * (2.0 + boost) / 2.0);
        }
        if (boost < 0) {
            return (int) Math.floor(value * 2.0 / (2.0 - boost));
        }
        return value;
    }

    private static String koChance(Map<Integer, Double> distribution, int hp) {
        double ohko = chanceAtLeast(distribution, hp);
        if (ohko >= 1.0 - 1.0e-9) {
            return "guaranteed OHKO";
        }
        if (ohko > 1.0e-9) {
            return percent(ohko * 100.0) + "% chance to OHKO";
        }
        Map<Integer, Double> twoTurns = convolve(distribution, distribution, hp);
        double two = chanceAtLeast(twoTurns, hp);
        if (two > 1.0e-9) {
            return percent(Math.min(1.0, two) * 100.0) + "% chance to 2HKO without recovery";
        }
        Map<Integer, Double> threeTurns = convolve(twoTurns, distribution, hp);
        double three = chanceAtLeast(threeTurns, hp);
        if (three > 1.0e-9) {
            return percent(Math.min(1.0, three) * 100.0) + "% chance to 3HKO without recovery";
        }
        return "possible 4HKO+";
    }

    private static Map<Integer, Double> convolve(Map<Integer, Double> left, Map<Integer, Double> right, int hp) {
        HashMap<Integer, Double> output = new HashMap<>();
        for (Map.Entry<Integer, Double> first : left.entrySet()) {
            for (Map.Entry<Integer, Double> second : right.entrySet()) {
                int total = Math.min(hp, first.getKey() + second.getKey());
                output.merge(total, first.getValue() * second.getValue(), Double::sum);
            }
        }
        return output;
    }

    private static double chanceAtLeast(Map<Integer, Double> distribution, int hp) {
        return distribution.entrySet().stream()
                .filter(entry -> entry.getKey() >= hp)
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    private static boolean isResistBerry(String item, PokeType type) {
        String normalized = normalize(item);
        return switch (type) {
            case NORMAL -> normalized.equals("chilanberry");
            case FIRE -> normalized.equals("occaberry");
            case WATER -> normalized.equals("passhoberry");
            case ELECTRIC -> normalized.equals("wacanberry");
            case GRASS -> normalized.equals("rindoberry");
            case ICE -> normalized.equals("yacheberry");
            case FIGHTING -> normalized.equals("chopleberry");
            case POISON -> normalized.equals("kebiaberry");
            case GROUND -> normalized.equals("shucaberry");
            case FLYING -> normalized.equals("cobaberry");
            case PSYCHIC -> normalized.equals("payapaberry");
            case BUG -> normalized.equals("tangaberry");
            case ROCK -> normalized.equals("chartiberry");
            case GHOST -> normalized.equals("kasibberry");
            case DRAGON -> normalized.equals("habanberry");
            case DARK -> normalized.equals("colburberry");
            case STEEL -> normalized.equals("babiriberry");
            case FAIRY -> normalized.equals("roseliberry");
            default -> false;
        };
    }

    private static String defensiveImmunity(PokemonSet attacker, PokemonSet defender, MoveData move, PokeType moveType,
                                            FieldState field, SideConditions defenderSide) {
        if (OHKO_MOVES.contains(move.id()) && attacker.level < defender.level) {
            return "OHKO level check";
        }
        if (move.id().equals("sheercold") && defender.defensiveTypes().contains(PokeType.ICE)) {
            return "Sheer Cold Ice immunity";
        }
        if (moveType == PokeType.GROUND && !field.gravity && !move.id().equals("thousandarrows")
                && hasItem(defender, "Air Balloon")) {
            return "Air Balloon immunity";
        }
        int priority = effectivePriority(attacker, move);
        if (priority > 0 && !field.alliedTarget && isGrounded(defender, field)
                && field.terrain == Terrain.PSYCHIC) {
            return "Psychic Terrain blocks priority";
        }
        if (ignoresDefensiveAbilities(attacker, move)) {
            return null;
        }
        if (field.alliedTarget && hasAbility(defender, "Telepathy")) return "Telepathy";
        if (OHKO_MOVES.contains(move.id()) && hasAbility(defender, "Sturdy")) {
            return "Sturdy";
        }
        if (priority > 0 && !field.alliedTarget) {
            if (hasAbility(defender, "Queenly Majesty") || hasAbility(defender, "Dazzling")
                    || hasAbility(defender, "Armor Tail")) return defender.ability + " blocks priority";
            for (String ability : List.of("Queenly Majesty", "Dazzling", "Armor Tail")) {
                if (hasPartnerAbility(defenderSide, ability)) return ability + " blocks priority";
            }
        }
        if (moveType == PokeType.WATER && (hasAbility(defender, "Water Absorb") || hasAbility(defender, "Storm Drain") || hasAbility(defender, "Dry Skin"))) {
            return defender.ability + " immunity";
        }
        if (moveType == PokeType.FIRE && (hasAbility(defender, "Flash Fire") || hasAbility(defender, "Well-Baked Body"))) {
            return defender.ability + " immunity";
        }
        if (moveType == PokeType.ELECTRIC && (hasAbility(defender, "Volt Absorb") || hasAbility(defender, "Lightning Rod") || hasAbility(defender, "Motor Drive"))) {
            return defender.ability + " immunity";
        }
        if (moveType == PokeType.GRASS && hasAbility(defender, "Sap Sipper")) {
            return "Sap Sipper immunity";
        }
        if (moveType == PokeType.GROUND && hasAbility(defender, "Earth Eater")) {
            return "Earth Eater immunity";
        }
        if (hasMoveFlag(move, "sound", SOUND_MOVES) && hasAbility(defender, "Soundproof")) {
            return "Soundproof immunity";
        }
        if (hasMoveFlag(move, "bullet", BULLET_MOVES) && hasAbility(defender, "Bulletproof")) {
            return "Bulletproof immunity";
        }
        if (hasMoveFlag(move, "wind", WIND_MOVES) && hasAbility(defender, "Wind Rider")) {
            return "Wind Rider immunity";
        }
        return null;
    }

    private static int effectivePriority(PokemonSet attacker, MoveData move) {
        int priority = move.priority();
        if (hasAbility(attacker, "Triage") && move.hasFlag("heal")) priority += 3;
        if (hasAbility(attacker, "Gale Wings") && move.type() == PokeType.FLYING
                && attacker.visibleHp() == attacker.maxHp()) priority += 1;
        return priority;
    }

    private static boolean isCriticalHit(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field) {
        boolean requested = field.criticalHit || move.hasFlag("alwayscrit")
                || hasAbility(attacker, "Merciless") && defender.status == StatusCondition.POISON;
        if (!requested || ignoresDefensiveAbilities(attacker, move)) {
            return requested;
        }
        return !hasAbility(defender, "Battle Armor") && !hasAbility(defender, "Shell Armor");
    }

    private static boolean ignoresDefensiveAbilities(PokemonSet attacker) {
        return ignoresDefensiveAbilities(attacker, null);
    }

    private static boolean ignoresDefensiveAbilities(PokemonSet attacker, MoveData move) {
        return hasAbility(attacker, "Mold Breaker") || hasAbility(attacker, "Teravolt")
                || hasAbility(attacker, "Turboblaze") || move != null
                && (move.hasFlag("ignoreability") || ABILITY_IGNORING_MOVES.contains(move.id()));
    }

    static boolean isContactMoveId(String moveId) {
        return CONTACT_MOVES.contains(normalize(moveId));
    }

    private static boolean isContactMove(MoveData move) {
        return move.hasFlag("contact") || (move.flags().isEmpty() && isContactMoveId(move.id()));
    }

    private static boolean hasMoveFlag(MoveData move, String flag, Set<String> fallbackIds) {
        return move.hasFlag(flag) || (move.flags().isEmpty() && fallbackIds.contains(move.id()));
    }

    private static boolean hasItem(PokemonSet pokemon, String item) {
        return normalize(pokemon.item).equals(normalize(item));
    }

    private static boolean isSpecies(PokemonSet pokemon, String speciesId) {
        String normalized = normalize(speciesId);
        return normalize(pokemon.species.id()).equals(normalized)
                || normalize(pokemon.species.name()).equals(normalized)
                || normalize(pokemon.species.cobblemonSpeciesId()).equals(normalized);
    }

    private static boolean slowStartActive(PokemonSet pokemon) {
        return hasAbility(pokemon, "Slow Start") && (pokemon.turnsActive < 0 || pokemon.turnsActive < 5);
    }

    private static boolean hasAbility(PokemonSet pokemon, String ability) {
        return normalize(pokemon.ability).equals(normalize(ability));
    }

    private static boolean hasAspect(PokemonSet pokemon, String aspect) {
        String expected = normalize(aspect);
        return pokemon.species.aspects().stream().map(DamageCalculator::normalize).anyMatch(expected::equals)
                || normalize(pokemon.species.id()).contains(expected);
    }

    private static PokeType itemMoveType(String heldItem, String moveId) {
        String item = normalize(heldItem);
        if (moveId.equals("judgment")) {
            for (Map.Entry<PokeType, Set<String>> entry : TYPE_BOOST_ITEMS.entrySet()) {
                if (entry.getValue().contains(item) && item.endsWith("plate")) return entry.getKey();
            }
        }
        if (moveId.equals("multiattack") && item.endsWith("memory")) {
            String typeName = item.substring(0, item.length() - "memory".length());
            PokeType type = PokeType.byName(typeName);
            if (type != PokeType.NONE) return type;
        }
        if (moveId.equals("technoblast")) {
            return switch (item) {
                case "burndrive" -> PokeType.FIRE;
                case "dousedrive" -> PokeType.WATER;
                case "shockdrive" -> PokeType.ELECTRIC;
                case "chilldrive" -> PokeType.ICE;
                default -> PokeType.NONE;
            };
        }
        if (moveId.equals("ivycudgel")) {
            return switch (item) {
                case "hearthflamemask" -> PokeType.FIRE;
                case "wellspringmask" -> PokeType.WATER;
                case "cornerstonemask" -> PokeType.ROCK;
                default -> PokeType.GRASS;
            };
        }
        return PokeType.NONE;
    }

    private static boolean lowHp(PokemonSet pokemon) {
        return pokemon.visibleHp() * 3 <= pokemon.maxHp();
    }

    private static boolean isGrounded(PokemonSet pokemon, FieldState field) {
        if (field.gravity) {
            return true;
        }
        if (hasItem(pokemon, "Iron Ball")) {
            return true;
        }
        return !pokemon.defensiveTypes().contains(PokeType.FLYING)
                && !hasAbility(pokemon, "Levitate")
                && !hasItem(pokemon, "Air Balloon");
    }

    private static boolean isSun(Weather weather) {
        return weather == Weather.SUN || weather == Weather.HARSH_SUN;
    }

    private static boolean isRain(Weather weather) {
        return weather == Weather.RAIN || weather == Weather.HEAVY_RAIN;
    }

    private static boolean weatherBallUsesWeather(PokemonSet attacker, Weather weather) {
        if (weather == Weather.NONE || weather == Weather.STRONG_WINDS) return false;
        return !(hasItem(attacker, "Utility Umbrella") && (isSun(weather) || isRain(weather)));
    }

    private static String strongWeatherBlock(PokeType moveType, Weather weather) {
        if (weather == Weather.HARSH_SUN && moveType == PokeType.WATER) return "Harsh Sun blocks Water moves";
        if (weather == Weather.HEAVY_RAIN && moveType == PokeType.FIRE) return "Heavy Rain blocks Fire moves";
        return null;
    }

    private static boolean paradoxBoosts(PokemonSet pokemon, FieldState field) {
        if (hasAbility(pokemon, "Protosynthesis"))
            return isSun(field.weather) || pokemon.paradoxBoostActive || hasItem(pokemon, "Booster Energy");
        if (hasAbility(pokemon, "Quark Drive"))
            return field.terrain == Terrain.ELECTRIC || pokemon.paradoxBoostActive || hasItem(pokemon, "Booster Energy");
        return false;
    }

    private static boolean hasUnnerve(PokemonSet pokemon, SideConditions side) {
        return hasAbility(pokemon, "Unnerve") || hasAbility(pokemon, "As One (Glastrier)")
                || hasAbility(pokemon, "As One (Spectrier)") || hasPartnerAbility(side, "Unnerve")
                || hasPartnerAbility(side, "As One (Glastrier)")
                || hasPartnerAbility(side, "As One (Spectrier)");
    }

    private static boolean punchingGloveRemovesContact(PokemonSet attacker, MoveData move) {
        return hasItem(attacker, "Punching Glove") && hasMoveFlag(move, "punch", PUNCH_MOVES);
    }

    private static void addModifier(List<Integer> modifiers, int modifier, List<String> notes, String note) {
        modifiers.add(modifier);
        if (note != null && !note.isBlank() && !notes.contains(note)) notes.add(note);
    }

    private static int q12(double modifier) {
        return Math.max(1, safeInt(Math.round(modifier * Q12)));
    }

    private static int chainModifiers(List<Integer> modifiers, int minimum) {
        long combined = Q12;
        for (int modifier : modifiers) {
            if (modifier == Q12) continue;
            combined = Math.floorDiv(combined * modifier + HALF, Q12);
        }
        return safeInt(Math.max(minimum, Math.min(131072L, combined)));
    }

    /** Game damage rounding: an exact half is rounded down. */
    private static int roundQ12(int value, int modifier) {
        long product = (long) value * modifier;
        return safeInt(Math.floorDiv(product + HALF - 1L, Q12));
    }

    private static int safeInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static double rawTypeEffectiveness(PokeType attackingType, List<PokeType> defendingTypes) {
        double effectiveness = 1.0;
        for (PokeType defendingType : defendingTypes) {
            effectiveness *= TYPE_CHART.get(attackingType).getOrDefault(defendingType, 1.0);
        }
        return effectiveness;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void strong(PokeType attacking, PokeType... defending) {
        for (PokeType type : defending) {
            TYPE_CHART.get(attacking).put(type, 2.0);
        }
    }

    private static void weak(PokeType attacking, PokeType... defending) {
        for (PokeType type : defending) {
            TYPE_CHART.get(attacking).put(type, 0.5);
        }
    }

    private static void immune(PokeType attacking, PokeType defending) {
        TYPE_CHART.get(attacking).put(defending, 0.0);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    record HitProfile(int minHits, int maxHits, boolean forcedHits, boolean multiAccuracy,
                      boolean parentalBond, boolean blockedFirstHit) {
    }

    private record DamageFormula(int spreadModifier, int weatherModifier, int criticalModifier,
                                 int stabModifier, double effectiveness, boolean burned,
                                 int finalModifier, int firstHitFinalModifier) {
    }

    private record HitCalculation(int level, int power, int attackStat, int defenseStat,
                                  DamageFormula formula, boolean parentalChild,
                                  boolean firstDamagingHit, boolean blocked) {
    }

    private record DamageDistribution(Map<Integer, Double> probabilities, int minDamage, int maxDamage) {
    }

    private record FinalModifierValues(int normal, int firstHit) {
    }

    private record FixedDamage(int minDamage, int maxDamage, String reason) {
    }
}
