package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import java.util.List;
import java.util.UUID;

/** Synchronous, read-only adaptation of Cobblemon plus this mod's public observations. */
final class BattleCalculationInputs {
    private static final Stats[] NATIVE_STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final Stat[] STATS = Stat.values();
    private static final java.util.Map<com.cobblemon.mod.common.pokemon.FormData, SpeciesData> FORMS = new java.util.IdentityHashMap<>();
    private static final java.util.Map<MoveTemplate, MoveDescription> MOVES = new java.util.IdentityHashMap<>();
    private static final java.util.Map<UUID, PokemonSet> PARTNERS = new java.util.LinkedHashMap<>();
    private static long resources = -1;
    private static net.minecraft.util.Language language;
    private static void validateResources() {
        if (resources == UiResourceEpoch.current() && language == net.minecraft.util.Language.getInstance()) return;
        resources = UiResourceEpoch.current(); language = net.minecraft.util.Language.getInstance();
        FORMS.clear(); MOVES.clear(); PARTNERS.clear();
        BattleRandomSets.reload();
    }

    static PokemonSet refresh(PokemonSet previous, ActiveClientBattlePokemon slot, boolean own,
                              MoveTemplate requestedMove) {
        if (slot == null || slot.getBattlePokemon() == null) return null;
        var live = slot.getBattlePokemon();
        var portrait = BattleUiState.opponentRenderable(live);
        var form = portrait.getForm();
        validateResources();
        var species = FORMS.get(form);
        if (species == null) {
            species = BattleCalcDex.findFormSpecies(live.getSpecies().showdownId(), form.getName(),
                    form.showdownId(), List.copyOf(portrait.getAspects()));
            if (species == null) species = BattleCalcDex.findSpeciesByQuery(live.getSpecies().showdownId());
            if (species != null) FORMS.put(form, species);
        }
        if (species == null) return null;
        PokemonSet set = previous == null ? new PokemonSet(species) : previous;
        set.species = species;
        set.battleId = live.getUuid().toString();
        set.battleName = live.getDisplayName().getString();
        set.battleFormObserved = true;
        set.level = live.getLevel();
        set.item = "None";
        set.ability = BattleCalcDex.defaultAbility(species);
        set.itemKnown = set.abilityKnown = set.natureKnown = set.statsKnown = set.movesKnown = false;
        set.nature = BattleCalcDex.nature("serious");
        set.observedMaxHp = -1;
        set.rankedProfileKey = "";
        set.rankedItemSuggested = false;
        set.rankedAbilitySuggested = false;
        set.rankedNatureSuggested = false;
        set.rankedEvsSuggested = false;
        set.rankedMoveUsage.clear();
        set.observedMoveIds.clear();
        set.manualMoveIds.clear();
        set.suppressedMoveIds.clear();
        set.moves.clear();
        set.movePp.clear();
        java.util.Arrays.fill(set.zMoves, false);
        for (int i = 0; i < STATS.length; i++) {
            set.evs.put(STATS[i], 0);
            set.ivs.put(STATS[i], 31);
        }
        applyResolvedBattleState(set, live.getUuid(), live.getStatChanges(),
                live.getStatus() == null ? "" : live.getStatus().getShowdownName());
        Pokemon owned = null;
        if (own && !BattleUiState.spectating()) {
            for (Pokemon value : slot.getActor().getPokemon()) {
                if (value.getUuid().equals(live.getUuid())) { owned = value; break; }
            }
            if (owned == null) {
                for (Pokemon value : CobblemonClient.INSTANCE.getStorage().getParty()) {
                    if (value != null && value.getUuid().equals(live.getUuid())) { owned = value; break; }
                }
            }
        }
        if (owned != null) {
            set.item = BattleUiState.heldItemId(owned.heldItem());
            set.ability = owned.getAbility().getName();
            set.nature = BattleCalcDex.nature((owned.getMintedNature() == null
                    ? owned.getNature() : owned.getMintedNature()).getName().getPath());
            set.itemKnown = set.abilityKnown = set.natureKnown = set.statsKnown = set.movesKnown = true;
            set.observedMaxHp = owned.getMaxHealth();
            for (int i = 0; i < STATS.length; i++) {
                set.evs.put(STATS[i], owned.getEvs().getOrDefault(NATIVE_STATS[i]));
                set.ivs.put(STATS[i], owned.getIvs().getOrDefault(NATIVE_STATS[i]));
            }
            for (var move : owned.getMoveSet()) {
                set.moves.add(move(move.getTemplate(), owned));
                set.movePp.add(move.getCurrentPp());
            }
        }
        // Battle properties may expose allocated-but-empty EV/IV maps for an opponent.
        // They are private-data inputs only and must never turn an unknown foe into a 0-EV set.
        if (owned == null && own && !BattleUiState.spectating()) {
            var properties = live.getProperties();
            if (properties.getHeldItem() != null && !properties.getHeldItem().isBlank()) {
                set.item = properties.getHeldItem().replace("cobblemon:", ""); set.itemKnown = true;
            }
            if (properties.getAbility() != null && !properties.getAbility().isBlank()) {
                set.ability = properties.getAbility(); set.abilityKnown = true;
            }
            if (properties.getNature() != null) {
                NatureData nature = BattleCalcDex.findNatureByQuery(properties.getNature());
                if (nature != null) { set.nature = nature; set.natureKnown = true; }
            }
            boolean evsSpecified = properties.getEvs() != null && properties.getEvs().iterator().hasNext();
            boolean ivsSpecified = properties.getIvs() != null && properties.getIvs().iterator().hasNext();
            for (int i=0; i<STATS.length; i++) {
                if (evsSpecified) set.evs.put(STATS[i], properties.getEvs().getOrDefault(NATIVE_STATS[i]));
                if (ivsSpecified) set.ivs.put(STATS[i], properties.getIvs().getOrDefault(NATIVE_STATS[i]));
            }
            if (evsSpecified && ivsSpecified) set.statsKnown = true;
            if (properties.getMoves() != null) {
                for (String id : properties.getMoves()) set.moves.add(BattleCalcDex.findMoveByQuery(id));
                set.movesKnown = set.moves.stream().anyMatch(java.util.Objects::nonNull);
            }
        }
        TeamMemberView known = own ? BattleUiState.ownMember(live.getUuid()) : BattleUiState.knownOpponent(live.getUuid());
        if (known != null) {
            var ability = BattleUiState.currentAbility(live.getUuid(), known.ability());
            if (ability != null) { set.ability = ability.suppressed() ? "None" : ability.id(); set.abilityKnown = true; }
            var item = BattleUiState.currentItem(live.getUuid(), owned == null ? known.heldItem() : owned.heldItem());
            if (!item.isEmpty() || BattleUiState.opponentItemKnownAbsent(live.getUuid())) {
                set.item = BattleUiState.heldItemId(item); set.itemKnown = true;
            }
            if (owned == null) for (MoveView move : known.knownMoves()) {
                MoveData data = BattleCalcDex.findMoveByQuery(move.id());
                if (data != null && set.moves.stream().noneMatch(value -> value != null && value.id().equals(data.id()))) set.moves.add(data);
                set.movePp.add(move.currentPp());
                set.observedMoveIds.add(move.id());
            }
        }
        // The former bridge selected raw battle sets after snapshot creation. Preserve
        // those inputs: never replace actual EVs/nature or infer the selected foe's item.
        if (set.item.isBlank()) set.item = "None";
        set.teraType = PokeType.byName(BattleUiState.teraType(live.getUuid()));
        set.terastallized = set.teraType != PokeType.NONE;
        var currentTypes = BattleUiState.currentTypeViews(live.getUuid(), form.getTypes());
        if (!set.terastallized && !currentTypes.isEmpty()) {
            PokeType primary = PokeType.byName(currentTypes.getFirst().id());
            PokeType secondary = currentTypes.size() > 1 ? PokeType.byName(currentTypes.get(1).id()) : PokeType.NONE;
            if (primary != species.primaryType() || secondary != species.secondaryType()) {
                set.species = new SpeciesData(species.id(), species.name(), primary, secondary, species.baseStats(),
                        species.notFullyEvolved(), species.texturePath(), species.cobblemonSpeciesId(), species.aspects(), species.weightKg());
            }
        }
        if (live.isHpFlat() && live.getMaxHp() > 0) set.observedMaxHp = Math.round(live.getMaxHp());
        float percentage = BattleHealthFormatting.percent(live.isHpFlat(), live.getHpValue(), live.getMaxHp());
        set.currentHp = live.getHpValue() <= 0 ? 0 : Math.max(1,
                live.isHpFlat() ? Math.round(live.getHpValue()) : Math.round(set.maxHp() * percentage / 100f));
        set.hpObservation = live.getHpValue();
        set.runtimeEffects = PokemonBattleEffects.snapshot(live.getUuid(), BattleUiState.turn()).stream().map(Object::toString).toList();
        BattleCalculationHistory.apply(live.getUuid(), set);
        if (requestedMove != null) {
            MoveData requested = move(requestedMove, owned);
            int index = -1;
            for (int i = 0; i < set.moves.size(); i++) if (set.moves.get(i) != null && set.moves.get(i).id().equals(requested.id())) { index = i; break; }
            if (index < 0) { if (set.moves.size() >= 4) set.moves.set(0, requested); else set.moves.add(requested); }
            else set.moves.set(index, requested);
        }
        while (set.moves.size() < 4) set.moves.add(null);
        return set;
    }

