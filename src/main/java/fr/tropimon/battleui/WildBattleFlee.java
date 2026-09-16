package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the client-only wild Run shortcut isolated from genuine PvP forfeits.
 *
 * Cobblemon 1.7.2 has no client-to-server flee response. Run therefore uses
 * Cobblemon's own forfeit response, while the two misleading end messages are
 * represented locally as the native Cobblemon flee message.
 */
public final class WildBattleFlee {
    private static final Duration MESSAGE_WINDOW = Duration.ofSeconds(15);

    private static UUID battleId;
    private static String playerName = "";
    private static Instant requestedAt;
    private static boolean fleeMessageEmitted;

    private WildBattleFlee() {
    }

    public static void begin(ClientBattle battle) {
        MinecraftClient client = MinecraftClient.getInstance();
        battleId = battle.getBattleId();
        playerName = client.player == null ? "" : client.player.getName().getString();
        requestedAt = Instant.now();
        fleeMessageEmitted = false;
    }

    public static void cancel() {
        battleId = null;
        playerName = "";
        requestedAt = null;
        fleeMessageEmitted = false;
    }

    public static List<Text> rewriteMessages(List<Text> messages) {
        if (!pending()) return messages;

        List<Text> rewritten = new ArrayList<>(messages.size());
        boolean completed = false;
        for (Text message : messages) {
            String key = translationKey(message);
            if ("cobblemon.battle.forfeit".equals(key) && isLocalPlayerMessage(message)) {
                if (!fleeMessageEmitted) {
                    rewritten.add(Text.translatable("cobblemon.battle.flee"));
                    fleeMessageEmitted = true;
                }
                continue;
            }
            if ("cobblemon.battle.lose".equals(key) && isLocalPlayerMessage(message)) {
                // This line is a consequence of the internal forfeit, not a
                // real exhaustion of the player's team.
                completed = true;
                continue;
            }
            rewritten.add(message);
        }

        if (completed) cancel();
        return List.copyOf(rewritten);
    }

    private static boolean pending() {
        if (battleId == null || requestedAt == null) return false;
        ClientBattle current = CobblemonClient.INSTANCE.getBattle();
        if (current != null && !battleId.equals(current.getBattleId())) {
            cancel();
            return false;
        }
        if (Duration.between(requestedAt, Instant.now()).compareTo(MESSAGE_WINDOW) <= 0) return true;
        cancel();
        return false;
    }

    private static boolean isLocalPlayerMessage(Text message) {
        if (playerName.isBlank()) return true;
        Object[] args = translationArgs(message);
        if (args.length == 0) return true;
        Object actor = args[0];
        String rendered = actor instanceof Text text ? text.getString() : String.valueOf(actor);
        return playerName.equalsIgnoreCase(rendered);
    }

    private static String translationKey(Text message) {
        if (message.getContent() instanceof TranslatableTextContent translatable) return translatable.getKey();
        for (Text sibling : message.getSiblings()) {
            String nested = translationKey(sibling);
            if (!nested.isBlank()) return nested;
        }
        return "";
    }

    private static Object[] translationArgs(Text message) {
        if (message.getContent() instanceof TranslatableTextContent translatable) return translatable.getArgs();
        for (Text sibling : message.getSiblings()) {
            Object[] nested = translationArgs(sibling);
            if (nested.length > 0) return nested;
        }
        return new Object[0];
    }
}
