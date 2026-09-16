package fr.tropimon.battleui;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BattleLogClassifier {
    private static final Pattern TURN_NUMBER = Pattern.compile("(?i)(?:turn|tour)\\D{0,12}(\\d+)");

    private BattleLogClassifier() {
    }

    public static BattleLogEntryType classify(String translationKey, String rendered) {
        String key = translationKey == null ? "" : translationKey.toLowerCase(Locale.ROOT);
        String text = rendered == null ? "" : rendered.toLowerCase(Locale.ROOT);

        if (key.equals("cobblemon.battle.turn") || TURN_NUMBER.matcher(text).find()) return BattleLogEntryType.TURN;
        if (containsAny(key, ".fainted", ".faint") || containsAny(text, " fainted", " est ko", " est k.o")) {
            return BattleLogEntryType.FAINT;
        }
        if (containsAny(key, ".switch", ".withdraw", ".dragged_out") ||
                containsAny(text, " sent out ", " go!", " withdrew ", " come back", " envoie ", " retire ", " reviens")) {
            return BattleLogEntryType.SWITCH;
        }
        if (containsAny(key, ".enditem", ".item.", ".item", ".poltergeist", ".leftovers") ||
                containsAny(text, " life orb", " leftovers", " heavy-duty boots", " baie ", " berry", " objet ", " item ")) {
            return BattleLogEntryType.ITEM;
        }
        if (containsAny(key, ".unboost", ".clearboost", ".clearallnegativeboost") ||
                containsAny(text, " fell", " lowered", " baisse", " diminu")) {
            return BattleLogEntryType.STAT_DOWN;
        }
        if ((containsAny(key, ".boost") && !containsAny(key, ".unboost")) ||
                containsAny(text, " rose", " sharply", " augmente", " augmente beaucoup")) {
            return BattleLogEntryType.STAT_UP;
        }
        if (containsAny(text, " seconds left", " secondes restantes", "battle timer", "minuteur de combat")) {
            return BattleLogEntryType.TIMER;
        }
        if (containsAny(key, ".damage", ".supereffective", ".resisted", ".crit", ".immune") ||
                containsAny(text, " dégâts", " damage", "super efficace", "super effective", "coup critique", "critical hit")) {
            return BattleLogEntryType.DAMAGE;
        }
        if (containsAny(key, ".heal", ".cure", ".restore") ||
                containsAny(text, " récupère", " recovered", "restaure", " restored", "soigné", " healed")) {
            return BattleLogEntryType.HEAL;
        }
        if (containsAny(key, ".ability.", ".endability") ||
                containsAny(text, " ability", " talent", " aptitude")) {
            return BattleLogEntryType.ABILITY;
        }
        if (containsAny(key, ".weather", ".field", ".terrain", ".sidestart", ".sideend") ||
                containsAny(text, "terrain", "météo", "weather", "distorsion", "trick room")) {
            return BattleLogEntryType.FIELD;
        }
        if (containsAny(key, ".status", ".start", ".end") ||
                containsAny(text, "empoison", "poison", "brûl", "burn", "paral", "endormi", "asleep", "gelé", "frozen")) {
            return BattleLogEntryType.STATUS;
        }
        if (containsAny(key, ".used_move", ".move", ".cant", ".fail") ||
                containsAny(text, " utilise ", " used ", "échoue", " failed")) {
            return BattleLogEntryType.MOVE;
        }
        return BattleLogEntryType.SYSTEM;
    }

    public static int findTurn(String translationKey, String rendered, Object[] translationArgs) {
        if ("cobblemon.battle.turn".equals(translationKey) && translationArgs != null && translationArgs.length > 0) {
            Object first = translationArgs[0];
            if (first instanceof Number number) return Math.max(0, number.intValue());
            try {
                return Math.max(0, Integer.parseInt(String.valueOf(first)));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher matcher = TURN_NUMBER.matcher(rendered == null ? "" : rendered);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
