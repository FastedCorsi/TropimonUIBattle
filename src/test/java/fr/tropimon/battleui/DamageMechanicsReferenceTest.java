package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.MoveTarget;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gen 9 mechanical fixtures cross-checked against @smogon/calc 0.11.0.
 * Expected rolls are constants on purpose: these tests must not compare the
 * cache with another call to the same calculator implementation.
 */
final class DamageMechanicsReferenceTest {
    @BeforeAll static void bootstrap() {
        DamageCacheParityTest.bootstrap();
    }

    @Test void basicGenNineOrderAndFoulPlayUseReferenceRolls() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        assertEquals(List.of(16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 19),
                calculate(attacker, defender, physical("tackle", PokeType.NORMAL, 40)).rolls());

        MoveData foulPlay = physical("foulplay", PokeType.DARK, 95);
        assertEquals(List.of(72, 72, 74, 74, 76, 76, 78, 78, 78, 80, 80, 82, 82, 84, 84, 86),
                calculate(attacker, defender, foulPlay).rolls());

        attacker.item = "Choice Band";
        assertEquals(List.of(108, 110, 110, 112, 112, 114, 116, 116, 118, 120, 120, 122, 124, 124, 126, 128),
                calculate(attacker, defender, foulPlay).rolls());

        attacker.item = "None";
        defender.boosts.put(Stat.ATK, 2);
        assertEquals(170, calculate(attacker, defender, foulPlay).maxDamage());
        defender.ability = "Unaware";
        assertEquals(86, calculate(attacker, defender, foulPlay).maxDamage());
    }

    @Test void onlyTheFirstDamagingMultiHitGetsMultiscaleOrTheBerry() {
        PokemonSet attacker = mew();
        PokemonSet defender = vaporeon();
        MoveData twoHitSeed = new MoveData("bulletseed", "Bullet Seed", PokeType.GRASS,
                DamageCategory.PHYSICAL, 25, false, false, Set.of("hits2"), 0);
        DamageResult baseline = calculate(attacker, defender, twoHitSeed);
        assertEquals(60, baseline.minDamage());
        assertEquals(72, baseline.maxDamage());

        defender.ability = "Multiscale";
        DamageResult scale = calculate(attacker, defender, twoHitSeed);
        assertEquals(45, scale.minDamage());
        assertEquals(54, scale.maxDamage());

        defender.ability = "None";
        defender.item = "Rindo Berry";
        DamageResult berry = calculate(attacker, defender, twoHitSeed);
        assertEquals(45, berry.minDamage());
        assertEquals(54, berry.maxDamage());

        attacker.ability = "Unnerve";
        assertEquals(60, calculate(attacker, defender, twoHitSeed).minDamage());

        attacker.ability = "Synchronize";
        FieldState doubles = new FieldState();
        doubles.doubles = true;
        doubles.attackerSide.partnerAbilities = List.of("Unnerve");
        assertEquals(60, calculate(attacker, defender, twoHitSeed, doubles).minDamage());
    }

    @Test void weatherUmbrellaAndExtremeWeatherRolesMatchTheReferenceEngine() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        FieldState field = new FieldState();
        field.weather = Weather.HEAVY_RAIN;
        assertEquals(0, calculate(attacker, defender, special("flamethrower", PokeType.FIRE, 90), field).maxDamage());
        defender.item = "Utility Umbrella";
        assertEquals(0, calculate(attacker, defender, special("flamethrower", PokeType.FIRE, 90), field).maxDamage(),
                "Utility Umbrella does not bypass Primordial Sea's move failure");

        field.weather = Weather.HARSH_SUN;
        attacker.item = "Utility Umbrella";
        defender.item = "None";
        assertEquals(0, calculate(attacker, defender, special("surf", PokeType.WATER, 90), field).maxDamage(),
                "Utility Umbrella does not bypass Desolate Land's move failure");

        attacker.item = "None";
        defender.item = "None";
        field.weather = Weather.RAIN;
        MoveData weatherBall = special("weatherball", PokeType.NORMAL, 50);
        assertEquals(List.of(58, 59, 60, 60, 61, 62, 62, 63, 64, 64, 65, 66, 66, 67, 68, 69),
                calculate(attacker, defender, weatherBall, field).rolls());
        attacker.item = "Utility Umbrella";
        assertEquals(List.of(20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 23, 24),
                calculate(attacker, defender, weatherBall, field).rolls());

        MoveData surf = special("surf", PokeType.WATER, 90);
        assertEquals(61, calculate(attacker, defender, surf, field).maxDamage(),
                "the attacker's umbrella does not suppress weather at the target");
        attacker.item = "None";
        defender.item = "Utility Umbrella";
        assertEquals(41, calculate(attacker, defender, surf, field).maxDamage());

        defender.item = "None";
        attacker.item = "Utility Umbrella";
        field.weather = Weather.SUN;
        assertEquals(List.of(15, 15, 15, 15, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18),
                calculate(attacker, defender, special("hydrosteam", PokeType.WATER, 80), field).rolls());

        PokemonSet flying = new PokemonSet(species("flyingmon", PokeType.FLYING, PokeType.NONE,
                100, 100, 100, 100, 100, 100, 20.0, false));
        flying.level = 50;
        field.weather = Weather.STRONG_WINDS;
        assertEquals(1.0, DamageCalculator.typeEffectiveness(
                special("thunderbolt", PokeType.ELECTRIC, 90), PokeType.ELECTRIC,
                flying, attacker, field, new java.util.ArrayList<>()));
    }

    @Test void utilityUmbrellaSuppressesWeatherStatAbilitiesButNotProtosynthesis() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        FieldState sun = new FieldState();
        sun.weather = Weather.SUN;

        attacker.ability = "Solar Power";
        int boosted = calculate(attacker, defender, special("psychic", PokeType.PSYCHIC, 90), sun).maxDamage();
        attacker.item = "Utility Umbrella";
        assertTrue(calculate(attacker, defender, special("psychic", PokeType.PSYCHIC, 90), sun).maxDamage() < boosted);

        attacker.ability = "Chlorophyll";
        assertEquals(DamageCalculator.stat(attacker, Stat.SPE, false),
                DamageCalculator.stat(attacker, Stat.SPE, false, sun));
        attacker.ability = "Protosynthesis";
        attacker.paradoxBoostActive = true;
        attacker.species.baseStats().put(Stat.SPA, 130);
        assertTrue(DamageCalculator.displayedStat(attacker, Stat.SPA, sun, new SideConditions())
                > DamageCalculator.stat(attacker, Stat.SPA, false));
    }

    @Test void expandingForceIceScalesAndPartnerAurasUseFinalModifiers() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        FieldState field = new FieldState();
        field.doubles = true;
        field.terrain = Terrain.PSYCHIC;
        field.attackerSide.spreadTargets = 2;
        DamageResult expanding = calculate(attacker, defender,
                special("expandingforce", PokeType.PSYCHIC, 80), field);
        assertEquals(33, expanding.minDamage());
        assertEquals(39, expanding.maxDamage());

        field = new FieldState();
        defender.ability = "Ice Scales";
        assertEquals(List.of(17, 17, 17, 18, 18, 18, 18, 18, 19, 19, 19, 19, 19, 20, 20, 20),
                calculate(attacker, defender, special("flamethrower", PokeType.FIRE, 90), field).rolls());

        defender.ability = "None";
        field.doubles = true;
        field.attackerSide.partnerAbilities = List.of("Dark Aura");
        assertEquals(List.of(80, 82, 82, 84, 84, 86, 86, 88, 88, 90, 90, 92, 92, 94, 94, 96),
                calculate(attacker, defender, special("darkpulse", PokeType.DARK, 80), field).rolls());
        field.defenderSide.partnerAbilities = List.of("Aura Break");
        assertEquals(List.of(46, 48, 48, 48, 48, 50, 50, 50, 52, 52, 52, 52, 54, 54, 54, 56),
                calculate(attacker, defender, special("darkpulse", PokeType.DARK, 80), field).rolls());
    }

    @Test void priorityProtectionAlliesDynamicTypesAndWeightModifiersStayCoherent() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        MoveData priority = new MoveData("quickattack", "Quick Attack", PokeType.NORMAL,
                DamageCategory.PHYSICAL, 40, false, true, Set.of("contact"), 1);
        FieldState field = new FieldState();
        field.doubles = true;
        field.defenderSide.partnerAbilities = List.of("Armor Tail");
        assertEquals(0, calculate(attacker, defender, priority, field).maxDamage());
        field.alliedTarget = true;
        assertTrue(calculate(attacker, defender, priority, field).maxDamage() > 0);

        attacker.terastallized = true;
        attacker.teraType = PokeType.FIRE;
        MoveData dance = special("revelationdance", PokeType.PSYCHIC, 90);
        assertEquals(PokeType.FIRE, DamageCalculator.effectiveMoveType(attacker, dance, field, new java.util.ArrayList<>()));

        PokemonSet weighted = vaporeon();
        weighted.species = species("weightmon", PokeType.WATER, PokeType.NONE,
                100, 100, 100, 100, 100, 100, 40.0, false);
        MoveData knot = special("grassknot", PokeType.GRASS, 1);
        assertEquals(60, DamageCalculator.effectivePower(attacker, weighted, knot, PokeType.GRASS,
                new FieldState(), new SideConditions(), new SideConditions(), new java.util.ArrayList<>(), new java.util.ArrayList<>()));
        weighted.ability = "Heavy Metal";
        assertEquals(80, DamageCalculator.effectivePower(attacker, weighted, knot, PokeType.GRASS,
                new FieldState(), new SideConditions(), new SideConditions(), new java.util.ArrayList<>(), new java.util.ArrayList<>()));
        weighted.item = "Float Stone";
        assertEquals(60, DamageCalculator.effectivePower(attacker, weighted, knot, PokeType.GRASS,
                new FieldState(), new SideConditions(), new SideConditions(), new java.util.ArrayList<>(), new java.util.ArrayList<>()));
    }

    @Test void electroBallEqualityAndEffectiveCategoriesUseTheActualCombatStats() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        MoveData electroBall = special("electroball", PokeType.ELECTRIC, 1);
        assertEquals(60, DamageCalculator.effectivePower(attacker, defender, electroBall, PokeType.ELECTRIC,
                new FieldState(), new SideConditions(), new SideConditions(), new java.util.ArrayList<>(), new java.util.ArrayList<>()));
        assertEquals(List.of(23, 24, 24, 24, 24, 25, 25, 25, 26, 26, 26, 26, 27, 27, 27, 28),
                calculate(attacker, defender, electroBall).rolls());

        attacker.species.baseStats().put(Stat.ATK, 130);
        attacker.species.baseStats().put(Stat.SPA, 70);
        MoveData teraBlast = special("terablast", PokeType.NORMAL, 80);
        attacker.terastallized = true;
        attacker.teraType = PokeType.FIRE;
        assertEquals(DamageCategory.PHYSICAL,
                DamageCalculator.resolveMove(attacker, defender, teraBlast, new FieldState(), new java.util.ArrayList<>()).category());
        MoveData photon = special("photongeyser", PokeType.PSYCHIC, 100);
        assertEquals(DamageCategory.PHYSICAL,
                DamageCalculator.resolveMove(attacker, defender, photon, new FieldState(), new java.util.ArrayList<>()).category());

        defender.species.baseStats().put(Stat.DEF, 200);
        defender.species.baseStats().put(Stat.SPD, 50);
        MoveData shell = physical("shellsidearm", PokeType.POISON, 90);
        MoveData resolved = DamageCalculator.resolveMove(attacker, defender, shell,
                new FieldState(), new java.util.ArrayList<>());
        assertEquals(DamageCategory.SPECIAL, resolved.category());
        defender.statsKnown = false;
        assertEquals(Stat.SPD, TropimonDamageCalcBridge.estimatedProfile(defender, resolved).defenseStat());

        PokemonSet tied = mew();
        tied.terastallized = true;
        tied.teraType = PokeType.FIRE;
        tied.item = "Choice Band";
        tied.boosts.put(Stat.ATK, 1);
        tied.boosts.put(Stat.SPA, 1);
        assertEquals(DamageCategory.SPECIAL, DamageCalculator.resolveMove(tied, mew(), teraBlast,
                new FieldState(), new java.util.ArrayList<>()).category(),
                "Choice Band must not influence Tera Blast's category comparison");

        tied.boosts.put(Stat.ATK, 2);
        MoveData physicalShell = DamageCalculator.resolveMove(tied, mew(), shell,
                new FieldState(), new java.util.ArrayList<>());
        assertEquals(DamageCategory.PHYSICAL, physicalShell.category());
        assertTrue(physicalShell.contact(), "physical Shell Side Arm must dynamically gain contact");
    }

    @Test void basePowerItemsUseReferenceFixedPointRounding() {
        PokemonSet attacker = mew();
        attacker.item = "Muscle Band";
        PokemonSet defender = mew();
        assertEquals(List.of(32, 32, 33, 33, 33, 34, 34, 34, 35, 35, 36, 36, 36, 37, 37, 38),
                calculate(attacker, defender, physical("razorshell", PokeType.WATER, 75)).rolls());
    }

    @Test void loadedDicePopulationBombAndParentalFixedDamageKeepTheirDistinctHitRules() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        attacker.item = "Loaded Dice";
        MoveData populationBomb = new MoveData("populationbomb", "Population Bomb", PokeType.NORMAL,
                DamageCategory.PHYSICAL, 20, false, true,
                Set.of("hits10", "multiaccuracy", "accuracy90", "contact"), 0);
        DamageResult bomb = calculate(attacker, defender, populationBomb);
        assertTrue(bomb.notes().contains("4-10 hits"));
        assertTrue(bomb.maxDamage() > bomb.minDamage());

        attacker.item = "None";
        attacker.ability = "Parental Bond";
        defender.item = "Focus Sash";
        defender.observedMaxHp = 80;
        defender.currentHp = 80;
        MoveData toss = physical("seismictoss", PokeType.FIGHTING, 0);
        DamageResult fixed = calculate(attacker, defender, toss);
        assertEquals(80, fixed.minDamage());
        assertEquals(80, fixed.maxDamage());
        assertNotEquals("survives at 1 HP", fixed.koChance());
        assertTrue(fixed.notes().contains("Parental Bond: 2 hits"));
    }

    @Test void signatureMovesIgnoreBreakableDefensiveAbilities() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        defender.ability = "Multiscale";
        MoveData photon = special("photongeyser", PokeType.PSYCHIC, 100);
        MoveData ordinary = special("psychic", PokeType.PSYCHIC, 100);
        assertTrue(calculate(attacker, defender, photon).maxDamage()
                > calculate(attacker, defender, ordinary).maxDamage());
    }

    @Test void anExplicitHiddenPowerSuffixOverridesTheTemplatesNominalNormalType() {
        MoveTemplate hiddenPower = TestMoveTemplates.create("hiddenpowerice", 0, ElementalTypes.NORMAL,
                DamageCategories.INSTANCE.getSPECIAL(), 60, MoveTarget.normal, 100, 15, 0, 1, new Double[0]);
        assertEquals(PokeType.ICE, BattleCalculationInputs.move(hiddenPower).type());
    }

    @Test void fluffyContactAndLongReachMatchReferenceRolls() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        defender.ability = "Fluffy";
        MoveData tackle = physical("tackle", PokeType.NORMAL, 40);
        assertEquals(List.of(8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9),
                calculate(attacker, defender, tackle).rolls());
        attacker.ability = "Long Reach";
        assertEquals(List.of(16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 19),
                calculate(attacker, defender, tackle).rolls());
    }

    @Test void fixedDamageShedinjaAndKoWordingExposeSurvivalWithoutFalseGuarantees() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        defender.item = "Focus Sash";
        MoveData finalGambit = special("finalgambit", PokeType.FIGHTING, 0);
        attacker.currentHp = attacker.maxHp();
        assertEquals("survives at 1 HP", calculate(attacker, defender, finalGambit).koChance());

        PokemonSet shedinja = new PokemonSet(species("shedinja", PokeType.BUG, PokeType.GHOST,
                1, 90, 45, 30, 30, 40, 1.2, false));
        assertEquals(1, shedinja.maxHp());

        DamageResult foulPlay = calculate(mew(), mew(), physical("foulplay", PokeType.DARK, 95));
        assertTrue(foulPlay.koChance().contains("chance to 3HKO without recovery"));
        assertFalse(foulPlay.koChance().contains("guaranteed 3HKO"));
    }

    @Test void shellSideArmComparesExactStatRatiosWithoutEarlyFlooring() {
        PokemonSet attacker = new PokemonSet(species("ratio-attacker", PokeType.PSYCHIC, PokeType.NONE,
                100, 101, 100, 100, 100, 100, 1.0, false));
        attacker.level = 50;
        attacker.nature = new NatureData("serious", "Serious", null, null);
        PokemonSet defender = mew();
        MoveData shell = physical("shellsidearm", PokeType.POISON, 90);
        assertEquals(DamageCategory.PHYSICAL, DamageCalculator.resolveMove(attacker, defender, shell,
                new FieldState(), new java.util.ArrayList<>()).category());
    }

    @Test void defensiveModifiersAreChainedOnceWithGenNineFixedPointRounding() {
        PokemonSet attacker = new PokemonSet(species("low-attack", PokeType.PSYCHIC, PokeType.NONE,
                100, 30, 100, 100, 100, 100, 1.0, false));
        attacker.level = 50;
        attacker.nature = new NatureData("serious", "Serious", null, null);
        attacker.ability = "Sword of Ruin";
        PokemonSet defender = new PokemonSet(species("odd-defense", PokeType.PSYCHIC, PokeType.NONE,
                100, 100, 81, 100, 100, 100, 1.0, false));
        defender.level = 50;
        defender.nature = new NatureData("serious", "Serious", null, null);
        defender.ability = "Fur Coat";
        assertEquals(10, calculate(attacker, defender, physical("oddrounding", PokeType.NORMAL, 55)).maxDamage());

        defender = new PokemonSet(species("paradox-defense", PokeType.PSYCHIC, PokeType.NONE,
                100, 30, 85, 30, 30, 30, 1.0, false));
        defender.level = 50;
        defender.nature = new NatureData("serious", "Serious", null, null);
        defender.ability = "Protosynthesis";
        FieldState sun = new FieldState();
        sun.weather = Weather.SUN;
        attacker.ability = "Synchronize";
        assertEquals(7, calculate(attacker, defender, physical("defensiveqp", PokeType.NORMAL, 31), sun).maxDamage());
    }

    @Test void progressiveMultiHitPowerIsModifiedAndRoundedPerHit() {
        PokemonSet attacker = new PokemonSet(species("triple-attacker", PokeType.PSYCHIC, PokeType.NONE,
                100, 30, 100, 100, 100, 100, 1.0, false));
        attacker.level = 50;
        attacker.nature = new NatureData("serious", "Serious", null, null);
        attacker.ability = "Technician";
        attacker.item = "Muscle Band";
        PokemonSet defender = new PokemonSet(species("triple-defender", PokeType.PSYCHIC, PokeType.NONE,
                100, 100, 31, 100, 100, 100, 1.0, false));
        defender.level = 50;
        defender.nature = new NatureData("serious", "Serious", null, null);
        MoveData tripleKick = new MoveData("triplekick", "Triple Kick", PokeType.NORMAL,
                DamageCategory.PHYSICAL, 10, false, true, Set.of("hits3", "contact"), 0);
        DamageResult result = calculate(attacker, defender, tripleKick);
        assertEquals(38, result.minDamage());
        assertEquals(47, result.maxDamage());
    }

    @Test void knockOffAndPunchingGloveUseTheReferenceBasePowerChain() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        attacker.ability = "Tough Claws";
        defender.item = "Leftovers";
        MoveData knockOff = physical("knockoff", PokeType.DARK, 65);
        assertEquals(127, DamageCalculator.effectivePower(attacker, defender, knockOff, PokeType.DARK,
                new FieldState(), new SideConditions(), new SideConditions(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>()));

        attacker.item = "Punching Glove";
        MoveData punch = new MoveData("testpunch", "Test Punch", PokeType.NORMAL,
                DamageCategory.PHYSICAL, 100, false, true, Set.of("contact", "punch"), 0);
        assertEquals(110, DamageCalculator.effectivePower(attacker, defender, punch, PokeType.NORMAL,
                new FieldState(), new SideConditions(), new SideConditions(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>()));
    }

    @Test void parentalBondHonoursExclusionFlagsAndFixedDamageShields() {
        PokemonSet attacker = mew();
        PokemonSet defender = mew();
        attacker.ability = "Parental Bond";
        MoveData charge = new MoveData("chargeattack", "Charge Attack", PokeType.NORMAL,
                DamageCategory.SPECIAL, 120, false, false, Set.of("charge"), 0);
        int excluded = calculate(attacker, defender, charge).maxDamage();
        attacker.ability = "Synchronize";
        assertEquals(calculate(attacker, defender, charge).maxDamage(), excluded);

        MoveData toss = physical("seismictoss", PokeType.FIGHTING, 0);
        defender.ability = "Disguise";
        assertEquals(0, calculate(attacker, defender, toss).maxDamage());
        attacker.ability = "Parental Bond";
        assertEquals(50, calculate(attacker, defender, toss).maxDamage());
        attacker.ability = "Synchronize";
        defender.ability = "Ice Face";
        assertEquals(0, calculate(attacker, defender, toss).maxDamage());
    }

    private static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move) {
        return calculate(attacker, defender, move, new FieldState());
    }

    private static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move, FieldState field) {
        return DamageCalculator.calculate(attacker, defender, move, field,
                field.attackerSide, field.defenderSide);
    }

    private static MoveData physical(String id, PokeType type, int power) {
        return new MoveData(id, id, type, DamageCategory.PHYSICAL, power, false, true,
                Set.of("contact"), 0);
    }

    private static MoveData special(String id, PokeType type, int power) {
        return new MoveData(id, id, type, DamageCategory.SPECIAL, power, false, false, Set.of(), 0);
    }

    private static PokemonSet mew() {
        PokemonSet pokemon = new PokemonSet(species("mew", PokeType.PSYCHIC, PokeType.NONE,
                100, 100, 100, 100, 100, 100, 4.0, false));
        pokemon.level = 50;
        pokemon.ability = "Synchronize";
        pokemon.item = "None";
        pokemon.nature = new NatureData("serious", "Serious", null, null);
        return pokemon;
    }

    private static PokemonSet vaporeon() {
        PokemonSet pokemon = new PokemonSet(species("vaporeon", PokeType.WATER, PokeType.NONE,
                130, 65, 60, 110, 95, 65, 29.0, false));
        pokemon.level = 50;
        pokemon.ability = "Water Absorb";
        pokemon.item = "None";
        pokemon.nature = new NatureData("serious", "Serious", null, null);
        return pokemon;
    }

    private static SpeciesData species(String id, PokeType first, PokeType second,
                                       int hp, int atk, int def, int spa, int spd, int spe,
                                       double weight, boolean nfe) {
        EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
        stats.put(Stat.HP, hp);
        stats.put(Stat.ATK, atk);
        stats.put(Stat.DEF, def);
        stats.put(Stat.SPA, spa);
        stats.put(Stat.SPD, spd);
        stats.put(Stat.SPE, spe);
        return new SpeciesData(id, id, first, second, stats, nfe, "", id, List.of(), weight);
    }
}
