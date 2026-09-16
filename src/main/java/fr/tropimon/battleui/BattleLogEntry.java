package fr.tropimon.battleui;

import net.minecraft.text.Text;

import java.time.Instant;

public record BattleLogEntry(
        Instant timestamp,
        Text message,
        String translationKey,
        BattleLogEntryType type,
        boolean mention,
        boolean showTimestamp,
        BattleLogImpact impact,
        int turn,
        BattleLogSide side
) {
    public BattleLogEntry(Instant timestamp, Text message, String translationKey,
                          BattleLogEntryType type, boolean mention, boolean showTimestamp) {
        this(timestamp, message, translationKey, type, mention, showTimestamp, BattleLogImpact.NONE, 0, BattleLogSide.NEUTRAL);
    }

    public BattleLogEntry(Instant timestamp, Text message, String translationKey,
                          BattleLogEntryType type, boolean mention, boolean showTimestamp, BattleLogImpact impact) {
        this(timestamp, message, translationKey, type, mention, showTimestamp, impact, 0, BattleLogSide.NEUTRAL);
    }

    public BattleLogEntry(Instant timestamp, Text message, String translationKey,
                          BattleLogEntryType type, boolean mention, boolean showTimestamp, BattleLogImpact impact, int turn) {
        this(timestamp, message, translationKey, type, mention, showTimestamp, impact, turn, BattleLogSide.NEUTRAL);
    }

    BattleLogEntry onSide(BattleLogSide value) {
        return new BattleLogEntry(timestamp, message, translationKey, type, mention, showTimestamp, impact, turn, value);
    }

    BattleLogEntry atTurn(int value) {
        return new BattleLogEntry(timestamp, message, translationKey, type, mention, showTimestamp,
                impact, Math.max(0, value), side);
    }
}
