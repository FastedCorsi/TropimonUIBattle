package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.MoveTarget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.*;
import net.minecraft.util.Language;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BattleLogReadabilityTest {
    private static Language originalLanguage;
    private static List<MoveTemplate> originalMoves;
    private static List<AbilityTemplate> originalAbilities;

    @BeforeAll static void prepareRegistries() {
        DamageCacheParityTest.bootstrap();
        originalLanguage = Language.getInstance();
        // Fabric's unit-test runtime does not run Cobblemon's item bootstrap. Minimal registered
        // stacks exercise its actual tooltip provider and language assets without a game client.
        for (String name : List.of("focus_sash", "leftovers", "red_card")) {
            var id = net.minecraft.util.Identifier.of("cobblemon", name);
            if (!net.minecraft.registry.Registries.ITEM.containsId(id))
                net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM, id,
                        new net.minecraft.item.Item(new net.minecraft.item.Item.Settings()));
        }
        originalMoves = List.copyOf(Moves.all());
        originalAbilities = List.copyOf(Abilities.all());
        Moves.INSTANCE.receiveSyncPacket$common(List.of(move("surf"), move("confusion"), move("toxic"), move("stealthrock"),
                move("disable"), move("spite"), move("trickroom"), move("knockoff")));
        Abilities.INSTANCE.receiveSyncPacket$common(List.of(ability("roughskin"), ability("sturdy"), ability("trace"), ability("truant")));
    }

    @AfterAll static void restoreRegistries() {
        Language.setInstance(originalLanguage);
        Moves.INSTANCE.receiveSyncPacket$common(originalMoves);
        Abilities.INSTANCE.receiveSyncPacket$common(originalAbilities);
    }

    @BeforeEach void english() throws Exception { language("en_us"); }

    @Test void percentagesAreExactBoldAndNeutralWithoutBoldingTheWholeSentence() {
        for (var type : List.of(BattleLogEntryType.DAMAGE, BattleLogEntryType.HEAL, BattleLogEntryType.STATUS)) {
            String sentence = "Creature: −32.4% HP, +12,5 % HP, 0.04%, <0.01%, 100%.";
            Text styled = format(Text.literal(sentence), "", type);
            assertEquals(sentence, styled.getString());
            for (String value : List.of("−32.4%", "+12,5 %", "0.04%", "<0.01%", "100%")) {
                Style style = styleAt(styled, value);
                assertTrue(style.isBold(), value);
                assertEquals(BattleLogTextFormatter.BODY_COLOR & 0xFFFFFF, style.getColor().getRgb());
            }
            assertFalse(styleAt(styled, "Creature").isBold());
            assertEquals(BattleLogTextFormatter.BODY_COLOR, BattleLogTextFormatter.baseColor(type));
        }
    }

    @Test void namesKeepTheirSideColorsAndSpeciesReplacement() throws Exception {
        var register = BattleUiState.class.getDeclaredMethod("registerLogIdentity", String.class, String.class,
                boolean.class, boolean.class);
        register.setAccessible(true);
        var field = BattleUiState.class.getDeclaredField("LOG_IDENTITIES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var identities = (Map<String, BattleUiState.LogIdentity>) field.get(null);
        var saved = new LinkedHashMap<>(identities);
        try {
            identities.clear();
            register.invoke(null, "TrainerBlue", "TrainerBlue", false, false);
            register.invoke(null, "TrainerRed", "TrainerRed", true, false);
            register.invoke(null, "FixtureNickname", "Garchomp", false, true);
            Text styled = format(Text.literal("TrainerBlue's FixtureNickname attacked TrainerRed: 25%."), "", BattleLogEntryType.MOVE);
            assertEquals("TrainerBlue's Garchomp attacked TrainerRed: 25%.", styled.getString());
            assertEquals(BattleLogTextFormatter.PLAYER_COLOR & 0xFFFFFF, styleAt(styled, "Garchomp").getColor().getRgb());
            assertEquals(BattleLogTextFormatter.OPPONENT_COLOR & 0xFFFFFF, styleAt(styled, "TrainerRed").getColor().getRgb());
        } finally { identities.clear(); identities.putAll(saved); }
    }

    @Test void attackArgumentsAndNestedComponentsHaveDescriptionsWithoutTypeColors() {
        Text used = Text.translatable("cobblemon.battle.used_move", Text.literal("Surfside"),
                Text.empty().append(Text.translatable("cobblemon.move.surf")));
        Text styled = format(Text.empty().append(used), "", BattleLogEntryType.MOVE);
        assertEquals(used.getString(), styled.getString());
        assertNull(styleAt(styled, "Surfside").getHoverEvent(), "Do not recognize Surf inside a Pokémon name");
        Style attack = styleAt(styled, "Surf!");
        assertEquals(BattleLogTextFormatter.BODY_COLOR & 0xFFFFFF, attack.getColor().getRgb());
        assertTrue(attack.isBold());
        String tooltip = hover(attack).getString();
        assertTrue(tooltip.contains(Text.translatable("cobblemon.move.surf.desc").getString()));
        assertTrue(tooltip.contains("Base values"));
        assertTrue(tooltip.contains("spread move"));
        assertTooltipReadable(hover(attack));
    }

    @Test void explicitAndImplicitAbilityNamesHaveTheSameFullDescription() {
        Text activated = Text.translatable("cobblemon.battle.ability.generic", Text.literal("Creature"),
                Text.translatable("cobblemon.ability.roughskin"));
        Text styled = format(activated, "cobblemon.battle.ability.generic", BattleLogEntryType.ABILITY);
        assertTrue(hover(styleAt(styled, "Rough Skin")).getString().contains(Text.translatable("cobblemon.ability.roughskin.desc").getString()));
        Text sturdy = format(Text.translatable("cobblemon.battle.ability.sturdy", Text.literal("Creature")),
                "cobblemon.battle.ability.sturdy", BattleLogEntryType.ABILITY);
        assertTrue(sturdy.getString().startsWith("[Sturdy]"));
        assertTooltipReadable(hover(styleAt(sturdy, "Sturdy")));
        assertFalse(styleAt(sturdy, "endured").isBold(), "An implicit ability must not bold the entire event");
    }

    @Test void itemsShowAllCobblemonLinesAndHardcodedConsumptionDoesNotDuplicateTheName() {
        Text focus = format(Text.translatable("cobblemon.battle.enditem.focussash", Text.literal("Creature")),
                "cobblemon.battle.enditem.focussash", BattleLogEntryType.ITEM);
        assertFalse(focus.getString().startsWith("["));
        Text tooltip = hover(styleAt(focus, "Focus Sash"));
        for (int i = 1; i <= 2; i++) assertTrue(tooltip.getString().contains(Text.translatable("item.cobblemon.focus_sash.tooltip_" + i).getString()));
        assertTooltipReadable(tooltip);
        Text leftovers = Text.translatable("cobblemon.battle.heal.leftovers", Text.literal("Creature"), Text.literal("Leftovers"));
        Text styled = format(leftovers, "cobblemon.battle.heal.leftovers", BattleLogEntryType.HEAL);
        assertEquals(leftovers.getString(), styled.getString());
        assertTrue(hover(styleAt(styled, "Leftovers")).getString().contains(Text.translatable("item.cobblemon.leftovers.tooltip").getString()));
    }

    @Test void malformedRedCardPlaceholdersAreRepairedWithoutInventingMissingPokemon() {
        String key = "cobblemon.battle.enditem.redcard";
        Text valid = Text.translatable(key, Text.literal("Holder"),
                Text.translatable("item.cobblemon.red_card"), Text.literal("Attacker"));
        assertSame(valid, BattleUiState.repairMalformedBattleMessage(valid, key,
                ((TranslatableTextContent) valid.getContent()).getArgs()));

        Text broken = Text.literal("%1$s held up its Red Card against %2$s!");
        Text repaired = BattleUiState.repairMalformedBattleMessage(broken, key, new Object[]{
                Text.literal("Holder"), Text.translatable("item.cobblemon.red_card"), Text.literal("Attacker")});
        assertEquals("Holder held up its Red Card against Attacker!", repaired.getString());
        assertFalse(repaired.getString().contains("$s"));

        Text missingNames = BattleUiState.repairMalformedBattleMessage(broken, key, new Object[]{
                Text.literal("Holder"), Text.translatable("item.cobblemon.red_card")});
        assertEquals("Red Card activated!", missingNames.getString());
        assertFalse(missingNames.getString().contains("$s"));
    }

    @Test void residualEffectsAndBlockedOrPpReducedMovesRemainHoverable() {
        Text rocks = format(Text.translatable("cobblemon.battle.sidestart.opponent.stealthrock"),
                "cobblemon.battle.sidestart.opponent.stealthrock", BattleLogEntryType.FIELD);
        assertTrue(rocks.getString().startsWith("[Stealth Rock]"));
        assertTrue(hover(styleAt(rocks, "Stealth Rock")).getString().contains(Text.translatable("cobblemon.move.stealthrock.desc").getString()));
        for (String key : List.of("cobblemon.battle.cant.disable", "cobblemon.battle.activate.spite")) {
            Text event = Text.translatable(key, Text.literal("Creature"), Text.literal("Surf"), 4);
            Text styled = format(event, key, BattleLogEntryType.MOVE);
            assertNotNull(hover(styleAt(styled, "Surf")));
            assertNotNull(hover(styleAt(styled, key.endsWith("spite") ? "Spite" : "Disable")));
        }
    }

    @Test void confusionStateDoesNotPretendTheMoveConfusionCausedIt() {
        Text event = Text.translatable("cobblemon.battle.start.confusion", Text.literal("Creature"));
        Text styled = format(event, "cobblemon.battle.start.confusion", BattleLogEntryType.STATUS);
        assertFalse(styled.getString().startsWith("["));
        assertNull(BattleLogCausality.resolve("cobblemon.battle.start.confusion"));
    }

    @Test void itemLossAndPoltergeistExposeTheRevealedItemAndMultipleAbilitiesKeepSeparateHovers() {
        Text knocked = format(Text.translatable("cobblemon.battle.enditem.knockoff", Text.literal("Target"),
                Text.translatable("item.cobblemon.focus_sash"), Text.literal("Attacker")),
                "cobblemon.battle.enditem.knockoff", BattleLogEntryType.ITEM);
        assertNotNull(hover(styleAt(knocked, "Knock Off")));
        assertTrue(hover(styleAt(knocked, "Focus Sash")).getString().contains("Consumed after use"));
        Text poltergeist = format(Text.translatable("cobblemon.battle.activate.poltergeist", Text.literal("Target"),
                Text.literal("Attacker"), Text.literal("Leftovers")),
                "cobblemon.battle.activate.poltergeist", BattleLogEntryType.MOVE);
        assertTrue(hover(styleAt(poltergeist, "Leftovers")).getString().contains("restores"));
        Text trace = format(Text.translatable("cobblemon.battle.ability.trace", Text.literal("Creature"),
                Text.literal("Target"), Text.translatable("cobblemon.ability.roughskin")),
                "cobblemon.battle.ability.trace", BattleLogEntryType.ABILITY);
        assertTrue(hover(styleAt(trace, "Trace")).getString().contains(Text.translatable("cobblemon.ability.trace.desc").getString()));
        assertTrue(hover(styleAt(trace, "Rough Skin")).getString().contains(Text.translatable("cobblemon.ability.roughskin.desc").getString()));
        var truant = BattleLogCausality.resolve("cobblemon.battle.cant.truant");
        assertNotNull(truant);
        assertTrue(truant.hover().getValue(HoverEvent.Action.SHOW_TEXT).getString()
                .contains(Text.translatable("cobblemon.ability.truant.desc").getString()));
    }

    @Test void vanillaItemHoversAndUnsyncedPublicDescriptionsRemainAvailable() {
        Text diamond = format(Text.translatable("item.minecraft.diamond"), "", BattleLogEntryType.ITEM);
        assertNotNull(styleAt(diamond, diamond.getString()).getHoverEvent().getValue(HoverEvent.Action.SHOW_ITEM));
        assertNull(Moves.getByName("flamethrower"));
        Text unsynced = format(Text.translatable("cobblemon.move.flamethrower"), "", BattleLogEntryType.MOVE);
        assertTrue(hover(styleAt(unsynced, "Flamethrower")).getString()
                .contains(Text.translatable("cobblemon.move.flamethrower.desc").getString()));
    }

    @Test void frenchNamesAndDescriptionsResolveAndUnknownContentIsNotInvented() throws Exception {
        language("fr_fr");
        Text event = Text.translatable("cobblemon.battle.used_move", Text.literal("Créature"), Text.translatable("cobblemon.move.surf"));
        Text styled = format(event, "cobblemon.battle.used_move", BattleLogEntryType.MOVE);
        assertTrue(hover(styleAt(styled, Text.translatable("cobblemon.move.surf").getString())).getString()
                .contains(Text.translatable("cobblemon.move.surf.desc").getString()));
        Text focus = format(Text.translatable("item.cobblemon.focus_sash"), "", BattleLogEntryType.ITEM);
        assertTrue(hover(styleAt(focus, focus.getString())).getString()
                .contains(Text.translatable("item.cobblemon.focus_sash.tooltip_2").getString()));
        Text unknown = Text.literal("Unknown custom effect.");
        assertEquals(unknown.getString(), format(unknown, "cobblemon.battle.start.unknownfixture", BattleLogEntryType.FIELD).getString());
    }

    @Test void preservedHoverAndPaletteHaveSufficientContrast() {
        Text source = Text.literal("Custom effect").styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Public description"))));
        assertEquals("Public description", hover(styleAt(format(source, "", BattleLogEntryType.SYSTEM), "Custom effect")).getString());
        for (int color : List.of(BattleLogTextFormatter.BODY_COLOR, BattleLogTextFormatter.MUTED_COLOR,
                BattleLogTextFormatter.PLAYER_COLOR, BattleLogTextFormatter.OPPONENT_COLOR)) {
            for (BattleLogSide side : BattleLogSide.values()) {
                int background = blend(side.background(), 0xEDF0F1);
                assertTrue((luminance(background) + 0.05) / (luminance(color) + 0.05) >= 4.5);
            }
        }
        assertTrue((BattleLogSide.PLAYER.background() >>> 24) < 32);
        assertTrue((BattleLogSide.OPPONENT.background() >>> 24) < 32);
    }

    private static Text format(Text text, String key, BattleLogEntryType type) {
        Object[] args = text.getContent() instanceof TranslatableTextContent content ? content.getArgs() : new Object[0];
        return BattleLogTextFormatter.style(text, key, args, type);
    }

    private static Style styleAt(Text text, String needle) {
        int wanted = text.getString().indexOf(needle);
        assertTrue(wanted >= 0, needle + " missing from " + text.getString());
        int[] offset = {0};
        return text.visit((style, value) -> {
            int from = offset[0]; offset[0] += value.length();
            return wanted >= from && wanted < offset[0] ? Optional.of(style) : Optional.empty();
        }, Style.EMPTY).orElseThrow();
    }

    private static Text hover(Style style) {
        assertNotNull(style.getHoverEvent());
        Text tooltip = style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        assertNotNull(tooltip);
        return tooltip;
    }

    private static void assertTooltipReadable(Text tooltip) {
        tooltip.visit((style, value) -> {
            if (!value.isBlank() && style.getColor() != null)
                assertTrue(luminance(style.getColor().getRgb()) > 0.35, value);
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static double luminance(int rgb) {
        double sum = 0; double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double value = (rgb >> (16 - i * 8) & 255) / 255D;
            sum += weights[i] * (value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4));
        }
        return sum;
    }

    private static int blend(int overlay, int background) {
        int alpha = overlay >>> 24, rgb = 0;
        for (int shift : new int[]{16, 8, 0}) rgb |= (((overlay >> shift & 255) * alpha + (background >> shift & 255) * (255 - alpha)) / 255) << shift;
        return rgb;
    }

    private static MoveTemplate move(String name) {
        return TestMoveTemplates.create(name, 0, ElementalTypes.WATER, DamageCategories.INSTANCE.getSPECIAL(),
                90, MoveTarget.allAdjacent, 100, 15, 0, 1, new Double[0]);
    }

    private static AbilityTemplate ability(String name) {
        return new AbilityTemplate(name, (template, forced, priority) -> null,
                "cobblemon.ability." + name, "cobblemon.ability." + name + ".desc");
    }

    private static void language(String code) throws Exception {
        Map<String, String> translations = new HashMap<>();
        var cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElseThrow();
        try (var stream = Files.newInputStream(cobblemon.findPath("assets/cobblemon/lang/" + code + ".json").orElseThrow())) {
            Language.load(stream, translations::put);
        }
        try (var stream = BattleLogReadabilityTest.class.getResourceAsStream("/assets/tropimon_ui_battle/lang/" + code + ".json")) {
            Language.load(stream, translations::put);
        }
        Language.setInstance(new Language() {
            @Override public String get(String key, String fallback) { return translations.getOrDefault(key, originalLanguage.get(key, fallback)); }
            @Override public boolean hasTranslation(String key) { return translations.containsKey(key) || originalLanguage.hasTranslation(key); }
            @Override public boolean isRightToLeft() { return false; }
            @Override public OrderedText reorder(StringVisitable text) { return originalLanguage.reorder(text); }
        });
    }
}
