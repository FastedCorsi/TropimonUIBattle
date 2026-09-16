package fr.tropimon.battleui;

import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public record TeamMemberView(
        UUID uuid,
        String name,
        int level,
        float hpPercent,
        String status,
        boolean fainted,
        boolean active,
        ItemStack heldItem,
        RenderablePokemon portrait,
        List<TypeView> types,
        AbilityView ability,
        List<MoveView> knownMoves,
        List<StatStageView> statStages,
        boolean known
) {
    public TeamMemberView {
        hpPercent = Math.max(0.0F, Math.min(100.0F, hpPercent));
        heldItem = heldItem == null ? ItemStack.EMPTY : heldItem.copy();
        types = types == null ? List.of() : List.copyOf(types);
        knownMoves = knownMoves == null ? List.of() : List.copyOf(knownMoves);
        statStages = statStages == null ? List.of() : List.copyOf(statStages);
    }

    TeamMemberView withActive(boolean value) {
        if (active == value && (value || statStages.isEmpty())) return this;
        return new TeamMemberView(uuid, name, level, hpPercent, status, fainted, value, heldItem,
                portrait, types, ability, knownMoves, value ? statStages : List.of(), known);
    }

    TeamMemberView withHealth(float value) {
        if (Float.compare(hpPercent, value) == 0 && fainted == (value <= 0.0F)) return this;
        return new TeamMemberView(uuid, name, level, value, status, value <= 0.0F, active, heldItem,
                portrait, types, ability, knownMoves, statStages, known);
    }

    TeamMemberView withItem(ItemStack item) {
        if (ItemStack.areEqual(heldItem, item)) return this;
        return new TeamMemberView(uuid, name, level, hpPercent, status, fainted, active, item,
                portrait, types, ability, knownMoves, statStages, known);
    }

    TeamMemberView withStatus(String value) {
        String next = value == null ? "" : value;
        if (status.equals(next)) return this;
        return new TeamMemberView(uuid, name, level, hpPercent, next, fainted, active, heldItem,
                portrait, types, ability, knownMoves, statStages, known);
    }

    TeamMemberView withTypes(List<TypeView> currentTypes) {
        if (types.equals(currentTypes)) return this;
        return new TeamMemberView(uuid, name, level, hpPercent, status, fainted, active, heldItem,
                portrait, currentTypes, ability, knownMoves, statStages, known);
    }

    TeamMemberView withAbility(AbilityView currentAbility) {
        if (java.util.Objects.equals(ability, currentAbility)) return this;
        return new TeamMemberView(uuid, name, level, hpPercent, status, fainted, active, heldItem,
                portrait, types, currentAbility, knownMoves, statStages, known);
    }

    static TeamMemberView reuse(TeamMemberView previous, TeamMemberView next) {
        return sameData(previous, next) ? previous : next;
    }

    static TeamMemberView snapshot(TeamMemberView previous, UUID uuid, String name, int level, float hp, String status,
                                   boolean fainted, boolean active, ItemStack item, RenderablePokemon portrait,
                                   List<TypeView> types, AbilityView ability, List<MoveView> moves,
                                   List<StatStageView> stages, boolean known) {
        if (previous != null && previous.uuid.equals(uuid) && previous.name.equals(name) && previous.level == level
                && Float.compare(previous.hpPercent, hp) == 0 && previous.status.equals(status)
                && previous.fainted == fainted && previous.active == active && previous.known == known
                && ItemStack.areEqual(previous.heldItem, item) && samePortrait(previous.portrait, portrait)
                && previous.types.equals(types) && java.util.Objects.equals(previous.ability, ability)
                && previous.knownMoves.equals(moves) && previous.statStages.equals(stages)) return previous;
        return new TeamMemberView(uuid,name,level,hp,status,fainted,active,item,portrait,types,ability,moves,stages,known);
    }

    static boolean sameData(TeamMemberView a, TeamMemberView b) {
        if (a == b) return true;
        return a != null && b != null && a.uuid.equals(b.uuid) && a.name.equals(b.name)
                && a.level == b.level && Float.compare(a.hpPercent, b.hpPercent) == 0 && a.status.equals(b.status)
                && a.fainted == b.fainted && a.active == b.active && a.known == b.known
                && ItemStack.areEqual(a.heldItem, b.heldItem) && samePortrait(a.portrait, b.portrait)
                && a.types.equals(b.types) && java.util.Objects.equals(a.ability, b.ability)
                && a.knownMoves.equals(b.knownMoves) && a.statStages.equals(b.statStages);
    }
    private static boolean samePortrait(RenderablePokemon a, RenderablePokemon b) {
        return a == b || a != null && b != null && a.getSpecies() == b.getSpecies()
                && a.getAspects().equals(b.getAspects()) && ItemStack.areEqual(a.getHeldItem(), b.getHeldItem());
    }
}

record TypeView(String id, Text name) {
    TypeView {
        id = id == null ? "normal" : id;
        name = name == null ? Text.empty() : name.copy();
    }
}

