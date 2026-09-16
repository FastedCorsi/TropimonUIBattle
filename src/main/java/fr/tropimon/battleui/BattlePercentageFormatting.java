package fr.tropimon.battleui;

import java.util.Locale;

final class BattlePercentageFormatting {
    private BattlePercentageFormatting() {
    }

    static String format(float value) {
        float safe = Math.max(0.0F, Math.min(100.0F, value));
        if (safe > 0.0F && safe < 0.01F) return "<0.01%";
        if (safe > 0.0F && safe < 1.0F) return decimal(safe, 2) + "%";
        if (safe > 99.9F && safe < 100.0F) return decimal(safe, 2) + "%";
        if (Math.abs(safe - Math.round(safe)) < 0.005F) return Math.round(safe) + "%";
        return String.format(Locale.ROOT, "%.1f%%", safe);
    }

    static String animated(float value, float target) {
        float safe = Math.max(0.0F, Math.min(100.0F, value));
        if (safe > 0.0F && safe < 1.0F) return format(safe);
        float tenth;
        if (target < safe) {
            tenth = (float) Math.floor((safe + 0.00001F) * 10.0F) / 10.0F;
        } else if (target > safe) {
            tenth = (float) Math.ceil((safe - 0.00001F) * 10.0F) / 10.0F;
        } else {
            tenth = Math.round(safe * 10.0F) / 10.0F;
        }
        return String.format(Locale.ROOT, "%.1f%%", tenth);
    }

    static String number(float value) {
        String formatted = format(value);
        return formatted.endsWith("%") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }

    private static String decimal(float value, int places) {
        String formatted = String.format(Locale.ROOT, "%." + places + "f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted.endsWith(".") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }
}