    static void applyResolvedBattleState(PokemonSet set, UUID pokemon,
                                         java.util.Map<com.cobblemon.mod.common.api.pokemon.stats.Stat, Integer> nativeStages,
                                         String nativeStatus) {
        var stages = BattleUiState.resolvedStatStageValues(pokemon, nativeStages);
        for (int i = 0; i < STATS.length; i++) {
            set.boosts.put(STATS[i], stages.getOrDefault(NATIVE_STATS[i].getShowdownId(), 0));
        }
        String resolvedStatus = BattleUiState.resolvedStatus(pokemon, nativeStatus);
        set.status = switch (resolvedStatus == null ? "" : resolvedStatus) {
            case "brn" -> StatusCondition.BURN; case "psn", "tox" -> StatusCondition.POISON;
            case "par" -> StatusCondition.PARALYSIS; case "slp" -> StatusCondition.SLEEP;
            case "frz" -> StatusCondition.FREEZE; default -> StatusCondition.NONE;
        };
    }

    static MoveData move(MoveTemplate move) {
        return move(move, null);
    }

    static MoveData move(MoveTemplate move, Pokemon nativeAttacker) {
        validateResources();
        MoveDescription previous = MOVES.get(move);
        String display = move.getDisplayName().getString();
        String moveId = BattleCalcDex.normalize(move.getName());
        String effectiveType = move.getElementalType().getName();
        if (moveId.startsWith("hiddenpower") && nativeAttacker != null) {
            effectiveType = move.getEffectiveElementalType(nativeAttacker).getName();
        } else if (moveId.startsWith("hiddenpower") && moveId.length() > "hiddenpower".length()) {
            PokeType suffix = PokeType.byName(moveId.substring("hiddenpower".length()));
            if (suffix != PokeType.NONE) effectiveType = suffix.name().toLowerCase(java.util.Locale.ROOT);
        }
        if (previous != null && previous.name.equals(move.getName()) && previous.display.equals(display)
                && previous.power == move.getPower() && previous.accuracy == move.getAccuracy()
                && previous.type.equals(effectiveType)
                && previous.category.equals(move.getDamageCategory().getName())
                && previous.priority == move.getPriority() && previous.target == move.getTarget()) return previous.data;
        MoveData data = BattleCalcDex.findMoveByQuery(move.getName());
        var category = DamageCategory.valueOf(move.getDamageCategory().getName().toUpperCase(java.util.Locale.ROOT));
        MoveData result = new MoveData(moveId, display,
                PokeType.byName(effectiveType), category, (int) Math.round(move.getPower()),
                data != null && data.spreadMove(), data != null && data.contact(),
                data == null ? java.util.Set.of() : data.flags(), move.getPriority());
        if (MOVES.size() >= 256) MOVES.clear();
        MOVES.put(move, new MoveDescription(move.getName(), display, move.getPower(), move.getAccuracy(),
                effectiveType, move.getDamageCategory().getName(), move.getPriority(), move.getTarget(), result));
        return result;
    }
    private record MoveDescription(String name, String display, double power, double accuracy, String type,
                                   String category, int priority, Object target, MoveData data) { }

