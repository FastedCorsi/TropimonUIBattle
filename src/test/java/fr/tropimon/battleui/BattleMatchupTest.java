package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleMatchupTest {
    private static final BattleMatchup.Pokemon ATTACKER = pokemon("normal");
    private static final BattleMatchup.Field CLEAR = BattleMatchup.Field.EMPTY;

    @Test void roostRemovesFlyingAgainstAllTypesAndPureFlyingBecomesNormal() {
        var target = effects(pokemon("electric", "flying"), "roost");
        assertMultiplier(1, "icebeam", "ice", ATTACKER, target, CLEAR);
        assertMultiplier(2, "earthquake", "ground", ATTACKER, target, CLEAR);
        assertMultiplier(1, "thunderbolt", "electric", ATTACKER, effects(pokemon("flying"), "roost"), CLEAR);
        assertMultiplier(2, "closecombat", "fighting", ATTACKER, effects(pokemon("flying"), "roost"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", ATTACKER, effects(ability(pokemon("electric"), "levitate"), "roost"), CLEAR);
    }

    @Test void thousandArrowsFirstHitIsNeutralButAfterGroundingUsesNormalWeaknesses() {
        var target = pokemon("electric", "flying");
        assertMultiplier(1, "thousandarrows", "ground", ATTACKER, target, CLEAR);
        assertMultiplier(2, "thousandarrows", "ground", ATTACKER, effects(target, "smackdown"), CLEAR);
        assertMultiplier(2, "thousandarrows", "ground", ATTACKER, target, field("", "", "gravity"));
        assertMultiplier(0, "thousandarrows", "ground", ATTACKER, ability(pokemon("steel"), "eartheater"), CLEAR);
        assertMultiplier(2, "thousandarrows", "ground", ATTACKER, ability(pokemon("electric"), "levitate"), CLEAR);
    }

    @Test void ironBallIsNeutralOnFlyingUnlessForcedGroundedAndSuppressedItemsDoNothing() {
        var target = item(pokemon("electric", "flying"), "ironball");
        assertMultiplier(1, "earthquake", "ground", ATTACKER, target, CLEAR);
        assertMultiplier(2, "earthquake", "ground", ATTACKER, target, field("", "", "gravity"));
        assertMultiplier(0, "earthquake", "ground", ATTACKER, target, field("", "", "magicroom"));
        assertMultiplier(0, "earthquake", "ground", ATTACKER, ability(target, "klutz"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", ATTACKER, effects(target, "embargo"), CLEAR);
    }

    @Test void levitateBalloonMagnetRiseTelekinesisAndEarthEaterRemainDistinct() {
        var electric = pokemon("electric");
        for (var target : List.of(ability(electric, "levitate"), item(electric, "airballoon"),
                effects(electric, "magnetrise"), effects(electric, "telekinesis"))) {
            assertMultiplier(0, "earthquake", "ground", ATTACKER, target, CLEAR);
            assertMultiplier(2, "earthquake", "ground", ATTACKER, target, field("", "", "gravity"));
        }
        assertMultiplier(0, "earthquake", "ground", ATTACKER, ability(electric, "eartheater"), field("", "", "gravity"));
        assertMultiplier(2, "earthquake", "ground", ATTACKER, item(electric, "airballoon"), field("", "", "magicroom"));
    }

    @Test void moldBreakerBypassesAbilitiesButNotFlyingOrBalloonOrAbilityShield() {
        var attacker = ability(ATTACKER, "moldbreaker");
        assertMultiplier(2, "earthquake", "ground", attacker, ability(pokemon("electric"), "levitate"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", attacker, ability(pokemon("electric", "flying"), "levitate"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", attacker, item(pokemon("electric"), "airballoon"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", attacker, item(ability(pokemon("electric"), "levitate"), "abilityshield"), CLEAR);
        assertMultiplier(2, "surf", "water", attacker, ability(pokemon("fire"), "waterabsorb"), CLEAR);
        assertMultiplier(1, "sunsteelstrike", "steel", ATTACKER, ability(pokemon("normal"), "wonderguard"), CLEAR);
    }

    @Test void scrappyMindsEyeAndRingTargetRemoveOnlyTheRelevantTypeImmunity() {
        for (String ability : List.of("scrappy", "mindseye")) {
            assertMultiplier(0.5, "tackle", "normal", ability(ATTACKER, ability), pokemon("ghost", "rock"), CLEAR);
            assertMultiplier(2, "closecombat", "fighting", ability(ATTACKER, ability), pokemon("ghost", "rock"), CLEAR);
        }
        assertMultiplier(1, "psychic", "psychic", ATTACKER, effects(pokemon("dark"), "miracleeye"), CLEAR);
        assertMultiplier(0.5, "tackle", "normal", ATTACKER, item(pokemon("ghost", "rock"), "ringtarget"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", ATTACKER, item(ability(pokemon("electric"), "levitate"), "ringtarget"), CLEAR);
    }

    @Test void freezeDryAndFlyingPressUseMoveSpecificPerTypeRules() {
        assertMultiplier(4, "freezedry", "ice", ATTACKER, pokemon("water", "flying"), CLEAR);
        assertMultiplier(4, "freezedry", "ice", ATTACKER, pokemon("water", "dragon"), CLEAR);
        assertMultiplier(2, "freezedry", "normal", ATTACKER, pokemon("water"), CLEAR);
        assertMultiplier(4, "flyingpress", "fighting", ATTACKER, pokemon("grass", "dark"), CLEAR);
        assertMultiplier(1, "flyingpress", "fighting", ATTACKER, pokemon("rock"), CLEAR);
        assertMultiplier(0, "flyingpress", "fighting", ATTACKER, pokemon("ghost"), CLEAR);
        assertMultiplier(1, "flyingpress", "fighting", ability(ATTACKER, "scrappy"), pokemon("ghost"), CLEAR);
    }

    @Test void deltaStreamRemovesOnlyTheFlyingWeaknessAndNotOtherTypeWeaknesses() {
        var winds = field("deltastream", "");
        assertMultiplier(2, "icebeam", "ice", ATTACKER, pokemon("dragon", "flying"), winds);
        assertMultiplier(2, "thunderbolt", "electric", ATTACKER, pokemon("water", "flying"), winds);
        assertMultiplier(1, "stoneedge", "rock", ATTACKER, pokemon("flying"), winds);
        assertMultiplier(4, "icebeam", "ice", ATTACKER, pokemon("dragon", "flying"), field("deltastream", "", "weathersuppressed"));
    }

    @Test void weatherBallTracksWeatherAndSuppressionWithoutTheDamageCalculator() {
        assertMultiplier(2, "weatherball", "normal", ATTACKER, pokemon("fire"), field("raindance", ""));
        assertMultiplier(0.5, "weatherball", "normal", ATTACKER, pokemon("fire"), field("sunnyday", ""));
        assertMultiplier(2, "weatherball", "normal", ATTACKER, pokemon("fire"), field("sandstorm", ""));
        assertMultiplier(2, "weatherball", "normal", ATTACKER, pokemon("dragon"), field("snow", ""));
        assertMultiplier(1, "weatherball", "normal", ATTACKER, pokemon("fire"), field("raindance", "", "weathersuppressed"));
        assertMultiplier(1, "weatherball", "normal", item(ATTACKER, "utilityumbrella"), pokemon("fire"), field("raindance", ""));
        assertMultiplier(0, "surf", "water", ATTACKER, pokemon("fire"), field("desolateland", ""));
        assertMultiplier(0, "flamethrower", "fire", ATTACKER, pokemon("grass"), field("primordialsea", ""));
    }

    @Test void changingAbilitiesHiddenPowerAndJudgmentDoNotUseTheOriginalMoveType() {
        assertMultiplier(2, "hypervoice", "normal", ability(ATTACKER, "pixilate"), pokemon("dragon"), CLEAR);
        assertMultiplier(0, "hypervoice", "normal", ability(ATTACKER, "galvanize"), pokemon("ground"), CLEAR);
        assertMultiplier(2, "hypervoice", "normal", ability(ATTACKER, "liquidvoice"), pokemon("fire"), CLEAR);
        assertMultiplier(0, "flamethrower", "fire", ability(ATTACKER, "normalize"), pokemon("ghost"), CLEAR);
        assertMultiplier(2, "hiddenpower", "ice", ability(ATTACKER, "normalize"), pokemon("dragon"), CLEAR);
        assertMultiplier(2, "judgment", "normal", item(ability(ATTACKER, "normalize"), "flameplate"), pokemon("grass"), CLEAR);
        assertMultiplier(0, "multiattack", "normal", item(ATTACKER, "electricmemory"), pokemon("ground"), CLEAR);
        assertMultiplier(1, "weatherball", "normal", ability(ATTACKER, "pixilate"), pokemon("dragon"), CLEAR);
    }

    @Test void terrainPulseDependsOnActualGrounding() {
        var terrain = field("", "electricterrain");
        assertMultiplier(2, "terrainpulse", "normal", ATTACKER, pokemon("water"), terrain);
        assertMultiplier(1, "terrainpulse", "normal", pokemon("flying"), pokemon("water"), terrain);
        assertMultiplier(1, "terrainpulse", "normal", ability(ATTACKER, "levitate"), pokemon("water"), terrain);
        assertMultiplier(2, "terrainpulse", "normal", pokemon("flying"), pokemon("water"), field("", "electricterrain", "gravity"));
        assertMultiplier(1, "terrainpulse", "normal", item(pokemon("flying"), "ringtarget"), pokemon("water"), terrain);
        var flyingRing = item(pokemon("electric", "flying"), "ringtarget");
        assertMultiplier(2, "earthquake", "ground", ATTACKER, flyingRing, terrain);
        var quick = new BattleMatchup.Move("quickattack", "normal", "physical", 1);
        assertEquals(1, BattleMatchup.analyze(quick, ATTACKER, flyingRing, field("", "psychicterrain")).multiplier());
    }

    @Test void teraAndStellarKeepTheRightOffensiveAndDefensiveTypes() {
        var teraWater = tera(pokemon("fire", "flying"), "water");
        assertMultiplier(2, "thunderbolt", "electric", ATTACKER, teraWater, CLEAR);
        assertMultiplier(1, "earthquake", "ground", ATTACKER, teraWater, CLEAR);
        assertMultiplier(2, "terablast", "normal", tera(ability(ATTACKER, "pixilate"), "water"), pokemon("fire"), CLEAR);
        assertMultiplier(4, "thunderbolt", "electric", ATTACKER, tera(pokemon("water", "flying"), "stellar"), CLEAR);
        assertMultiplier(2, "terablast", "normal", tera(ATTACKER, "stellar"), teraWater, CLEAR);
        assertMultiplier(1, "terablast", "normal", tera(ATTACKER, "stellar"), pokemon("water"), CLEAR);
        assertMultiplier(0, "earthquake", "ground", ATTACKER, effects(tera(pokemon("normal"), "flying"), "roost"), CLEAR);
    }

    @Test void fixedDamageAndOhkoAreNotShownAsTypeDamageMultipliers() {
        var fixed = BattleMatchup.analyze(move("seismictoss", "fighting"), ATTACKER, pokemon("normal"), CLEAR);
        assertEquals(BattleMatchup.DamageKind.FIXED, fixed.kind());
        assertEquals(1, fixed.multiplier());
        assertMultiplier(0, "seismictoss", "fighting", ATTACKER, pokemon("ghost"), CLEAR);
        assertMultiplier(0, "fissure", "ground", ATTACKER, ability(pokemon("electric"), "sturdy"), CLEAR);
        assertMultiplier(0, "sheercold", "ice", ATTACKER, pokemon("ice"), CLEAR);
        assertMultiplier(1, "struggle", "normal", ATTACKER, ability(pokemon("ghost"), "wonderguard"), CLEAR);
    }

    @Test void absorptionSoundBulletAndWindImmunitiesRespectSuppression() {
        for (var test : List.of(List.of("surf", "water", "waterabsorb"), List.of("thunderbolt", "electric", "lightningrod"),
                List.of("flamethrower", "fire", "wellbakedbody"), List.of("seedbomb", "grass", "sapsipper"),
                List.of("hypervoice", "normal", "soundproof"), List.of("shadowball", "ghost", "bulletproof"),
                List.of("hurricane", "flying", "windrider"))) {
            var target = ability(pokemon("psychic"), test.get(2));
            assertMultiplier(0, test.get(0), test.get(1), ATTACKER, target, CLEAR);
            assertTrue(BattleMatchup.analyze(move(test.get(0), test.get(1)), ATTACKER,
                    effects(target, "abilitysuppressed"), CLEAR).multiplier() > 0);
        }
    }

    @Test void priorityBlockedByPsychicTerrainOrArmorTailDoesNotClaimAnEffectiveHit() {
        var quick = new BattleMatchup.Move("quickattack", "normal", "physical", 1);
        assertEquals(0, BattleMatchup.analyze(quick, ATTACKER, pokemon("normal"), field("", "psychicterrain")).multiplier());
        assertEquals(1, BattleMatchup.analyze(quick, ATTACKER, pokemon("flying"), field("", "psychicterrain")).multiplier());
        assertEquals(0, BattleMatchup.analyze(quick, ATTACKER, ability(pokemon("normal"), "armortail"), CLEAR).multiplier());
    }

    @Test void unknownTypesDoNotSilentlyBecomeNeutralAndStatusMovesHaveNoDamageBadge() {
        assertFalse(BattleMatchup.analyze(move("tackle", "normal"), ATTACKER, BattleMatchup.Pokemon.EMPTY, CLEAR).known());
        var status = BattleMatchup.analyze(new BattleMatchup.Move("protect", "normal", "status", 4), ATTACKER, pokemon("ghost"), CLEAR);
        assertEquals(BattleMatchup.DamageKind.STATUS, status.kind());
        assertFalse(status.known());
        assertFalse(BattleMatchup.analyze(move("custommove", "customtype"), ATTACKER, pokemon("fire"), CLEAR).known());
    }

    @Test void teraShellChangesEffectivenessAtFullHpAndGasCanSuppressIt() {
        var shell = ability(pokemon("normal"), "terashell");
        assertMultiplier(0.5, "closecombat", "fighting", ATTACKER, shell, CLEAR);
        assertMultiplier(0.5, "tackle", "normal", ATTACKER, shell, CLEAR);
        assertMultiplier(2, "closecombat", "fighting", ability(ATTACKER, "moldbreaker"), shell, CLEAR);
        assertMultiplier(2, "closecombat", "fighting", ATTACKER, shell, field("", "", "neutralizinggas"));
        assertMultiplier(0.5, "closecombat", "fighting", ATTACKER, item(shell, "abilityshield"), field("", "", "neutralizinggas"));
        var hurt = new BattleMatchup.Pokemon(shell.types(), shell.ability(), "", Set.of(), "", false);
        assertMultiplier(2, "closecombat", "fighting", ATTACKER, hurt, CLEAR);
    }

    @Test void utilityUmbrellaDoesNotLetWaterBypassDesolateLandsGlobalMoveFailure() {
        assertMultiplier(0, "surf", "water", item(ATTACKER, "utilityumbrella"), pokemon("fire"), field("desolateland", ""));
    }

    @Test void repeatedReadsCannotMutatePokemonOrFieldAndChangingTargetsRecomputes() {
        var move = move("thunderbolt", "electric");
        var target = pokemon("water", "flying");
        for (int read = 0; read < 100; read++) {
            assertEquals(4, BattleMatchup.analyze(move, ATTACKER, target, CLEAR).multiplier());
            assertEquals(0, BattleMatchup.analyze(move, ATTACKER, pokemon("ground"), CLEAR).multiplier());
            assertEquals(1, BattleMatchup.analyze(move, ATTACKER, pokemon("normal"), CLEAR).multiplier());
        }
        assertEquals(List.of("water", "flying"), target.types());
    }

    @Test void naturalGiftUsesTheCurrentActiveBerryAndCannotGuessCustomBerries() {
        assertMultiplier(2, "naturalgift", "normal", item(ATTACKER, "occaberry"), pokemon("grass"), CLEAR);
        assertMultiplier(2, "naturalgift", "normal", item(ATTACKER, "sitrusberry"), pokemon("poison"), CLEAR);
        assertMultiplier(0, "naturalgift", "normal", item(ATTACKER, "sitrusberry"), pokemon("dark"), CLEAR);
        for (String item : List.of("", "leftovers", "customberry")) {
            assertFalse(BattleMatchup.analyze(move("naturalgift", "normal"), item(ATTACKER, item), pokemon("normal"), CLEAR).known());
        }
        assertFalse(BattleMatchup.analyze(move("naturalgift", "normal"), item(ATTACKER, "occaberry"),
                pokemon("grass"), field("", "", "magicroom")).known());
    }

    @Test void activeAbilityContextRefreshesAfterSwitchesAndRespectsShieldUnderGas() {
        var weather = field("raindance", "");
        var gas = ability(pokemon("poison"), "neutralizinggas");
        var cloud = ability(pokemon("normal"), "cloudnine");
        var active = BattleMatchup.withActiveAbilities(weather, List.of(gas, cloud), List.of());
        assertMultiplier(2, "weatherball", "normal", ATTACKER, pokemon("fire"), active);
        active = BattleMatchup.withActiveAbilities(weather, List.of(gas, item(cloud, "abilityshield")), List.of());
        assertMultiplier(1, "weatherball", "normal", ATTACKER, pokemon("fire"), active);
        active = BattleMatchup.withActiveAbilities(weather, List.of(cloud), List.of());
        assertMultiplier(1, "weatherball", "normal", ATTACKER, pokemon("fire"), active);
        active = BattleMatchup.withActiveAbilities(weather, List.of(), List.of());
        assertMultiplier(2, "weatherball", "normal", ATTACKER, pokemon("fire"), active);
    }

    @Test void partnerPriorityProtectionIsSuppressedOrBypassedUnlessAbilityShieldApplies() {
        var quick = new BattleMatchup.Move("quickattack", "normal", "physical", 1);
        var tail = ability(pokemon("normal"), "armortail");
        var gas = ability(pokemon("poison"), "neutralizinggas");
        var target = pokemon("normal");
        var field = BattleMatchup.withActiveAbilities(CLEAR, List.of(tail), List.of(tail));
        assertEquals(0, BattleMatchup.analyze(quick, ATTACKER, target, field).multiplier());
        assertEquals(1, BattleMatchup.analyze(quick, ability(ATTACKER, "moldbreaker"), target, field).multiplier());
        assertEquals(1, BattleMatchup.analyze(quick, ability(ATTACKER, "moldbreaker"), item(target, "abilityshield"), field).multiplier());
        field = BattleMatchup.withActiveAbilities(CLEAR, List.of(tail, gas), List.of(tail));
        assertEquals(1, BattleMatchup.analyze(quick, ATTACKER, target, field).multiplier());
        var shieldTail = item(tail, "abilityshield");
        field = BattleMatchup.withActiveAbilities(CLEAR, List.of(shieldTail, gas), List.of(shieldTail));
        assertEquals(0, BattleMatchup.analyze(quick, ATTACKER, target, field).multiplier());
        field = BattleMatchup.withActiveAbilities(CLEAR, List.of(shieldTail), List.of(shieldTail));
        assertEquals(0, BattleMatchup.analyze(quick, ability(ATTACKER, "moldbreaker"), target, field).multiplier());
    }

    static BattleMatchup.Pokemon pokemon(String... types) { return new BattleMatchup.Pokemon(List.of(types), "", "", Set.of(), "", true); }
    static BattleMatchup.Pokemon ability(BattleMatchup.Pokemon p, String ability) { return new BattleMatchup.Pokemon(p.types(), ability, p.item(), p.effects(), p.teraType(), p.fullHp()); }
    static BattleMatchup.Pokemon item(BattleMatchup.Pokemon p, String item) { return new BattleMatchup.Pokemon(p.types(), p.ability(), item, p.effects(), p.teraType(), p.fullHp()); }
    static BattleMatchup.Pokemon effects(BattleMatchup.Pokemon p, String... effects) { return new BattleMatchup.Pokemon(p.types(), p.ability(), p.item(), Set.of(effects), p.teraType(), p.fullHp()); }
    static BattleMatchup.Pokemon tera(BattleMatchup.Pokemon p, String type) { return new BattleMatchup.Pokemon(p.types(), p.ability(), p.item(), p.effects(), type, p.fullHp()); }
    static BattleMatchup.Field field(String weather, String terrain, String... effects) { return new BattleMatchup.Field(weather, terrain, Set.of(effects), Set.of()); }
    static BattleMatchup.Move move(String id, String type) { return new BattleMatchup.Move(id, type, "special", 0); }
    static void assertMultiplier(double expected, String id, String type, BattleMatchup.Pokemon attacker, BattleMatchup.Pokemon target, BattleMatchup.Field field) {
        assertEquals(expected, BattleMatchup.analyze(move(id, type), attacker, target, field).multiplier(), id + " -> " + target);
    }
}
