package fr.tropimon.battleui;

import java.util.Locale;

final class PokemonSpeedRange {
    private PokemonSpeedRange() {
    }

    static SpeedRangeView calculate(int baseSpeed, int level, int speedStage, String status,
                                    String ability, String item, PublicEffects effects) {
        if (baseSpeed <= 0 || level <= 0) return SpeedRangeView.unknown();
        int safeLevel = Math.max(1, Math.min(100, level));
        int minimum = nature(stat(baseSpeed, 0, 0, safeLevel), 0.9D);
        int maximum = nature(stat(baseSpeed, 31, 252, safeLevel), 1.1D);

        double modifier = stageMultiplier(speedStage);
        String normalizedStatus = normalize(status);
        String normalizedAbility = normalize(ability);
        String normalizedItem = normalize(item);

        if (normalizedAbility.equals("quickfeet") && !normalizedStatus.isBlank()) {
            modifier *= 1.5D;
        } else if (normalizedStatus.equals("par") || normalizedStatus.contains("paralysis")) {
            modifier *= 0.5D;
        }

        if (normalizedItem.equals("choicescarf")) modifier *= 1.5D;
        if (normalizedItem.equals("ironball") || normalizedItem.equals("machobrace") ||
                normalizedItem.startsWith("power")) modifier *= 0.5D;

        PublicEffects publicEffects = effects == null ? PublicEffects.NONE : effects;
        if (publicEffects.tailwind()) modifier *= 2.0D;
        if ((publicEffects.rain() && normalizedAbility.equals("swiftswim")) ||
                (publicEffects.sun() && normalizedAbility.equals("chlorophyll")) ||
                (publicEffects.sand() && normalizedAbility.equals("sandrush")) ||
                (publicEffects.snow() && normalizedAbility.equals("slushrush")) ||
                (publicEffects.electricTerrain() && normalizedAbility.equals("surgesurfer"))) {
            modifier *= 2.0D;
        }

        int effectiveMinimum = apply(minimum, modifier);
        int effectiveMaximum = apply(maximum, modifier);
        return new SpeedRangeView(minimum, maximum, effectiveMinimum, effectiveMaximum);
    }

    private static int stat(int base, int iv, int ev, int level) {
        return ((2 * base + iv + ev / 4) * level) / 100 + 5;
    }

    private static int nature(int value, double modifier) {
        return Math.max(1, (int) Math.floor(value * modifier));
    }

    private static int apply(int value, double modifier) {
        return Math.max(1, (int) Math.floor(value * modifier));
    }

    static double stageMultiplier(int rawStage) {
        int stage = Math.max(-6, Math.min(6, rawStage));
        return stage >= 0 ? (2.0D + stage) / 2.0D : 2.0D / (2.0D - stage);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    record PublicEffects(boolean rain, boolean sun, boolean sand, boolean snow,
                         boolean electricTerrain, boolean tailwind) {
        private static final PublicEffects NONE = new PublicEffects(false, false, false, false, false, false);
    }
}
