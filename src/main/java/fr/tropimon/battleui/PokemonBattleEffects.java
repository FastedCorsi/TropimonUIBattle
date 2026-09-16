package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks public, temporary effects announced by Cobblemon battle messages. */
final class PokemonBattleEffects {
    private static final Map<UUID, LinkedHashMap<String, ActiveEffect>> ACTIVE = new LinkedHashMap<>();
    private static final Set<String> SINGLE_MOVE_EFFECTS = Set.of("destinybond", "glaiverush", "grudge", "rage");
    private static ObservationMode observationMode = ObservationMode.PARTICIPANT;

    private PokemonBattleEffects() {
    }

    static synchronized void reset() {
        ACTIVE.clear();
        observationMode = ObservationMode.PARTICIPANT;
    }

    static synchronized void beginMessageBatch(boolean spectating) {
        ObservationMode next = spectating ? ObservationMode.SPECTATOR : ObservationMode.PARTICIPANT;
        if (next != observationMode) {
            ACTIVE.clear();
            observationMode = next;
        }
    }

    static synchronized void reassign(UUID apparent, UUID actual) {
        if (apparent.equals(actual)) return;
        var effects = ACTIVE.remove(apparent);
        if (effects != null) ACTIVE.put(actual, effects);
    }

    static synchronized void accept(UUID pokemon, String key, Object[] args, int turn) {
        if (pokemon == null || key == null || key.isBlank()) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("cobblemon.battle.start.")) {
            String id = suffix(normalized, "cobblemon.battle.start.");
            if (Set.of("typeadd", "typechange", "reflecttype", "futuresight", "doomdesire").contains(id)) return;
            int counter = counter(id, args);
            DurationRange duration = duration(id);
            int expiresAfter = id.equals("yawn") ? Math.max(1, turn) + 1 : 0;
            put(pokemon, id, counter, expiresAfter, Math.max(1, turn), duration, detail(pokemon, id, args));
            return;
        }
        if (normalized.startsWith("cobblemon.battle.end.")) {
            remove(pokemon, suffix(normalized, "cobblemon.battle.end."));
            return;
        }
        if (normalized.startsWith("cobblemon.battle.singleturn.")) {
            put(pokemon, suffix(normalized, "cobblemon.battle.singleturn."), 0, Math.max(1, turn),
                    Math.max(1, turn), new DurationRange(1, 1), "");
            return;
        }
        if (normalized.startsWith("cobblemon.battle.singlemove.")) {
            put(pokemon, suffix(normalized, "cobblemon.battle.singlemove."), 0, 0,
                    Math.max(1, turn), new DurationRange(1, 1), "");
            return;
        }
        if (normalized.equals("cobblemon.battle.mustrecharge")) {
            put(pokemon, "recharge", 0, Math.max(1, turn) + 1,
                    Math.max(1, turn), new DurationRange(1, 1), "");
            return;
        }
        if (normalized.startsWith("cobblemon.battle.activate.") && isTrappingActivation(normalized)) {
            String id = suffix(normalized, "cobblemon.battle.activate.");
            DurationRange duration = id.equals("trapped") ? DurationRange.DYNAMIC : new DurationRange(4, 7);
            put(pokemon, id, 0, 0, Math.max(1, turn), duration, "");
            return;
        }
        if (normalized.startsWith("cobblemon.status.")) {
            acceptPersistentStatus(pokemon, normalized, turn);
            return;
        }
        if (normalized.startsWith("cobblemon.battle.prepare.")) {
            String id = suffix(normalized, "cobblemon.battle.prepare.");
            put(pokemon, id, 0, Math.max(1, turn) + 1,
                    Math.max(1, turn), new DurationRange(1, 1), "");
            return;
        }
        if (normalized.startsWith("cobblemon.battle.cant.")) {
            String id = suffix(normalized, "cobblemon.battle.cant.");
            if (Set.of("disable", "taunt", "healblock", "imprison", "throatchop", "recharge").contains(id)) {
                DurationRange duration = duration(id);
                put(pokemon, id, 0, 0, Math.max(1, turn), duration,
                        detail(pokemon, id, args));
            }
            return;
        }
        if (normalized.equals("cobblemon.battle.heal.wish")) {
            remove(pokemon, "wish");
        } else if (normalized.equals("cobblemon.battle.end.futuresight")) {
            remove(pokemon, "futuresight");
        } else if (normalized.equals("cobblemon.battle.end.doomdesire")) {
            remove(pokemon, "doomdesire");
        }
    }

    static synchronized void synchronizeStatus(UUID pokemon, String status, int turn) {
        if (pokemon == null) return;
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        if (!normalized.equals("slp") && effects != null) effects.remove("sleep");
        if (!normalized.equals("tox") && effects != null) effects.remove("toxic");
        if (normalized.equals("slp") && (effects == null || !effects.containsKey("sleep"))) {
            putTickingStatus(pokemon, "sleep", Math.max(1, turn), new DurationRange(1, 3));
        }
        if (normalized.equals("tox") && (effects == null || !effects.containsKey("toxic"))) {
            putTickingStatus(pokemon, "toxic", Math.max(1, turn), DurationRange.DYNAMIC);
        }
    }

    /** A Lum Berry cures every persistent status and confusion on its consumer. */
    static synchronized void clearCuredStatus(UUID pokemon) {
        if (pokemon == null) return;
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        if (effects == null) return;
        effects.keySet().removeAll(Set.of("sleep", "toxic", "confusion"));
        if (effects.isEmpty()) ACTIVE.remove(pokemon);
    }

    static synchronized void acceptMove(UUID pokemon, String moveId, int turn) {
        if (pokemon == null || moveId == null || moveId.isBlank()) return;
        LinkedHashMap<String, ActiveEffect> active = ACTIVE.get(pokemon);
        if (active != null) {
            active.keySet().removeAll(SINGLE_MOVE_EFFECTS);
            if (active.isEmpty()) ACTIVE.remove(pokemon);
        }
        String id = normalize(moveId);
        if (PROTECT_MOVES.contains(id)) {
            LinkedHashMap<String, ActiveEffect> effects = ACTIVE.computeIfAbsent(pokemon,
                    ignored -> new LinkedHashMap<>());
            ActiveEffect previous = effects.get("protectchain");
            int chain = previous == null || previous.lastUpdatedTurn() < Math.max(1, turn) - 1
                    ? 1 : previous.counter() + 1;
            effects.put("protectchain", new ActiveEffect("protectchain", chain,
                    Math.max(1, turn) + 1, Math.max(1, turn), Math.max(1, turn),
                    new DurationRange(1, 1), ""));
        } else {
            remove(pokemon, "protectchain");
        }
    }

    static synchronized String detail(UUID pokemon, String id, int currentTurn) {
        if (pokemon == null || id == null) return "";
        return snapshot(pokemon, currentTurn).stream()
                .filter(effect -> effect.id().equals(normalize(id)))
                .map(PokemonEffectView::detail).findFirst().orElse("");
    }

    static synchronized void retainActive(Set<UUID> activePokemon) {
        if (activePokemon == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.keySet().removeIf(uuid -> !activePokemon.contains(uuid));
    }

    static synchronized List<PokemonEffectView> snapshot(UUID pokemon, int currentTurn) {
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        if (effects == null || effects.isEmpty()) return List.of();
        effects.entrySet().removeIf(entry -> entry.getValue().expiresAfterTurn() > 0 &&
                currentTurn > entry.getValue().expiresAfterTurn());
        if (effects.isEmpty()) {
            ACTIVE.remove(pokemon);
            return List.of();
        }
        List<PokemonEffectView> result = new ArrayList<>(effects.size());
        for (ActiveEffect effect : effects.values()) {
            int elapsed = Math.max(1, currentTurn - effect.startedTurn() + 1);
            int minimumRemaining = remaining(effect.duration().minimum(), elapsed);
            int maximumRemaining = remaining(effect.duration().maximum(), elapsed);
            String counterText = counterText(effect, elapsed, minimumRemaining, maximumRemaining);
            result.add(new PokemonEffectView(effect.id(), effect.counter(), counterText,
                    effect.detail(), effect.startedTurn(), minimumRemaining, maximumRemaining));
        }
        return List.copyOf(result);
    }

    static synchronized boolean active(UUID pokemon, String id, int currentTurn) {
        if (pokemon == null || id == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return snapshot(pokemon, currentTurn).stream().anyMatch(effect -> effect.id().equals(normalized));
    }

    private static void put(UUID pokemon, String id, int counter, int expiresAfterTurn,
                            int startedTurn, DurationRange duration, String detail) {
        if (id == null || id.isBlank()) return;
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.computeIfAbsent(pokemon,
                ignored -> new LinkedHashMap<>());
        ActiveEffect previous = effects.get(id);
        int actualStart = previous == null ? Math.max(1, startedTurn) : previous.startedTurn();
        effects.put(id, new ActiveEffect(id, Math.max(0, counter), Math.max(0, expiresAfterTurn),
                actualStart, Math.max(1, startedTurn), duration == null ? DurationRange.DYNAMIC : duration,
                detail == null || detail.isBlank() ? previous == null ? "" : previous.detail() : detail));
    }

    private static void putTickingStatus(UUID pokemon, String id, int startedTurn, DurationRange duration) {
        int currentTurn = Math.max(1, startedTurn);
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.computeIfAbsent(pokemon,
                ignored -> new LinkedHashMap<>());
        effects.put(id, new ActiveEffect(id, 0, 0, currentTurn,
                currentTurn - 1, duration == null ? DurationRange.DYNAMIC : duration, ""));
    }

    private static void remove(UUID pokemon, String id) {
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        if (effects == null) return;
        effects.remove(id);
        if (effects.isEmpty()) ACTIVE.remove(pokemon);
    }

    private static int counter(String id, Object[] args) {
        if (!(id.equals("perish") || id.equals("stockpile"))) return 0;
        Object value = args != null && args.length > 1 ? args[1] : null;
        if (value instanceof Number number) return Math.max(0, number.intValue());
        if (value instanceof Text text) value = text.getString();
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String detail(UUID pokemon, String id, Object[] args) {
        if (!(id.equals("disable") || id.equals("mimic") || id.equals("lockon") || id.equals("encore") ||
                id.equals("taunt") || id.equals("healblock") || id.equals("imprison") || id.equals("throatchop"))) return "";
        Object value = args != null && args.length > 1 ? args[1] : null;
        String announced = value instanceof Text text ? text.getString() : value == null ? "" : String.valueOf(value);
        if (announced.isBlank() && id.equals("encore")) {
            return BattleUiState.lastSelectedMove(pokemon);
        }
        return announced;
    }

    private static void acceptPersistentStatus(UUID pokemon, String key, int turn) {
        if (key.equals("cobblemon.status.sleep.apply")) {
            putTickingStatus(pokemon, "sleep", Math.max(1, turn), new DurationRange(1, 3));
        } else if (key.equals("cobblemon.status.sleep.is")) {
            increment(pokemon, "sleep", turn);
        } else if (key.equals("cobblemon.status.sleep.cure")) {
            remove(pokemon, "sleep");
        } else if (key.equals("cobblemon.status.poisonbadly.apply")) {
            putTickingStatus(pokemon, "toxic", Math.max(1, turn), DurationRange.DYNAMIC);
        } else if (key.equals("cobblemon.status.poison.hurt") && has(pokemon, "toxic")) {
            increment(pokemon, "toxic", turn);
        } else if (key.endsWith(".cure")) {
            remove(pokemon, "toxic");
        }
    }

    private static void increment(UUID pokemon, String id, int turn) {
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        if (effects == null || !effects.containsKey(id)) return;
        ActiveEffect previous = effects.get(id);
        if (previous.lastUpdatedTurn() == Math.max(1, turn)) return;
        effects.put(id, new ActiveEffect(id, previous.counter() + 1, previous.expiresAfterTurn(),
                previous.startedTurn(), Math.max(1, turn), previous.duration(), previous.detail()));
    }

    private static boolean has(UUID pokemon, String id) {
        LinkedHashMap<String, ActiveEffect> effects = ACTIVE.get(pokemon);
        return effects != null && effects.containsKey(id);
    }

    private static DurationRange duration(String id) {
        return switch (id) {
            case "confusion" -> new DurationRange(2, 5);
            case "encore", "taunt", "telekinesis" -> new DurationRange(3, 3);
            case "disable" -> new DurationRange(4, 4);
            case "healblock", "embargo", "magnetrise" -> new DurationRange(5, 5);
            case "throatchop" -> new DurationRange(2, 2);
            case "dynamax", "gmax" -> new DurationRange(3, 3);
            case "futuresight", "doomdesire" -> new DurationRange(3, 3);
            case "uproar" -> new DurationRange(3, 3);
            default -> DurationRange.DYNAMIC;
        };
    }

    private static int remaining(int duration, int elapsed) {
        return duration <= 0 ? 0 : Math.max(0, duration - elapsed + 1);
    }

    private static String counterText(ActiveEffect effect, int elapsed,
                                      int minimumRemaining, int maximumRemaining) {
        if (effect.id().equals("sleep")) return Math.max(1, effect.counter() + 1) + "/3";
        if (effect.id().equals("toxic")) return Integer.toString(Math.max(1, effect.counter() + 1));
        if (effect.id().equals("protectchain")) return Integer.toString(Math.max(1, effect.counter()));
        if (effect.counter() > 0) return Integer.toString(effect.counter());
        if (maximumRemaining <= 0) return "";
        if (minimumRemaining != maximumRemaining) return minimumRemaining + "–" + maximumRemaining;
        return Integer.toString(maximumRemaining);
    }

    private static boolean isTrappingActivation(String key) {
        return Set.of("bind", "clamp", "firespin", "infestation", "magmastorm", "sandtomb",
                "snaptrap", "thundercage", "trapped", "whirlpool", "wrap")
                .contains(suffix(key, "cobblemon.battle.activate."));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String suffix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : "";
    }

    record PokemonEffectView(String id, int counter, String counterText, String detail,
                             int startedTurn, int minimumRemaining, int maximumRemaining) {
        PokemonEffectView {
            counterText = counterText == null ? "" : counterText;
            detail = detail == null ? "" : detail;
        }

        Text label() {
            var move = Moves.getByName(id);
            if (move != null) return move.getDisplayName();
            var ability = Abilities.get(id);
            if (ability != null) {
                String name = ability.getDisplayName();
                return name != null && name.contains(".") ? Text.translatable(name) : Text.literal(name);
            }
            return switch (id) {
                case "confusion" -> Text.translatable("text.tropimon_ui_battle.volatile.confusion");
                case "perish" -> Text.translatable("text.tropimon_ui_battle.volatile.perish");
                case "recharge" -> Text.translatable("text.tropimon_ui_battle.volatile.recharge");
                case "trapped" -> Text.translatable("text.tropimon_ui_battle.volatile.trapped");
                case "sleep" -> Text.translatable("text.tropimon_ui_battle.volatile.sleep");
                case "toxic" -> Text.translatable("text.tropimon_ui_battle.volatile.toxic");
                case "protectchain" -> Text.translatable("text.tropimon_ui_battle.volatile.protect_chain");
                case "wish" -> Text.translatable("text.tropimon_ui_battle.volatile.wish");
                case "futuresight" -> Text.translatable("text.tropimon_ui_battle.volatile.future_sight");
                case "doomdesire" -> Text.translatable("text.tropimon_ui_battle.volatile.doom_desire");
                case "dynamax" -> Text.literal("Dynamax");
                case "gmax" -> Text.literal("Gigamax");
                default -> Text.literal(humanize(id));
            };
        }

        private static String humanize(String value) {
            if (value == null || value.isBlank()) return "?";
            String spaced = value.replace('_', ' ').replace('-', ' ');
            return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        }
    }

    private static final Set<String> PROTECT_MOVES = Set.of("protect", "detect", "endure", "kingsshield",
            "spikyshield", "banefulbunker", "silktrap", "burningbulwark", "maxguard", "obstruct");

    private record ActiveEffect(String id, int counter, int expiresAfterTurn, int startedTurn,
                                int lastUpdatedTurn, DurationRange duration, String detail) {
    }

    private record DurationRange(int minimum, int maximum) {
        private static final DurationRange DYNAMIC = new DurationRange(0, 0);
    }

    private enum ObservationMode { PARTICIPANT, SPECTATOR }
}
