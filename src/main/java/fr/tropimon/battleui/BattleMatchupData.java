package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only adaptation of information actually available to Cobblemon's client. */
final class BattleMatchupData {
    private BattleMatchupData() { }

    static BattleMatchup.Result analyze(MoveTemplate move, Pokemon nativeAttacker, UUID attackerId,
                                       BattleUiState.ActiveTargetView target, BattleMatchup.Field field) {
        TeamMemberView own = BattleUiState.ownMember(attackerId);
        String type = move.getElementalType().getName();
        String moveId = BattleMatchup.id(move.getName());
        if (moveId.startsWith("hiddenpower") && nativeAttacker != null) {
            type = move.getEffectiveElementalType(nativeAttacker).getName();
        }
        if (moveId.equals("aurawheel") && own != null && own.portrait() != null) {
            type = BattleMatchup.id(own.portrait().getForm().getName()).contains("hangry") ? "dark" : "electric";
        }
        if ((moveId.equals("ragingbull") || moveId.equals("ivycudgel")) && own != null && own.portrait() != null) {
            var originalTypes = new HashSet<String>();
            for (var original : own.portrait().getForm().getTypes()) originalTypes.add(original.getName());
            type = originalTypes.contains("fire") ? "fire" : originalTypes.contains("water") ? "water"
                    : moveId.equals("ivycudgel") ? originalTypes.contains("rock") ? "rock" : "grass"
                    : originalTypes.contains("fighting") ? "fighting" : "normal";
        }
        var attack = own == null ? BattleMatchup.Pokemon.EMPTY : pokemon(own);
        var defense = pokemon(target);
        return BattleMatchup.analyze(new BattleMatchup.Move(moveId, type,
                move.getDamageCategory().getName(), move.getPriority()), attack, defense, field);
    }

    static BattleMatchup.Pokemon pokemon(TeamMemberView member) {
        return new BattleMatchup.Pokemon(member.types().stream().map(TypeView::id).toList(),
                abilityId(member.ability()), BattleUiState.heldItemId(member.heldItem()),
                effects(member.uuid(), member.ability()), BattleUiState.teraType(member.uuid()), member.hpPercent() >= 100);
    }

    static BattleMatchup.Pokemon pokemon(BattleUiState.ActiveTargetView member) {
        return new BattleMatchup.Pokemon(member.types().stream().map(TypeView::id).toList(),
                abilityId(member.ability()), member.heldItemId(), effects(member.uuid(), member.ability()),
                BattleUiState.teraType(member.uuid()), member.hpPercent() >= 100);
    }

    static Set<String> effects(UUID uuid, AbilityView ability) {
        var result = new HashSet<String>();
        for (var effect : PokemonBattleEffects.snapshot(uuid, BattleUiState.turn())) result.add(effect.id());
        if (ability != null && ability.suppressed()) result.add("abilitysuppressed");
        return Set.copyOf(result);
    }

    static BattleMatchup.Field field(BattleUiState.ActiveTargetView target) {
        List<BattleMatchup.Pokemon> active = new ArrayList<>();
        List<BattleMatchup.Pokemon> partners = new ArrayList<>();
        for (var member : BattleUiState.ownTeam()) if (member.active() && !member.fainted()) active.add(pokemon(member));
        for (var member : BattleUiState.opponentTeam()) {
            if (!member.active() || member.fainted() || target != null && member.uuid().equals(target.uuid())) continue;
            var value = pokemon(member);
            active.add(value);
            partners.add(value);
        }
        if (target != null) active.add(pokemon(target));
        return BattleMatchup.withActiveAbilities(baseField(), active, partners);
    }

    static Map<UUID, BattleMatchup.Field> liveFields(List<BattleUiState.ActiveTargetView> opponents) {
        List<BattleMatchup.Pokemon> active = new ArrayList<>();
        for (var member : BattleUiState.activeOwnMembers()) active.add(pokemon(member));
        for (var opponent : opponents) active.add(pokemon(opponent));
        var fields = new HashMap<UUID, BattleMatchup.Field>();
        var base = baseField();
        for (var target : opponents) {
            var partners = opponents.stream().filter(other -> !other.uuid().equals(target.uuid()))
                    .map(BattleMatchupData::pokemon).toList();
            fields.put(target.uuid(), BattleMatchup.withActiveAbilities(base, active, partners));
        }
        return Map.copyOf(fields);
    }

    static BattleMatchup.Field liveField(BattleUiState.ActiveTargetView target,
                                         List<BattleMultiTargetView> activeTargets, boolean targetAllied) {
        List<BattleMatchup.Pokemon> active = activeTargets.stream().map(BattleMultiTargetView::view)
                .map(BattleMatchupData::pokemon).toList();
        List<BattleMatchup.Pokemon> partners = activeTargets.stream()
                .filter(value -> value.allied() == targetAllied && !value.view().uuid().equals(target.uuid()))
                .map(BattleMultiTargetView::view).map(BattleMatchupData::pokemon).toList();
        return BattleMatchup.withActiveAbilities(baseField(), active, partners);
    }

    record BattleMultiTargetView(BattleUiState.ActiveTargetView view, boolean allied) { }

    private static BattleMatchup.Field baseField() {
        var global = new HashSet<String>();
        String weather = "";
        String terrain = "";
        for (var effect : BattleFieldEffects.snapshot(BattleUiState.turn())) {
            if (effect.side() != BattleFieldEffects.EffectSide.FIELD) continue;
            global.add(effect.id());
            if (WEATHERS.contains(effect.id())) weather = effect.id();
            if (effect.id().endsWith("terrain")) terrain = effect.id();
        }
        return new BattleMatchup.Field(weather, terrain, global, Set.of());
    }

    static String abilityId(AbilityView ability) {
        return ability == null || ability.suppressed() ? "" : BattleMatchup.id(ability.id());
    }

    private static final Set<String> WEATHERS = Set.of("raindance", "primordialsea", "sunnyday", "desolateland",
            "sandstorm", "hail", "snow", "snowscape", "deltastream");
}