    static void refreshField(FieldState field, ClientBattle battle, ActiveClientBattlePokemon attacker,
                             ActiveClientBattlePokemon defender, MoveTemplate move) {
        field.weather = Weather.NONE; field.terrain = Terrain.NONE;
        field.doubles = battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
        field.alliedTarget = attacker.isAllied(defender);
        field.trickRoom = BattleFieldEffects.active("trickroom");
        field.wonderRoom = BattleFieldEffects.active("wonderroom");
        field.gravity = BattleFieldEffects.active("gravity");
        for (var effect : BattleUiState.effects()) {
            if (effect.side() != BattleFieldEffects.EffectSide.FIELD) continue;
            switch (effect.id()) {
                case "raindance" -> field.weather = Weather.RAIN;
                case "primordialsea" -> field.weather = Weather.HEAVY_RAIN;
                case "sunnyday" -> field.weather = Weather.SUN;
                case "desolateland" -> field.weather = Weather.HARSH_SUN;
                case "sandstorm" -> field.weather = Weather.SAND;
                case "hail", "snow", "snowscape" -> field.weather = Weather.SNOW;
                case "deltastream" -> field.weather = Weather.STRONG_WINDS;
                case "electricterrain" -> field.terrain = Terrain.ELECTRIC;
                case "grassyterrain" -> field.terrain = Terrain.GRASSY;
                case "mistyterrain" -> field.terrain = Terrain.MISTY;
                case "psychicterrain" -> field.terrain = Terrain.PSYCHIC;
            }
        }
        if (BattleMoveDynamics.weatherSuppressed()) field.weather = Weather.NONE;
        boolean random = field.doubles && random(battle);
        int spreadTargets = move == null ? 1 : BattleMultiTargeting.spreadTargetCount(attacker, move.getTarget());
        if (move != null && field.terrain == Terrain.PSYCHIC
                && BattleCalcDex.normalize(move.getName()).equals("expandingforce")) {
            int opponents = 0;
            for (var active : BattleMultiTargeting.all(attacker)) if (!attacker.isAllied(active)) opponents++;
            spreadTargets = Math.max(spreadTargets, Math.max(1, opponents));
        }
        side(field.attackerSide, attacker, BattleUiState.effectSide(attacker.getBattlePokemon().getUuid()),
                field.doubles, random, spreadTargets);
        side(field.defenderSide, defender, BattleUiState.effectSide(defender.getBattlePokemon().getUuid()),
                field.doubles, random, spreadTargets);
    }

