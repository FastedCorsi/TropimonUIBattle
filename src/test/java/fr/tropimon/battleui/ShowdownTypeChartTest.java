package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class ShowdownTypeChartTest {
    // damageTaken: 0 neutral, 1 weak, 2 resistant, 3 immune. Defender rows; attack columns below.
    // Snapshot: https://github.com/smogon/pokemon-showdown/blob/master/data/typechart.ts (2026-08-30).
    private static final String[] TYPES = "normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost dragon dark steel fairy".split(" ");
    private static final String DATA = """
            bug 010020202100100000
            dark 000000100031020201
            dragon 022221000000001001
            electric 000200001200000020
            fairy 000000210002003210
            fighting 000000000112200201
            fire 021022001002100022
            flying 000121203002100000
            ghost 300000320002010100
            grass 012221012101000000
            ground 001311020000200000
            ice 010002100000100010
            normal 000000100000030000
            poison 000020221012000002
            psychic 000000200021010100
            rock 221010121200000010
            steel 210022131222202022
            water 022112000000000020
            """;

    @Test void all324SingleAnd2754DualTypeCombinationsMatchTheOfficialChart() {
        Map<String, String> rows = new LinkedHashMap<>();
        DATA.lines().forEach(line -> { String[] parts = line.split(" "); rows.put(parts[0], parts[1]); });
        for (int attack = 0; attack < TYPES.length; attack++) {
            for (int first = 0; first < TYPES.length; first++) {
                double expected = value(rows.get(TYPES[first]).charAt(attack));
                assertEquals(expected, PokemonTypeMatchups.factor(TYPES[attack], TYPES[first]));
                for (int second = first + 1; second < TYPES.length; second++) {
                    double dual = expected * value(rows.get(TYPES[second]).charAt(attack));
                    var result = BattleMatchup.analyze(BattleMatchupTest.move("testmove", TYPES[attack]),
                            BattleMatchupTest.pokemon("normal"), BattleMatchupTest.pokemon(TYPES[first], TYPES[second]),
                            BattleMatchup.Field.EMPTY);
                    assertEquals(dual, result.multiplier(), TYPES[attack] + " against " + TYPES[first] + "/" + TYPES[second]);
                }
            }
        }
    }

    private static double value(char code) {
        return switch (code) { case '1' -> 2; case '2' -> 0.5; case '3' -> 0; default -> 1; };
    }
}
