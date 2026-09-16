package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Public-information matchup preview for the native Cobblemon switch selector. */
public final class SwitchMatchupRenderer {
    private static final int LINE_HEIGHT = 10;
    private static final LastValueCache<SummaryKey, List<Line>> LINES = new LastValueCache<>();
    private static final LastValueCache<WrappedKey, WrappedContent> WRAPPED = new LastValueCache<>();

    private SwitchMatchupRenderer() { }

    public static void render(BattleSwitchPokemonSelection selection, DrawContext context, int mouseX, int mouseY) {
        if (BattleUiRenderer.historyCoversPointer(mouseX, mouseY)) return;
        BattleSwitchPokemonSelection.SwitchTile hovered = selection.getTiles().stream()
                .filter(tile -> tile.isHovered(mouseX, mouseY)).findFirst().orElse(null);
        if (hovered == null || hovered.isFainted() || hovered.getPokemon() == null) return;

        Pokemon pokemon = hovered.getPokemon();
        TeamMemberView candidate = BattleUiState.ownMember(pokemon.getUuid());
        List<BattleUiState.ActiveTargetView> targets = BattleUiState.activeOpponentTargets();
        if (candidate == null || targets.isEmpty()) return;

        SummaryKey key = key(candidate, pokemon, targets);
        List<Line> lines = LINES.get(key, () -> build(candidate, pokemon, targets));
        if (lines.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int margin = 6;
        int width = Math.min(270, Math.max(1, screenWidth - margin * 2));
        int textWidth = Math.max(1, width - 16);
        int maximumHeight = maximumPanelHeight(screenHeight, margin);
        var wrappedKey = new WrappedKey(lines, textWidth, maximumHeight, renderer,
                net.minecraft.util.Language.getInstance(), UiResourceEpoch.current());
        WrappedContent content = WRAPPED.get(wrappedKey,
                () -> boundedContent(renderer, lines, textWidth, maximumHeight));
        List<WrappedLine> wrapped = content.lines();
        int height = content.height();

        float left = selection.getTiles().stream().map(BattleSwitchPokemonSelection.SwitchTile::getX)
                .min(Comparator.naturalOrder()).orElse(hovered.getX());
        float right = selection.getTiles().stream().map(tile -> tile.getX() + BattleSwitchPokemonSelection.SwitchTile.SELECT_WIDTH)
                .max(Comparator.naturalOrder()).orElse(hovered.getX() + BattleSwitchPokemonSelection.SwitchTile.SELECT_WIDTH);
        float top = selection.getTiles().stream().map(BattleSwitchPokemonSelection.SwitchTile::getY)
                .min(Comparator.naturalOrder()).orElse(hovered.getY());
        PanelPosition position = panelPosition(left, right, top, width, height, screenWidth, screenHeight, margin);
        int x = position.x();
        int y = position.y();

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 350);
        try {
            BattleUiSkin.drawDarkPanel(context, x, y, width, height);
            context.fill(x + 4, y + 4, x + width / 2, y + 6, 0xFF55D5DE);
            context.fill(x + width / 2, y + 4, x + width - 4, y + 6, 0xFFFF5E6C);
            int lineY = y + 9;
            for (WrappedLine line : wrapped) {
                if (line.separator()) {
                    context.fill(x + 8, lineY, x + width - 8, lineY + 1, 0x6640515C);
                    lineY += 4;
                }
                context.drawText(renderer, line.text(), x + 8, lineY, line.color(), false);
                lineY += LINE_HEIGHT;
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    static PanelPosition panelPosition(float partyLeft, float partyRight, float partyTop,
                                       int width, int height, int screenWidth, int screenHeight, int margin) {
        int partyCenter = Math.round((partyLeft + partyRight) / 2.0F);
        int maximumX = Math.max(0, screenWidth - width);
        int minimumX = Math.min(Math.max(0, margin), maximumX);
        int preferredMaximumX = Math.max(minimumX, maximumX - Math.max(0, margin));
        int x = Math.clamp(partyCenter - width / 2, minimumX, preferredMaximumX);
        int y = Math.round(partyTop) - height - 22;
        int maximumY = Math.max(0, screenHeight - height);
        int minimumY = Math.min(Math.max(0, margin), maximumY);
        int preferredMaximumY = Math.max(minimumY, maximumY - Math.max(0, margin));
        return new PanelPosition(x, Math.clamp(y, minimumY, preferredMaximumY));
    }

    static int maximumPanelHeight(int screenHeight, int margin) {
        return Math.max(1, screenHeight - Math.max(0, margin) * 2);
    }

    private static List<Line> build(TeamMemberView candidate, Pokemon pokemon,
                                    List<BattleUiState.ActiveTargetView> targets) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.title", candidate.name())
                .copy().styled(style -> style.withBold(true)), 0xFFFFFFFF, false));
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.public"), 0xFF9FB2BC, false));
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.defense")
                .copy().styled(style -> style.withBold(true)), 0xFF55D5DE, true));
        for (BattleUiState.ActiveTargetView target : targets) {
            Threat threat = threat(candidate, target);
            lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.against",
                    target.name(), defenseLabel(threat.multiplier()), Text.translatable(threat.revealedMoves()
                            ? "text.tropimon_ui_battle.switch_matchup.revealed_moves"
                            : "text.tropimon_ui_battle.switch_matchup.known_types")),
                    effectivenessColor(threat.multiplier()), false));
        }

        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.offense")
                .copy().styled(style -> style.withBold(true)), 0xFFFFC857, true));
        int damagingMoves = 0;
        for (Move move : pokemon.getMoveSet()) {
            MoveTemplate template = move.getTemplate();
            if (template == null || template.getDamageCategory().getName().equalsIgnoreCase("status")) continue;
            damagingMoves++;
            List<Text> values = new ArrayList<>();
            double strongest = Double.NaN;
            for (BattleUiState.ActiveTargetView target : targets) {
                BattleMatchup.Result result = BattleMatchupData.analyze(template, pokemon, pokemon.getUuid(), target,
                        BattleMatchupData.field(target));
                values.add(Text.translatable("text.tropimon_ui_battle.switch_matchup.move_target",
                        target.name(), resultLabel(result)));
                if (result.known() && (!Double.isFinite(strongest) || result.multiplier() > strongest)) {
                    strongest = result.multiplier();
                }
            }
            lines.add(new Line(Text.translatable("text.tropimon_ui_battle.switch_matchup.move",
                    emphasizedMoveName(template), join(values)), effectivenessColor(strongest), false));
        }
        if (damagingMoves == 0) lines.add(new Line(
                Text.translatable("text.tropimon_ui_battle.switch_matchup.no_attack"), 0xFF9FB2BC, false));
        return List.copyOf(lines);
    }

    private static Threat threat(TeamMemberView candidate, BattleUiState.ActiveTargetView target) {
        TeamMemberView knownTarget = BattleUiState.member(target.uuid());
        List<MoveTemplate> revealed = knownTarget == null ? List.of() : knownTarget.knownMoves().stream()
                .map(move -> Moves.getByName(move.id()))
                .filter(move -> move != null && !move.getDamageCategory().getName().equalsIgnoreCase("status"))
                .toList();
        BattleMatchup.Pokemon attacker = BattleMatchupData.pokemon(target);
        BattleMatchup.Pokemon defender = BattleMatchupData.pokemon(candidate);
        BattleMatchup.Field field = BattleMatchupData.field(target);
        double maximum = Double.NaN;
        if (!revealed.isEmpty()) {
            for (MoveTemplate move : revealed) {
                BattleMatchup.Result result = BattleMatchup.analyze(new BattleMatchup.Move(move.getName(),
                        move.getElementalType().getName(), move.getDamageCategory().getName(), move.getPriority()),
                        attacker, defender, field);
                if (result.kind() == BattleMatchup.DamageKind.NORMAL && result.known()
                        && (!Double.isFinite(maximum) || result.multiplier() > maximum)) {
                    maximum = result.multiplier();
                }
            }
            if (Double.isFinite(maximum)) return new Threat(maximum, true);
        }
        for (TypeView type : target.types()) {
            BattleMatchup.Result result = BattleMatchup.analyze(new BattleMatchup.Move(
                    "switchpreview", type.id(), "physical", 0), attacker, defender, field);
            if (result.known() && (!Double.isFinite(maximum) || result.multiplier() > maximum)) {
                maximum = result.multiplier();
            }
        }
        return new Threat(maximum, false);
    }

    static Text defenseLabel(double multiplier) {
        if (!Double.isFinite(multiplier)) return Text.translatable("text.tropimon_ui_battle.switch_matchup.unknown");
        String value = MoveTooltipRenderer.formatMultiplier(multiplier);
        String key = multiplier <= 0 ? "immune" : multiplier < 1 ? "resists"
                : multiplier > 1 ? "weak" : "neutral";
        return Text.translatable("text.tropimon_ui_battle.switch_matchup." + key, value);
    }

    private static Text resultLabel(BattleMatchup.Result result) {
        return switch (result.kind()) {
            case FIXED -> Text.translatable("text.tropimon_ui_battle.matchup_fixed");
            case OHKO -> Text.translatable("text.tropimon_ui_battle.matchup_ohko");
            case STATUS -> Text.literal("—");
            case NORMAL -> Text.literal("×" + (result.known()
                    ? MoveTooltipRenderer.formatMultiplier(result.multiplier()) : "?"));
        };
    }

    static Text emphasizedMoveName(MoveTemplate move) {
        return move.getDisplayName().copy().styled(style -> style.withBold(true)
                .withColor(MoveTooltipRenderer.typeColor(move.getElementalType().getName())));
    }

    private static Text join(List<Text> values) {
        var result = Text.empty();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(Text.literal(" · "));
            result.append(values.get(i));
        }
        return result;
    }

    private static int effectivenessColor(double multiplier) {
        if (!Double.isFinite(multiplier)) return 0xFF9AA8AF;
        if (multiplier <= 0) return 0xFF8C969C;
        if (multiplier < 1) return 0xFFFFA45B;
        if (multiplier > 1) return 0xFF63D66F;
        return 0xFFE4EDF2;
    }

    private static List<WrappedLine> wrap(TextRenderer renderer, List<Line> lines, int width) {
        List<WrappedLine> result = new ArrayList<>();
        for (Line line : lines) {
            List<OrderedText> parts = renderer.wrapLines(line.text(), width);
            if (parts.isEmpty()) continue;
            for (int i = 0; i < parts.size(); i++) {
                result.add(new WrappedLine(parts.get(i), line.color(), line.separator() && i == 0));
            }
        }
        return List.copyOf(result);
    }

    private static WrappedContent boundedContent(TextRenderer renderer, List<Line> lines, int width,
                                                 int maximumHeight) {
        List<WrappedLine> all = wrap(renderer, lines, width);
        List<WrappedLine> visible = new ArrayList<>();
        int height = 14;
        for (WrappedLine line : all) {
            int addition = LINE_HEIGHT + (line.separator() ? 4 : 0);
            if (height + addition > maximumHeight) break;
            visible.add(line);
            height += addition;
        }
        if (visible.size() < all.size()) {
            int ellipsisHeight = LINE_HEIGHT;
            while (!visible.isEmpty() && height + ellipsisHeight > maximumHeight) {
                WrappedLine removed = visible.removeLast();
                height -= LINE_HEIGHT + (removed.separator() ? 4 : 0);
            }
            if (height + ellipsisHeight <= maximumHeight) {
                visible.add(new WrappedLine(Text.literal("…").asOrderedText(), 0xFF9FB2BC, false));
                height += ellipsisHeight;
            }
        }
        return new WrappedContent(List.copyOf(visible), Math.min(maximumHeight, Math.max(1, height)));
    }

    private static SummaryKey key(TeamMemberView candidate, Pokemon pokemon,
                                  List<BattleUiState.ActiveTargetView> targets) {
        List<String> moves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) if (move.getTemplate() != null) moves.add(move.getTemplate().getName());
        List<TargetKey> targetKeys = targets.stream()
                .map(target -> targetKey(target, BattleUiState.member(target.uuid()))).toList();
        return new SummaryKey(candidate.uuid(), candidate.name(), candidate.types().stream().map(TypeView::id).toList(),
                BattleMatchupData.abilityId(candidate.ability()), BattleUiState.heldItemId(candidate.heldItem()),
                BattleUiState.teraType(candidate.uuid()), candidate.hpPercent() >= 100.0F, List.copyOf(moves), targetKeys,
                activeAbilityKeys(BattleUiState.ownTeam()),
                BattleFieldEffects.snapshot(BattleUiState.turn()),
                PokemonBattleEffects.snapshot(candidate.uuid(), BattleUiState.turn()).stream()
                        .map(PokemonBattleEffects.PokemonEffectView::id).toList(),
                BattleUiState.turn(), Locale.getDefault(), net.minecraft.util.Language.getInstance(),
                UiResourceEpoch.current());
    }

    static TargetKey targetKey(BattleUiState.ActiveTargetView target, TeamMemberView member) {
        List<String> knownMoves = member == null ? List.of()
                : member.knownMoves().stream().map(MoveView::id).toList();
        return new TargetKey(target.uuid(), target.name(), target.types().stream().map(TypeView::id).toList(),
                BattleMatchupData.abilityId(target.ability()), target.heldItemId(), target.status(),
                BattleUiState.teraType(target.uuid()), target.hpPercent() >= 100.0F, knownMoves,
                PokemonBattleEffects.snapshot(target.uuid(), BattleUiState.turn()).stream()
                        .map(PokemonBattleEffects.PokemonEffectView::id).sorted().toList());
    }

    static List<ActiveAbilityKey> activeAbilityKeys(List<TeamMemberView> members) {
        if (members == null || members.isEmpty()) return List.of();
        return members.stream().filter(member -> member.active() && !member.fainted())
                .map(member -> new ActiveAbilityKey(BattleMatchupData.abilityId(member.ability()),
                        BattleUiState.heldItemId(member.heldItem()),
                        BattleMatchupData.effects(member.uuid(), member.ability()).stream().sorted().toList()))
                .sorted(Comparator.comparing(ActiveAbilityKey::ability)
                        .thenComparing(ActiveAbilityKey::item)
                        .thenComparing(key -> String.join("\u0000", key.effects())))
                .toList();
    }

    private record Line(Text text, int color, boolean separator) { }
    private record WrappedLine(OrderedText text, int color, boolean separator) { }
    private record WrappedKey(List<Line> lines, int width, int maximumHeight, TextRenderer renderer,
                              net.minecraft.util.Language language, long resources) { }
    private record WrappedContent(List<WrappedLine> lines, int height) { }
    private record Threat(double multiplier, boolean revealedMoves) { }
    record PanelPosition(int x, int y) { }
    record TargetKey(java.util.UUID uuid, String name, List<String> types, String ability, String item,
                     String status, String teraType, boolean fullHp, List<String> moves, List<String> effects) { }
    record ActiveAbilityKey(String ability, String item, List<String> effects) { }
    private record SummaryKey(java.util.UUID uuid, String name, List<String> types, String ability, String item,
                              String teraType, boolean fullHp, List<String> moves, List<TargetKey> targets,
                              List<ActiveAbilityKey> activeAbilities,
                              List<BattleFieldEffects.EffectView> fieldEffects, List<String> effects, int turn,
                              Locale locale, net.minecraft.util.Language language, long resourceEpoch) { }
}
