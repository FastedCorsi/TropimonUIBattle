package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

public final class MoveTooltipRenderer {
    private static final int LINE_HEIGHT = 11;
    private static final LastValueCache<ContentKey, Content> CONTENT = new LastValueCache<>();
    private static final LastValueCache<MatchupKey, Map<BattleMoveSelection.MoveTile, List<TargetMatchup>>>
            MATCHUPS = new LastValueCache<>();

    private MoveTooltipRenderer() {
    }

    public static void render(BattleMoveSelection selection, DrawContext context, int mouseX, int mouseY) {
        Map<BattleMoveSelection.MoveTile, List<TargetMatchup>> matchups = matchups(selection);
        int dx = BattleActionPanel.offsetX(), dy = BattleActionPanel.offsetY();
        renderEffectivenessBadges(selection, context, matchups);
        if (BattleUiRenderer.historyCoversPointer(mouseX, mouseY)) return;
        BattleMoveSelection.MoveTile hovered = null;
        for (BattleMoveSelection.MoveTile tile : selection.getMoveTiles()) {
            if (tile.isHovered(mouseX - dx, mouseY - dy)) {
                hovered = tile;
                break;
            }
        }
        if (hovered == null) {
            var shift = selection.getShiftButton();
            int controlsX = dx, controlsY = dy;
            if (shift != null && shift.isHovered(mouseX - controlsX, mouseY - controlsY))
                renderShiftTooltip(selection, context, Math.round(shift.getX()) + controlsX,
                        Math.round(shift.getY()) + controlsY);
            return;
        }
        if (hovered.getMoveTemplate() == null) return;

        MoveTemplate move = hovered.getMoveTemplate();
        UUID attacker = selection.getRequest().getActivePokemon().getBattlePokemon() == null ? null
                : selection.getRequest().getActivePokemon().getBattlePokemon().getUuid();
        BattleMoveDynamics.Analysis dynamics = BattleMoveDynamics.analyze(move, hovered.getMove(), attacker);
        MinecraftClient client = MinecraftClient.getInstance();
        BattleUiLayout layout = BattleUiLayout.calculate(client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(), Math.max(6, BattleUiState.ownTeam().size()),
                Math.max(1, BattleUiState.opponentSlotCount()));
        int width = layout.tooltipWidth();
        int padding = layout.tooltipPadding();
        TextRenderer renderer = client.textRenderer;
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(move.getDisplayName().copy().styled(style -> style.withBold(true)), 0xFFFFFFFF));
        lines.add(new Line(Text.literal(move.getElementalType().getDisplayName().getString() + " • " +
                move.getDamageCategory().getDisplayName().getString()), typeColor(move.getElementalType().getName())));

        String power = move.getPower() <= 0 ? "—" : integerOrDecimal(move.getPower());
        String accuracy = dynamics.accuracy().alwaysHits() ? Text.translatable(
                "text.tropimon_ui_battle.accuracy.always").getString()
                : integerOrDecimal(dynamics.accuracy().effective()) + "%";
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.power", power), 0xFFDDE6EA));
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.accuracy", accuracy), 0xFFDDE6EA));
        if (!dynamics.accuracy().alwaysHits() && move.getAccuracy() >= 0 &&
                Math.abs(dynamics.accuracy().effective() - move.getAccuracy()) > 0.05D) {
            lines.add(new Line(Text.translatable("text.tropimon_ui_battle.accuracy_changed",
                    integerOrDecimal(move.getAccuracy()) + "%", accuracy), 0xFFFFC857));
        }
        lines.add(new Line(Text.translatable(dynamics.spread()
                ? "text.tropimon_ui_battle.move_target_spread"
                : "text.tropimon_ui_battle.move_target", dynamics.target()), 0xFF9FB2BC));
        if (move.getPriority() != 0) {
            lines.add(new Line(Text.translatable("text.tropimon_ui_battle.priority", move.getPriority()), 0xFFFFC857));
        }
        if (!dynamics.restriction().getString().isBlank()) {
            lines.add(new Line(dynamics.restriction().copy().styled(style -> style.withBold(true)), 0xFFFF6B72));
        }
        java.util.LinkedHashSet<String> warningKeys = new java.util.LinkedHashSet<>();
        for (BattleUiState.ActiveTargetView target : BattleUiState.activeOpponentTargets()) {
            for (Text warning : BattleMoveWarnings.forTarget(move, attacker, target)) {
                if (warningKeys.add(warning.getString())) lines.add(new Line(warning.copy().styled(
                        style -> style.withBold(true)), 0xFFFFC857));
            }
        }

