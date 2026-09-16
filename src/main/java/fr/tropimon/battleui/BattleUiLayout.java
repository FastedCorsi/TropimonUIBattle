package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import net.minecraft.util.math.MathHelper;

/** Responsive layout derived from Minecraft's scaled GUI dimensions. */
final class BattleUiLayout {
    private final int screenWidth;
    private final int screenHeight;
    private final int margin;
    private final int teamTop;
    private final int teamTileWidth;
    private final int teamTileHeight;
    private final int teamTileGap;
    private final int teamPortraitSize;
    private final int ownTeamX;
    private final int opponentTeamX;
    private final int ownTeamWidth;
    private final int opponentTeamWidth;
    private final int fieldEffectsY;
    private final int fieldEffectsWidth;
    private final int tooltipWidth;
    private final int tooltipPadding;
    private final int historyWidth;
    private final int historyMaxHeight;

    private BattleUiLayout(int screenWidth, int screenHeight, int margin, int teamTop,
                           int teamTileWidth, int teamTileHeight, int teamTileGap, int teamPortraitSize,
                           int ownTeamX, int opponentTeamX, int ownTeamWidth, int opponentTeamWidth,
                           int fieldEffectsY, int fieldEffectsWidth, int tooltipWidth, int tooltipPadding,
                           int historyWidth, int historyMaxHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.margin = margin;
        this.teamTop = teamTop;
        this.teamTileWidth = teamTileWidth;
        this.teamTileHeight = teamTileHeight;
        this.teamTileGap = teamTileGap;
        this.teamPortraitSize = teamPortraitSize;
        this.ownTeamX = ownTeamX;
        this.opponentTeamX = opponentTeamX;
        this.ownTeamWidth = ownTeamWidth;
        this.opponentTeamWidth = opponentTeamWidth;
        this.fieldEffectsY = fieldEffectsY;
        this.fieldEffectsWidth = fieldEffectsWidth;
        this.tooltipWidth = tooltipWidth;
        this.tooltipPadding = tooltipPadding;
        this.historyWidth = historyWidth;
        this.historyMaxHeight = historyMaxHeight;
    }

    static BattleUiLayout calculate(int rawWidth, int rawHeight, int rawOwnSlots, int rawOpponentSlots) {
        int width = Math.max(160, rawWidth);
        int height = Math.max(90, rawHeight);
        int ownSlots = Math.max(1, rawOwnSlots);
        int opponentSlots = Math.max(1, rawOpponentSlots);
        int margin = MathHelper.clamp(Math.round(Math.min(width, height) * 0.018F), 4, 10);

        int preferredTileWidth = width >= 900 ? 36 : width >= 620 ? 32 : width >= 480 ? 27 : 23;
        if (height < 220) preferredTileWidth -= 2;
        if (height < 150) preferredTileWidth -= 2;
        int gap = preferredTileWidth >= 24 ? 2 : 1;
        int centerGap = MathHelper.clamp(width / 40, 8, 24);
        int gapsWidth = (ownSlots + opponentSlots - 2) * gap;
        int maximumTileWidth = Math.max(16,
                (width - margin * 2 - centerGap - gapsWidth) / (ownSlots + opponentSlots));
        int tileWidth = MathHelper.clamp(Math.min(preferredTileWidth, maximumTileWidth), 16, 36);
        gap = tileWidth >= 24 ? 2 : 1;
        gapsWidth = (ownSlots + opponentSlots - 2) * gap;
        maximumTileWidth = Math.max(16,
                (width - margin * 2 - centerGap - gapsWidth) / (ownSlots + opponentSlots));
        tileWidth = Math.min(tileWidth, maximumTileWidth);

        int tileHeight = TeamIconLayout.heightFor(tileWidth);
        int portraitSize = TeamIconLayout.portraitSizeFor(tileWidth);
        int ownWidth = teamWidth(ownSlots, tileWidth, gap);
        int opponentWidth = teamWidth(opponentSlots, tileWidth, gap);
        int responsiveInset = Math.max(margin,
                (width - ownWidth - opponentWidth - centerGap) / 2);
        // Keep compact team rows clear of Cobblemon's 140 px active card and
        // our 14 px movement handle, with a small visible gutter between them.
        int nativeHudClearance = BattleOverlay.HORIZONTAL_INSET + BattleOverlay.TILE_WIDTH
                + BattlePokemonHudLayout.HANDLE_SIZE + 2;
        int preferredInset = Math.min(nativeHudClearance, Math.max(margin, width / 4));
        int sideInset = Math.min(preferredInset, responsiveInset);
        int ownX = MathHelper.clamp(sideInset, margin, Math.max(margin, width - ownWidth - margin));
        int opponentX = MathHelper.clamp(width - sideInset - opponentWidth, margin,
                Math.max(margin, width - opponentWidth - margin));

        // Rounding at very small widths must never make the two team rows overlap.
        if (ownX + ownWidth + centerGap > opponentX) {
            int sharedGap = Math.max(4, width - ownWidth - opponentWidth);
            ownX = Math.max(0, sharedGap / 2);
            opponentX = Math.min(width - opponentWidth, ownX + ownWidth + Math.max(0, sharedGap - ownX * 2));
        }

        int teamTop = MathHelper.clamp(height / 70, 2, 5);
        int belowTeamsY = Math.min(height - 30, teamTop + tileHeight + 22);
        // Keep global effects on the screen centre, even when the team sizes differ.
        // Only use the top gap when it is wide enough; large GUI scales fall back below the teams.
        int centreRoom = Math.min(width / 2 - (ownX + ownWidth), opponentX - width / 2);
        int topEffectsWidth = Math.max(0, centreRoom * 2 - 8);
        boolean effectsFitAtTop = topEffectsWidth >= 64;
        int fieldY = effectsFitAtTop ? Math.max(18, teamTop) : belowTeamsY;
        int fieldWidth = effectsFitAtTop ? Math.min(270, topEffectsWidth) : Math.max(100, width / 2);

        int availablePanelWidth = Math.max(120, width - margin * 2);
        int tooltipMin = Math.min(190, availablePanelWidth);
        int tooltipMax = Math.min(270, availablePanelWidth);
        int tooltipWidth = MathHelper.clamp(Math.round(width * 0.38F), tooltipMin, tooltipMax);
        int tooltipPadding = tooltipWidth < 210 ? 6 : 8;

        int historyMin = Math.min(220, availablePanelWidth);
        int historyMax = Math.min(360, availablePanelWidth);
        int historyWidth = MathHelper.clamp(Math.round(width * 0.38F), historyMin, historyMax);
        int historyMaxHeight = Math.max(70, Math.min(270, height - margin * 2));

        return new BattleUiLayout(width, height, margin, teamTop, tileWidth, tileHeight, gap,
                portraitSize, ownX, opponentX, ownWidth, opponentWidth, fieldY, fieldWidth,
                tooltipWidth, tooltipPadding, historyWidth, historyMaxHeight);
    }

