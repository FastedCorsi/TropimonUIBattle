package fr.tropimon.battleui;

/** Presentation only: never changes effect durations, uncertainty ranges or layers. */
final class BattleEffectPresentation {
    static Metrics metrics(int width, int height) {
        if (width < 420 || height < 200) return new Metrics(30, 34, 18, 4);
        if (width < 620 || height < 280) return new Metrics(36, 39, 22, 4);
        return new Metrics(42, 43, 26, 5);
    }

    static int counterColor(BattleFieldEffects.EffectView effect) {
        // Layer counts are not expiry warnings.
        if (effect.layered() || effect.maximumRemainingTurns() <= 0) {
            return effect.side() == BattleFieldEffects.EffectSide.OPPONENT_FIELD ? 0xFFFF8891 : 0xFF6CE5ED;
        }
        if (effect.maximumRemainingTurns() == 1) return 0xFFFF6E79;
        if (effect.maximumRemainingTurns() == 2) return 0xFFFFCA62;
        return 0xFF6CE5ED;
    }

    static float counterScale(int textWidth, int availableWidth) {
        return Math.min(1.15F, Math.max(1, availableWidth - 4) / (float) Math.max(1, textWidth));
    }

    record Metrics(int tileWidth, int tileHeight, int iconSize, int gap) { }
    private BattleEffectPresentation() { }
}