        if (!move.getDamageCategory().getName().equalsIgnoreCase("status")) {
            List<TargetMatchup> targets = matchups.getOrDefault(hovered, List.of());
            boolean unknownItem = false;
            boolean identifyTarget = targets.size() > 1;
            for (TargetMatchup targetMatchup : targets) {
                BattleUiState.ActiveTargetView target = targetMatchup.target();
                BattleMatchup.Result matchup = targetMatchup.result();
                double effectiveness = matchup.multiplier();
                lines.add(new Line(matchupSummary(target.name(), matchupLabel(matchup),
                        targetMatchup.allied(), identifyTarget), effectivenessColor(effectiveness)));
                BattleMoveDynamics.Accuracy targetAccuracy = BattleMoveDynamics.accuracyAgainst(move, attacker, target);
                String targetAccuracyText = targetAccuracy.alwaysHits()
                        ? Text.translatable("text.tropimon_ui_battle.accuracy.always").getString()
                        : integerOrDecimal(targetAccuracy.effective()) + "%";
                if (targets.size() > 1 && (targetAccuracy.alwaysHits() != dynamics.accuracy().alwaysHits()
                        || Math.abs(targetAccuracy.effective() - dynamics.accuracy().effective()) > 0.05D)) {
                    lines.add(new Line(Text.translatable("text.tropimon_ui_battle.accuracy_against",
                            target.name(), targetAccuracyText), 0xFFFFC857));
                }
                if (!matchup.type().isBlank() && !matchup.type().equalsIgnoreCase(move.getElementalType().getName()) &&
                        !matchup.type().equals("typeless")) {
                    lines.add(new Line(Text.translatable(identifyTarget
                                    ? "text.tropimon_ui_battle.effective_type_against"
                                    : "text.tropimon_ui_battle.effective_type",
                            identifyTarget ? new Object[]{target.name(), typeName(matchup.type())}
                                    : new Object[]{typeName(matchup.type())}), typeColor(matchup.type())));
                }
                if (matchup.blocked()) {
                    lines.add(new Line(blockReason(matchup), 0xFFFFC857));
                    continue;
                }
                if (!matchup.known()) continue;
                Optional<TropimonDamageCalcBridge.DamageEstimate> targetEstimate =
                        TropimonDamageCalcBridge.analyze(move, target, attacker);
                if (targetEstimate.isPresent()) {
                    TropimonDamageCalcBridge.DamageEstimate value = targetEstimate.get();
                    if (!consistentEstimate(matchup, value)) {
                        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.damage_context_unavailable"), 0xFF9AA8AF));
                        continue;
                    }
                    unknownItem |= value.unknownItemAssumption();
                    if (value.effectivePower() > 0 && value.effectivePower() != Math.round(move.getPower())) {
                        lines.add(new Line(Text.translatable(identifyTarget
                                        ? "text.tropimon_ui_battle.effective_power_against"
                                        : "text.tropimon_ui_battle.effective_power",
                                identifyTarget ? new Object[]{target.name(), value.effectivePower()}
                                        : new Object[]{value.effectivePower()}), 0xFFFFC857));
                    }
                    String prefix = value.approximate() ? "≈ " : "";
                    String range = prefix + damagePercent(value.minPercent()) + "–" +
                            damagePercent(value.maxPercent()) + "%";
                    lines.add(new Line(damageSummary(target.name(), range, identifyTarget)
                                    .copy().styled(style -> style.withBold(true)),
                            value.maxPercent() >= 100.0D ? 0xFFFF6B72 : 0xFF6FE4F2));
                    if (!identifyTarget && value.estimatedProfile() != null) {
                        lines.add(new Line(estimatedProfileSummary(value.estimatedProfile()), 0xFF9FB2BC));
                    }
                    if (!value.koChance().isBlank()) {
                        lines.add(new Line(koSummary(target.name(), value.koChance(), identifyTarget), 0xFFFFC857));
                    }
                }
            }
            if (unknownItem) {
                lines.add(new Line(Text.translatable("text.tropimon_ui_battle.damage_item_assumption_none"),
                        0xFFFFC857));
            }
        }

