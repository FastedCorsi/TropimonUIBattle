package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.item.ItemStack;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Restores the public cause which some Cobblemon translations omit from their visible sentence. */
final class BattleLogCausality {
    private static final Map<String, Cause> CAUSES = Map.ofEntries(
            Map.entry("cobblemon.battle.damage.ironbarbs", Cause.ability("ironbarbs")),
            Map.entry("cobblemon.battle.damage.roughskin", Cause.ability("roughskin")),
            Map.entry("cobblemon.battle.damage.baddreams", Cause.ability("baddreams")),
            Map.entry("cobblemon.battle.damage.dryskin", Cause.ability("dryskin")),
            Map.entry("cobblemon.battle.damage.liquidooze", Cause.ability("liquidooze")),
            Map.entry("cobblemon.battle.heal.raindish", Cause.ability("raindish")),
            Map.entry("cobblemon.battle.heal.poisonheal", Cause.ability("poisonheal")),
            Map.entry("cobblemon.battle.heal.cheekpouch", Cause.ability("cheekpouch")),
            Map.entry("cobblemon.battle.heal.dryskin", Cause.ability("dryskin")),
            Map.entry("cobblemon.battle.heal.eartheater", Cause.ability("eartheater")),
            Map.entry("cobblemon.battle.heal.icybody", Cause.ability("icebody")),
            Map.entry("cobblemon.battle.heal.voltabsorb", Cause.ability("voltabsorb")),
            Map.entry("cobblemon.battle.heal.waterabsorb", Cause.ability("waterabsorb")),
            Map.entry("cobblemon.battle.damage.lifeorb", Cause.item("life_orb")),
            Map.entry("cobblemon.battle.damage.rockyhelmet", Cause.item("rocky_helmet")),
            Map.entry("cobblemon.battle.heal.leftovers", Cause.item("leftovers")),
            Map.entry("cobblemon.battle.damage.bind", Cause.move("bind")),
            Map.entry("cobblemon.battle.damage.clamp", Cause.move("clamp")),
            Map.entry("cobblemon.battle.damage.firespin", Cause.move("firespin")),
            Map.entry("cobblemon.battle.damage.infestation", Cause.move("infestation")),
            Map.entry("cobblemon.battle.damage.leechseed", Cause.move("leechseed")),
            Map.entry("cobblemon.battle.damage.magmastorm", Cause.move("magmastorm")),
            Map.entry("cobblemon.battle.damage.nightmare", Cause.move("nightmare")),
            Map.entry("cobblemon.battle.damage.saltcure", Cause.move("saltcure")),
            Map.entry("cobblemon.battle.damage.sandtomb", Cause.move("sandtomb")),
            Map.entry("cobblemon.battle.damage.spikes", Cause.move("spikes")),
            Map.entry("cobblemon.battle.damage.stealthrock", Cause.move("stealthrock")),
            Map.entry("cobblemon.battle.damage.thundercage", Cause.move("thundercage")),
            Map.entry("cobblemon.battle.damage.whirlpool", Cause.move("whirlpool")),
            Map.entry("cobblemon.battle.damage.wrap", Cause.move("wrap")),
            Map.entry("cobblemon.battle.heal.aquaring", Cause.move("aquaring")),
            Map.entry("cobblemon.battle.heal.ingrain", Cause.move("ingrain")),
            Map.entry("cobblemon.battle.heal.grassyterrain", Cause.move("grassyterrain")),
            Map.entry("cobblemon.battle.heal.wish", Cause.move("wish")),
            Map.entry("cobblemon.status.burn.hurt", Cause.status("text.tropimon_ui_battle.status.brn")),
            Map.entry("cobblemon.status.poison.hurt", Cause.status("text.tropimon_ui_battle.status.tox"))
    );

    private BattleLogCausality() {
    }

    static CauseText resolve(String key) {
        key = key == null ? "" : key.toLowerCase(Locale.ROOT);
        Cause cause = CAUSES.get(key);
        if (cause != null) return cause.resolve();
        if (!key.startsWith("cobblemon.battle.")) return null;
        String[] parts = key.substring("cobblemon.battle.".length()).split("\\.");
        if (parts.length < 2) return null;
        String family = parts[0], id = parts[parts.length - 1];
        // "confusion" is a volatile state, not proof that the move Confusion caused it.
        if (family.equals("start") && id.equals("confusion")) return null;
        if (Set.of("generic", "replace", "receiver", "item").contains(id)) return null;
        if (family.equals("ability")) return Cause.ability(id).resolve();
        if (family.equals("item") || family.equals("enditem")) {
            CauseText item = Cause.item(id).resolve();
            if (item != null) return item;
            if (Set.of("frisk", "harvest").contains(id)) return Cause.ability(id).resolve();
            return Cause.move(id).resolve();
        }
        if (Set.of("activate", "start", "end", "cant", "fieldstart", "fieldend", "sidestart", "sideend").contains(family)) {
            CauseText move = Cause.move(id).resolve();
            return move != null ? move : Cause.ability(id).resolve();
        }
        return null;
    }

    static MutableText prefix(CauseText causeText, String rendered, MutableText message) {
        if (causeText == null || causeText.label().getString().isBlank()) return message;
        // Already named in the event: the formatter attaches the hover to that occurrence.
        if (BattleUiState.nameMatches(rendered, causeText.label().getString())) return message;
        MutableText label = causeText.label().copy().styled(style -> {
            style = style.withColor(BattleLogTextFormatter.BODY_COLOR).withBold(true).withUnderline(false);
            return causeText.hover() == null ? style : style.withHoverEvent(causeText.hover());
        });
        return Text.literal("[").styled(style -> style.withColor(BattleLogTextFormatter.MUTED_COLOR))
                .append(label).append(Text.literal("] ").styled(style -> style.withColor(BattleLogTextFormatter.MUTED_COLOR)))
                .append(message);
    }

    private record Cause(Kind kind, String id) {
        static Cause ability(String id) { return new Cause(Kind.ABILITY, id); }
        static Cause move(String id) { return new Cause(Kind.MOVE, id); }
        static Cause item(String id) { return new Cause(Kind.ITEM, id); }
        static Cause status(String id) { return new Cause(Kind.STATUS, id); }

        CauseText resolve() {
            if (kind == Kind.ABILITY) {
                AbilityTemplate ability = Abilities.get(id);
                if (ability == null) return null;
                Text name = BattleLogTextFormatter.localized(ability.getDisplayName());
                Text hover = BattleLogTextFormatter.abilityTooltip(ability);
                return new CauseText(name, new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
            }
            if (kind == Kind.MOVE) {
                MoveTemplate move = Moves.getByName(id);
                if (move == null) return null;
                Text hover = BattleLogTextFormatter.moveTooltip(move);
                return new CauseText(move.getDisplayName(), new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
            }
            if (kind == Kind.ITEM) {
                ItemStack stack = BattleLogTextFormatter.findItem(id);
                if (stack.isEmpty()) return null;
                return new CauseText(stack.getName(), new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        BattleLogTextFormatter.itemTooltip(stack)));
            }
            return new CauseText(Text.translatable(id), null);
        }
    }

    private enum Kind { ABILITY, MOVE, ITEM, STATUS }

    record CauseText(Text label, HoverEvent hover) {
    }
}