/** Exact private battle stats, available only for the local participant's own party. */
record BattleStatsView(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {
    static final BattleStatsView UNKNOWN = new BattleStatsView(0, 0, 0, 0, 0, 0);

    BattleStatsView {
        hp = Math.max(0, hp);
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        specialAttack = Math.max(0, specialAttack);
        specialDefense = Math.max(0, specialDefense);
        speed = Math.max(0, speed);
    }

    boolean known() {
        return hp > 0 && attack > 0 && defense > 0 && specialAttack > 0 && specialDefense > 0 && speed > 0;
    }
}

record AbilityView(String id, Text name, Text description, boolean suppressed, boolean hidden) {
    AbilityView(String id, Text name, Text description, boolean suppressed) {
        this(id, name, description, suppressed, false);
    }
    AbilityView(Text name, Text description) {
        this("", name, description, false);
    }

    AbilityView(String id, Text name, Text description) {
        this(id, name, description, false);
    }

    AbilityView {
        id = id == null ? "" : id;
        name = name == null ? Text.empty() : name.copy();
        description = description == null ? Text.empty() : description.copy();
    }

    AbilityView withSuppressed(boolean value) {
        if (suppressed == value) return this;
        return new AbilityView(id, name, description, value, hidden);
    }

    AbilityView withHidden(boolean value) {
        if (hidden == value) return this;
        return new AbilityView(id, name, description, suppressed, value);
    }
}

record MoveView(String id, Text name, Text description, String type, int currentPp, int maxPp,
                int ppUsed, boolean ppEstimated, MoveOrigin origin, int minCurrentPp, int minMaxPp) {
    MoveView(String id, Text name, Text description, String type, int currentPp, int maxPp) {
        this(id, name, description, type, currentPp, maxPp,
                currentPp >= 0 && maxPp > 0 ? Math.max(0, maxPp - currentPp) : 0, false,
                MoveOrigin.NATIVE, currentPp, maxPp);
    }

    MoveView(String id, Text name, Text description, String type, int currentPp, int maxPp,
             int ppUsed, boolean ppEstimated) {
        this(id, name, description, type, currentPp, maxPp, ppUsed, ppEstimated,
                MoveOrigin.NATIVE, currentPp, maxPp);
    }

    MoveView(String id, Text name, Text description, String type, int currentPp, int maxPp,
             int ppUsed, boolean ppEstimated, MoveOrigin origin) {
        this(id, name, description, type, currentPp, maxPp, ppUsed, ppEstimated,
                origin, currentPp, maxPp);
    }

    static MoveView estimatedRange(String id, Text name, Text description, String type,
                                   int minMaxPp, int maxPp) {
        int lower = Math.max(1, minMaxPp);
        int upper = Math.max(lower, maxPp);
        return new MoveView(id, name, description, type, upper, upper, 0, true,
                MoveOrigin.NATIVE, lower, lower);
    }

    MoveView {
        id = id == null ? "" : id;
        name = name == null ? Text.empty() : name.copy();
        description = description == null ? Text.empty() : description.copy();
        type = type == null ? "normal" : type;
        origin = origin == null ? MoveOrigin.NATIVE : origin;
        maxPp = Math.max(0, maxPp);
        minMaxPp = Math.max(0, Math.min(maxPp, minMaxPp));
        if (currentPp < 0) {
            currentPp = -1;
            minCurrentPp = -1;
        } else {
            currentPp = Math.min(maxPp, currentPp);
            minCurrentPp = Math.max(0, Math.min(Math.min(currentPp, minMaxPp), minCurrentPp));
        }
        ppUsed = Math.max(0, ppUsed);
    }

    boolean hasPpRange() {
        return currentPp >= 0 && (minCurrentPp != currentPp || minMaxPp != maxPp);
    }

    MoveView spendPp(int amount) {
        int safeAmount = Math.max(0, amount);
        int remaining = currentPp < 0 ? Math.max(0, maxPp - safeAmount)
                : Math.max(0, currentPp - safeAmount);
        int minimumRemaining = minCurrentPp < 0 ? Math.max(0, minMaxPp - safeAmount)
                : Math.max(0, minCurrentPp - safeAmount);
        MoveOrigin nextOrigin = origin == MoveOrigin.CALLED ? MoveOrigin.NATIVE : origin;
        return new MoveView(id, name, description, type, remaining, maxPp,
                ppUsed + safeAmount, true, nextOrigin, minimumRemaining, minMaxPp);
    }

    MoveView restorePp(int amount) {
        if (currentPp < 0) return this;
        int restored = Math.max(0, amount);
        return new MoveView(id, name, description, type,
                Math.min(maxPp, currentPp + restored), maxPp, ppUsed, true, origin,
                Math.min(minMaxPp, minCurrentPp + restored), minMaxPp);
    }

    MoveView restorePpFromEmpty(int amount) {
        int restored = Math.max(0, amount);
        return new MoveView(id, name, description, type,
                Math.min(maxPp, restored), maxPp, ppUsed, true, origin,
                Math.min(minMaxPp, restored), minMaxPp);
    }

    MoveView exhaustPp() {
        return new MoveView(id, name, description, type, 0, maxPp,
                ppUsed, true, origin, 0, minMaxPp);
    }

    MoveView asCalled() {
        return new MoveView(id, name, description, type, -1, maxPp, 0, true,
                MoveOrigin.CALLED, -1, minMaxPp);
    }

    MoveView asCopied(int pp) {
        int safePp = Math.max(1, pp);
        return new MoveView(id, name, description, type, safePp, safePp, 0, true,
                MoveOrigin.COPIED, safePp, safePp);
    }

    MoveView asTransformed() {
        return new MoveView(id, name, description, type, 5, 5, 0, true,
                MoveOrigin.TRANSFORMED, 5, 5);
    }

    MoveView atFullPp() {
        return new MoveView(id, name, description, type, maxPp, maxPp, 0, true,
                origin, minMaxPp, minMaxPp);
    }
}

enum MoveOrigin {
    NATIVE,
    CALLED,
    COPIED,
    TRANSFORMED
}

record StatStageView(Text name, String id, int stage) {
    StatStageView {
        name = name == null ? Text.empty() : name.copy();
        id = id == null ? "" : id;
        stage = Math.max(-6, Math.min(6, stage));
    }
}