    private static int teamWidth(int slots, int tileWidth, int gap) {
        return slots * (tileWidth + gap) - gap;
    }

    int screenWidth() { return screenWidth; }
    int screenHeight() { return screenHeight; }
    int margin() { return margin; }
    int teamTop() { return teamTop; }
    int teamTileWidth() { return teamTileWidth; }
    int teamTileHeight() { return teamTileHeight; }
    int teamTileGap() { return teamTileGap; }
    int teamPortraitSize() { return teamPortraitSize; }
    int teamSlotX(int slot, int slots, boolean opponent) {
        int visualSlot = opponent ? slots - 1 - slot : slot;
        return (opponent ? opponentTeamX : ownTeamX) + visualSlot * (teamTileWidth + teamTileGap);
    }
    int ownTeamX() { return ownTeamX; }
    int opponentTeamX() { return opponentTeamX; }
    int ownTeamWidth() { return ownTeamWidth; }
    int opponentTeamWidth() { return opponentTeamWidth; }
    int fieldEffectsY() { return fieldEffectsY; }
    int fieldEffectsWidth() { return fieldEffectsWidth; }
    int sideEffectsY(int pokemonPerSide, int actorsPerSide) {
        // Cobblemon stacks compact active portraits in doubles/multi battles. Keep one
        // shared effect row below the entire stack, not below the party preview.
        boolean compact = pokemonPerSide > 1;
        int tileHeight = compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT;
        int spacing = compact ? BattleOverlay.COMPACT_VERTICAL_SPACING : BattleOverlay.VERTICAL_SPACING;
        return BattleOverlay.VERTICAL_INSET + Math.max(0, pokemonPerSide - 1) * spacing
                + Math.max(0, actorsPerSide - 1) * 10 + tileHeight + 2;
    }

    int sideEffectsInset(int effectTileWidth, boolean compact) {
        int portraitOffset = compact ? BattleOverlay.COMPACT_PORTRAIT_OFFSET_X : BattleOverlay.PORTRAIT_OFFSET_X;
        int portraitDiameter = compact ? BattleOverlay.COMPACT_PORTRAIT_DIAMETER : BattleOverlay.PORTRAIT_DIAMETER;
        int portraitCentre = BattleOverlay.HORIZONTAL_INSET + portraitOffset + (portraitDiameter + 1) / 2;
        return Math.max(margin, portraitCentre - effectTileWidth / 2);
    }

    int sideEffectsWidth(int inset) {
        // Reserve the central global-effect column even at large GUI scales; side
        // effects wrap beneath their own active card instead of crossing the weather.
        int centreLeft = screenWidth / 2 - fieldEffectsWidth / 2;
        return Math.max(1, Math.min(BattleOverlay.TILE_WIDTH, centreLeft - inset - 4));
    }
    int tooltipWidth() { return tooltipWidth; }
    int tooltipPadding() { return tooltipPadding; }
    int historyWidth() { return historyWidth; }
    int historyMaxHeight() { return historyMaxHeight; }
}
