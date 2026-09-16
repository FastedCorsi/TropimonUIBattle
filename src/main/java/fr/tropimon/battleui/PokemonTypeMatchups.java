package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PokemonTypeMatchups {
    private static final List<String> TYPES = List.of(
            "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
    );
    private static final Map<String, Map<String, Double>> CHART = buildChart();

    private PokemonTypeMatchups() {
    }

    static double factor(String attack, String defense) {
        return CHART.getOrDefault(normalize(attack), Map.of()).getOrDefault(normalize(defense), 1.0D);
    }

    static boolean knownType(String type) { return CHART.containsKey(normalize(type)); }

    static double effectiveness(String attackingType, List<TypeView> defendingTypes) {
        String attack = normalize(attackingType);
        Map<String, Double> row = CHART.get(attack);
        if (row == null || defendingTypes == null || defendingTypes.isEmpty()) return 1.0D;
        double multiplier = 1.0D;
        for (TypeView defending : defendingTypes) {
            multiplier *= row.getOrDefault(normalize(defending.id()), 1.0D);
        }
        return multiplier;
    }

    static double effectiveness(MoveTemplate move, BattleUiState.ActiveTargetView target) {
        if (move == null || target == null) return 1.0D;
        return effectiveness(move.getName(), move.getElementalType().getName(), target);
    }

    static double effectiveness(String rawMoveId, String rawAttackingType,
                                BattleUiState.ActiveTargetView target) {
        if (target == null) return 1.0D;
        return BattleMatchup.analyze(new BattleMatchup.Move(rawMoveId, rawAttackingType, "physical", 0),
                BattleMatchup.Pokemon.EMPTY, BattleMatchupData.pokemon(target), BattleMatchupData.field(target)).multiplier();
    }

    static List<TypeMatchupView> weaknesses(List<TypeView> defendingTypes) {
        List<TypeMatchupView> result = new ArrayList<>();
        for (String type : TYPES) {
            double multiplier = effectiveness(type, defendingTypes);
            if (multiplier > 1.0D) result.add(new TypeMatchupView(type, multiplier));
        }
        result.sort((left, right) -> {
            int multiplier = Double.compare(right.multiplier(), left.multiplier());
            return multiplier != 0 ? multiplier : Integer.compare(TYPES.indexOf(left.type()), TYPES.indexOf(right.type()));
        });
        return List.copyOf(result);
    }

    private static Map<String, Map<String, Double>> buildChart() {
        Map<String, Map<String, Double>> chart = new LinkedHashMap<>();
        for (String type : TYPES) chart.put(type, new HashMap<>());

        weak(chart, "normal", "rock", "steel"); immune(chart, "normal", "ghost");
        strong(chart, "fire", "grass", "ice", "bug", "steel"); weak(chart, "fire", "fire", "water", "rock", "dragon");
        strong(chart, "water", "fire", "ground", "rock"); weak(chart, "water", "water", "grass", "dragon");
        strong(chart, "electric", "water", "flying"); weak(chart, "electric", "electric", "grass", "dragon"); immune(chart, "electric", "ground");
        strong(chart, "grass", "water", "ground", "rock"); weak(chart, "grass", "fire", "grass", "poison", "flying", "bug", "dragon", "steel");
        strong(chart, "ice", "grass", "ground", "flying", "dragon"); weak(chart, "ice", "fire", "water", "ice", "steel");
        strong(chart, "fighting", "normal", "ice", "rock", "dark", "steel"); weak(chart, "fighting", "poison", "flying", "psychic", "bug", "fairy"); immune(chart, "fighting", "ghost");
        strong(chart, "poison", "grass", "fairy"); weak(chart, "poison", "poison", "ground", "rock", "ghost"); immune(chart, "poison", "steel");
        strong(chart, "ground", "fire", "electric", "poison", "rock", "steel"); weak(chart, "ground", "grass", "bug"); immune(chart, "ground", "flying");
        strong(chart, "flying", "grass", "fighting", "bug"); weak(chart, "flying", "electric", "rock", "steel");
        strong(chart, "psychic", "fighting", "poison"); weak(chart, "psychic", "psychic", "steel"); immune(chart, "psychic", "dark");
        strong(chart, "bug", "grass", "psychic", "dark"); weak(chart, "bug", "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy");
        strong(chart, "rock", "fire", "ice", "flying", "bug"); weak(chart, "rock", "fighting", "ground", "steel");
        strong(chart, "ghost", "psychic", "ghost"); weak(chart, "ghost", "dark"); immune(chart, "ghost", "normal");
        strong(chart, "dragon", "dragon"); weak(chart, "dragon", "steel"); immune(chart, "dragon", "fairy");
        strong(chart, "dark", "psychic", "ghost"); weak(chart, "dark", "fighting", "dark", "fairy");
        strong(chart, "steel", "ice", "rock", "fairy"); weak(chart, "steel", "fire", "water", "electric", "steel");
        strong(chart, "fairy", "fighting", "dragon", "dark"); weak(chart, "fairy", "fire", "poison", "steel");
        return Map.copyOf(chart);
    }

    private static void strong(Map<String, Map<String, Double>> chart, String attack, String... defenders) {
        put(chart, attack, 2.0D, defenders);
    }

    private static void weak(Map<String, Map<String, Double>> chart, String attack, String... defenders) {
        put(chart, attack, 0.5D, defenders);
    }

    private static void immune(Map<String, Map<String, Double>> chart, String attack, String... defenders) {
        put(chart, attack, 0.0D, defenders);
    }

    private static void put(Map<String, Map<String, Double>> chart, String attack, double value, String... defenders) {
        Map<String, Double> row = chart.get(attack);
        for (String defender : defenders) row.put(defender, value);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    record TypeMatchupView(String type, double multiplier) {
    }
}