        var key = new ContentKey(List.copyOf(lines), move.getDescription().copy(), width, padding,
                layout.screenHeight(), layout.margin(), net.minecraft.util.Language.getInstance(), UiResourceEpoch.current());
        Content content = CONTENT.get(key, () -> wrapContent(renderer, key.lines(), key.description(), key.width(),
                key.padding(), key.screenHeight(), key.margin()));
        List<WrappedLine> wrappedLines = content.lines();
        List<OrderedText> description = content.description();
        int height = content.height();
        float rightEdge = Float.NEGATIVE_INFINITY;
        float topEdge = Float.POSITIVE_INFINITY;
        for (BattleMoveSelection.MoveTile tile : selection.getMoveTiles()) {
            rightEdge = Math.max(rightEdge, tile.getX() + BattleMoveSelection.MOVE_WIDTH
                    + BattleActionPanel.moveTileOffsetX(tile.getX()));
            topEdge = Math.min(topEdge, tile.getY() + BattleActionPanel.moveTilesOffsetY());
        }
        int hoveredX = Math.round(hovered.getX()) + BattleActionPanel.moveTileOffsetX(hovered.getX());
        int x = Math.round(rightEdge) + 10;
        int y = Math.round(topEdge);
        int screenWidth = layout.screenWidth();
        int screenHeight = layout.screenHeight();
        int margin = layout.margin();
        if (x + width > screenWidth - margin) {
            x = Math.max(margin, hoveredX - width - 10);
        }
        if (y + height > screenHeight - margin) y = screenHeight - height - margin;
        x = Math.max(margin, x);
        y = Math.max(margin, y);

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 350.0F);
        try {
            BattleUiSkin.drawDarkPanel(context, x, y, width, height);
            context.fill(x + 4, y + 4, x + width / 2, y + 6, 0xFF55D5DE);
            context.fill(x + width / 2, y + 4, x + width - 4, y + 6, 0xFFFF5E6C);

            int lineY = y + padding;
            for (WrappedLine line : wrappedLines) {
                context.drawTextWithShadow(renderer, line.text(), x + padding, lineY, line.color());
                lineY += LINE_HEIGHT;
            }
            if (!description.isEmpty()) {
                context.fill(x + padding, lineY + 1, x + width - padding, lineY + 2, 0x6640515C);
                lineY += 5;
                for (OrderedText line : description) {
                    context.drawText(renderer, line, x + padding, lineY, 0xFFB8C5CC, false);
                    lineY += 10;
                }
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void renderShiftTooltip(BattleMoveSelection selection, DrawContext context, int anchorX, int anchorY) {
        MinecraftClient client = MinecraftClient.getInstance();
        BattleUiLayout layout = BattleUiLayout.calculate(client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(), Math.max(6, BattleUiState.ownTeam().size()),
                Math.max(1, BattleUiState.opponentSlotCount()));
        String position = BattleUiState.positionLabel(selection.getRequest().getActivePokemon().getBattlePokemon() == null
                ? null : selection.getRequest().getActivePokemon().getBattlePokemon().getUuid());
        List<Line> lines = List.of(
                new Line(Text.translatable("text.tropimon_ui_battle.shift_native").copy()
                        .styled(style -> style.withBold(true)), 0xFF55D5DE),
                new Line(Text.translatable("text.tropimon_ui_battle.shift_position", position, "C"), 0xFFDDE6EA));
        int width = Math.min(220, layout.tooltipWidth());
        Content content = wrapContent(client.textRenderer, lines, Text.empty(), width, layout.tooltipPadding(),
                layout.screenHeight(), layout.margin());
        int x = Math.max(layout.margin(), Math.min(layout.screenWidth() - width - layout.margin(), anchorX));
        int y = Math.max(layout.margin(), anchorY - content.height() - 6);
        drawContent(context, client.textRenderer, content, x, y, width, layout.tooltipPadding());
    }

    public static void renderTarget(BattleTargetSelection selection, DrawContext context, int mouseX, int mouseY) {
        if (BattleUiRenderer.historyCoversPointer(mouseX, mouseY)) return;
        BattleTargetSelection.TargetTile hovered = null;
        for (var tile : selection.getTargetTiles()) if (tile.isHovered(mouseX, mouseY)) { hovered = tile; break; }
        if (hovered == null || hovered.getTarget() == null || hovered.getTarget().getBattlePokemon() == null) return;
        MoveTemplate move = hovered.getMoveTemplate();
        if (move == null) move = com.cobblemon.mod.common.api.moves.Moves.getByName(selection.getMove().getMove());
        if (move == null) move = com.cobblemon.mod.common.api.moves.Moves.getByName(selection.getMove().getId());
        if (move == null) return;
        var target = BattleUiState.activeTargetView(hovered.getTarget());
        if (target == null) return;
        var nativeAttacker = selection.getRequest().getActivePokemon();
        UUID attacker = nativeAttacker.getBattlePokemon() == null ? null : nativeAttacker.getBattlePokemon().getUuid();
        boolean allied = nativeAttacker.isAllied(hovered.getTarget());
        List<BattleMatchupData.BattleMultiTargetView> allActive = BattleMultiTargeting.all(nativeAttacker).stream()
                .map(active -> new BattleMatchupData.BattleMultiTargetView(BattleUiState.activeTargetView(active),
                        nativeAttacker.isAllied(active))).filter(value -> value.view() != null).toList();
        var field = BattleMatchupData.liveField(target, allActive, allied);
        var matchup = BattleMatchupData.analyze(move, null, attacker, target, field);
        var accuracy = BattleMoveDynamics.accuracyAgainst(move, attacker, target);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(move.getDisplayName().copy().styled(style -> style.withBold(true)), 0xFFFFFFFF));
        lines.add(new Line(matchupSummary(target.name(), matchupLabel(matchup), allied, false),
                effectivenessColor(matchup.multiplier())));
        lines.add(new Line(Text.translatable("text.tropimon_ui_battle.accuracy",
                accuracy.alwaysHits() ? Text.translatable("text.tropimon_ui_battle.accuracy.always").getString()
                        : integerOrDecimal(accuracy.effective()) + "%"), 0xFFDDE6EA));
        for (Text warning : BattleMoveWarnings.forTarget(move, attacker, target))
            lines.add(new Line(warning.copy().styled(style -> style.withBold(true)), 0xFFFFC857));
        if (matchup.blocked()) lines.add(new Line(blockReason(matchup), 0xFFFFC857));
        if (!move.getDamageCategory().getName().equalsIgnoreCase("status") && !matchup.blocked()) {
            Optional<TropimonDamageCalcBridge.DamageEstimate> estimate =
                    TropimonDamageCalcBridge.analyze(move, target, attacker);
            if (estimate.isPresent()) {
                TropimonDamageCalcBridge.DamageEstimate value = estimate.get();
                if (!consistentEstimate(matchup, value)) {
                    lines.add(new Line(Text.translatable("text.tropimon_ui_battle.damage_context_unavailable"),
                            0xFF9AA8AF));
                } else {
                String range = (value.approximate() ? "≈ " : "") + damagePercent(value.minPercent()) + "–"
                        + damagePercent(value.maxPercent()) + "%";
                lines.add(new Line(damageSummary(target.name(), range, false).copy().styled(style -> style.withBold(true)),
                        value.maxPercent() >= 100 ? 0xFFFF6B72 : 0xFF6FE4F2));
                if (value.estimatedProfile() != null)
                    lines.add(new Line(estimatedProfileSummary(value.estimatedProfile()), 0xFF9FB2BC));
                if (!value.koChance().isBlank()) lines.add(new Line(
                        koSummary(target.name(), value.koChance(), false), 0xFFFFC857));
                }
            }
        }
        TeamMemberView member = BattleUiState.member(target.uuid());
        if (member != null && !member.knownMoves().isEmpty()) lines.add(new Line(Text.translatable(
                "text.tropimon_ui_battle.target_known_moves", member.knownMoves().size()), 0xFF9FB2BC));

        MinecraftClient client = MinecraftClient.getInstance();
        BattleUiLayout layout = BattleUiLayout.calculate(client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(), Math.max(6, BattleUiState.ownTeam().size()),
                Math.max(1, BattleUiState.opponentSlotCount()));
        int width = layout.tooltipWidth(), padding = layout.tooltipPadding();
        TextRenderer renderer = client.textRenderer;
        Content content = wrapContent(renderer, lines, move.getDescription(), width, padding,
                layout.screenHeight(), layout.margin());
        int x = Math.round(hovered.getX() + BattleTargetSelection.TARGET_WIDTH + 8);
        int y = Math.round(hovered.getY());
        if (x + width > layout.screenWidth() - layout.margin()) x = Math.max(layout.margin(),
                Math.round(hovered.getX()) - width - 8);
        if (y + content.height() > layout.screenHeight() - layout.margin())
            y = layout.screenHeight() - content.height() - layout.margin();
        drawContent(context, renderer, content, Math.max(layout.margin(), x), Math.max(layout.margin(), y), width, padding);
    }

    private static void drawContent(DrawContext context, TextRenderer renderer, Content content,
                                    int x, int y, int width, int padding) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 350.0F);
        try {
            BattleUiSkin.drawDarkPanel(context, x, y, width, content.height());
            context.fill(x + 4, y + 4, x + width / 2, y + 6, 0xFF55D5DE);
            context.fill(x + width / 2, y + 4, x + width - 4, y + 6, 0xFFFF5E6C);
            int lineY = y + padding;
            for (WrappedLine line : content.lines()) {
                context.drawTextWithShadow(renderer, line.text(), x + padding, lineY, line.color());
                lineY += LINE_HEIGHT;
            }
            if (!content.description().isEmpty()) {
                context.fill(x + padding, lineY + 1, x + width - padding, lineY + 2, 0x6640515C);
                lineY += 5;
                for (OrderedText line : content.description()) {
                    context.drawText(renderer, line, x + padding, lineY, 0xFFB8C5CC, false);
                    lineY += 10;
                }
            }
        } finally { context.getMatrices().pop(); }
    }

    private static Content wrapContent(TextRenderer renderer, List<Line> lines, Text descriptionText,
                                       int width, int padding, int screenHeight, int margin) {
        List<WrappedLine> wrappedLines = new ArrayList<>();
        for (Line line : lines) {
            for (OrderedText wrapped : renderer.wrapLines(line.text(), width - padding * 2)) {
                wrappedLines.add(new WrappedLine(wrapped, line.color()));
            }
        }
        List<OrderedText> description = new ArrayList<>(
                renderer.wrapLines(descriptionText, width - padding * 2));
        int maxHeight = Math.max(60, screenHeight - margin * 2);
        int height = padding * 2 + wrappedLines.size() * LINE_HEIGHT +
                (description.isEmpty() ? 0 : 5 + description.size() * 10);
        boolean descriptionClipped = false;
        while (height > maxHeight && !description.isEmpty()) {
            description.removeLast();
            descriptionClipped = true;
            height = padding * 2 + wrappedLines.size() * LINE_HEIGHT +
                    (description.isEmpty() ? 0 : 5 + description.size() * 10);
        }
        if (descriptionClipped && !description.isEmpty()) {
            description.set(description.size() - 1, Text.literal("…").asOrderedText());
        }
        boolean detailsClipped = false;
        while (height > maxHeight && !wrappedLines.isEmpty()) {
            wrappedLines.removeLast();
            detailsClipped = true;
            height = padding * 2 + wrappedLines.size() * LINE_HEIGHT;
        }
        if (detailsClipped && !wrappedLines.isEmpty()) {
            wrappedLines.set(wrappedLines.size() - 1, new WrappedLine(Text.literal("…").asOrderedText(), 0xFF9AA8AF));
        }
        return new Content(List.copyOf(wrappedLines), List.copyOf(description), height);
    }
    private record ContentKey(List<Line> lines, Text description, int width, int padding,
                              int screenHeight, int margin, net.minecraft.util.Language language, long resources) { }
    private record Content(List<WrappedLine> lines, List<OrderedText> description, int height) { }

    private static Map<BattleMoveSelection.MoveTile, List<TargetMatchup>> matchups(BattleMoveSelection selection) {
        List<BattleUiState.ActiveTargetView> opponents = BattleUiState.activeOpponentTargets();
        var nativeAttacker = selection.getRequest().getActivePokemon();
        List<BattleMatchupData.BattleMultiTargetView> allActive = BattleMultiTargeting.all(nativeAttacker).stream()
                .map(active -> new BattleMatchupData.BattleMultiTargetView(BattleUiState.activeTargetView(active),
                        nativeAttacker.isAllied(active)))
                .filter(value -> value.view() != null).toList();
        UUID attacker = nativeAttacker.getBattlePokemon() == null ? null
                : nativeAttacker.getBattlePokemon().getUuid();
        Map<BattleMoveSelection.MoveTile,
                List<com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon>> legalTargets = new LinkedHashMap<>();
        List<TileMatchupSignature> tiles = new ArrayList<>();
        for (var tile : selection.getMoveTiles()) {
            MoveTemplate move = tile.getMoveTemplate();
            if (move == null || move.getDamageCategory().getName().equalsIgnoreCase("status")) continue;
            List<com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon> legal =
                    BattleMultiTargeting.live(tile.getTargetList());
            if (legal.isEmpty()) legal = BattleMultiTargeting.affected(nativeAttacker, move.getTarget());
            legalTargets.put(tile, legal);
            String form = tile.getPokemon() == null || tile.getPokemon().getForm() == null ? ""
                    : tile.getPokemon().getForm().getName();
            List<String> aspects = tile.getPokemon() == null ? List.of()
                    : tile.getPokemon().getAspects().stream().sorted().toList();
            tiles.add(new TileMatchupSignature(tile, move.getName(), form, aspects,
                    legal.stream().map(active -> active.getBattlePokemon().getUuid()).toList()));
        }
        List<CombatantMatchupSignature> combatants = new ArrayList<>();
        for (var value : allActive) combatants.add(combatantSignature(value.view(), value.allied()));
        for (var value : opponents) combatants.add(combatantSignature(value, false));
        for (var value : BattleUiState.activeOwnMembers()) combatants.add(combatantSignature(value));
        MatchupKey key = new MatchupKey(selection, attacker, List.copyOf(tiles), List.copyOf(combatants),
                BattleFieldEffects.snapshot(BattleUiState.turn()), BattleUiState.turn(), UiResourceEpoch.current());
        return MATCHUPS.get(key, () -> calculateMatchups(selection, opponents, nativeAttacker, allActive,
                attacker, legalTargets));
    }

    private static Map<BattleMoveSelection.MoveTile, List<TargetMatchup>> calculateMatchups(
            BattleMoveSelection selection, List<BattleUiState.ActiveTargetView> opponents,
            com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon nativeAttacker,
            List<BattleMatchupData.BattleMultiTargetView> allActive, UUID attacker,
            Map<BattleMoveSelection.MoveTile,
                    List<com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon>> legalTargets) {
        Map<BattleMoveSelection.MoveTile, List<TargetMatchup>> result = new LinkedHashMap<>();
        for (var tile : selection.getMoveTiles()) {
            MoveTemplate move = tile.getMoveTemplate();
            if (move == null || move.getDamageCategory().getName().equalsIgnoreCase("status")) continue;
            List<TargetMatchup> values = new ArrayList<>();
            List<com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon> legal =
                    legalTargets.getOrDefault(tile, List.of());
            if (!legal.isEmpty()) {
                for (var active : legal) {
                    var target = BattleUiState.activeTargetView(active);
                    if (target == null) continue;
                    boolean allied = nativeAttacker.isAllied(active);
                    var field = BattleMatchupData.liveField(target, allActive, allied);
                    values.add(new TargetMatchup(target, BattleMatchupData.analyze(move, tile.getPokemon(), attacker,
                            target, field), allied));
                }
            } else {
                // Singly-selected moves do not expose the chosen target until click time.
                // Cobblemon's fixed target list is used whenever it exists; this fallback
                // keeps the ordinary singles preview useful.
                Map<UUID, BattleMatchup.Field> fields = BattleMatchupData.liveFields(opponents);
                for (var target : opponents) values.add(new TargetMatchup(target,
                        BattleMatchupData.analyze(move, tile.getPokemon(), attacker, target, fields.get(target.uuid())), false));
            }
            result.put(tile, List.copyOf(values));
        }
        return Map.copyOf(result);
    }

    private static CombatantMatchupSignature combatantSignature(BattleUiState.ActiveTargetView value,
                                                                 boolean allied) {
        return new CombatantMatchupSignature(value.uuid(), allied,
                value.types().stream().map(TypeView::id).toList(), BattleMatchupData.abilityId(value.ability()),
                value.heldItemId(), value.status(), value.statStages(), value.hpPercent() >= 100.0F,
                BattleUiState.teraType(value.uuid()), PokemonBattleEffects.snapshot(value.uuid(), BattleUiState.turn()), "");
    }

    private static CombatantMatchupSignature combatantSignature(TeamMemberView value) {
        String form = value.portrait() == null || value.portrait().getForm() == null ? ""
                : value.portrait().getForm().getName();
        return new CombatantMatchupSignature(value.uuid(), true,
                value.types().stream().map(TypeView::id).toList(), BattleMatchupData.abilityId(value.ability()),
                BattleUiState.heldItemId(value.heldItem()), value.status(), value.statStages(),
                value.hpPercent() >= 100.0F, BattleUiState.teraType(value.uuid()),
                PokemonBattleEffects.snapshot(value.uuid(), BattleUiState.turn()), form);
    }

    private static void renderEffectivenessBadges(BattleMoveSelection selection, DrawContext context,
                                                  Map<BattleMoveSelection.MoveTile, List<TargetMatchup>> matchups) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        for (BattleMoveSelection.MoveTile tile : selection.getMoveTiles()) {
            List<TargetMatchup> values = matchups.getOrDefault(tile, List.of());
            if (values.isEmpty()) continue;
            var labels = values.stream().map(value -> matchupLabel(value.result()).getString()).toList();
            String label = labels.stream().distinct().count() == 1 ? labels.getFirst() : String.join("/", labels);
            var box = MoveBadgeLayout.inside(tile.getX(), tile.getY());
            var badge = ppStyledLabel(label);
            // Keep PP-size text, including in doubles. Long summaries are detailed per target on hover.
            if (renderer.getWidth(badge) > box.width() - 2) badge = ppStyledLabel("×…");
            double multiplier = values.getFirst().result().multiplier();
            boolean same = values.stream().allMatch(value -> Double.compare(value.result().multiplier(), multiplier) == 0);
            int color = same ? effectivenessColor(multiplier) : 0xFFE4EDF2;
            float alpha = selection.getOpacity() * (tile.getSelectable() && tile.getMove().getPp() > 0 ? 1.0F : 0.45F);
            if (alpha <= 0.01F) continue;
            context.getMatrices().push();
            try {
                context.getMatrices().translate(BattleActionPanel.moveTileOffsetX(tile.getX()),
                        BattleActionPanel.moveTilesOffsetY(), 4);
                BattleUiSkin.drawMovePpBackground(context, box.x(), box.y(), box.width(), box.height(), alpha);
                // Exact native PP typography: DEFAULT_LARGE (uniform), bold, scale 1, y+14,
                // centred without shadow. Use Cobblemon's renderer so GUI/font settings stay in sync.
                com.cobblemon.mod.common.client.render.RenderHelperKt.drawScaledText(context,
                        com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE(), badge,
                        tile.getX() + 32F, tile.getY() + 14F, 1F, alpha, Integer.MAX_VALUE,
                        withAlpha(color, alpha), true, false, null, null);
            } finally {
                context.getMatrices().pop();
            }
        }
    }

    static net.minecraft.text.MutableText ppStyledLabel(String label) {
        return Text.literal(label).styled(style -> style.withBold(true).withFont(
                com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.max(4, Math.min(255, Math.round((color >>> 24) * alpha))) << 24) | color & 0xFFFFFF;
    }

    static boolean consistentEstimate(BattleMatchup.Result matchup, TropimonDamageCalcBridge.DamageEstimate estimate) {
        if (!matchup.known() || matchup.blocked() || !Double.isFinite(estimate.minPercent()) ||
                !Double.isFinite(estimate.maxPercent()) || estimate.minPercent() < 0 || estimate.maxPercent() < estimate.minPercent()) return false;
        if (!BattleMatchup.id(estimate.effectiveType()).equals(BattleMatchup.id(matchup.type()))) return false;
        return matchup.kind() != BattleMatchup.DamageKind.NORMAL || Math.abs(estimate.effectiveness() - matchup.multiplier()) < 0.00001D;
    }

    static Text matchupSummary(String target, Text matchup, boolean allied, boolean identifyTarget) {
        if (identifyTarget) return Text.translatable(allied ? "text.tropimon_ui_battle.matchup_against_ally"
                : "text.tropimon_ui_battle.matchup_against", target, matchup);
        return Text.translatable(allied ? "text.tropimon_ui_battle.matchup_ally"
                : "text.tropimon_ui_battle.matchup", matchup);
    }

    static Text damageSummary(String target, String range, boolean identifyTarget) {
        return identifyTarget ? Text.translatable("text.tropimon_ui_battle.estimated_damage_against", target, range)
                : Text.translatable("text.tropimon_ui_battle.estimated_damage", range);
    }

    static Text koSummary(String target, String chance, boolean identifyTarget) {
        return identifyTarget ? Text.translatable("text.tropimon_ui_battle.ko_chance_against", target, chance)
                : Text.translatable("text.tropimon_ui_battle.ko_chance", chance);
    }

    static Text estimatedProfileSummary(TropimonDamageCalcBridge.EstimatedProfile profile) {
        String statKey = profile.defenseStat() == Stat.DEF ? "def" : "spd";
        return Text.translatable("text.tropimon_ui_battle.damage_profile",
                Text.translatable("cobblemon.nature." + profile.natureId()), profile.hpEv(),
                profile.defenseEv(), Text.translatable("text.tropimon_ui_battle.stat_short." + statKey));
    }

    private static Text matchupLabel(BattleMatchup.Result result) {
        if (!result.known()) return Text.literal("×?");
        if (result.blocked()) return Text.literal("×0");
        return switch (result.kind()) {
            case FIXED -> Text.translatable("text.tropimon_ui_battle.matchup_fixed");
            case OHKO -> Text.translatable("text.tropimon_ui_battle.matchup_ohko");
            default -> Text.literal("×" + formatMultiplier(result.multiplier()));
        };
    }

    private static Text blockReason(BattleMatchup.Result result) {
        Text source = Text.literal(result.source());
        if (result.reason().equals("ability")) {
            var ability = com.cobblemon.mod.common.api.abilities.Abilities.get(result.source());
            if (ability != null) source = Text.translatable(ability.getDisplayName());
        } else if (result.reason().equals("weather") || result.reason().equals("terrain")) {
            source = Text.translatable("text.tropimon_ui_battle.effect." + result.source());
        } else if (result.reason().equals("type") && !result.source().isBlank()) source = typeName(result.source());
        else if (result.reason().equals("item")) source = Text.translatable("item.cobblemon.air_balloon");
        if (result.reason().equals("type") || result.reason().equals("effect")) {
            return Text.translatable("text.tropimon_ui_battle.matchup_blocked_type");
        }
        return Text.translatable("text.tropimon_ui_battle.matchup_blocked_by", source);
    }

    static String integerOrDecimal(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    static String damagePercent(double value) {
        if (value > 0 && value < 0.01) return "<0.01";
        if (value > 0 && value < 1) return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return integerOrDecimal(value);
    }

    static int typeColor(String type) {
        int dark = BattleLogTextFormatter.typeColor(type);
        int red = Math.min(255, ((dark >> 16) & 0xFF) + 48);
        int green = Math.min(255, ((dark >> 8) & 0xFF) + 48);
        int blue = Math.min(255, (dark & 0xFF) + 48);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static Text typeName(String type) {
        var elemental = ElementalTypes.get(type);
        return elemental == null ? Text.literal(type) : elemental.getDisplayName();
    }

    static String formatMultiplier(double multiplier) {
        return Double.isFinite(multiplier) ? java.math.BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString() : "?";
    }

    private static int effectivenessColor(double multiplier) {
        if (!Double.isFinite(multiplier)) return 0xFF9AA8AF;
        if (multiplier <= 0.0D) return 0xFF8C969C;
        if (multiplier <= 0.25D) return 0xFFFF765B;
        if (multiplier < 1.0D) return 0xFFFFA45B;
        if (multiplier >= 4.0D) return 0xFF8AF59A;
        if (multiplier > 1.0D) return 0xFF63D66F;
        return 0xFFDDE6EA;
    }

    private record Line(Text text, int color) {
    }

    private record WrappedLine(OrderedText text, int color) {
    }

    private record TileMatchupSignature(BattleMoveSelection.MoveTile tile, String move, String form,
                                        List<String> aspects, List<UUID> legalTargets) { }
    private record CombatantMatchupSignature(UUID uuid, boolean allied, List<String> types, String ability,
                                             String item, String status, List<StatStageView> statStages,
                                             boolean fullHealth, String teraType,
                                             List<PokemonBattleEffects.PokemonEffectView> effects, String form) { }
    private record MatchupKey(BattleMoveSelection selection, UUID attacker, List<TileMatchupSignature> tiles,
                              List<CombatantMatchupSignature> combatants,
                              List<BattleFieldEffects.EffectView> fieldEffects, int turn,
                              long resourceEpoch) { }
    private record TargetMatchup(BattleUiState.ActiveTargetView target, BattleMatchup.Result result,
                                 boolean allied) { }
}
