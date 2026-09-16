package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class BattleLogTextFormatter {
    static final int PLAYER_COLOR = 0xFF17616B;
    static final int OPPONENT_COLOR = 0xFF973838;
    static final int BODY_COLOR = 0xFF29353B;
    static final int MUTED_COLOR = 0xFF58666D;
    private static final Pattern PERCENTAGE = Pattern.compile("(?<![\\p{L}\\p{N}])[+−-]?[<≤]?\\d+(?:[.,]\\d+)?[ \\u00a0\\u202f]*%");
    private static final Map<String, ItemStack> ITEM_INDEX = new HashMap<>();
    private static Language itemIndexLanguage;
    private static int itemIndexRegistrySize = -1;

    private BattleLogTextFormatter() {
    }

    static Text style(Text original, String translationKey, Object[] args, BattleLogEntryType type) {
        String rendered = original.getString();
        int baseColor = baseColor(type);
        List<Token> tokens = new ArrayList<>();
        String key = translationKey == null ? "" : translationKey;
        if (!(original.getContent() instanceof TranslatableTextContent content) || content.getArgs() != args)
            collectTokens(key, args, tokens, 0);
        collectText(original, key, -1, tokens, 0);
        var cause = BattleLogCausality.resolve(key);
        if (cause != null) tokens.add(new Token(cause.label().getString(), cause.label().getString(),
                BODY_COLOR, true, false, cause.hover()));
        for (BattleUiState.LogIdentity identity : BattleUiState.logIdentities(rendered)) {
            if (!BattleUiState.nameMatches(rendered, identity.alias())) continue;
            tokens.add(new Token(identity.alias(), identity.displayName(),
                    identity.opponent() ? OPPONENT_COLOR : PLAYER_COLOR, true, false, null));
        }

        List<PositionedToken> positioned = new ArrayList<>();
        for (Token token : new java.util.LinkedHashSet<>(tokens)) {
            if (token.label().isBlank()) continue;
            var matcher = BattleUiState.namePattern(token.label()).matcher(rendered);
            while (matcher.find()) {
                int start = matcher.start();
                String replacement = token.label().equals(token.replacement()) ? matcher.group() : token.replacement();
                positioned.add(new PositionedToken(start, matcher.end(), new Token(matcher.group(), replacement,
                        token.color(), token.bold(), token.underline(), token.hover())));
            }
        }
        var percentages = PERCENTAGE.matcher(rendered);
        while (percentages.find()) positioned.add(new PositionedToken(percentages.start(), percentages.end(),
                new Token(percentages.group(), percentages.group(), BODY_COLOR, true, false, null)));
        positioned.sort(Comparator.comparingInt(PositionedToken::start)
                .thenComparing((left, right) -> Integer.compare(right.end(), left.end())));

        MutableText result = Text.empty();
        int cursor = 0;
        for (PositionedToken token : positioned) {
            if (token.start() < cursor) continue;
            if (token.start() > cursor) {
                result.append(colored(rendered.substring(cursor, token.start()), baseColor));
            }
            result.append(styledToken(token.token()));
            cursor = token.end();
        }
        if (cursor < rendered.length()) result.append(colored(rendered.substring(cursor), baseColor));
        return BattleLogCausality.prefix(cause, rendered, result);
    }

    private static void collectTokens(String parentKey, Object[] args, List<Token> tokens, int depth) {
        if (args == null || depth > 16) return;
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (arg instanceof Text text) collectText(text, parentKey, index, tokens, depth + 1);
            else if (arg instanceof String label) collectText(Text.literal(label), parentKey, index, tokens, depth + 1);
        }
    }

    private static void collectText(Text text, String parentKey, int index, List<Token> tokens, int depth) {
        if (depth > 16) return;
        String key = text.getContent() instanceof TranslatableTextContent content ? content.getKey() : "";
        String label = text.getString();
        ItemStack item = itemStack(key);
        if (item.isEmpty() && itemArgument(parentKey, index)) item = findItem(label);
        HoverEvent hover = text.getStyle().getHoverEvent();
        if (!item.isEmpty()) hover = Registries.ITEM.getId(item.getItem()).getNamespace().equals("cobblemon")
                ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, itemTooltip(item))
                : new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(item));
        else {
            AbilityTemplate ability = ability(key, label, parentKey, index);
            MoveTemplate move = move(key, label, parentKey, index);
            if (ability != null) hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, abilityTooltip(ability));
            else if (move != null) hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, moveTooltip(move));
            else if (key.startsWith("cobblemon.move.") || key.startsWith("cobblemon.ability.")) {
                // Custom server content may have localized descriptions before its registry is synced.
                if (Language.getInstance().hasTranslation(key + ".desc"))
                    hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            descriptionTooltip(text, Text.translatable(key + ".desc")));
            }
        }
        if (hover != null && !label.isBlank()) tokens.add(new Token(label, label, BODY_COLOR, true, false, hover));
        if (text.getContent() instanceof TranslatableTextContent content)
            collectTokens(content.getKey(), content.getArgs(), tokens, depth + 1);
        for (Text sibling : text.getSiblings()) collectText(sibling, parentKey, index, tokens, depth + 1);
    }

    private static boolean itemArgument(String key, int index) {
        return index == 1 && (key.startsWith("cobblemon.battle.item.") || key.startsWith("cobblemon.battle.enditem.") ||
                Set.of("cobblemon.battle.damage.item", "cobblemon.battle.heal.item", "cobblemon.battle.heal.leftovers").contains(key)) ||
                index == 2 && key.equals("cobblemon.battle.activate.poltergeist");
    }

    private static MutableText colored(String value, int color) {
        return Text.literal(value).styled(style -> style.withColor(color));
    }

    private static MutableText styledToken(Token token) {
        return Text.literal(token.replacement()).styled(style -> {
            style = style.withColor(token.color()).withBold(token.bold()).withUnderline(token.underline());
            return token.hover() == null ? style : style.withHoverEvent(token.hover());
        });
    }

    private static ItemStack itemStack(String translationKey) {
        if (translationKey == null || !translationKey.startsWith("item.")) return ItemStack.EMPTY;
        String[] parts = translationKey.split("\\.", 3);
        if (parts.length != 3) return ItemStack.EMPTY;
        Identifier identifier = Identifier.tryParse(parts[1] + ":" + parts[2]);
        if (identifier == null) return ItemStack.EMPTY;
        return Registries.ITEM.getOrEmpty(identifier)
                .map(item -> item.getDefaultStack())
                .orElse(ItemStack.EMPTY);
    }

    private static MoveTemplate move(String nestedKey, String label, String parentKey, int index) {
        boolean moveArgument = (nestedKey != null && (nestedKey.startsWith("cobblemon.move.") || nestedKey.startsWith("move."))) ||
                (index == 1 && (parentKey.endsWith("used_move") || parentKey.endsWith("used_move_on") ||
                        parentKey.startsWith("cobblemon.battle.cant.") ||
                        Set.of("cobblemon.battle.activate.spite", "cobblemon.battle.start.disable",
                                "cobblemon.battle.ability.magicbounce").contains(parentKey)));
        if (!moveArgument) return null;

        if (nestedKey != null && !nestedKey.isBlank()) {
            String candidate = nestedKey.substring(nestedKey.lastIndexOf('.') + 1);
            MoveTemplate byKey = Moves.getByName(candidate);
            if (byKey == null) byKey = Moves.getByName(candidate.replace("_", ""));
            if (byKey != null) return byKey;
        }

        for (MoveTemplate candidate : Moves.all()) {
            if (candidate.getDisplayName().getString().equalsIgnoreCase(label) || candidate.getName().equalsIgnoreCase(label)) return candidate;
        }
        return null;
    }

    private static AbilityTemplate ability(String nestedKey, String label, String parentKey, int index) {
        boolean abilityArgument = nestedKey != null && nestedKey.startsWith("cobblemon.ability.") ||
                index == 1 && Set.of("cobblemon.battle.ability.generic", "cobblemon.battle.ability.replace",
                        "cobblemon.battle.ability.receiver").contains(parentKey) ||
                index == 2 && parentKey.equals("cobblemon.battle.ability.trace");
        if (!abilityArgument) return null;

        if (nestedKey != null && !nestedKey.isBlank()) {
            String candidate = nestedKey.substring(nestedKey.lastIndexOf('.') + 1);
            AbilityTemplate byKey = Abilities.get(candidate);
            if (byKey != null) return byKey;
        }

        for (AbilityTemplate candidate : Abilities.all()) {
            if (localized(candidate.getDisplayName()).getString().equalsIgnoreCase(label)) return candidate;
        }
        return null;
    }

    static Text abilityTooltip(AbilityTemplate ability) {
        return descriptionTooltip(localized(ability.getDisplayName()), localized(ability.getDescription()));
    }

    static Text localized(String value) {
        if (value == null || value.isBlank()) return Text.empty();
        return value.indexOf('.') >= 0 ? Text.translatable(value) : Text.literal(value);
    }

    static Text moveTooltip(MoveTemplate move) {
        String power = move.getPower() <= 0 ? "—" : MoveTooltipRenderer.integerOrDecimal(move.getPower());
        String accuracy = move.getAccuracy() < 0 ? "—" : MoveTooltipRenderer.integerOrDecimal(move.getAccuracy()) + "%";
        return Text.empty()
                .append(move.getDisplayName().copy().formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal("\n" + move.getElementalType().getDisplayName().getString() + " • " +
                        move.getDamageCategory().getDisplayName().getString()).formatted(Formatting.GRAY))
                .append(Text.literal("\n"))
                .append(Text.translatable("text.tropimon_ui_battle.history.base_values").formatted(Formatting.GRAY))
                .append(Text.literal("\n"))
                .append(Text.translatable("text.tropimon_ui_battle.power", power).formatted(Formatting.WHITE))
                .append(Text.literal("  "))
                .append(Text.translatable("text.tropimon_ui_battle.accuracy", accuracy).formatted(Formatting.WHITE))
                .append(Text.literal("\n"))
                .append(Text.translatable("text.tropimon_ui_battle.priority", move.getPriority()).formatted(Formatting.GRAY))
                .append(Text.literal("\n"))
                .append(Text.translatable(BattleMoveDynamics.spread(move.getTarget())
                        ? "text.tropimon_ui_battle.move_target_spread" : "text.tropimon_ui_battle.move_target",
                        BattleMoveDynamics.targetLabel(move.getTarget())).formatted(Formatting.GRAY))
                .append(Text.literal("\n"))
                .append(move.getDescription().copy().formatted(Formatting.WHITE));
    }

    static synchronized ItemStack findItem(String value) {
        if (value == null || value.isBlank()) return ItemStack.EMPTY;
        Language language = Language.getInstance();
        int registrySize = Registries.ITEM.getIds().size();
        if (itemIndexLanguage != language || itemIndexRegistrySize != registrySize) {
            ITEM_INDEX.clear();
            for (var item : Registries.ITEM) {
                Identifier id = Registries.ITEM.getId(item);
                if (!id.getNamespace().equals("cobblemon")) continue;
                ItemStack stack = item.getDefaultStack();
                ITEM_INDEX.putIfAbsent(normalizeItem(id.getPath()), stack);
                ITEM_INDEX.putIfAbsent(normalizeItem(item.getName().getString()), stack);
            }
            itemIndexLanguage = language;
            itemIndexRegistrySize = registrySize;
        }
        ItemStack item = ITEM_INDEX.get(normalizeItem(value));
        return item == null ? ItemStack.EMPTY : item.copy();
    }

    private static String normalizeItem(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static Text itemTooltip(ItemStack item) {
        MutableText result = item.getName().copy().formatted(Formatting.WHITE, Formatting.BOLD);
        // Call the official description provider directly: all lines, no Shift or tooltip-mod dependency.
        for (Text line : com.cobblemon.mod.common.client.tooltips.CobblemonTooltipGenerator.INSTANCE
                .generateTooltip(item, List.of())) {
            result.append(Text.literal("\n")).append(line.copy().formatted(Formatting.WHITE).styled(style -> style.withBold(false)));
        }
        return result;
    }

    private static Text descriptionTooltip(Text title, Text description) {
        return Text.empty().append(title.copy().formatted(Formatting.WHITE, Formatting.BOLD)).append("\n")
                .append(description.copy().formatted(Formatting.WHITE));
    }

    static int baseColor(BattleLogEntryType type) {
        return switch (type) {
            case TIMER, SYSTEM -> MUTED_COLOR;
            default -> BODY_COLOR;
        };
    }

    static int typeColor(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "fire" -> 0xFFD94A24;
            case "water" -> 0xFF2876C7;
            case "grass" -> 0xFF2D8C38;
            case "electric" -> 0xFFB88A00;
            case "ice" -> 0xFF258B93;
            case "fighting" -> 0xFFA9332A;
            case "poison" -> 0xFF8F3FA4;
            case "ground" -> 0xFF96712D;
            case "flying" -> 0xFF586FB3;
            case "psychic" -> 0xFFD13D70;
            case "bug" -> 0xFF718719;
            case "rock" -> 0xFF87732A;
            case "ghost" -> 0xFF62518D;
            case "dragon" -> 0xFF5840B8;
            case "dark" -> 0xFF5D4A43;
            case "steel" -> 0xFF5F727C;
            case "fairy" -> 0xFFC45D9B;
            default -> 0xFF39484F;
        };
    }

    private record Token(String label, String replacement, int color, boolean bold, boolean underline,
                         HoverEvent hover) {
    }

    private record PositionedToken(int start, int end, Token token) {
    }
}
