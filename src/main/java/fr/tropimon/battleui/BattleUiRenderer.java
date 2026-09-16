package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BattleUiRenderer {
    private static final int HEADER_HEIGHT = 28;
    private static final int LINE_HEIGHT = 10;
    private static final int DARK = 0xFC182026;
    private static final int EDGE = 0xFF40515C;
    private static final int CYAN = 0xFF55D5DE;
    private static final int RED = 0xFFFF5E6C;
    private static final Identifier THEME_DAY_ICON = Identifier.of("tropimon_ui_battle",
            "textures/gui/theme/theme_day.png");
    private static final Identifier THEME_NIGHT_ICON = Identifier.of("tropimon_ui_battle",
            "textures/gui/theme/theme_night.png");
    private static final BattleHistoryNavigation HISTORY = new BattleHistoryNavigation();
    private static final BattleHistoryWindow HISTORY_WINDOW = new BattleHistoryWindow();
    private static java.nio.file.Path historySettingsPath;
    private static final int MENU_ROW_HEIGHT = 14;
    private static Bounds historyBounds = Bounds.EMPTY;
    private static Bounds turnButtonBounds = Bounds.EMPTY;
    private static Bounds themeButtonBounds = Bounds.EMPTY;
    private static Bounds turnMenuBounds = Bounds.EMPTY;
    private static boolean turnMenuOpen;
    private static int turnMenuOffset;
    private static int turnMenuVisibleRows = 1;
    private static long cachedLogRevision = -1;
    private static List<DisplayLine> cachedLogLines = List.of();
    private static List<BattleHistoryNavigation.Turn> cachedTurns = List.of();
    private static final IncrementalHistory<BattleLogEntry, DisplayLine> LOG_LAYOUT = new IncrementalHistory<>();
    private static TeamMemberView hoveredTeamMember;
    private static BattleFieldEffects.EffectView hoveredFieldEffect;
    private static final LastValueCache<TeamTooltipKey, List<TooltipRow>> TEAM_TOOLTIP = new LastValueCache<>();
    private static final LastValueCache<TooltipWrapKey, List<TooltipDisplayLine>> TOOLTIP_WRAP = new LastValueCache<>();

    private BattleUiRenderer() {
    }

    public static void renderHud(DrawContext context) {
        if (!BattleUiState.active() || battleIsMinimised()) {
            hoveredTeamMember = null;
            hoveredFieldEffect = null;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            hoveredTeamMember = null;
            hoveredFieldEffect = null;
            return;
        }

        BattleUiLayout layout = layout(client);
        renderFieldEffects(context, client, layout);
        hoveredTeamMember = renderTeams(context, client, layout);
    }

    private static BattleUiLayout layout(MinecraftClient client) {
        return BattleTeamHudPanel.layout(client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight());
    }

    private static void renderFieldEffects(DrawContext context, MinecraftClient client, BattleUiLayout layout) {
        List<BattleFieldEffects.EffectView> effects = BattleUiState.effects();
        hoveredFieldEffect = null;
        if (effects.isEmpty()) return;
        int screenWidth = layout.screenWidth();
        List<BattleFieldEffects.EffectView> field = new ArrayList<>();
        List<BattleFieldEffects.EffectView> playerField = new ArrayList<>();
        List<BattleFieldEffects.EffectView> opponentField = new ArrayList<>();
        for (BattleFieldEffects.EffectView effect : effects) {
            switch (effect.side()) {
                case PLAYER_FIELD -> playerField.add(effect);
                case OPPONENT_FIELD -> opponentField.add(effect);
                case FIELD -> field.add(effect);
            }
        }

        var metrics = BattleEffectPresentation.metrics(layout.screenWidth(), layout.screenHeight());
        BattleFieldEffects.EffectView hovered = null;
        hovered = firstNonNull(hovered, renderEffectRow(context, client, field,
                screenWidth / 2, layout.fieldEffectsY(), layout.fieldEffectsWidth(),
                BattleEffectRowLayout.Alignment.CENTER, metrics));
        var battle = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle();
        var battleType = battle == null ? null : battle.getBattleFormat().getBattleType();
        int pokemonPerSide = battleType == null ? 1 : battleType.getPokemonPerSide();
        int actorsPerSide = battleType == null ? 1 : battleType.getActorsPerSide();
        int sideY = layout.sideEffectsY(pokemonPerSide, actorsPerSide);
        int sideInset = layout.sideEffectsInset(metrics.tileWidth(), pokemonPerSide > 1);
        int sideWidth = layout.sideEffectsWidth(sideInset);
        boolean compact = pokemonPerSide > 1;
        hovered = firstNonNull(hovered, renderEffectRow(context, client, playerField,
                sideInset + BattlePokemonHudPanel.offsetX(true, compact),
                sideY + BattleStatBadges.sideExtraHeight(true) + BattlePokemonHudPanel.offsetY(true, compact),
                sideWidth, BattleEffectRowLayout.Alignment.LEFT, metrics));
        hovered = firstNonNull(hovered, renderEffectRow(context, client, opponentField,
                screenWidth - sideInset + BattlePokemonHudPanel.offsetX(false, compact),
                sideY + BattleStatBadges.sideExtraHeight(false) + BattlePokemonHudPanel.offsetY(false, compact), sideWidth,
                BattleEffectRowLayout.Alignment.RIGHT, metrics));
        hoveredFieldEffect = hovered;
    }

    private static BattleFieldEffects.EffectView renderEffectRow(DrawContext context, MinecraftClient client,
            List<BattleFieldEffects.EffectView> effects, int anchorX, int startY, int maxRowWidth,
            BattleEffectRowLayout.Alignment alignment, BattleEffectPresentation.Metrics metrics) {
        if (effects.isEmpty()) return null;
        TextRenderer renderer = client.textRenderer;
        List<BattleEffectRowLayout.Position> positions = BattleEffectRowLayout.calculate(effects.size(),
                anchorX, startY, maxRowWidth, metrics.tileWidth(), metrics.tileHeight(), metrics.gap(), alignment);
        BattleFieldEffects.EffectView hovered = null;
        for (int index = 0; index < effects.size(); index++) {
            BattleFieldEffects.EffectView effect = effects.get(index);
            BattleEffectRowLayout.Position position = positions.get(index);
            int iconSize = Math.min(metrics.iconSize(), position.width());
            int iconX = position.x() + (position.width() - iconSize) / 2;
            // The PNG's alpha is the only silhouette: no tile, frame or counter plate behind it.
            BattleEffectIcon.forEffect(effect.id()).draw(context, iconX, position.y(), iconSize);
            Text counter = effect.iconCounter().copy().formatted(Formatting.BOLD);
            if (!counter.getString().isEmpty()) {
                float counterScale = BattleEffectPresentation.counterScale(renderer.getWidth(counter), position.width());
                drawCenteredOutlinedText(context, renderer, counter,
                        position.x() + position.width() / 2.0F, position.y() + iconSize + 2,
                        BattleEffectPresentation.counterColor(effect), counterScale);
            }
            if (tileContains(client, position.x(), position.y(), position.width(), position.height())) {
                hovered = effect;
            }
        }
        return hovered;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static TeamMemberView renderTeams(DrawContext context, MinecraftClient client, BattleUiLayout layout) {
        List<TeamMemberView> own = BattleUiState.ownHudTeam();
        List<TeamMemberView> opponent = BattleUiState.opponentHudTeam();
        int ownSlots = Math.max(own.size(), 6);
        int opponentSlots = Math.max(BattleUiState.opponentSlotCount(), opponent.size());

        TeamMemberView ownHovered = renderTeam(context, client, layout,
                BattleUiState.ownSideName(), own, ownSlots, false,
                BattleTeamHudPanel.offsetX(true, layout), BattleTeamHudPanel.offsetY(true, layout));
        TeamMemberView opponentHovered = renderTeam(context, client, layout,
                BattleUiState.opponentSideName(), opponent, opponentSlots, true,
                BattleTeamHudPanel.offsetX(false, layout), BattleTeamHudPanel.offsetY(false, layout));
        return firstNonNull(ownHovered, opponentHovered);
    }

    private static TeamMemberView renderTeam(DrawContext context, MinecraftClient client, BattleUiLayout layout,
                                             String label, List<TeamMemberView> team,
                                             int slots, boolean opponent, int offsetX, int offsetY) {
        if (slots <= 0) return null;
        TextRenderer renderer = client.textRenderer;
        int tileWidth = layout.teamTileWidth();
        int tileHeight = layout.teamTileHeight();
        int portraitSize = layout.teamPortraitSize();
        String fallback = opponent
                ? Text.translatable("text.tropimon_ui_battle.opponent_team").getString()
                : Text.translatable("text.tropimon_ui_battle.your_team").getString();
        String renderedLabel = label == null || label.isBlank() ? fallback : label;
        int tileY = layout.teamTop() + offsetY;
        TeamMemberView hovered = null;
        for (int slot = 0; slot < slots; slot++) {
            int tileX = layout.teamSlotX(slot, slots, opponent) + offsetX;
            TeamMemberView member = slot < team.size() ? team.get(slot) : null;
            var active = member == null ? null : BattleUiState.activeBattlePokemon(member.uuid());
            var live = active == null ? null : active.getBattlePokemon();
            member = BattleHealthFormatting.displayedMember(member, live);
            TeamIconLayout icon = TeamIconLayout.of(tileX, tileY, tileWidth, tileHeight, portraitSize);

            if (member == null || !member.known()) {
                Text unknown = Text.literal("?").formatted(Formatting.BOLD);
                context.drawTextWithShadow(renderer, unknown,
                        tileX + tileWidth / 2 - renderer.getWidth(unknown) / 2,
                        tileY + (tileWidth - renderer.fontHeight) / 2, 0xFF87979F);
                continue;
            }

            String position = BattleUiState.positionLabel(member.uuid());

            boolean portraitDrawn = PokemonPortraitRenderer.draw(context, member.uuid(), member.portrait(),
                    icon.portraitX(), icon.portraitY(), icon.portraitSize());
            if (!portraitDrawn) {
                String name = renderer.trimToWidth(member.name(), tileWidth - 4);
                drawCenteredScaledText(context, renderer, name, tileX + tileWidth / 2.0F,
                        tileY + tileWidth / 2.0F - 3,
                        member.fainted() ? 0xFF879197 : 0xFFF4F7F8, 0.72F);
            }

            Float hpTarget = BattleUiState.animatedHealthTarget(live);
            String hp = hpTarget == null ? BattlePercentageFormatting.format(member.hpPercent())
                    : BattlePercentageFormatting.animated(member.hpPercent(), hpTarget);
            boolean activeMember = member.active();
            Text hpText = Text.literal(hp).styled(style -> style.withBold(activeMember));
            float hpScale = Math.min(0.9F,
                    (tileWidth - 4.0F) / Math.max(1.0F, renderer.getWidth(hpText)));
            context.getMatrices().push();
            try {
                // Keep the overlay layer separate from Cobblemon's z=1000 model rendering.
                context.getMatrices().translate(0, 0, 1100);
                drawCenteredOutlinedText(context, renderer, hpText, tileX + tileWidth / 2.0F,
                        icon.hpY(), healthColor(member.hpPercent()), hpScale);

                String status = member.fainted() ? "fnt" : member.status();
                Identifier statusTexture = BattleUiSkin.statusTexture(status);
                if (statusTexture != null) {
                    BattleUiSkin.drawScaled(context, statusTexture, tileX, icon.statusY(),
                            tileWidth, icon.statusHeight(), 74, 7);
                    Text statusLabel = BattleUiSkin.statusLabel(status);
                    float statusScale = Math.min(0.8F,
                            (tileWidth - 4.0F) / Math.max(1.0F, renderer.getWidth(statusLabel)));
                    drawCenteredOutlinedText(context, renderer, statusLabel, tileX + tileWidth / 2.0F,
                            icon.statusY() + (icon.statusHeight() - renderer.fontHeight * statusScale) / 2.0F,
                            0xFFFFFFFF, statusScale);
                }
                if (!position.isBlank()) {
                    Text badge = MoveTooltipRenderer.ppStyledLabel(position);
                    int badgeWidth = Math.max(12, renderer.getWidth(badge) + 4);
                    int badgeX = opponent ? tileX + 1 : tileX + tileWidth - badgeWidth - 1;
                    BattleUiSkin.drawMovePpBackground(context, badgeX, tileY + 1, badgeWidth, 9, 1.0F);
                    drawCenteredOutlinedText(context, renderer, badge, badgeX + badgeWidth / 2.0F,
                            tileY + 1, 0xFFFFFFFF, 0.72F);
                }
            } finally {
                context.getMatrices().pop();
            }

            if (tileContains(client, tileX, tileY, tileWidth, tileHeight)) hovered = member;
        }

        renderTrainerGroups(context, renderer, layout, team, slots, opponent, renderedLabel,
                tileY, tileHeight, offsetX);
        return hovered;
    }

    private static void renderTrainerGroups(DrawContext context, TextRenderer renderer, BattleUiLayout layout,
                                            List<TeamMemberView> team, int slots, boolean opponent,
                                            String fallback, int tileY, int tileHeight, int offsetX) {
        // Singles are labelled above their active HP tile. Keep this row only
        // where it materially separates several trainers on the same side.
        if (!BattleTeamHudPanel.hasTrainerGroups(team)) return;
        slots = Math.min(slots, team.size());
        int start = 0;
        while (start < slots) {
            TeamMemberView first = start < team.size() ? team.get(start) : null;
            String trainer = first == null ? "" : BattleUiState.trainerName(first.uuid());
            if (trainer.isBlank()) trainer = fallback;
            int end = start + 1;
            while (end < slots) {
                TeamMemberView next = end < team.size() ? team.get(end) : null;
                String nextTrainer = next == null ? "" : BattleUiState.trainerName(next.uuid());
                if (nextTrainer.isBlank()) nextTrainer = fallback;
                if (!nextTrainer.equals(trainer)) break;
                end++;
            }
            int firstX = layout.teamSlotX(start, slots, opponent) + offsetX;
            int lastX = layout.teamSlotX(end - 1, slots, opponent) + offsetX;
            int left = Math.min(firstX, lastX);
            int right = Math.max(firstX, lastX) + layout.teamTileWidth();
            String fitted = renderer.trimToWidth(trainer, Math.max(1, right - left));
            context.drawTextWithShadow(renderer, fitted,
                    left + (right - left - renderer.getWidth(fitted)) / 2,
                    tileY + tileHeight + 2, opponent ? 0xFFFF9AA2 : 0xFF8DEBF1);
            if (end < slots) {
                int boundary = (right + layout.teamSlotX(end, slots, opponent) + offsetX) / 2;
                context.fill(boundary, tileY + 2, boundary + 1, tileY + tileHeight - 1,
                        opponent ? 0xCCFF5E6C : 0xCC55D5DE);
            }
            start = end;
        }
    }

    private static void drawCenteredScaledText(DrawContext context, TextRenderer renderer, String text,
                                                float centerX, float y, int color, float scale) {
        float safeScale = Math.max(0.5F, Math.min(1.0F, scale));
        context.getMatrices().push();
        context.getMatrices().translate(centerX, y, 3.0F);
        context.getMatrices().scale(safeScale, safeScale, 1.0F);
        context.drawTextWithShadow(renderer, text, -renderer.getWidth(text) / 2, 0, color);
        context.getMatrices().pop();
    }

    /** Four-sided outline stays legible against bright sky/snow without an opaque plate. */
    private static void drawCenteredOutlinedText(DrawContext context, TextRenderer renderer, Text text,
                                                 float centerX, float y, int color, float scale) {
        if (scale <= 0.0F) return;
        context.getMatrices().push();
        try {
            context.getMatrices().translate(centerX - renderer.getWidth(text) * scale / 2.0F, y, 3.0F);
            context.getMatrices().scale(scale, scale, 1.0F);
            context.drawText(renderer, text, -1, 0, 0xFF142027, false);
            context.drawText(renderer, text, 1, 0, 0xFF142027, false);
            context.drawText(renderer, text, 0, -1, 0xFF142027, false);
            context.drawText(renderer, text, 0, 1, 0xFF142027, false);
            context.getMatrices().translate(0, 0, 1.0F);
            context.drawText(renderer, text, 0, 0, color, false);
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void renderTeamTooltip(DrawContext context, MinecraftClient client, TeamMemberView member) {
        var active = BattleUiState.activeBattlePokemon(member.uuid());
        var live = active == null ? null : active.getBattlePokemon();
        TeamMemberView displayed = BattleHealthFormatting.displayedMember(member, live);
        boolean opponent = BattleUiState.publicInformationOnly(displayed.uuid());
        OpponentKnowledgeView knowledge = BattleUiState.opponentKnowledge(displayed.uuid());
        SpeedRangeView speed = opponent ? knowledge.speedRange() : BattleUiState.speedRange(displayed);
        BattleStatsView battleStats = BattleUiState.ownBattleStats(displayed.uuid());
        var effects = BattleUiState.pokemonEffects(displayed.uuid());
        var key = new TeamTooltipKey(displayed, opponent, BattleUiState.opponentItemKnownAbsent(displayed.uuid()), knowledge,
                speed, battleStats, effects, net.minecraft.util.Language.getInstance(), UiResourceEpoch.current());
        List<TooltipRow> rows = TEAM_TOOLTIP.get(key,
                () -> teamTooltipRows(displayed, opponent, knowledge, speed, battleStats, effects));
        drawBoundedTooltip(context, client, layout(client), rows);
    }

    private static List<TooltipRow> teamTooltipRows(TeamMemberView member, boolean opponent,
            OpponentKnowledgeView knowledge, SpeedRangeView speed,
            BattleStatsView battleStats,
            List<PokemonBattleEffects.PokemonEffectView> activeEffects) {
        List<TooltipRow> rows = new ArrayList<>();
        String header = member.level() > 0 ? member.name() + "  Lv." + member.level() : member.name();
        rows.add(new TooltipRow(Text.literal(header).formatted(Formatting.BOLD), false));
        MutableText health = Text.translatable("text.tropimon_ui_battle.hp_percent",
                BattlePercentageFormatting.format(member.hpPercent()));
        if (member.fainted()) health.append(Text.literal("  KO").formatted(Formatting.RED, Formatting.BOLD));
        else if (!member.status().isBlank()) health.append(Text.literal("  ")).append(statusName(member.status())
                .copy().styled(style -> style.withColor(statusColor(member.status())).withBold(true)));
        rows.add(new TooltipRow(health, false));
        if (battleStats.known()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.battle_stats_physical",
                    battleStats.hp(), battleStats.attack(), battleStats.defense())
                    .formatted(Formatting.AQUA), false));
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.battle_stats_special",
                    battleStats.specialAttack(), battleStats.specialDefense(), battleStats.speed())
                    .formatted(Formatting.AQUA), false));
        }

        if (!member.types().isEmpty()) {
            MutableText types = Text.translatable("text.tropimon_ui_battle.types");
            for (int index = 0; index < member.types().size(); index++) {
                TypeView type = member.types().get(index);
                if (index > 0) types.append(Text.literal(" / ").formatted(Formatting.GRAY));
                types.append(type.name().copy().styled(style ->
                        style.withColor(BattleLogTextFormatter.typeColor(type.id())).withBold(true)));
            }
            rows.add(new TooltipRow(types, true));

            List<PokemonTypeMatchups.TypeMatchupView> weaknesses = PokemonTypeMatchups.weaknesses(member.types());
            if (!weaknesses.isEmpty()) {
                MutableText weaknessLine = Text.translatable("text.tropimon_ui_battle.weaknesses");
                for (int index = 0; index < weaknesses.size(); index++) {
                    PokemonTypeMatchups.TypeMatchupView weakness = weaknesses.get(index);
                    if (index > 0) weaknessLine.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
                    ElementalType elementalType = ElementalTypes.get(weakness.type());
                    Text typeName = elementalType == null ? Text.literal(weakness.type()) : elementalType.getDisplayName();
                    String multiplier = weakness.multiplier() == 4.0D ? " ×4" : " ×2";
                    weaknessLine.append(typeName.copy().styled(style ->
                                    style.withColor(BattleLogTextFormatter.typeColor(weakness.type()))))
                            .append(Text.literal(multiplier).formatted(Formatting.GRAY));
                }
                rows.add(new TooltipRow(weaknessLine, false));
            }
        }

        ItemStack heldItem = member.heldItem();
        if (!heldItem.isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.held_item",
                    itemWithHover(heldItem)), true));
        } else if (BattleUiState.opponentItemKnownAbsent(member.uuid())) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.no_held_item")
                    .formatted(Formatting.GRAY), true));
        }
        if (member.ability() != null && !member.ability().name().getString().isBlank()) {
            MutableText ability = Text.translatable("text.tropimon_ui_battle.ability", member.ability().name())
                    .styled(style -> style.withColor(0xFFB980E2).withBold(true));
            if (member.ability().suppressed()) {
                ability.append(Text.literal("  ")).append(Text.translatable(
                        "text.tropimon_ui_battle.ability_suppressed").formatted(Formatting.RED));
            }
            rows.add(new TooltipRow(ability, true));
            if (!member.ability().description().getString().isBlank()) {
                rows.add(new TooltipRow(member.ability().description().copy().formatted(Formatting.GRAY), false));
            }
        } else if (opponent && !knowledge.possibleAbilities().isEmpty()) {
            MutableText abilities = Text.translatable("text.tropimon_ui_battle.possible_abilities")
                    .formatted(Formatting.BOLD).append(Text.literal(" "));
            for (int index = 0; index < knowledge.possibleAbilities().size(); index++) {
                AbilityView possible = knowledge.possibleAbilities().get(index);
                if (index > 0) abilities.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
                MutableText name = possible.name().copy();
                if (possible.hidden()) name.append(Text.literal(" (")).append(Text.translatable(
                        "text.tropimon_ui_battle.hidden_ability_short")).append(Text.literal(")"));
                abilities.append(name.styled(style -> style.withColor(0xFFB980E2)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                possible.name().copy().formatted(Formatting.BOLD).append(Text.literal("\n"))
                                        .append(possible.description().copy().formatted(Formatting.GRAY))))));
            }
            rows.add(new TooltipRow(abilities, true));
        }
        if (!battleStats.known() && speed.known()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.speed_range",
                    speed.minimum(), speed.maximum()).formatted(Formatting.AQUA), true));
            if (speed.modified()) {
                rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.speed_range_effective",
                        speed.effectiveMinimum(), speed.effectiveMaximum()).formatted(Formatting.GRAY), false));
            }
        }
        if (!knowledge.itemHistory().isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.item_history")
                    .formatted(Formatting.BOLD), true));
            int first = 0;
            for (int index = first; index < knowledge.itemHistory().size(); index++) {
                ItemHistoryView event = knowledge.itemHistory().get(index);
                rows.add(new TooltipRow(Text.translatable(event.kind().translationKey(),
                        event.turn(), itemWithHover(event.item())).formatted(Formatting.GRAY), false));
            }
        }
        if (opponent && !knowledge.formHistory().isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.form_history")
                    .formatted(Formatting.BOLD), true));
            int first = 0;
            for (int index = first; index < knowledge.formHistory().size(); index++) {
                FormHistoryView event = knowledge.formHistory().get(index);
                Text history = event.kind() == FormEventKind.TERASTALLIZED
                        ? Text.translatable(event.kind().translationKey(), event.turn(), event.to())
                        : Text.translatable(event.kind().translationKey(), event.turn(), event.from(), event.to());
                rows.add(new TooltipRow(history.copy().formatted(Formatting.GRAY), false));
            }
        }
        if (!member.statStages().isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.stat_changes")
                    .formatted(Formatting.BOLD), true));
            MutableText stages = Text.empty();
            for (int index = 0; index < member.statStages().size(); index++) {
                StatStageView stage = member.statStages().get(index);
                if (index > 0) stages.append(Text.literal("  "));
                String signed = stage.stage() > 0 ? "+" + stage.stage() : Integer.toString(stage.stage());
                int color = stage.stage() > 0 ? 0xFF63D66F : 0xFFFF5E6C;
                stages.append(Text.empty().append(stage.name()).append(Text.literal(" " + signed))
                        .styled(style -> style.withColor(color)));
            }
            rows.add(new TooltipRow(stages, false));
        }
        if (!activeEffects.isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.active_effects")
                    .formatted(Formatting.BOLD), true));
            MutableText effects = Text.empty();
            for (int index = 0; index < activeEffects.size(); index++) {
                var effect = activeEffects.get(index);
                if (index > 0) effects.append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY));
                effects.append(effect.label().copy().styled(style -> style.withColor(0xFFFFC857)));
                if (!effect.detail().isBlank()) {
                    effects.append(Text.literal(" (" + effect.detail() + ")").formatted(Formatting.GRAY));
                }
                if (!effect.counterText().isBlank()) {
                    effects.append(Text.literal(" " + effect.counterText()).formatted(Formatting.RED, Formatting.BOLD));
                }
            }
            rows.add(new TooltipRow(effects, false));
        }
        if (!member.knownMoves().isEmpty()) {
            rows.add(new TooltipRow(Text.translatable("text.tropimon_ui_battle.known_moves")
                    .formatted(Formatting.BOLD), true));
            MutableText moves = Text.empty();
            for (int index = 0; index < member.knownMoves().size(); index++) {
                MoveView move = member.knownMoves().get(index);
                if (index > 0) moves.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
                moves.append(move.name().copy().styled(style ->
                        style.withColor(BattleLogTextFormatter.typeColor(move.type()))));
                if (opponent) {
                    if (move.origin() == MoveOrigin.CALLED) {
                        moves.append(Text.translatable("text.tropimon_ui_battle.opponent_move_called")
                                .formatted(Formatting.GRAY));
                    } else if (move.currentPp() >= 0 && move.maxPp() > 0) {
                        String key = move.origin() == MoveOrigin.TRANSFORMED
                                ? "text.tropimon_ui_battle.opponent_move_pp_transformed"
                                : move.origin() == MoveOrigin.COPIED
                                ? "text.tropimon_ui_battle.opponent_move_pp_copied"
                                : "text.tropimon_ui_battle.opponent_move_pp";
                        if (move.hasPpRange()) {
                            moves.append(Text.translatable("text.tropimon_ui_battle.opponent_move_pp_range",
                                            ppRange(move.minCurrentPp(), move.currentPp()),
                                            ppRange(move.minMaxPp(), move.maxPp()))
                                    .formatted(Formatting.GRAY));
                        } else {
                            moves.append(Text.translatable(key, move.currentPp(), move.maxPp(), move.ppUsed())
                                    .formatted(Formatting.GRAY));
                        }
                    }
                }
            }
            rows.add(new TooltipRow(moves, false));
        }

        return List.copyOf(rows);
    }

    private static String ppRange(int minimum, int maximum) {
        return minimum == maximum ? Integer.toString(minimum) : minimum + "–" + maximum;
    }

    private static Text itemWithHover(ItemStack item) {
        if (item == null || item.isEmpty()) return Text.empty();
        return item.getName().copy().styled(style -> style.withBold(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                        new HoverEvent.ItemStackContent(item.copy()))));
    }

    private static void drawBoundedTooltip(DrawContext context, MinecraftClient client,
                                           BattleUiLayout layout, List<TooltipRow> rows) {
        TextRenderer renderer = client.textRenderer;
        int screenWidth = layout.screenWidth();
        int screenHeight = layout.screenHeight();
        int margin = layout.margin();
        int width = layout.tooltipWidth();
        int padding = layout.tooltipPadding();
        int wrapWidth = Math.max(20, width - padding * 2);
        var wrapKey = new TooltipWrapKey(List.copyOf(rows), wrapWidth,
                net.minecraft.util.Language.getInstance(), UiResourceEpoch.current());
        List<TooltipDisplayLine> lines = TOOLTIP_WRAP.get(wrapKey, () -> {
            List<TooltipDisplayLine> result = new ArrayList<>();
            for (TooltipRow row : rows) {
                List<OrderedText> wrapped = renderer.wrapLines(row.text(), wrapWidth);
                for (int index = 0; index < wrapped.size(); index++) {
                    result.add(new TooltipDisplayLine(wrapped.get(index), row.gapBefore() && index == 0));
                }
            }
            return List.copyOf(result);
        });

        int maxHeight = Math.max(30, screenHeight - margin * 2);
        int contentHeight = padding * 2;
        List<TooltipDisplayLine> visible = new ArrayList<>();
        for (TooltipDisplayLine line : lines) {
            int addition = LINE_HEIGHT + (line.gapBefore() ? 3 : 0);
            if (contentHeight + addition > maxHeight - 2) break;
            visible.add(line);
            contentHeight += addition;
        }
        if (visible.size() < lines.size() && !visible.isEmpty()) {
            visible.set(visible.size() - 1, new TooltipDisplayLine(Text.literal("…").asOrderedText(), false));
        }

        int mouseX = scaledMouseX(client);
        int mouseY = scaledMouseY(client);
        int x = mouseX + 12;
        if (x + width > screenWidth - margin) x = mouseX - width - 12;
        x = MathHelper.clamp(x, margin, Math.max(margin, screenWidth - width - margin));
        int y = MathHelper.clamp(mouseY + 12, margin,
                Math.max(margin, screenHeight - contentHeight - margin));

        // BattleGUI renders after the HUD. A high local Z keeps Cobblemon's
        // movement prompt and other screen widgets from cutting through this panel.
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 450.0F);
        try {
            BattleUiSkin.drawDarkPanel(context, x, y, width, contentHeight);
            context.fill(x + 4, y + 4, x + width / 2, y + 6, CYAN);
            context.fill(x + width / 2, y + 4, x + width - 4, y + 6, RED);
            int lineY = y + padding;
            for (TooltipDisplayLine line : visible) {
                if (line.gapBefore()) lineY += 3;
                context.drawText(renderer, line.text(), x + padding, lineY, 0xFFFFFFFF, false);
                lineY += LINE_HEIGHT;
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    public static void renderBattleScreen(DrawContext context, int mouseX, int mouseY) {
        if (!replacesNativeHistory()) {
            finishHistoryInteraction();
            closeTurnMenu();
            historyBounds = Bounds.EMPTY;
            turnButtonBounds = Bounds.EMPTY;
            themeButtonBounds = Bounds.EMPTY;
            return;
        }
        context.getMatrices().push();
        try {
            // A movable panel must also cover the z=1000 Pokémon portraits when placed over them.
            context.getMatrices().translate(0, 0, 2000);
            renderHistoryWindow(context, mouseX, mouseY);
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void renderHistoryWindow(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        BattleUiLayout layout = layout(client);
        int screenWidth = layout.screenWidth();
        int screenHeight = layout.screenHeight();
        int width = HISTORY_WINDOW.width(screenWidth, layout.margin(), layout.historyWidth());
        TextRenderer renderer = client.textRenderer;
        int innerWidth = width - 17;
        List<DisplayLine> lines = flattenedLog(renderer, innerWidth);
        int desiredVisibleLines = MathHelper.clamp(lines.size(), 9, 20);
        int minimumHeight = Math.min(123, layout.historyMaxHeight());
        int defaultHeight = MathHelper.clamp(HEADER_HEIGHT + 11 + desiredVisibleLines * LINE_HEIGHT,
                minimumHeight, layout.historyMaxHeight());
        var window = HISTORY_WINDOW.layout(screenWidth, screenHeight, layout.margin(), layout.historyWidth(), defaultHeight);
        int height = window.height();
        int x = window.x();
        int y = window.y();
        historyBounds = new Bounds(x, y, width, height);
        int innerX = x + 7;
        int innerY = y + HEADER_HEIGHT + 4;
        int innerHeight = height - HEADER_HEIGHT - 11;
        int visible = Math.max(1, innerHeight / LINE_HEIGHT);
        HISTORY.update(cachedTurns, lines.size(), visible);
        int maxScroll = HISTORY.maximumStart();
        int start = HISTORY.startLine();
        int end = HISTORY.endLine();

        BattleUiSkin.drawTropimonLightPanel(context, x, y, width, height);
        context.fill(x + 7, y + 5, x + width - 7, y + HEADER_HEIGHT, BattleUiTheme.historyHeader());
        context.fill(x + 7, y + HEADER_HEIGHT - 2, x + width / 2, y + HEADER_HEIGHT, CYAN);
        context.fill(x + width / 2, y + HEADER_HEIGHT - 2, x + width - 7, y + HEADER_HEIGHT, RED);

        Text title = Text.translatable("text.tropimon_ui_battle.history");
        Text turn = Text.translatable("text.tropimon_ui_battle.turn", BattleUiState.turn());
        String timeValue = BattleUiState.combatTime().orElseGet(() -> formatDuration(BattleUiState.elapsed()));
        Text time = Text.literal(renderer.trimToWidth("◷ " + timeValue, Math.max(30, width / 3)));
        int headerRight = x + width - 10;
        int timeX = headerRight - renderer.getWidth(time);
        int buttonWidth = Math.min(Math.max(42, renderer.getWidth(turn) + 15), width - renderer.getWidth(time) - 22);
        int turnX = timeX - 5 - buttonWidth;
        turnButtonBounds = new Bounds(turnX, y + 9, buttonWidth, 14);
        int themeWidth = 20;
        int themeX = Math.max(x + 8, turnX - themeWidth - 4);
        themeButtonBounds = new Bounds(themeX, y + 9, themeWidth, 14);
        layoutTurnMenu(renderer, layout);
        int titleWidth = Math.max(0, themeX - (x + 12));
        String fittedTitle = renderer.trimToWidth(title.getString(), titleWidth);
        if (!fittedTitle.isBlank()) {
            context.drawTextWithShadow(renderer, fittedTitle, x + 8, y + 12, 0xFFFFFFFF);
        }
        boolean turnHovered = turnButtonBounds.contains(mouseX, mouseY);
        BattleUiSkin.drawCobblemonButton(context, turnX, turnButtonBounds.y(), buttonWidth,
                turnButtonBounds.height(), turnHovered || turnMenuOpen);
        String fittedTurn = renderer.trimToWidth(turn.getString(), buttonWidth - 14);
        int turnTextX = turnX + (buttonWidth - 8 - renderer.getWidth(fittedTurn)) / 2;
        context.drawTextWithShadow(renderer, fittedTurn, turnTextX, y + 12, 0xFFFFFFFF);
        drawChevron(context, turnX + buttonWidth - 8, y + 14, turnMenuOpen, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, time, timeX, y + 12, 0xFFE4EDF2);
        boolean themeHovered = themeButtonBounds.contains(mouseX, mouseY);
        BattleUiSkin.drawCobblemonButton(context, themeX, themeButtonBounds.y(), themeWidth,
                themeButtonBounds.height(), themeHovered);
        drawThemeIcon(context, themeX + themeWidth / 2, y + 16, BattleUiTheme.night());

        Style hoveredStyle = null;
        context.enableScissor(innerX, innerY, innerX + innerWidth, innerY + innerHeight);
        int lineY = innerY;
        for (int index = start; index < end; index++) {
            DisplayLine line = lines.get(index);
            int textX = innerX;
            if (line.side() != BattleLogSide.NEUTRAL) {
                context.fill(innerX - 2, lineY - 1, innerX + innerWidth, lineY + 9,
                        BattleUiTheme.sideRow(line.side()));
                if (line.impact() != BattleLogImpact.NONE) {
                    context.fill(innerX - 2, lineY - 1, innerX, lineY + 9, line.side().accent());
                    textX += 3;
                }
            } else if (line.type() == BattleLogEntryType.TURN) {
                context.fill(innerX - 2, lineY - 1, innerX + innerWidth, lineY + 9, BattleUiTheme.neutralRow());
            } else if (line.type() == BattleLogEntryType.HEADER) {
                context.fill(innerX - 2, lineY - 1, innerX + innerWidth, lineY + 9, BattleUiTheme.neutralRow());
            }
            context.drawText(renderer, line.text(), textX, lineY, 0xFFFFFFFF, false);
            if (mouseY >= lineY && mouseY < Math.min(innerY + innerHeight, lineY + LINE_HEIGHT) &&
                    mouseX >= textX && mouseX < innerX + innerWidth) {
                int relativeX = mouseX - textX;
                if (relativeX <= renderer.getWidth(line.text())) {
                    Style style = renderer.getTextHandler().getStyleAt(line.text(), relativeX);
                    if (style != null && style.getHoverEvent() != null) hoveredStyle = style;
                }
            }
            lineY += LINE_HEIGHT;
        }
        context.disableScissor();

        if (maxScroll > 0) {
            int trackTop = innerY;
            int trackHeight = innerHeight;
            int thumbHeight = Math.max(12, trackHeight * visible / Math.max(lines.size(), 1));
            int thumbTravel = trackHeight - thumbHeight;
            int fromTop = thumbTravel * start / maxScroll;
            context.fill(x + width - 7, trackTop, x + width - 4, trackTop + trackHeight, BattleUiTheme.scrollTrack());
            context.fill(x + width - 7, trackTop + fromTop, x + width - 4,
                    trackTop + fromTop + thumbHeight, HISTORY.followingLatest() ? CYAN : 0xFF8EA4B0);
        }

        if (!HISTORY.followingLatest()) {
            Text down = Text.literal("▼");
            int downX = x + width - 32;
            int downY = y + height - 12;
            context.fill(downX - 3, downY - 2, downX + renderer.getWidth(down) + 3, downY + 10, DARK);
            context.drawTextWithShadow(renderer, down, downX, downY, 0xFFFFFFFF);
        }

        BattleUiSkin.drawTropimonFrame(context, x, y, width, height, 1.0F);
        int gripColor = HISTORY_WINDOW.onResizeBorder(mouseX, mouseY) || HISTORY_WINDOW.interacting() ? CYAN : 0xFF8EA4B0;
        for (int offset = 0; offset < 3; offset++) {
            context.fill(x + width - 5 - offset * 3, y + height - 5,
                    x + width - 3 - offset * 3, y + height - 3, gripColor);
            context.fill(x + width - 5, y + height - 5 - offset * 3,
                    x + width - 3, y + height - 3 - offset * 3, gripColor);
        }
        if (turnMenuOpen) {
            renderTurnMenu(context, renderer, mouseX, mouseY);
            return;
        }
        if (HISTORY_WINDOW.interacting()) return;
        if (hoveredStyle != null) context.drawHoverEvent(renderer, hoveredStyle, mouseX, mouseY);
        if (hoveredTeamMember != null && !historyBounds.contains(mouseX, mouseY)) {
            renderTeamTooltip(context, client, hoveredTeamMember);
        } else if (hoveredFieldEffect != null && !historyBounds.contains(mouseX, mouseY)) {
            drawBoundedTooltip(context, client, layout, hoveredFieldEffect.tooltipLines().stream()
                    .map(text -> new TooltipRow(text, false)).toList());
        } else if (turnHovered) {
            drawBoundedTooltip(context, client, layout,
                    List.of(new TooltipRow(Text.translatable("text.tropimon_ui_battle.history.choose_turn"), false)));
        } else if (HISTORY_WINDOW.onResizeBorder(mouseX, mouseY) ||
                (historyBounds.contains(mouseX, mouseY) && mouseY < y + HEADER_HEIGHT)) {
            drawBoundedTooltip(context, client, layout,
                    List.of(new TooltipRow(Text.translatable("text.tropimon_ui_battle.history.move_resize"), false)));
        }
    }

    private static void layoutTurnMenu(TextRenderer renderer, BattleUiLayout layout) {
        int labelWidth = Math.max(renderer.getWidth(Text.translatable("text.tropimon_ui_battle.history.latest")),
                renderer.getWidth(turnLabel(0)));
        if (!HISTORY.turns().isEmpty()) {
            labelWidth = Math.max(labelWidth, renderer.getWidth(turnLabel(HISTORY.turns().getLast().number())));
        }
        var menu = calculateTurnMenu(layout, historyBounds.width(), historyBounds.y() + HEADER_HEIGHT,
                turnButtonBounds.x() + turnButtonBounds.width(), labelWidth, HISTORY.turns().size());
        turnMenuVisibleRows = menu.visibleRows();
        turnMenuBounds = new Bounds(menu.x(), menu.y(), menu.width(), menu.height());
        turnMenuOffset = BattleHistoryNavigation.menuOffset(turnMenuOffset, HISTORY.turns().size(), turnMenuVisibleRows);
    }

    static TurnMenuLayout calculateTurnMenu(BattleUiLayout layout, int historyWidth, int top,
                                            int anchorRight, int labelWidth, int turnCount) {
        int width = Math.min(Math.max(116, labelWidth + 22), historyWidth - 14);
        int availableHeight = layout.screenHeight() - layout.margin() - top;
        int rows = Math.min(turnCount, Math.min(8, Math.max(1, (availableHeight - 28) / MENU_ROW_HEIGHT)));
        int x = MathHelper.clamp(anchorRight - width, layout.margin(), layout.screenWidth() - width - layout.margin());
        int height = 28 + rows * MENU_ROW_HEIGHT;
        int y = MathHelper.clamp(top, layout.margin(), layout.screenHeight() - height - layout.margin());
        return new TurnMenuLayout(x, y, width, height, rows);
    }

    private static Text turnLabel(int number) {
        return number == 0 ? Text.translatable("text.tropimon_ui_battle.history.start")
                : Text.translatable("text.tropimon_ui_battle.turn", number);
    }

    private static void renderTurnMenu(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
        Bounds box = turnMenuBounds;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 500.0F);
        try {
            BattleUiSkin.drawDarkPanel(context, box.x(), box.y(), box.width(), box.height());
            context.fill(box.x() + 4, box.y() + 4, box.x() + box.width() - 4,
                    box.y() + box.height() - 4, DARK);
            drawMenuRow(context, renderer, Text.translatable("text.tropimon_ui_battle.history.latest"),
                    box.y() + 6, HISTORY.followingLatest(), mouseX, mouseY);
            context.fill(box.x() + 6, box.y() + 20, box.x() + box.width() - 6, box.y() + 21, EDGE);
            int top = box.y() + 22;
            for (int row = 0; row < turnMenuVisibleRows; row++) {
                var turn = HISTORY.turns().get(turnMenuOffset + row);
                drawMenuRow(context, renderer, turnLabel(turn.number()), top + row * MENU_ROW_HEIGHT,
                        !HISTORY.followingLatest() && turn.number() == HISTORY.viewedTurn(), mouseX, mouseY);
            }
            int total = HISTORY.turns().size();
            if (total > turnMenuVisibleRows) {
                int trackHeight = turnMenuVisibleRows * MENU_ROW_HEIGHT;
                int thumb = Math.max(8, trackHeight * turnMenuVisibleRows / total);
                int thumbY = top + (trackHeight - thumb) * turnMenuOffset / (total - turnMenuVisibleRows);
                context.fill(box.x() + box.width() - 7, top, box.x() + box.width() - 5, top + trackHeight, EDGE);
                context.fill(box.x() + box.width() - 7, thumbY, box.x() + box.width() - 5, thumbY + thumb, CYAN);
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void drawMenuRow(DrawContext context, TextRenderer renderer, Text label, int y,
                                     boolean selected, int mouseX, int mouseY) {
        Bounds box = turnMenuBounds;
        boolean hovered = new Bounds(box.x() + 5, y, box.width() - 14, MENU_ROW_HEIGHT).contains(mouseX, mouseY);
        if (hovered || selected) {
            context.fill(box.x() + 5, y, box.x() + box.width() - 9, y + MENU_ROW_HEIGHT,
                    hovered ? 0x6655D5DE : 0x3355D5DE);
        }
        if (selected) context.fill(box.x() + 5, y + 2, box.x() + 7, y + MENU_ROW_HEIGHT - 2, CYAN);
        String fitted = renderer.trimToWidth(label.getString(), Math.max(1, box.width() - 24));
        context.drawTextWithShadow(renderer, fitted, box.x() + 10, y + 3, selected ? CYAN : 0xFFE4EDF2);
    }

    private static void drawChevron(DrawContext context, int x, int y, boolean up, int color) {
        for (int row = 0; row < 3; row++) {
            int inset = up ? 2 - row : row;
            context.fill(x + inset, y + row, x + 5 - inset, y + row + 1, color);
        }
    }

    private static void drawThemeIcon(DrawContext context, int centerX, int centerY, boolean night) {
        context.drawTexture(night ? THEME_NIGHT_ICON : THEME_DAY_ICON,
                centerX - 6, centerY - 6, 0, 0, 12, 12, 12, 12);
    }

    public static boolean handleScroll(double rawMouseX, double rawMouseY, double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof BattleGUI) || !BattleUiState.active()) return false;
        double mouseX = rawMouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = rawMouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        return handleScaledScroll(mouseX, mouseY, vertical);
    }

    public static boolean handleScaledScroll(double mouseX, double mouseY, double vertical) {
        if (!replacesNativeHistory() || vertical == 0.0D) return false;
        if (HISTORY_WINDOW.interacting()) return true;
        int rows = vertical > 0 ? -3 : 3;
        if (turnMenuOpen) {
            turnMenuOffset = BattleHistoryNavigation.menuOffset(turnMenuOffset + rows,
                    HISTORY.turns().size(), turnMenuVisibleRows);
            return true;
        }
        if (!historyBounds.contains(mouseX, mouseY)) return false;
        if (HISTORY.maximumStart() > 0) HISTORY.scroll(rows);
        return true;
    }

    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (!replacesNativeHistory()) return false;
        if (HISTORY_WINDOW.interacting()) {
            if (button == 0) finishHistoryInteraction();
            return true;
        }
        if (button == 0 && themeButtonBounds.contains(mouseX, mouseY)) {
            MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance
                    .master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            BattleUiTheme.toggle();
            cachedLogRevision = -1;
            return true;
        }
        if (button == 0 && turnButtonBounds.contains(mouseX, mouseY) && !HISTORY.turns().isEmpty()) {
            MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance
                    .master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            turnMenuOpen = !turnMenuOpen;
            int selected = HISTORY.turns().size() - 1;
            if (!HISTORY.followingLatest()) {
                for (int index = 0; index < HISTORY.turns().size(); index++) {
                    if (HISTORY.turns().get(index).number() == HISTORY.viewedTurn()) selected = index;
                }
            }
            turnMenuOffset = BattleHistoryNavigation.menuOffset(selected - turnMenuVisibleRows / 2,
                    HISTORY.turns().size(), turnMenuVisibleRows);
            return true;
        }
        if (turnMenuOpen) {
            if (button == 0 && turnMenuBounds.contains(mouseX, mouseY)) {
                if (mouseX < turnMenuBounds.x() + 5 || mouseX >= turnMenuBounds.x() + turnMenuBounds.width() - 9) {
                    return true;
                }
                int relativeY = (int) mouseY - turnMenuBounds.y();
                if (relativeY >= 6 && relativeY < 20) {
                    HISTORY.followLatest();
                } else if (relativeY >= 22 && relativeY < 22 + turnMenuVisibleRows * MENU_ROW_HEIGHT) {
                    int row = (relativeY - 22) / MENU_ROW_HEIGHT;
                    HISTORY.jumpToTurn(HISTORY.turns().get(turnMenuOffset + row).number());
                } else return true;
            }
            closeTurnMenu();
            // Dismissing the popup must not also choose a battle action underneath it.
            return true;
        }
        if (!historyBounds.contains(mouseX, mouseY)) return false;
        if (button == 1 && mouseY < historyBounds.y() + HEADER_HEIGHT && mouseX < turnButtonBounds.x()) {
            HISTORY_WINDOW.reset();
            finishHistoryInteraction();
            return true;
        }
        if (HISTORY_WINDOW.begin(mouseX, mouseY, button, HEADER_HEIGHT)) return true;
        if (button == 0 && !HISTORY.followingLatest() &&
                mouseX >= historyBounds.x() + historyBounds.width() - 38 &&
                mouseX < historyBounds.x() + historyBounds.width() - 17 &&
                mouseY >= historyBounds.y() + historyBounds.height() - 16) {
            HISTORY.followLatest();
            return true;
        }
        // The movable panel can cover battle controls; never click through it.
        return true;
    }

    public static boolean handleDrag(double mouseX, double mouseY, int button) {
        if (!HISTORY_WINDOW.interacting()) return false;
        if (!replacesNativeHistory()) { finishHistoryInteraction(); return false; }
        if (button != 0) return false;
        BattleUiLayout layout = layout(MinecraftClient.getInstance());
        return HISTORY_WINDOW.drag(mouseX, mouseY, layout.screenWidth(), layout.screenHeight(), layout.margin());
    }

    static boolean historyCoversPointer(double mouseX, double mouseY) {
        return replacesNativeHistory() && (turnMenuOpen || HISTORY_WINDOW.interacting() ||
                historyBounds.contains(mouseX, mouseY));
    }

    public static void finishHistoryInteraction() {
        if (!HISTORY_WINDOW.finish() || historySettingsPath == null) return;
        try { HISTORY_WINDOW.save(historySettingsPath); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not save UI Battle history layout; local settings left unchanged.");
        }
    }

    static void loadHistoryWindow(java.nio.file.Path file) {
        historySettingsPath = file;
        try { HISTORY_WINDOW.load(file); }
        catch (java.io.IOException | RuntimeException ignored) {
            TropimonUIBattleClient.LOGGER.warn("Could not read UI Battle history layout; using the default layout.");
        }
    }

    public static boolean handleKeyPress(int keyCode) {
        if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || !replacesNativeHistory()) return false;
        if (HISTORY_WINDOW.interacting()) { finishHistoryInteraction(); return true; }
        if (!turnMenuOpen) return false;
        closeTurnMenu();
        return true;
    }

    private static void closeTurnMenu() {
        turnMenuOpen = false;
    }

    public static void resetScroll() {
        finishHistoryInteraction();
        HISTORY.reset();
        closeTurnMenu();
        turnMenuOffset = 0;
        historyBounds = Bounds.EMPTY;
        turnButtonBounds = Bounds.EMPTY;
        themeButtonBounds = Bounds.EMPTY;
        turnMenuBounds = Bounds.EMPTY;
        cachedLogRevision = -1;
        cachedLogLines = List.of();
        cachedTurns = List.of();
        LOG_LAYOUT.clear();
        TEAM_TOOLTIP.clear();
        TOOLTIP_WRAP.clear();
        PokemonPortraitRenderer.clear();
    }

    public static boolean replacesNativeHistory() {
        return MinecraftClient.getInstance().currentScreen instanceof BattleGUI &&
                BattleUiState.active() && !battleIsMinimised();
    }

    private static boolean battleIsMinimised() {
        var battle = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle();
        return battle != null && battle.getMinimised();
    }

    private static List<DisplayLine> flatten(TextRenderer renderer, List<BattleLogEntry> entries, int width) {
        List<DisplayLine> result = new ArrayList<>();
        for (BattleLogEntry entry : entries) {
            MutableText rendered = Text.empty();
            if (entry.showTimestamp()) {
                String stamp = formatDuration(BattleUiState.elapsedAt(entry.timestamp())) + "  ";
                rendered.append(Text.literal(stamp).styled(style -> style.withColor(
                        BattleUiTheme.night() ? 0xFF91AAB7 : BattleLogTextFormatter.MUTED_COLOR)));
            }
            Text message = BattleUiTheme.historyText(entry.message());
            if (entry.type() == BattleLogEntryType.TURN) {
                int body = 0xFFFFC857;
                rendered.append(Text.literal("━━ ").styled(style -> style.withColor(body)))
                        .append(forceColor(message, body, true))
                        .append(Text.literal(" ━━").styled(style -> style.withColor(body)));
            } else if (entry.type() == BattleLogEntryType.HEADER) {
                rendered.append(message.copy().styled(style -> style.withColor(BattleUiTheme.night() ? 0xFFE6F2F6 : 0xFF15242B).withBold(true)));
            } else {
                rendered.append(message.copy());
            }

            List<OrderedText> wrapped = renderer.wrapLines(rendered, width - 4);
            for (int index = 0; index < wrapped.size(); index++) {
                result.add(new DisplayLine(wrapped.get(index), entry.type(), entry.mention(),
                        index == 0, entry.impact(), entry.turn(), entry.side()));
            }
        }
        return result;
    }

    private static MutableText forceColor(Text source, int color, boolean bold) {
        MutableText result = source.copyContentOnly().setStyle(source.getStyle().withColor(color).withBold(bold));
        for (Text sibling : source.getSiblings()) result.append(forceColor(sibling, color, bold));
        return result;
    }

    private static List<DisplayLine> flattenedLog(TextRenderer renderer, int width) {
        long revision = BattleUiState.logRevision();
        var key = new LogLayoutKey(width, net.minecraft.util.Language.getInstance(), renderer,
                UiResourceEpoch.current(), BattleUiTheme.night());
        if (cachedLogRevision != revision || !LOG_LAYOUT.layoutMatches(key)) {
            var delta = BattleUiState.logChanges(LOG_LAYOUT.epoch(), LOG_LAYOUT.entryCount(), !LOG_LAYOUT.layoutMatches(key));
            LOG_LAYOUT.update(delta.epoch(), key, delta.from(), delta.entries(),
                    entry -> flatten(renderer, List.of(entry), width), DisplayLine::turn);
            cachedLogLines = LOG_LAYOUT.lines();
            cachedTurns = LOG_LAYOUT.turns();
            cachedLogRevision = delta.revision();
        }
        return cachedLogLines;
    }

    private record LogLayoutKey(int width, net.minecraft.util.Language language, TextRenderer renderer,
                                long resources, boolean night) { }
    private record TeamTooltipKey(TeamMemberView member, boolean opponent, boolean noItem,
                                  OpponentKnowledgeView knowledge, SpeedRangeView speed,
                                  BattleStatsView battleStats,
                                  List<PokemonBattleEffects.PokemonEffectView> effects,
                                  net.minecraft.util.Language language, long resources) { }
    private record TooltipWrapKey(List<TooltipRow> rows, int width, net.minecraft.util.Language language, long resources) { }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
    }

    private static int healthColor(float percent) {
        if (percent <= 20.0F) return 0xFFFF5E6C;
        if (percent <= 50.0F) return 0xFFFFC857;
        return 0xFF63D66F;
    }

    private static int statusColor(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "brn" -> 0xFFFF7043;
            case "par" -> 0xFFFFD447;
            case "psn", "tox" -> 0xFFD66BE5;
            case "slp" -> 0xFFAEB8C0;
            case "frz" -> 0xFF6FE4F2;
            default -> 0xFFFFC857;
        };
    }

    private static Text statusName(String status) {
        String id = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "brn", "par", "psn", "tox", "slp", "frz" ->
                    Text.translatable("text.tropimon_ui_battle.status." + id);
            default -> Text.literal(status == null ? "" : status.toUpperCase(Locale.ROOT));
        };
    }

    private static int statusBackground(String status) {
        int tint = statusColor(status);
        int red = (3 * 0x18 + ((tint >> 16) & 0xFF)) / 4;
        int green = (3 * 0x20 + ((tint >> 8) & 0xFF)) / 4;
        int blue = (3 * 0x26 + (tint & 0xFF)) / 4;
        return 0xEE000000 | red << 16 | green << 8 | blue;
    }

    private static boolean tileContains(MinecraftClient client, int x, int y, int width, int height) {
        int mouseX = scaledMouseX(client);
        int mouseY = scaledMouseY(client);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int scaledMouseX(MinecraftClient client) {
        return (int) (client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
    }

    private static int scaledMouseY(MinecraftClient client) {
        return (int) (client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
    }

    private record TooltipRow(Text text, boolean gapBefore) {
    }

    private record TooltipDisplayLine(OrderedText text, boolean gapBefore) {
    }

    private record DisplayLine(OrderedText text, BattleLogEntryType type, boolean highlight,
                               boolean firstOfEntry, BattleLogImpact impact, int turn, BattleLogSide side) {
    }

    record TurnMenuLayout(int x, int y, int width, int height, int visibleRows) { }

    private record Bounds(int x, int y, int width, int height) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
