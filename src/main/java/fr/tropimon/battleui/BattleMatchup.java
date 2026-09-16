package fr.tropimon.battleui;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Type effectiveness, not a damage ratio. Public inputs only; independent of the optional calculator. */
final class BattleMatchup {
    enum DamageKind { NORMAL, FIXED, OHKO, STATUS }

    record Move(String id, String type, String category, int priority) {
        Move { id = BattleMatchup.id(id); type = BattleMatchup.id(type); category = BattleMatchup.id(category); }
        DamageKind kind() {
            if (category.equals("status")) return DamageKind.STATUS;
            if (OHKO.contains(id)) return DamageKind.OHKO;
            return FIXED.contains(id) ? DamageKind.FIXED : DamageKind.NORMAL;
        }
    }

    record Pokemon(List<String> types, String ability, String item, Set<String> effects,
                   String teraType, boolean fullHp) {
        static final Pokemon EMPTY = new Pokemon(List.of(), "", "", Set.of(), "", false);
        Pokemon {
            types = types.stream().map(BattleMatchup::id).distinct().toList();
            ability = id(ability); item = id(item); teraType = id(teraType);
            effects = effects.stream().map(BattleMatchup::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        boolean effect(String id) { return effects.contains(id); }
    }

    record Field(String weather, String terrain, Set<String> effects, Set<String> targetSideAbilities) {
        static final Field EMPTY = new Field("", "", Set.of(), Set.of());
        Field {
            weather = id(weather); terrain = id(terrain);
            effects = Set.copyOf(effects); targetSideAbilities = Set.copyOf(targetSideAbilities);
        }
        boolean effect(String id) { return effects.contains(id); }
    }

    record Result(String type, double multiplier, DamageKind kind, String reason, String source) {
        boolean known() { return Double.isFinite(multiplier); }
        boolean blocked() { return known() && multiplier == 0.0D; }
    }

    static Result analyze(Move move, Pokemon attacker, Pokemon defender, Field field) {
        DamageKind kind = move.kind();
        String type = effectiveType(move, attacker, field);
        if (kind == DamageKind.STATUS) return new Result(type, Double.NaN, kind, "status", "");
        if ((!PokemonTypeMatchups.knownType(type) && !type.equals("stellar")) || defender.types().isEmpty() ||
                defender.types().stream().anyMatch(value -> !PokemonTypeMatchups.knownType(value))) {
            return new Result(type, Double.NaN, kind, "unknown", "");
        }
        String attackerAbility = ability(attacker, field);
        String defenderAbility = ability(defender, field);
        String item = item(defender, field);
        boolean breaksAbilities = Set.of("moldbreaker", "turboblaze", "teravolt").contains(attackerAbility) ||
                IGNORE_ABILITY.contains(move.id());
        boolean ignoresAbility = breaksAbilities && !item.equals("abilityshield");
        if (ignoresAbility) defenderAbility = "";
        List<String> types = defensiveTypes(defender);
        boolean forcedGround = forcedGround(defender, field);
        boolean grounded = grounded(defender, field, ignoresAbility, item.equals("ringtarget"));
        String weather = field.effect("weathersuppressed") ? "" : field.weather();

        // Struggle is typeless: neither Normal immunities nor Wonder Guard apply.
        if (move.id().equals("struggle")) return new Result("typeless", 1, kind, "", "");
        if ((weather.equals("desolateland") && type.equals("water")) ||
                (weather.equals("primordialsea") && type.equals("fire"))) {
            return blocked(type, kind, "weather", weather);
        }
        if (kind == DamageKind.OHKO && (defenderAbility.equals("sturdy") ||
                move.id().equals("sheercold") && types.contains("ice"))) {
            return blocked(type, kind, defenderAbility.equals("sturdy") ? "ability" : "type",
                    defenderAbility.equals("sturdy") ? "sturdy" : "ice");
        }

        if (type.equals("ground") && !grounded && !move.id().equals("thousandarrows")) {
            boolean flying = types.contains("flying") && !item.equals("ringtarget");
            String source = flying ? "flying" : defenderAbility.equals("levitate") ? "levitate"
                    : item.equals("airballoon") ? "airballoon" : "airborne";
            return blocked(type, kind, flying ? "type" : source.equals("levitate") ? "ability"
                    : source.equals("airballoon") ? "item" : "effect", source);
        }

        double multiplier = type.equals("stellar") ? (defender.teraType().isEmpty() ? 1 : 2) : 1;
        if (!type.equals("stellar")) {
            for (String defendingType : types) {
                double factor = PokemonTypeMatchups.factor(type, defendingType);
                if (factor == 0 && (item.equals("ringtarget") ||
                        type.equals("ground") && (grounded || move.id().equals("thousandarrows")) ||
                        defendingType.equals("ghost") && Set.of("normal", "fighting").contains(type) &&
                                (Set.of("scrappy", "mindseye").contains(attackerAbility) || defender.effect("foresight")) ||
                        defendingType.equals("dark") && type.equals("psychic") && defender.effect("miracleeye"))) factor = 1;
                if (move.id().equals("freezedry") && defendingType.equals("water")) factor = 2;
                if (move.id().equals("flyingpress")) factor *= PokemonTypeMatchups.factor("flying", defendingType);
                if (weather.equals("deltastream") && defendingType.equals("flying") && factor > 1) factor = 1;
                multiplier *= factor;
            }
        }
        // On an airborne Flying target the FIRST Thousand Arrows hit is neutral across BOTH types.
        // Iron Ball has the same all-types override unless Gravity/Smack Down/Ingrain already grounds it.
        if (type.equals("ground") && types.contains("flying") &&
                (move.id().equals("thousandarrows") && !grounded || item.equals("ironball") && !forcedGround)) {
            multiplier = 1;
        }
        if (multiplier == 0) return blocked(type, kind, "type", "");
        if (defenderAbility.equals("terashell") && defender.fullHp() && multiplier >= 1) multiplier = 0.5;

        boolean abilityBlocks = switch (defenderAbility) {
            case "eartheater" -> type.equals("ground"); // Thousand Arrows does NOT bypass absorption.
            case "waterabsorb", "stormdrain", "dryskin" -> type.equals("water");
            case "voltabsorb", "lightningrod", "motordrive" -> type.equals("electric");
            case "flashfire", "wellbakedbody" -> type.equals("fire");
            case "sapsipper" -> type.equals("grass");
            case "soundproof" -> SOUND.contains(move.id());
            case "bulletproof" -> BULLET.contains(move.id());
            case "windrider" -> WIND.contains(move.id());
            case "wonderguard" -> multiplier <= 1;
            case "telepathy" -> field.effect("allytarget");
            default -> false;
        };
        if (abilityBlocks) return blocked(type, kind, "ability", defenderAbility);
        int priority = move.priority() + (attackerAbility.equals("galewings") && type.equals("flying") && attacker.fullHp() ? 1 : 0)
                + (attackerAbility.equals("triage") && HEALING_DAMAGE.contains(move.id()) ? 3 : 0);
        if (priority > 0 && !field.effect("allytarget")) {
            if (field.terrain().equals("psychicterrain") && grounded(defender, field, false)) {
                return blocked(type, kind, "terrain", "psychicterrain");
            }
            if (Set.of("queenlymajesty", "dazzling", "armortail").contains(defenderAbility)) {
                return blocked(type, kind, "ability", defenderAbility);
            }
            for (String partner : field.targetSideAbilities()) {
                boolean shielded = partner.startsWith("shield:");
                String partnerAbility = shielded ? partner.substring(7) : partner;
                if ((!breaksAbilities || shielded) && Set.of("queenlymajesty", "dazzling", "armortail").contains(partnerAbility)) {
                    return blocked(type, kind, "ability", partnerAbility);
                }
            }
        }
        // Type weaknesses/resistances do not multiply fixed-damage or OHKO moves.
        return new Result(type, kind == DamageKind.NORMAL ? multiplier : 1, kind, "", "");
    }

    private static Result blocked(String type, DamageKind kind, String reason, String source) {
        return new Result(type, 0, kind, reason, source);
    }

    static List<String> defensiveTypes(Pokemon pokemon) {
        if (!pokemon.teraType().isEmpty() && !pokemon.teraType().equals("stellar")) return List.of(pokemon.teraType());
        if (!pokemon.effect("roost") || !pokemon.teraType().isEmpty()) return pokemon.types();
        List<String> types = pokemon.types().stream().filter(type -> !type.equals("flying")).toList();
        return types.isEmpty() && !pokemon.types().isEmpty() ? List.of("normal") : types;
    }

    static boolean forcedGround(Pokemon pokemon, Field field) {
        return field.effect("gravity") || pokemon.effect("smackdown") || pokemon.effect("ingrain");
    }

    static boolean grounded(Pokemon pokemon, Field field, boolean ignoreAbility) {
        return grounded(pokemon, field, ignoreAbility, false);
    }

    private static boolean grounded(Pokemon pokemon, Field field, boolean ignoreAbility, boolean negateFlyingImmunity) {
        String item = item(pokemon, field);
        if (forcedGround(pokemon, field) || item.equals("ironball")) return true;
        // Ring Target removes Ground's type immunity, but does not make terrain affect a Flying Pokémon.
        if (defensiveTypes(pokemon).contains("flying") && !negateFlyingImmunity) return false;
        if (!ignoreAbility && ability(pokemon, field).equals("levitate")) return false;
        return !item.equals("airballoon") && !pokemon.effect("magnetrise") && !pokemon.effect("telekinesis");
    }

    static String ability(Pokemon pokemon, Field field) {
        if (pokemon.effect("abilitysuppressed")) return "";
        boolean shield = pokemon.item().equals("abilityshield") && !field.effect("magicroom") && !pokemon.effect("embargo");
        if (field.effect("neutralizinggas") && !shield &&
                !Set.of("neutralizinggas", "multitype", "rkssystem", "schooling", "shieldsdown", "stancechange",
                        "comatose", "disguise", "iceface", "gulpmissile", "asoneglastrier", "asonespectrier",
                        "zerotohero", "battlebond", "powerconstruct", "zenmode", "terashift").contains(pokemon.ability())) return "";
        return pokemon.ability();
    }

    static String item(Pokemon pokemon, Field field) {
        return field.effect("magicroom") || pokemon.effect("embargo") || ability(pokemon, field).equals("klutz")
                ? "" : pokemon.item();
    }

    static String effectiveWeather(Pokemon pokemon, Field field) {
        if (field.effect("weathersuppressed")) return "";
        String weather = field.weather();
        if (item(pokemon, field).equals("utilityumbrella") &&
                Set.of("sunnyday", "desolateland", "raindance", "primordialsea").contains(weather)) return "";
        return weather;
    }

    static Field withActiveAbilities(Field field, List<Pokemon> active, List<Pokemon> targetPartners) {
        var effects = new java.util.HashSet<>(field.effects());
        if (active.stream().anyMatch(pokemon -> pokemon.ability().equals("neutralizinggas") &&
                !pokemon.effect("abilitysuppressed"))) effects.add("neutralizinggas");
        Field gasContext = new Field(field.weather(), field.terrain(), effects, Set.of());
        if (active.stream().map(pokemon -> ability(pokemon, gasContext))
                .anyMatch(value -> value.equals("cloudnine") || value.equals("airlock"))) effects.add("weathersuppressed");
        var sideAbilities = new java.util.HashSet<String>();
        for (Pokemon partner : targetPartners) {
            String ability = ability(partner, gasContext);
            if (!ability.isEmpty()) sideAbilities.add((item(partner, gasContext).equals("abilityshield") ? "shield:" : "") + ability);
        }
        return new Field(field.weather(), field.terrain(), effects, sideAbilities);
    }

    static String effectiveType(Move move, Pokemon attacker, Field field) {
        String type = move.type();
        String ability = ability(attacker, field);
        String item = item(attacker, field);
        String weather = effectiveWeather(attacker, field);
        switch (move.id()) {
            case "weatherball" -> type = switch (weather) {
                case "sunnyday", "desolateland" -> "fire";
                case "raindance", "primordialsea" -> "water";
                case "sandstorm" -> "rock";
                case "hail", "snow", "snowscape" -> "ice";
                default -> "normal";
            };
            case "terrainpulse" -> {
                if (grounded(attacker, field, false)) type = switch (field.terrain()) {
                    case "electricterrain" -> "electric";
                    case "grassyterrain" -> "grass";
                    case "mistyterrain" -> "fairy";
                    case "psychicterrain" -> "psychic";
                    default -> "normal";
                };
            }
            case "terablast", "terastarstorm" -> {
                if (!attacker.teraType().isEmpty()) type = attacker.teraType();
            }
            case "revelationdance" -> type = defensiveTypes(attacker).isEmpty() ? "" : defensiveTypes(attacker).getFirst();
            case "judgment" -> type = PLATES.getOrDefault(item, "normal");
            case "multiattack" -> type = item.endsWith("memory") ? item.substring(0, item.length() - 6) : "normal";
            case "technoblast" -> type = switch (item) {
                case "burndrive" -> "fire"; case "chilldrive" -> "ice";
                case "dousedrive" -> "water"; case "shockdrive" -> "electric"; default -> "normal";
            };
            // Unknown/custom berries stay unknown rather than inventing a Normal-type hit.
            case "naturalgift" -> type = naturalGiftType(item);
            // Raging Bull/Ivy Cudgel/Aura Wheel use the actual form, supplied by the runtime adapter.
            default -> { }
        }
        boolean canChange = !NO_TYPE_CONVERSION.contains(move.id()) && !move.id().startsWith("hiddenpower") &&
                !(move.id().equals("terablast") && !attacker.teraType().isEmpty()) &&
                !move.id().startsWith("max") && !move.id().startsWith("gmax");
        if (canChange) {
            if (ability.equals("normalize")) type = "normal";
            else if (type.equals("normal")) type = switch (ability) {
                case "pixilate" -> "fairy"; case "aerilate" -> "flying";
                case "refrigerate" -> "ice"; case "galvanize" -> "electric"; default -> type;
            };
            if (ability.equals("liquidvoice") && SOUND.contains(move.id())) type = "water";
        }
        if (!move.id().equals("struggle")) {
            if (attacker.effect("electrify") || field.effect("iondeluge") && type.equals("normal")) type = "electric";
        }
        return type;
    }

    static String id(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Set<String> ids(String values) { return Set.of(values.split(" ")); }

    private static String naturalGiftType(String item) {
        return switch (item) {
            case "cornnberry", "enigmaberry", "figyberry", "tangaberry" -> "bug";
            case "colburberry", "iapapaberry", "marangaberry", "rowapberry", "spelonberry" -> "dark";
            case "aguavberry", "habanberry", "jabocaberry", "nomelberry" -> "dragon";
            case "belueberry", "pechaberry", "wacanberry", "wepearberry", "psncureberry" -> "electric";
            case "keeberry", "roseliberry" -> "fairy";
            case "chopleberry", "kelpsyberry", "leppaberry", "salacberry", "mysteryberry" -> "fighting";
            case "blukberry", "cheriberry", "occaberry", "watmelberry", "przcureberry" -> "fire";
            case "cobaberry", "grepaberry", "lansatberry", "lumberry", "miracleberry" -> "flying";
            case "custapberry", "kasibberry", "magoberry", "rabutaberry" -> "ghost";
            case "liechiberry", "pinapberry", "rawstberry", "rindoberry", "iceberry" -> "grass";
            case "apicotberry", "hondewberry", "persimberry", "shucaberry", "bitterberry" -> "ground";
            case "aspearberry", "ganlonberry", "pomegberry", "yacheberry", "burntberry" -> "ice";
            case "chilanberry" -> "normal";
            case "kebiaberry", "oranberry", "petayaberry", "qualotberry", "berry" -> "poison";
            case "payapaberry", "sitrusberry", "starfberry", "tamatoberry", "goldberry" -> "psychic";
            case "chartiberry", "magostberry", "micleberry", "wikiberry" -> "rock";
            case "babiriberry", "pamtreberry", "razzberry" -> "steel";
            case "chestoberry", "durinberry", "nanabberry", "passhoberry", "mintberry" -> "water";
            default -> "";
        };
    }
    static final Set<String> SOUND = ids("alluringvoice boomburst bugbuzz chatter clangingscales clangoroussoul clangoroussoulblaze confide disarmingvoice echoedvoice eeriespell grasswhistle growl healbell howl hypervoice metalsound nobleroar overdrive partingshot perishsong psychicnoise relicsong roar round screech sing snarl snore sparklingaria supersonic torchsong uproar");
    static final Set<String> BULLET = ids("acidspray aurasphere barrage beakblast bulletseed eggbomb electroball energyball focusblast gyroball iceball magnetbomb mistball mudbomb octazooka pollenpuff pyroball rockblast rockwrecker searingshot seedbomb shadowball sludgebomb syrupbomb weatherball zapcannon");
    static final Set<String> WIND = ids("aeroblast aircutter bleakwindstorm blizzard fairywind gust heatwave hurricane icywind petalblizzard sandsearstorm sandstorm springtidestorm tailwind twister whirlwind wildboltstorm");
    private static final Set<String> IGNORE_ABILITY = ids("gmaxdrumsolo gmaxfireball gmaxhydrosnipe lightthatburnsthesky menacingmoonrazemaelstrom moongeistbeam photongeyser searingsunrazesmash sunsteelstrike");
    private static final Set<String> NO_TYPE_CONVERSION = ids("hiddenpower judgment multiattack naturalgift revelationdance struggle technoblast terrainpulse weatherball");
    private static final Set<String> FIXED = ids("sonicboom dragonrage seismictoss nightshade psywave superfang naturesmadness ruination endeavor counter mirrorcoat metalburst comeuppance finalgambit guardianofalola");
    private static final Set<String> OHKO = ids("fissure guillotine horndrill sheercold");
    private static final Set<String> HEALING_DAMAGE = ids("absorb drainpunch drainingkiss dreameater gigadrain hornleech leechlife megadrain oblivionwing paraboliccharge bitterblade");
    private static final Map<String, String> PLATES = Map.ofEntries(
            Map.entry("flameplate", "fire"), Map.entry("splashplate", "water"), Map.entry("zapplate", "electric"),
            Map.entry("meadowplate", "grass"), Map.entry("icicleplate", "ice"), Map.entry("fistplate", "fighting"),
            Map.entry("toxicplate", "poison"), Map.entry("earthplate", "ground"), Map.entry("skyplate", "flying"),
            Map.entry("mindplate", "psychic"), Map.entry("insectplate", "bug"), Map.entry("stoneplate", "rock"),
            Map.entry("spookyplate", "ghost"), Map.entry("dracoplate", "dragon"), Map.entry("dreadplate", "dark"),
            Map.entry("ironplate", "steel"), Map.entry("pixieplate", "fairy"));
}
