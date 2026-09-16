package fr.tropimon.battleui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Independent fixtures for edge cases found during the final 0.1.22 review. */
final class DamageLateReviewRegressionTest {
    @BeforeAll static void bootstrap() {
        DamageCacheParityTest.bootstrap();
    }

    @Test void shellSideArmUsesTheUnroundedStatRatio() {
        PokemonSet attacker = pokemon("ratio-attacker", PokeType.PSYCHIC,
                100, 101, 100, 100, 100, 100);
        PokemonSet defender = pokemon("ratio-defender", PokeType.WATER,
                100, 100, 100, 100, 100, 100);
        MoveData shellSideArm = physical("shellsidearm", PokeType.POISON, 90, "contact");

        assertEquals(DamageCategory.PHYSICAL, DamageCalculator.resolveMove(
                attacker, defender, shellSideArm, new FieldState(), new java.util.ArrayList<>()).category());
    }

    @Test void shellSideArmUsesWonderRoomRawStatsWithTheOppositeDefenseStages() {
        PokemonSet attacker = pokemon("wonder-ratio-attacker", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        PokemonSet defender = pokemon("wonder-ratio-defender", PokeType.WATER,
                100, 100, 200, 100, 50, 100);
        defender.boosts.put(Stat.DEF, 2);
        defender.boosts.put(Stat.SPD, -1);
        FieldState field = new FieldState();
        field.wonderRoom = true;

        MoveData resolved = DamageCalculator.resolveMove(attacker, defender,
                physical("shellsidearm", PokeType.POISON, 90, "contact"), field,
                new java.util.ArrayList<>());
        assertEquals(DamageCategory.SPECIAL, resolved.category());
    }

    @Test void defensiveModifiersAreChainedBeforeTheirSingleRounding() {
        PokemonSet attacker = pokemon("ruin-attacker", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        attacker.ability = "Sword of Ruin";
        PokemonSet defender = pokemon("fur-defender", PokeType.NORMAL,
                100, 100, 81, 100, 100, 100);
        defender.ability = "Fur Coat";

        DamageResult result = calculate(attacker, defender,
                physical("reviewstrike", PokeType.WATER, 43, "contact"));
        assertEquals(17, result.maxDamage(), "101 Def x chained 0.75 x 2 rounds to 151, not 152");
    }

    @Test void progressiveMovesReapplyBasePowerModifiersToEachRawPower() {
        PokemonSet attacker = pokemon("kick-attacker", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        attacker.ability = "Technician";
        attacker.item = "Muscle Band";
        PokemonSet defender = pokemon("kick-defender", PokeType.WATER,
                100, 100, 100, 100, 100, 100);

        MoveData tripleKick = physical("triplekick", PokeType.FIGHTING, 10, "contact", "hits3");
        DamageResult combined = calculate(attacker, defender, tripleKick);
        DamageResult first = calculate(attacker, defender, physical("kick-one", PokeType.FIGHTING, 10, "contact"));
        DamageResult second = calculate(attacker, defender, physical("kick-two", PokeType.FIGHTING, 20, "contact"));
        DamageResult third = calculate(attacker, defender, physical("kick-three", PokeType.FIGHTING, 30, "contact"));

        assertEquals(first.minDamage() + second.minDamage() + third.minDamage(), combined.minDamage());
        assertEquals(first.maxDamage() + second.maxDamage() + third.maxDamage(), combined.maxDamage());
    }

    @Test void parentalBondKnockOffConsumesTheTargetsItemBeforeTheChildHit() {
        PokemonSet parent = pokemon("parent", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        parent.ability = "Parental Bond";
        PokemonSet defender = pokemon("item-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        defender.item = "Leftovers";
        MoveData knockOff = physical("knockoff", PokeType.DARK, 65, "contact");

        DamageResult actual = calculate(parent, defender, knockOff);
        PokemonSet ordinary = parent.copy();
        ordinary.ability = "Synchronize";
        DamageResult first = calculate(ordinary, defender, knockOff);
        PokemonSet itemless = defender.copy();
        itemless.item = "None";
        DamageResult second = calculate(ordinary, itemless, knockOff);

        assertEquals(first.maxDamage() + parentalChildMax(second.maxDamage()), actual.maxDamage());
        assertEquals(first.minDamage() + parentalChildMin(second.maxDamage()), actual.minDamage());
    }

    @Test void punchingGloveRemovesContactBeforeToughClawsIsEvaluated() {
        PokemonSet glove = pokemon("glove", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        glove.item = "Punching Glove";
        PokemonSet target = pokemon("glove-target", PokeType.WATER,
                100, 100, 100, 100, 100, 100);
        MoveData punch = physical("drainpunch", PokeType.FIGHTING, 75, "contact", "punch");

        DamageResult itemOnly = calculate(glove, target, punch);
        glove.ability = "Tough Claws";
        DamageResult withToughClaws = calculate(glove, target, punch);
        assertEquals(itemOnly.rolls(), withToughClaws.rolls());
    }

    @Test void parentalBondHonoursMoveExclusionFlags() {
        PokemonSet parent = pokemon("parent", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        PokemonSet target = pokemon("parent-target", PokeType.WATER,
                100, 100, 100, 100, 100, 100);

        for (String exclusion : List.of("noparentalbond", "charge", "futuremove")) {
            MoveData move = physical("excluded-" + exclusion, PokeType.NORMAL, 80, "contact", exclusion);
            DamageResult ordinary = calculate(parent, target, move);
            parent.ability = "Parental Bond";
            DamageResult excluded = calculate(parent, target, move);
            assertEquals(ordinary.rolls(), excluded.rolls(), exclusion);
            parent.ability = "Synchronize";
        }
    }

    @Test void disguiseAndIceFaceBlockTheFirstParentalFixedDamageHit() {
        PokemonSet parent = pokemon("fixed-parent", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        parent.ability = "Parental Bond";

        PokemonSet disguised = pokemon("disguised", PokeType.GHOST,
                100, 100, 100, 100, 100, 100);
        disguised.ability = "Disguise";
        assertEquals(50, calculate(parent, disguised,
                special("nightshade", PokeType.GHOST, 0)).maxDamage());

        PokemonSet iceFace = pokemon("ice-face", PokeType.ICE,
                100, 100, 100, 100, 100, 100);
        iceFace.ability = "Ice Face";
        assertEquals(50, calculate(parent, iceFace,
                physical("seismictoss", PokeType.FIGHTING, 0, "contact")).maxDamage());
    }

    @Test void deterministicStatChangesAreAppliedBetweenHits() {
        PokemonSet attacker = pokemon("multi-attacker", PokeType.PSYCHIC,
                100, 100, 100, 100, 100, 100);
        PokemonSet target = pokemon("multi-target", PokeType.WATER,
                100, 100, 100, 100, 100, 100);

        attacker.ability = "Parental Bond";
        MoveData powerUpPunch = physical("poweruppunch", PokeType.FIGHTING, 40,
                "contact", "punch", "selfboostatk1");
        DamageResult parental = calculate(attacker, target, powerUpPunch);
        PokemonSet ordinary = attacker.copy();
        ordinary.ability = "Synchronize";
        DamageResult first = calculate(ordinary, target, powerUpPunch);
        ordinary.boosts.put(Stat.ATK, 1);
        DamageResult boosted = calculate(ordinary, target, powerUpPunch);
        assertEquals(first.maxDamage() + parentalChildMax(boosted.maxDamage()), parental.maxDamage());

        attacker.ability = "Synchronize";
        MoveData twoHit = physical("doublehit", PokeType.NORMAL, 35, "contact", "hits2");
        target.ability = "Stamina";
        DamageResult stamina = calculate(attacker, target, twoHit);
        DamageResult beforeBoost = calculate(attacker, target,
                physical("single-one", PokeType.NORMAL, 35, "contact"));
        target.boosts.put(Stat.DEF, 1);
        DamageResult afterBoost = calculate(attacker, target,
                physical("single-two", PokeType.NORMAL, 35, "contact"));
        assertEquals(beforeBoost.maxDamage() + afterBoost.maxDamage(), stamina.maxDamage());
    }

    @Test void wonderRoomSwapsOnlyRawDefensiveStats() {
        PokemonSet attacker = pokemon("wonder-attacker", PokeType.NORMAL,
                100, 120, 100, 120, 100, 100);
        PokemonSet physicalTarget = pokemon("wonder-physical", PokeType.ICE,
                100, 100, 200, 100, 50, 100);
        physicalTarget.ability = "Fur Coat";
        PokemonSet physicalReference = pokemon("wonder-physical-reference", PokeType.ICE,
                100, 100, 50, 100, 200, 100);
        physicalReference.ability = "Fur Coat";
        FieldState wonderRoom = new FieldState();
        wonderRoom.wonderRoom = true;

        DamageResult physicalInRoom = calculate(attacker, physicalTarget,
                physical("wonder-hit", PokeType.NORMAL, 80, "contact"), wonderRoom);
        DamageResult physicalReferenceResult = calculate(attacker, physicalReference,
                physical("wonder-hit", PokeType.NORMAL, 80, "contact"));
        assertEquals(physicalReferenceResult.rolls(), physicalInRoom.rolls(),
                "Fur Coat and the physical channel must remain active after the raw stats swap");

        PokemonSet specialTarget = pokemon("wonder-special", PokeType.ROCK,
                100, 100, 200, 100, 50, 100);
        specialTarget.item = "Assault Vest";
        PokemonSet specialReference = pokemon("wonder-special-reference", PokeType.ROCK,
                100, 100, 50, 100, 200, 100);
        specialReference.item = "Assault Vest";
        wonderRoom.weather = Weather.SAND;
        FieldState referenceSand = new FieldState();
        referenceSand.weather = Weather.SAND;

        DamageResult specialInRoom = calculate(attacker, specialTarget,
                special("wonder-beam", PokeType.NORMAL, 80), wonderRoom);
        DamageResult specialReferenceResult = calculate(attacker, specialReference,
                special("wonder-beam", PokeType.NORMAL, 80), referenceSand);
        assertEquals(specialReferenceResult.rolls(), specialInRoom.rolls(),
                "Assault Vest and Sand must remain on the special channel after the raw stats swap");
    }

    @Test void parentalBondUsesTheActualNumberOfSpreadTargets() {
        PokemonSet parent = pokemon("spread-parent", PokeType.NORMAL,
                100, 120, 100, 100, 100, 100);
        parent.ability = "Parental Bond";
        PokemonSet target = pokemon("spread-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        MoveData spread = new MoveData("spread-test", "spread-test", PokeType.NORMAL,
                DamageCategory.PHYSICAL, 60, true, false, Set.of(), 0);

        PokemonSet ordinary = parent.copy();
        ordinary.ability = "Synchronize";
        DamageResult ordinarySingles = calculate(ordinary, target, spread);
        DamageResult parentalSingles = calculate(parent, target, spread);
        assertTrue(parentalSingles.maxDamage() > ordinarySingles.maxDamage());

        FieldState doubles = new FieldState();
        doubles.doubles = true;
        doubles.attackerSide.spreadTargets = 1;
        DamageResult ordinaryDoubles = calculate(ordinary, target, spread, doubles);
        DamageResult parentalDoubles = calculate(parent, target, spread, doubles);
        assertTrue(parentalDoubles.maxDamage() > ordinaryDoubles.maxDamage(),
                "A spread move that actually hits one target still activates Parental Bond");

        doubles.attackerSide.spreadTargets = 2;
        ordinaryDoubles = calculate(ordinary, target, spread, doubles);
        parentalDoubles = calculate(parent, target, spread, doubles);
        assertEquals(ordinaryDoubles.rolls(), parentalDoubles.rolls());
    }

    @Test void weakArmorWaitsUntilTheWholeMoveEndsBeforeWhiteHerbActivates() {
        PokemonSet attacker = pokemon("herb-attacker", PokeType.NORMAL,
                100, 120, 100, 100, 100, 100);
        PokemonSet target = pokemon("herb-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        target.ability = "Weak Armor";
        target.item = "White Herb";
        MoveData twoHit = physical("herb-double-hit", PokeType.NORMAL, 40, "contact", "hits2");

        DamageResult result = calculate(attacker, target, twoHit);
        PokemonSet ordinaryTarget = target.copy();
        ordinaryTarget.ability = "Synchronize";
        ordinaryTarget.item = "None";
        DamageResult firstHit = calculate(attacker, ordinaryTarget,
                physical("herb-one-hit", PokeType.NORMAL, 40, "contact"));
        ordinaryTarget.boosts.put(Stat.DEF, -1);
        DamageResult secondHit = calculate(attacker, ordinaryTarget,
                physical("herb-second-hit", PokeType.NORMAL, 40, "contact"));
        assertEquals(firstHit.minDamage() + secondHit.minDamage(), result.minDamage());
        assertEquals(firstHit.maxDamage() + secondHit.maxDamage(), result.maxDamage());
    }

    @Test void bodyPressUsesSpecialDefenseStagesDuringWonderRoom() {
        PokemonSet attacker = pokemon("wonder-press-attacker", PokeType.FIGHTING,
                100, 50, 200, 50, 50, 100);
        attacker.boosts.put(Stat.DEF, 2);
        attacker.boosts.put(Stat.SPD, -1);
        PokemonSet defender = pokemon("wonder-press-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        MoveData bodyPress = physical("bodypress", PokeType.FIGHTING, 80, "contact");

        FieldState wonderRoom = new FieldState();
        wonderRoom.wonderRoom = true;
        DamageResult actual = calculate(attacker, defender, bodyPress, wonderRoom);

        PokemonSet reference = attacker.copy();
        reference.boosts.put(Stat.DEF, -1);
        reference.boosts.put(Stat.SPD, 0);
        DamageResult expected = calculate(reference, defender, bodyPress);
        assertEquals(expected.rolls(), actual.rolls());
    }

    @Test void abilityIgnoringMovesStillTriggerPostHitAbilitiesBetweenHits() {
        PokemonSet attacker = pokemon("breaker-attacker", PokeType.NORMAL,
                100, 120, 100, 100, 100, 100);
        attacker.ability = "Mold Breaker";
        PokemonSet target = pokemon("post-hit-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        MoveData twoHit = physical("breaker-double-hit", PokeType.NORMAL, 40,
                "contact", "hits2", "ignoreability");

        PokemonSet neutral = target.copy();
        DamageResult firstHit = calculate(attacker, neutral,
                physical("breaker-first-hit", PokeType.NORMAL, 40, "contact", "ignoreability"));

        target.ability = "Weak Armor";
        PokemonSet weakened = neutral.copy();
        weakened.boosts.put(Stat.DEF, -1);
        DamageResult weakArmorSecond = calculate(attacker, weakened,
                physical("breaker-weak-second", PokeType.NORMAL, 40, "contact", "ignoreability"));
        DamageResult weakArmor = calculate(attacker, target, twoHit);
        assertEquals(firstHit.maxDamage() + weakArmorSecond.maxDamage(), weakArmor.maxDamage());

        target.ability = "Stamina";
        PokemonSet hardened = neutral.copy();
        hardened.boosts.put(Stat.DEF, 1);
        DamageResult staminaSecond = calculate(attacker, hardened,
                physical("breaker-stamina-second", PokeType.NORMAL, 40, "contact", "ignoreability"));
        DamageResult stamina = calculate(attacker, target, twoHit);
        assertEquals(firstHit.maxDamage() + staminaSecond.maxDamage(), stamina.maxDamage());
    }

    @Test void knockOffDoesNotBoostAgainstAnUnremovableItem() {
        PokemonSet attacker = pokemon("knock-attacker", PokeType.DARK,
                100, 120, 100, 100, 100, 100);
        PokemonSet arceus = pokemon("arceus", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        arceus.item = "Flame Plate";
        PokemonSet itemless = arceus.copy();
        itemless.item = "None";
        MoveData knockOff = physical("knockoff", PokeType.DARK, 65, "contact");

        assertEquals(calculate(attacker, itemless, knockOff).rolls(),
                calculate(attacker, arceus, knockOff).rolls());
    }

    @Test void parentalBondRecomputesHalfHpFixedDamageForTheSecondHit() {
        PokemonSet parent = pokemon("fixed-half-parent", PokeType.DARK,
                100, 100, 100, 100, 100, 100);
        parent.ability = "Parental Bond";
        PokemonSet target = pokemon("fixed-half-target", PokeType.NORMAL,
                100, 100, 100, 100, 100, 100);
        target.observedMaxHp = 100;
        target.currentHp = 100;

        assertEquals(75, calculate(parent, target,
                physical("superfang", PokeType.NORMAL, 0, "contact")).maxDamage());
        assertEquals(75, calculate(parent, target,
                special("naturesmadness", PokeType.FAIRY, 0)).maxDamage());
        assertEquals(75, calculate(parent, target,
                special("ruination", PokeType.DARK, 0)).maxDamage());

        target.ability = "Disguise";
        assertEquals(50, calculate(parent, target,
                special("ruination", PokeType.DARK, 0)).maxDamage());
    }

    private static int parentalChildMax(int fullHitMax) {
        return roundQ12(fullHitMax, 1024);
    }

    private static int parentalChildMin(int fullHitMax) {
        return roundQ12(fullHitMax, 1024) * 85 / 100;
    }

    private static int roundQ12(int value, int modifier) {
        return (value * modifier + 2047) / 4096;
    }

    private static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move) {
        FieldState field = new FieldState();
        return calculate(attacker, defender, move, field);
    }

    private static DamageResult calculate(PokemonSet attacker, PokemonSet defender, MoveData move,
                                          FieldState field) {
        return DamageCalculator.calculate(attacker, defender, move, field,
                field.attackerSide, field.defenderSide);
    }

    private static MoveData physical(String id, PokeType type, int power, String... flags) {
        Set<String> moveFlags = Set.of(flags);
        return new MoveData(id, id, type, DamageCategory.PHYSICAL, power, false,
                moveFlags.contains("contact"), moveFlags, 0);
    }

    private static MoveData special(String id, PokeType type, int power, String... flags) {
        Set<String> moveFlags = Set.of(flags);
        return new MoveData(id, id, type, DamageCategory.SPECIAL, power, false,
                moveFlags.contains("contact"), moveFlags, 0);
    }

    private static PokemonSet pokemon(String id, PokeType type, int hp, int atk, int def,
                                      int spa, int spd, int spe) {
        EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
        stats.put(Stat.HP, hp);
        stats.put(Stat.ATK, atk);
        stats.put(Stat.DEF, def);
        stats.put(Stat.SPA, spa);
        stats.put(Stat.SPD, spd);
        stats.put(Stat.SPE, spe);
        PokemonSet pokemon = new PokemonSet(new SpeciesData(id, id, type, PokeType.NONE,
                stats, false, "", id, List.of(), 50.0));
        pokemon.level = 50;
        pokemon.ability = "Synchronize";
        pokemon.item = "None";
        pokemon.nature = new NatureData("serious", "Serious", null, null);
        return pokemon;
    }
}
