package fr.tropimon.battleui;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BattleLogEntryTest {
    @Test
    void recordsTurnWithoutRewritingTimestampMessageOrDamageMetadata() {
        var original = new BattleLogEntry(Instant.EPOCH, Text.literal("dégâts"), "health",
                BattleLogEntryType.DAMAGE, true, true, BattleLogImpact.DAMAGE_DEALT).onSide(BattleLogSide.OPPONENT);
        var stamped = original.atTurn(17);
        assertEquals(17, stamped.turn());
        assertEquals(original.timestamp(), stamped.timestamp());
        assertSame(original.message(), stamped.message());
        assertEquals(original.impact(), stamped.impact());
        assertEquals(BattleLogSide.OPPONENT, stamped.side());
        assertEquals(original.translationKey(), stamped.translationKey());
        assertTrue(stamped.mention());
        assertTrue(stamped.showTimestamp());
        assertEquals(0, original.turn());
        assertEquals(0, original.atTurn(-1).turn());
    }
}