    private static void side(SideConditions result, ActiveClientBattlePokemon source,
                             BattleFieldEffects.EffectSide side, boolean doubles, boolean random, int spreadTargets) {
        int turn = BattleUiState.turn();
        result.reflect = BattleFieldEffects.activeOnSide("reflect", side, turn);
        result.lightScreen = BattleFieldEffects.activeOnSide("lightscreen", side, turn);
        result.auroraVeil = BattleFieldEffects.activeOnSide("auroraveil", side, turn);
        result.tailwind = BattleFieldEffects.activeOnSide("tailwind", side, turn);
        result.helpingHand = doubles && PokemonBattleEffects.active(source.getBattlePokemon().getUuid(), "helpinghand", BattleUiState.turn());
        result.wideGuard = doubles && BattleCalculationHistory.wideGuard(side);
        result.quickGuard = BattleCalculationHistory.guard(side, BattleCalculationHistory.Guard.QUICK);
        result.matBlock = BattleCalculationHistory.guard(side, BattleCalculationHistory.Guard.MAT_BLOCK);
        result.craftyShield = BattleCalculationHistory.guard(side, BattleCalculationHistory.Guard.CRAFTY);
        result.partnerAbility = "None"; result.partnerName = ""; result.partnerAbilities = List.of();
        result.friendGuard = false;
        result.spreadTargets = Math.max(1, spreadTargets);
        if (!doubles) return;
        var partnerAbilities = new java.util.ArrayList<String>();
        var partnerNames = new java.util.ArrayList<String>();
        boolean neutralizingGas = BattleMoveDynamics.neutralizingGasActive();
        for (var actor : source.getActor().getSide().getActors()) for (var active : actor.getActivePokemon()) {
            if (active == source || active.getBattlePokemon() == null || active.getBattlePokemon().getHpValue() <= 0) continue;
            UUID id = active.getBattlePokemon().getUuid();
            TeamMemberView member = side == BattleFieldEffects.EffectSide.PLAYER_FIELD ? BattleUiState.ownMember(id) : BattleUiState.knownOpponent(id);
            var ability = BattleUiState.currentAbility(id, member == null ? null : member.ability());
            String heldItem = member == null ? "" : BattleUiState.heldItemId(member.heldItem());
            String activeAbility = ability == null || ability.suppressed() ? ""
                    : BattleMoveDynamics.abilityUnderGas(ability.id(), heldItem, neutralizingGas);
            if (!activeAbility.isBlank()) partnerAbilities.add(activeAbility);
            else if (ability == null && random) {
                // Snapshot partner abilities previously used deterministic Random Battle
                // inference. Keep it locally, without touching the selected combatants.
                PokemonSet partner = refresh(PARTNERS.get(id), active,
                        side == BattleFieldEffects.EffectSide.PLAYER_FIELD, null);
                if (partner != null) {
                    BattleRandomSets.applyInference(partner);
                    if (partner.abilityKnown && !neutralizingGas) partnerAbilities.add(partner.ability);
                    if (PARTNERS.size() >= 24) PARTNERS.clear();
                    PARTNERS.put(id, partner);
                }
            }
            if (member != null && !member.name().isBlank()) partnerNames.add(member.name());
        }
        result.partnerAbilities = List.copyOf(partnerAbilities);
        result.partnerAbility = partnerAbilities.isEmpty() ? "None" : partnerAbilities.getFirst();
        result.partnerName = String.join(", ", partnerNames);
    }

    static boolean random(ClientBattle battle) {
        var format = battle.getBattleFormat();
        return BattleUiState.isRandomFormat(format.getMod(), format.getRuleSet(),
                format.getBattleType().getName());
    }

    private BattleCalculationInputs() { }
}
