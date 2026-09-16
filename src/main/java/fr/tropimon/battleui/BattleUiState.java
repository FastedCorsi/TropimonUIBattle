package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BattleUiState {
    private static final int MAX_LOG_ENTRIES = 25_000;
    private static final RevisionedJournal<BattleLogEntry> LOG = new RevisionedJournal<>(MAX_LOG_ENTRIES);
    private static final LinkedHashMap<UUID, TeamMemberView> OPPONENT_TEAM = new LinkedHashMap<>();
    // Public observations on both sides when spectating; never read either party's private data.
    private static final Set<UUID> SPECTATED_LEFT = new java.util.HashSet<>();
    private static boolean spectating;
    private static final Map<UUID, Float> LAST_HEALTH = new java.util.HashMap<>();
    // A faint packet is more authoritative than the public active-slot HP,
    // which can keep its pre-KO value while Cobblemon plays the faint animation.
    private static final Set<UUID> CONFIRMED_FAINTS = new java.util.HashSet<>();
    // A public cure event is authoritative when Cobblemon's party snapshot lags behind it.
    private static final Set<UUID> CONFIRMED_STATUS_CURES = new java.util.HashSet<>();
    private static final BattleAppearanceTracker<OpponentMemory> APPEARANCES = new BattleAppearanceTracker<>();
    private static final Map<UUID, ItemStack> REVEALED_ITEMS = new java.util.HashMap<>();
    private static final Set<UUID> REVEALED_NO_ITEM = new java.util.HashSet<>();
    private static final Map<UUID, List<ItemHistoryView>> ITEM_HISTORY = new java.util.HashMap<>();
    private static final Map<UUID, List<FormHistoryView>> FORM_HISTORY = new java.util.HashMap<>();
    private static final Map<UUID, String> LAST_SCANNED_FORMS = new java.util.HashMap<>();
    private static final Map<UUID, FormEventKind> PENDING_FORM_EVENTS = new java.util.HashMap<>();
    private static final Map<UUID, AbilityView> REVEALED_ABILITIES = new java.util.HashMap<>();
    private static final Set<UUID> SUPPRESSED_ABILITIES = new java.util.HashSet<>();
    private static final Map<UUID, LinkedHashMap<String, MoveView>> REVEALED_MOVES = new java.util.HashMap<>();
    private static final Map<UUID, LinkedHashMap<String, MoveView>> TRANSFORMED_MOVESETS = new java.util.HashMap<>();
    private static final Map<UUID, PendingMoveUse> PENDING_CALLED_MOVES = new java.util.HashMap<>();
    private static final Map<UUID, PendingMoveTarget> PENDING_MOVE_TARGETS = new java.util.HashMap<>();
    private static PendingHazardMove pendingHazardMove;
    private static PendingSideEffectMove pendingSideEffectMove;
    private static PendingSideRemoval pendingSideRemoval;
    private static CurrentMove currentMove;
    private static long moveSequence;
    private static PendingItemSwap pendingItemSwap;
    private static final Map<UUID, Integer> FREE_NEXT_MOVE_USE = new java.util.HashMap<>();
    private static final Map<UUID, String> LAST_SELECTED_MOVES = new java.util.HashMap<>();
    private static final Map<UUID, PendingBatonPass> PENDING_BATON_PASSES = new java.util.HashMap<>();
    private static final Map<UUID, DynamicTypeState> DYNAMIC_TYPES = new java.util.HashMap<>();
    private static final Map<UUID, String> TERA_TYPES = new java.util.HashMap<>();
    private static final Set<UUID> PREVIOUSLY_ACTIVE = new java.util.HashSet<>();
    private static final Map<UUID, BattleFieldEffects.EffectSide> PENDING_SPECTATOR_SWITCH_INS =
            new java.util.HashMap<>();
    private static final Map<String, LogIdentity> LOG_IDENTITIES = new LinkedHashMap<>();
    private static final Map<UUID, Set<String>> OWN_POKEMON_ALIASES = new java.util.HashMap<>();
    private static final Map<UUID, Set<String>> OPPONENT_POKEMON_ALIASES = new java.util.HashMap<>();
    private static final Set<String> OWN_ACTOR_NAMES = new java.util.LinkedHashSet<>();
    private static final Set<String> OPPONENT_ACTOR_NAMES = new java.util.LinkedHashSet<>();
    private static final Map<UUID, String> TRAINER_BY_POKEMON = new java.util.HashMap<>();
    private static final BattleStatStageTracker OBSERVED_STAT_STAGES = new BattleStatStageTracker();
    private static final Pattern TURN_TIMER = Pattern.compile("(?i)(\\d+)\\s*(?:seconds?|secondes?)\\s*(?:left|remaining|restantes?|restants?)");
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("(?i)(?:%|/)\\d+\\$s");
    private static final Map<String, Pattern> NAME_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ItemStack> ITEM_BY_BATTLE_ID = new java.util.HashMap<>();

    private static UUID battleId;
    private static Instant startedAt;
    private static Instant endedAt;
    private static Instant turnDeadline;
    private static int turn;
    private static int scanCooldown;
    private static String timedPlayer = "";
    private static String ownSideName = "";
    private static String opponentSideName = "";
    private static List<TeamMemberView> ownTeam = List.of();
    private static List<TeamMemberView> opponentSnapshot = List.of();
    private static BattleTeamPreview.Snapshot teamPreview = BattleTeamPreview.Snapshot.EMPTY;
    private static List<TeamMemberView> ownHudSnapshot = List.of();
    private static List<TeamMemberView> opponentHudSnapshot = List.of();
    private static final Map<UUID, RenderablePokemon> PORTRAITS = new java.util.HashMap<>();
    private static final Map<UUID, TeamMemberView> LIVE_OWN = new java.util.HashMap<>();
    private static final Map<UUID, BattleStatsView> OWN_BATTLE_STATS = new java.util.HashMap<>();
    private static final Map<TypeKey, List<TypeView>> TYPE_VIEWS = new java.util.HashMap<>();

    private BattleUiState() {
    }

    public static void tick(MinecraftClient client) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        BattleTeamPreview.tick(client, battle != null);
        if (battle == null) {
            if (battleId != null && endedAt == null) endedAt = Instant.now();
            battleId = null;
            turnDeadline = null;
            return;
        }

        beginIfNeeded(battle);

        if (--scanCooldown <= 0) {
            scanCooldown = 5;
            scanTeams(client, battle, true);
        }
    }

    public static void acceptMessages(Iterable<Text> messages) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        beginIfNeeded(battle);

        MinecraftClient client = MinecraftClient.getInstance();
        // Observe new switch-ins before processing their first item/ability/move message.
        scanTeams(client, battle, false);
        String playerName = client.player == null ? "" : client.player.getName().getString().toLowerCase(Locale.ROOT);

        BattleFieldEffects.beginMessageBatch(spectating);
        PokemonBattleEffects.beginMessageBatch(spectating);
        try {
            synchronized (LOG) {
                for (Text message : messages) {
                    String key = translationKey(message);
                    Object[] args = translationArgs(message);
                    boolean malformedRedCard = malformedRedCard(message, key);
                    if (malformedRedCard) message = repairMalformedBattleMessage(message, key, args);
                    if (malformedRedCard && key.isBlank()) key = "cobblemon.battle.enditem.redcard";
                    String rendered = message.getString();
                    BattleLogEntryType type = BattleLogClassifier.classify(key, rendered);
                    int parsedTurn = BattleLogClassifier.findTurn(key, rendered, args);
                    if (parsedTurn >= 0) turn = parsedTurn;
                    UUID messagePokemon = pokemonFromArgument(argument(args, 0));
                    BattleCalculationHistory.accept(messagePokemon, key, args, turn);
                    rememberFaintState(messagePokemon, key);
                    rememberItemTransferContext(key, args);
                    BattleFieldEffects.accept(key, turn, effectSideFromArgument(argument(args, 0)));
                    rememberFormEvent(key, args);
                    acceptDynamicPokemonState(key, args);
                    rememberStatusApplication(key, args);
                    PokemonBattleEffects.accept(pokemonEffectSubject(key, args), key, args, turn);
                    acceptTurnTimer(rendered);
                    rememberItemState(key, rendered, args);
                    rememberSlotEffects(key, args);
                    rememberPpContext(key, args);
                    rememberUsedMoveEffects(key, args);
                    rememberRevealedMove(key, args);
                    rememberPpChange(key, args);
                    rememberCopiedMove(key, args);
                    rememberRevealedAbility(key, args);
                    rememberStatStages(key, args);

                    // Le paquet de PV permet d'afficher une perte en pourcentage, comme Showdown.
                    if ("cobblemon.battle.damage_dealt".equals(key)) continue;

                    boolean mention = !playerName.isBlank() && rendered.toLowerCase(Locale.ROOT).contains(playerName);
                    Text styled = BattleLogTextFormatter.style(message, key, args, type);
                    addEntry(new BattleLogEntry(Instant.now(), styled, key, type, mention,
                            type != BattleLogEntryType.TURN && type != BattleLogEntryType.HEADER)
                            .onSide(logSide(args, rendered, type)));
                }
            }
        } finally {
            BattleFieldEffects.endMessageBatch();
        }
    }

    public static void acceptHealthChange(BattleHealthChangePacket packet) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        beginIfNeeded(battle);

        var located = battle.getPokemonFromPNX(packet.getPnx());
        ClientBattleActor actor = located.getFirst();
        ActiveClientBattlePokemon active = located.getSecond();
        if (active == null || active.getBattlePokemon() == null) return;

        ClientBattlePokemon pokemon = active.getBattlePokemon();
        boolean opponent = isOpponent(actor, battle);
        if (opponent || spectating) APPEARANCES.observe(active, pokemon.getUuid(), () -> opponentMemory(pokemon.getUuid()));
        float oldHealth = LAST_HEALTH.getOrDefault(pokemon.getUuid(), pokemon.getHpValue());
        float oldMaxHealth = pokemon.getMaxHp();
        float newMaxHealth = packet.getNewMaxHealth() == null ? oldMaxHealth : packet.getNewMaxHealth();
        float newHealth = packet.getNewHealth();
        if (pokemon.isHpFlat() && (oldMaxHealth <= 0.0F || newMaxHealth <= 0.0F)) return;
        if (newHealth > 0.0F) CONFIRMED_FAINTS.remove(pokemon.getUuid());
        LAST_HEALTH.put(pokemon.getUuid(), newHealth);

        float oldPercent = BattleHealthFormatting.percent(pokemon.isHpFlat(), oldHealth, oldMaxHealth);
        float newPercent = BattleHealthFormatting.percent(pokemon.isHpFlat(), newHealth, newMaxHealth);
        // The packet is the final value for the log. Team portraits follow the
        // native animation instead of jumping here and rewinding at the next scan.

        float change = newPercent - oldPercent;
        // Very small positive HP values are meaningful (Focus Sash/Sturdy and
        // custom high-HP formats). Do not round a real packet change away.
        if (turn <= 0 || Math.abs(change) < 0.0001F) return;

        String percentage = BattlePercentageFormatting.number(Math.abs(change));
        RenderablePokemon healthPortrait = opponentRenderable(pokemon);
        MutableText name = Text.literal(speciesName(pokemon.getSpecies(), healthPortrait.getForm()))
                .styled(style -> style.withBold(true));
        Text message = change < 0
                ? Text.translatable("text.tropimon_ui_battle.hp_lost", name, percentage)
                : Text.translatable("text.tropimon_ui_battle.hp_restored", name, percentage);
        BattleLogEntryType type = change < 0 ? BattleLogEntryType.DAMAGE : BattleLogEntryType.HEAL;
        BattleLogImpact impact = change < 0
                ? opponent ? BattleLogImpact.DAMAGE_DEALT : BattleLogImpact.DAMAGE_RECEIVED
                : opponent ? BattleLogImpact.HEAL_OPPONENT : BattleLogImpact.HEAL_OWN;
        Text styled = BattleLogTextFormatter.style(message, "tropimon_ui_battle.health", translationArgs(message), type);
        synchronized (LOG) {
            addEntry(new BattleLogEntry(Instant.now(), styled, "tropimon_ui_battle.health",
                    type, false, true, impact).onSide(opponent ? BattleLogSide.OPPONENT : BattleLogSide.PLAYER));
        }
    }

    /**
     * Uses Cobblemon's authoritative faint packet so every KO cause follows the
     * same path: direct damage, recoil/self-KO, Destiny Bond, Perish Song and OHKO moves.
     */
    public static void acceptFaint(BattleFaintPacket packet) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || packet == null) return;
        beginIfNeeded(battle);
        var located = battle.getPokemonFromPNX(packet.getPnx());
        ClientBattleActor actor = located.getFirst();
        ActiveClientBattlePokemon active = located.getSecond();
        if (active == null || active.getBattlePokemon() == null) return;
        UUID pokemon = active.getBattlePokemon().getUuid();
        boolean opponent = isOpponent(actor, battle);
        if (spectating && !opponent) SPECTATED_LEFT.add(pokemon);
        markKnownFainted(pokemon, opponent);
    }

    private static void rememberFaintState(UUID pokemon, String key) {
        if (pokemon == null || key == null ||
                !(key.endsWith(".faint") || key.endsWith(".fainted"))) return;
        markKnownFainted(pokemon, isOpponentMember(pokemon));
    }

    static void markKnownFainted(UUID pokemon, boolean opponent) {
        if (pokemon == null) return;
        if (pendingSideRemoval != null && (pendingSideRemoval.moveId().equals("rapidspin")
                || pendingSideRemoval.moveId().equals("mortalspin"))
                && pendingSideRemoval.user().equals(pokemon)
                && belongsToCurrentMove(pendingSideRemoval.user(), pendingSideRemoval.turn(),
                pendingSideRemoval.sequence())) {
            if (pendingSideRemoval.activation() != null) {
                BattleFieldEffects.cancelObservedSideRemoval(pendingSideRemoval.activation());
            }
            pendingSideRemoval = null;
        }
        CONFIRMED_FAINTS.add(pokemon);
        LAST_HEALTH.put(pokemon, 0.0F);
        updateKnownHealth(pokemon, 0.0F, opponent);
        BattleCalculationHistory.faint(pokemon, turn);
    }

    private static void beginIfNeeded(ClientBattle battle) {
        if (battle.getBattleId().equals(battleId)) return;
        battleId = battle.getBattleId();
        startedAt = Instant.now();
        endedAt = null;
        turnDeadline = null;
        timedPlayer = "";
        turn = 0;
        scanCooldown = 0;
        ownSideName = "";
        opponentSideName = "";
        ownTeam = List.of();
        opponentSnapshot = List.of();
        PORTRAITS.clear();
        LIVE_OWN.clear(); OWN_BATTLE_STATS.clear(); TYPE_VIEWS.clear();
        OPPONENT_TEAM.clear();
        SPECTATED_LEFT.clear();
        CONFIRMED_FAINTS.clear();
        CONFIRMED_STATUS_CURES.clear();
        spectating = battle.getSpectating();
        BattleTeamPreview.Snapshot capturedPreview = BattleTeamPreview.consume(System.currentTimeMillis());
        teamPreview = spectating ? BattleTeamPreview.Snapshot.EMPTY : capturedPreview;
        ownHudSnapshot = List.of();
        opponentHudSnapshot = List.of();
        APPEARANCES.clear();
        LAST_HEALTH.clear();
        REVEALED_ITEMS.clear();
        REVEALED_NO_ITEM.clear();
        ITEM_HISTORY.clear();
        FORM_HISTORY.clear();
        LAST_SCANNED_FORMS.clear();
        PENDING_FORM_EVENTS.clear();
        REVEALED_ABILITIES.clear();
        SUPPRESSED_ABILITIES.clear();
        REVEALED_MOVES.clear();
        TRANSFORMED_MOVESETS.clear();
        PENDING_CALLED_MOVES.clear();
        PENDING_MOVE_TARGETS.clear();
        pendingHazardMove = null;
        pendingSideEffectMove = null;
        pendingSideRemoval = null;
        currentMove = null;
        moveSequence = 0;
        pendingItemSwap = null;
        FREE_NEXT_MOVE_USE.clear();
        LAST_SELECTED_MOVES.clear();
        PENDING_BATON_PASSES.clear();
        DYNAMIC_TYPES.clear();
        TERA_TYPES.clear();
        PREVIOUSLY_ACTIVE.clear();
        PENDING_SPECTATOR_SWITCH_INS.clear();
        LOG_IDENTITIES.clear();
        OWN_POKEMON_ALIASES.clear();
        OPPONENT_POKEMON_ALIASES.clear();
        OWN_ACTOR_NAMES.clear();
        OPPONENT_ACTOR_NAMES.clear();
        TRAINER_BY_POKEMON.clear();
        NAME_PATTERNS.clear();
        ITEM_BY_BATTLE_ID.clear();
        OBSERVED_STAT_STAGES.reset();
        BattleFieldEffects.reset();
        PokemonBattleEffects.reset();
        BattleCalculationHistory.reset();
        TropimonDamageCalcBridge.reset();
        synchronized (LOG) {
            LOG.clear();
            addBattleHeader(battle);
        }
        BattleUiRenderer.resetScroll();
    }

    private static void addBattleHeader(ClientBattle battle) {
        boolean random = isRandomFormat(battle.getBattleFormat().getMod(), battle.getBattleFormat().getRuleSet(),
                battle.getBattleFormat().getBattleType().getName());
        Text format = random
                ? Text.translatable("text.tropimon_ui_battle.random_battle")
                : battle.getBattleFormat().getBattleType().getDisplayName();
        Text title = Text.translatable("text.tropimon_ui_battle.format", battle.getBattleFormat().getGen(), format);
        addEntry(new BattleLogEntry(Instant.now(), title, "tropimon_ui_battle.header", BattleLogEntryType.HEADER, false, false));

        for (String rule : battle.getBattleFormat().getRuleSet()) {
            String normalized = normalizeRule(rule);
            String key = switch (normalized) {
                case "speciesclause" -> "text.tropimon_ui_battle.rule.species_clause";
                case "hppercentagemod" -> "text.tropimon_ui_battle.rule.hp_percentage";
                case "sleepclausemod", "sleepclause" -> "text.tropimon_ui_battle.rule.sleep_clause";
                case "illusionlevelmod" -> "text.tropimon_ui_battle.rule.illusion_level";
                case "rated" -> "text.tropimon_ui_battle.rated";
                default -> "";
            };
            if (!key.isBlank()) {
                addEntry(new BattleLogEntry(Instant.now(), Text.translatable(key), key,
                        BattleLogEntryType.SYSTEM, false, false));
            }
        }

        String side1 = sideName(battle.getSide1());
        String side2 = sideName(battle.getSide2());
        if (!side1.isBlank() && !side2.isBlank()) {
            Text start = Text.translatable("text.tropimon_ui_battle.battle_started", side1, side2);
            addEntry(new BattleLogEntry(Instant.now(), start, "tropimon_ui_battle.battle_started",
                    BattleLogEntryType.HEADER, false, false));
        }
    }

    private static void scanTeams(MinecraftClient client, ClientBattle battle) {
        scanTeams(client, battle, true);
    }

    private static void scanTeams(MinecraftClient client, ClientBattle battle, boolean resolveSwitchIns) {
        scanTeams(battle, client.player == null ? null : client.player.getUuid(), resolveSwitchIns);
    }

    static void scanTeams(ClientBattle battle, UUID viewer) {
        scanTeams(battle, viewer, true);
    }

    private static void scanTeams(ClientBattle battle, UUID viewer, boolean resolveSwitchIns) {
        ClientBattleSide localSide = leftSide(battle, viewer);
        if (localSide == null) return;
        ClientBattleSide opponentSide = localSide == battle.getSide1() ? battle.getSide2() : battle.getSide1();
        ownSideName = sideName(localSide);
        opponentSideName = sideName(opponentSide);
        registerActorIdentities(localSide, false);
        registerActorIdentities(opponentSide, true);
        Map<UUID, ClientBattlePokemon> localActive = activePokemonById(localSide);
        Map<UUID, ClientBattlePokemon> opponentActive = activePokemonById(opponentSide);
        java.util.HashSet<UUID> activePokemon = new java.util.HashSet<>(localActive.keySet());
        activePokemon.addAll(opponentActive.keySet());
        if (spectating) {
            localActive.keySet().stream().filter(id -> !PREVIOUSLY_ACTIVE.contains(id))
                    .forEach(id -> PENDING_SPECTATOR_SWITCH_INS.put(id,
                            BattleFieldEffects.EffectSide.PLAYER_FIELD));
            opponentActive.keySet().stream().filter(id -> !PREVIOUSLY_ACTIVE.contains(id))
                    .forEach(id -> PENDING_SPECTATOR_SWITCH_INS.put(id,
                            BattleFieldEffects.EffectSide.OPPONENT_FIELD));
        }
        clearSwitchedOutDynamicTypes(activePokemon);
        PokemonBattleEffects.retainActive(activePokemon);

        List<TeamMemberView> localViews = new ArrayList<>();
        if (!spectating) OWN_BATTLE_STATS.clear();
        if (!spectating) for (ClientBattleActor actor : localSide.getActors()) {
            Set<UUID> activeIds = activeIds(actor);
            for (Pokemon pokemon : actor.getPokemon()) {
                TRAINER_BY_POKEMON.put(pokemon.getUuid(), actor.getDisplayName().getString());
                String speciesName = speciesName(pokemon.getSpecies(), pokemon.getForm());
                registerPokemonIdentity(pokemon.getDisplayName(false).getString(), speciesName, false);
                registerPokemonAlias(pokemon.getUuid(), pokemon.getDisplayName(false).getString(), false);
                registerPokemonAlias(pokemon.getUuid(), speciesName, false);
                if (pokemon.getNickname() != null) {
                    registerPokemonIdentity(pokemon.getNickname().getString(), speciesName, false);
                    registerPokemonAlias(pokemon.getUuid(), pokemon.getNickname().getString(), false);
                }
                ClientBattlePokemon activeView = localActive.get(pokemon.getUuid());
                if (activeView != null) {
                    registerPokemonIdentity(activeView.getDisplayName().getString(), speciesName, false);
                    registerPokemonAlias(pokemon.getUuid(), activeView.getDisplayName().getString(), false);
                }
                float hp = CONFIRMED_FAINTS.contains(pokemon.getUuid()) || pokemon.getMaxHealth() <= 0
                        ? 0.0F : 100.0F * pokemon.getCurrentHealth() / pokemon.getMaxHealth();
                String status = visibleStatus(pokemon.getUuid(),
                        pokemon.getStatus() == null ? "" : pokemon.getStatus().getStatus().getShowdownName());
                ItemStack item = currentItem(pokemon.getUuid(), pokemon.heldItem());
                AbilityView ability = REVEALED_ABILITIES.getOrDefault(pokemon.getUuid(),
                        pokemon.getAbility() == null ? null : abilityView(pokemon.getAbility().getTemplate()));
                if (ability != null && SUPPRESSED_ABILITIES.contains(pokemon.getUuid())) {
                    ability = ability.withSuppressed(true);
                }
                List<MoveView> moves = new ArrayList<>();
                for (Move move : pokemon.getMoveSet()) moves.add(moveView(move));
                if (viewer != null && actor.getUuid().equals(viewer)) {
                    OWN_BATTLE_STATS.put(pokemon.getUuid(), new BattleStatsView(
                            pokemon.getMaxHealth(), pokemon.getStat(Stats.ATTACK),
                            pokemon.getStat(Stats.DEFENCE), pokemon.getStat(Stats.SPECIAL_ATTACK),
                            pokemon.getStat(Stats.SPECIAL_DEFENCE), pokemon.getStat(Stats.SPEED)));
                }
                TeamMemberView previous = null;
                for (TeamMemberView member : ownTeam) if (member.uuid().equals(pokemon.getUuid())) { previous = member; break; }
                localViews.add(TeamMemberView.snapshot(previous, pokemon.getUuid(), speciesName,
                        pokemon.getLevel(), hp, status, pokemon.getCurrentHealth() <= 0,
                        activeIds.contains(pokemon.getUuid()), item, pokemon.asRenderablePokemon(),
                        currentTypeViews(pokemon.getUuid(), pokemon.getForm().getTypes()), ability, moves,
                        statStages(localActive.get(pokemon.getUuid())), true));
                PokemonBattleEffects.synchronizeStatus(pokemon.getUuid(), status, turn);
                LAST_HEALTH.putIfAbsent(pokemon.getUuid(), (float) pokemon.getCurrentHealth());
            }
        }
        if (!spectating && !ownTeam.equals(localViews)) ownTeam = List.copyOf(localViews);

        OPPONENT_TEAM.replaceAll((uuid, member) -> member.withActive(activePokemon.contains(uuid)));
        if (spectating) scanPublicSide(localSide, localActive, false);
        scanPublicSide(opponentSide, opponentActive, true);
        if (spectating && resolveSwitchIns) resolveSpectatorSwitchIns(activePokemon);
    }

    private static void resolveSpectatorSwitchIns(Set<UUID> activePokemon) {
        var iterator = PENDING_SPECTATOR_SWITCH_INS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BattleFieldEffects.EffectSide> entry = iterator.next();
            if (!activePokemon.contains(entry.getKey()) || absorbToxicSpikesOnEntry(entry.getKey(), entry.getValue())) {
                iterator.remove();
            }
        }
    }

    /** Returns false only while the public switch-in identity is not available yet. */
    static boolean absorbToxicSpikesOnEntry(UUID pokemon, BattleFieldEffects.EffectSide side) {
        if (!spectating || pokemon == null || side == null || side == BattleFieldEffects.EffectSide.FIELD) return true;
        if (!BattleFieldEffects.activeOnSide("toxicspikes", side)) return true;
        TeamMemberView member = memberById(pokemon);
        if (member == null || member.types().isEmpty()) return false;
        boolean poison = member.types().stream().anyMatch(type -> type.id().equalsIgnoreCase("poison"));
        if (!poison) return true;

        String item = heldItemId(currentItem(pokemon, member.heldItem()));
        boolean forcedGround = BattleFieldEffects.active("gravity") || item.equals("iron_ball")
                || item.equals("ironball") || PokemonBattleEffects.active(pokemon, "smackdown", turn)
                || PokemonBattleEffects.active(pokemon, "ingrain", turn);
        if (!forcedGround) {
            boolean flying = member.types().stream().anyMatch(type -> type.id().equalsIgnoreCase("flying"));
            AbilityView ability = abilityFor(pokemon);
            boolean levitate = ability != null && normalizeMove(ability.id()).equals("levitate");
            if (ability == null) {
                levitate = possibleAbilities(member).stream()
                        .anyMatch(candidate -> normalizeMove(candidate.id()).equals("levitate"));
            }
            boolean itemSuppressed = BattleFieldEffects.active("magicroom")
                    || PokemonBattleEffects.active(pokemon, "embargo", turn)
                    || ability != null && normalizeMove(ability.id()).equals("klutz");
            boolean balloon = !itemSuppressed && (item.equals("air_balloon") || item.equals("airballoon"));
            boolean airborneEffect = PokemonBattleEffects.active(pokemon, "magnetrise", turn)
                    || PokemonBattleEffects.active(pokemon, "telekinesis", turn);
            if (flying || levitate || balloon || airborneEffect) return true;
        }
        BattleFieldEffects.removeObservedSideConditions(Map.of(side, Set.of("toxicspikes")));
        return true;
    }

    private static void scanPublicSide(ClientBattleSide side, Map<UUID, ClientBattlePokemon> activeById, boolean right) {
        for (ClientBattleActor actor : side.getActors()) {
            for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                ClientBattlePokemon pokemon = active.getBattlePokemon();
                if (pokemon == null) continue;
                TRAINER_BY_POKEMON.put(pokemon.getUuid(), actor.getDisplayName().getString());
                if (!right) SPECTATED_LEFT.add(pokemon.getUuid());
                APPEARANCES.observe(active, pokemon.getUuid(), () -> opponentMemory(pokemon.getUuid()));
                float hp = CONFIRMED_FAINTS.contains(pokemon.getUuid()) ? 0.0F
                        : BattleHealthFormatting.percent(pokemon.isHpFlat(), pokemon.getHpValue(), pokemon.getMaxHp());
                String status = visibleStatus(pokemon.getUuid(),
                        pokemon.getStatus() == null ? "" : pokemon.getStatus().getShowdownName());
                TeamMemberView previous = OPPONENT_TEAM.get(pokemon.getUuid());
                ItemStack item = REVEALED_NO_ITEM.contains(pokemon.getUuid())
                        ? ItemStack.EMPTY
                        : REVEALED_ITEMS.getOrDefault(pokemon.getUuid(),
                        previous == null ? ItemStack.EMPTY : previous.heldItem());
                AbilityView ability = REVEALED_ABILITIES.getOrDefault(pokemon.getUuid(),
                        previous == null ? null : previous.ability());
                if (ability != null) ability = ability.withSuppressed(SUPPRESSED_ABILITIES.contains(pokemon.getUuid()));
                List<MoveView> moves = List.copyOf(activeOpponentMoveMap(pokemon.getUuid()).values());
                RenderablePokemon portrait = opponentRenderable(pokemon);
                String speciesName = speciesName(pokemon.getSpecies(), portrait.getForm());
                rememberScannedForm(pokemon.getUuid(), speciesName);
                registerPokemonIdentity(pokemon.getDisplayName().getString(), speciesName, right);
                registerPokemonAlias(pokemon.getUuid(), pokemon.getDisplayName().getString(), right);
                registerPokemonAlias(pokemon.getUuid(), speciesName, right);
                OPPONENT_TEAM.put(pokemon.getUuid(), TeamMemberView.snapshot(previous, pokemon.getUuid(),
                        speciesName, pokemon.getLevel(), hp, status,
                        hp <= 0.0F, true, item, portrait,
                        currentTypeViews(pokemon.getUuid(), portrait.getForm().getTypes()), ability, moves,
                        statStages(activeById.get(pokemon.getUuid())), true));
                PokemonBattleEffects.synchronizeStatus(pokemon.getUuid(), status, turn);
                LAST_HEALTH.putIfAbsent(pokemon.getUuid(), pokemon.getHpValue());
            }
        }
    }

    private static Map<UUID, ClientBattlePokemon> activePokemonById(ClientBattleSide side) {
        Map<UUID, ClientBattlePokemon> result = new java.util.HashMap<>();
        for (ClientBattleActor actor : side.getActors()) {
            for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                ClientBattlePokemon pokemon = active.getBattlePokemon();
                if (pokemon != null) result.put(pokemon.getUuid(), pokemon);
                if (pokemon != null) BattleCalculationHistory.observe(active, pokemon.getUuid(),
                        isOpponent(actor, CobblemonClient.INSTANCE.getBattle())
                                ? BattleFieldEffects.EffectSide.OPPONENT_FIELD : BattleFieldEffects.EffectSide.PLAYER_FIELD);
            }
        }
        return result;
    }

    /** Called at the actual native slot change, after switch animations rather than when they are queued. */
    public static void beforeActivePokemonChanged(ActiveClientBattlePokemon slot, ClientBattlePokemon incoming) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        beginIfNeeded(battle);
        if (slot.getBattlePokemon() == incoming) return;
        boolean opponent = spectating
                ? slot.getActor() != null && slot.getActor().getSide() != battle.getSide2()
                : isOpponent(slot.getActor(), battle);
        if (spectating && incoming != null) {
            BattleFieldEffects.EffectSide incomingSide = opponent
                    ? BattleFieldEffects.EffectSide.OPPONENT_FIELD
                    : BattleFieldEffects.EffectSide.PLAYER_FIELD;
            PENDING_SPECTATOR_SWITCH_INS.put(incoming.getUuid(), incomingSide);
            // A previously revealed Pokemon already has enough public data to
            // resolve its entry here. A first appearance stays pending until the
            // normal public-team scan has registered its species and types.
            if (absorbToxicSpikesOnEntry(incoming.getUuid(), incomingSide)) {
                PENDING_SPECTATOR_SWITCH_INS.remove(incoming.getUuid());
            }
        }
        ClientBattlePokemon outgoing = slot.getBattlePokemon();
        if (outgoing != null) {
            if (incoming != null && consumePendingBatonPass(outgoing.getUuid())) {
                OBSERVED_STAT_STAGES.transfer(outgoing.getUuid(), incoming.getUuid());
            } else {
                OBSERVED_STAT_STAGES.forget(outgoing.getUuid());
            }
            clearTemporaryDynamicType(outgoing.getUuid());
        }
        // An Illusion reveal already restored the decoy's pre-appearance HP.
        // Its slot is no longer observed: never overwrite that restoration here.
        if (outgoing != null && (!(opponent || spectating) || APPEARANCES.observes(slot, outgoing.getUuid()))) {
            float outgoingHealth = CONFIRMED_FAINTS.contains(outgoing.getUuid()) ? 0.0F
                    : BattleHealthFormatting.percent(outgoing.isHpFlat(), outgoing.getHpValue(), outgoing.getMaxHp());
            updateKnownHealth(outgoing.getUuid(), outgoingHealth, opponent);
        }
        if (incoming == null) { APPEARANCES.leave(slot); BattleCalculationHistory.leave(slot); return; }
        if (incoming.getHpValue() > 0.0F) CONFIRMED_FAINTS.remove(incoming.getUuid());
        BattleCalculationHistory.observe(slot, incoming.getUuid(), opponent
                ? BattleFieldEffects.EffectSide.OPPONENT_FIELD : BattleFieldEffects.EffectSide.PLAYER_FIELD);
        if (spectating && !opponent) SPECTATED_LEFT.add(incoming.getUuid());
        if (opponent || spectating) {
            APPEARANCES.begin(slot, incoming.getUuid(), opponentMemory(incoming.getUuid()));
        }
        LAST_HEALTH.put(incoming.getUuid(), incoming.getHpValue());
    }

    public static void refreshTeams() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != null) scanTeams(MinecraftClient.getInstance(), battle);
    }

    public static void acceptIdentityReplacement(com.cobblemon.mod.common.net.messages.client.battle.BattleReplacePokemonPacket packet) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        beginIfNeeded(battle);
        var located = battle.getPokemonFromPNX(packet.getPnx());
        var slot = located.getSecond();
        if (slot == null || slot.getBattlePokemon() == null || !(spectating || isOpponent(located.getFirst(), battle))) return;
        var apparent = slot.getBattlePokemon();
        UUID actual = packet.getRealPokemon().getUuid();
        var reveal = APPEARANCES.reveal(slot, apparent.getUuid(), actual);
        if (reveal == null) return;
        reconcileIllusion(apparent.getUuid(), actual, reveal.before());
        BattleCalculationHistory.reveal(slot, apparent.getUuid(), actual);
        String oldName = speciesName(apparent.getSpecies(), opponentRenderable(apparent).getForm());
        var properties = packet.getRealPokemon().getProperties();
        var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(properties.getSpecies());
        RenderablePokemon realPokemon = species == null ? null
                : new RenderablePokemon(species, packet.getRealPokemon().getAspects(), ItemStack.EMPTY);
        String realName = species == null ? packet.getRealPokemon().getDisplayName().getString()
                : speciesName(species, realPokemon.getForm());
        DynamicTypeState dynamicTypes = DYNAMIC_TYPES.get(actual);
        if (dynamicTypes != null && !dynamicTypes.persistent() && realPokemon != null) {
            DYNAMIC_TYPES.put(actual, new DynamicTypeState(dynamicTypes.types(), false,
                    typeViews(realPokemon.getForm().getTypes())));
        }
        rememberFormHistory(actual, oldName, realName, FormEventKind.ILLUSION_REVEALED);
        LAST_SCANNED_FORMS.put(actual, realName);
        LAST_HEALTH.put(actual, packet.getRealPokemon().getHpValue());
    }

    static void reconcileIllusion(UUID apparent, UUID actual, OpponentMemory before) {
        if (apparent.equals(actual)) return;
        if (SPECTATED_LEFT.contains(apparent)) SPECTATED_LEFT.add(actual);
        if (CONFIRMED_FAINTS.remove(apparent)) CONFIRMED_FAINTS.add(actual);
        OpponentMemory during = opponentMemory(apparent);
        before.restore(apparent);
        REVEALED_MOVES.put(actual, BattleIllusionMoves.transfer(before.moves(), during.moves(),
                REVEALED_MOVES.getOrDefault(actual, new LinkedHashMap<>())));
        if (!java.util.Objects.equals(during.transformed(), before.transformed()) && during.transformed() != null) {
            TRANSFORMED_MOVESETS.put(actual, new LinkedHashMap<>(during.transformed()));
        }
        if (!ItemStack.areEqual(during.item(), before.item()) || during.noItem() != before.noItem()) {
            if (during.item().isEmpty()) REVEALED_ITEMS.remove(actual); else REVEALED_ITEMS.put(actual, during.item().copy());
            if (during.noItem()) REVEALED_NO_ITEM.add(actual); else REVEALED_NO_ITEM.remove(actual);
        }
        appendNewHistory(ITEM_HISTORY, actual, before.items(), during.items());
        appendNewHistory(FORM_HISTORY, actual, before.forms(), during.forms());
        // This identity replacement is a public Illusion reveal, for both normal and Hisuian Zoroark.
        REVEALED_ABILITIES.put(actual, new AbilityView("illusion", Text.translatable("cobblemon.ability.illusion"),
                Text.translatable("cobblemon.ability.illusion.desc")));
        if (during.suppressed()) SUPPRESSED_ABILITIES.add(actual);
        if (!java.util.Objects.equals(during.types(), before.types())) restoreValue(DYNAMIC_TYPES, actual, during.types());
        if (!java.util.Objects.equals(during.tera(), before.tera())) restoreValue(TERA_TYPES, actual, during.tera());
        if (!java.util.Objects.equals(during.lastMove(), before.lastMove())) restoreValue(LAST_SELECTED_MOVES, actual, during.lastMove());
        if (!during.observedStages().equals(before.observedStages())) {
            OBSERVED_STAT_STAGES.replace(actual, during.observedStages());
        }
        if (during.statusCured() != before.statusCured()) {
            if (during.statusCured()) CONFIRMED_STATUS_CURES.add(actual);
            else CONFIRMED_STATUS_CURES.remove(actual);
        }
        transferValue(PENDING_CALLED_MOVES, apparent, actual);
        transferValue(PENDING_MOVE_TARGETS, apparent, actual);
        transferValue(FREE_NEXT_MOVE_USE, apparent, actual);
        transferValue(PENDING_BATON_PASSES, apparent, actual);
        PokemonBattleEffects.reassign(apparent, actual);
        PENDING_FORM_EVENTS.remove(apparent);
        refreshOpponentMoves(actual);
    }

    private static <T> void appendNewHistory(Map<UUID, List<T>> history, UUID actual, List<T> before, List<T> during) {
        List<T> combined = new ArrayList<>(history.getOrDefault(actual, List.of()));
        for (T value : during) if (!before.contains(value) && !combined.contains(value)) combined.add(value);
        while (combined.size() > 12) combined.removeFirst();
        if (!combined.isEmpty()) history.put(actual, List.copyOf(combined));
    }

    private static <T> void transferValue(Map<UUID, T> values, UUID from, UUID to) {
        T value = values.remove(from);
        if (value != null) values.put(to, value);
    }

    private static <T> void restoreValue(Map<UUID, T> values, UUID pokemon, T value) {
        if (value == null) values.remove(pokemon); else values.put(pokemon, value);
    }

    static OpponentMemory opponentMemory(UUID pokemon) {
        return new OpponentMemory(OPPONENT_TEAM.get(pokemon), LAST_HEALTH.get(pokemon),
                Map.copyOf(REVEALED_MOVES.getOrDefault(pokemon, new LinkedHashMap<>())),
                TRANSFORMED_MOVESETS.containsKey(pokemon) ? Map.copyOf(TRANSFORMED_MOVESETS.get(pokemon)) : null,
                REVEALED_ITEMS.getOrDefault(pokemon, ItemStack.EMPTY).copy(), REVEALED_NO_ITEM.contains(pokemon),
                ITEM_HISTORY.getOrDefault(pokemon, List.of()), FORM_HISTORY.getOrDefault(pokemon, List.of()),
                REVEALED_ABILITIES.get(pokemon), SUPPRESSED_ABILITIES.contains(pokemon), LAST_SCANNED_FORMS.get(pokemon),
                Set.copyOf(publicAliases(pokemon).getOrDefault(pokemon, Set.of())), DYNAMIC_TYPES.get(pokemon),
                TERA_TYPES.get(pokemon), LAST_SELECTED_MOVES.get(pokemon),
                OBSERVED_STAT_STAGES.stages(pokemon), CONFIRMED_STATUS_CURES.contains(pokemon));
    }

    record OpponentMemory(TeamMemberView member, Float health, Map<String, MoveView> moves,
            Map<String, MoveView> transformed, ItemStack item, boolean noItem,
            List<ItemHistoryView> items, List<FormHistoryView> forms, AbilityView ability, boolean suppressed,
            String scannedForm, Set<String> aliases, DynamicTypeState types, String tera, String lastMove,
            Map<String, Integer> observedStages, boolean statusCured) {
        void restore(UUID pokemon) {
            restoreValue(OPPONENT_TEAM, pokemon, member == null ? null : member.withActive(false));
            restoreValue(LAST_HEALTH, pokemon, health);
            if (moves.isEmpty()) REVEALED_MOVES.remove(pokemon); else REVEALED_MOVES.put(pokemon, new LinkedHashMap<>(moves));
            restoreValue(TRANSFORMED_MOVESETS, pokemon, transformed == null ? null : new LinkedHashMap<>(transformed));
            restoreValue(REVEALED_ITEMS, pokemon, item.isEmpty() ? null : item.copy());
            if (noItem) REVEALED_NO_ITEM.add(pokemon); else REVEALED_NO_ITEM.remove(pokemon);
            restoreValue(ITEM_HISTORY, pokemon, items.isEmpty() ? null : items);
            restoreValue(FORM_HISTORY, pokemon, forms.isEmpty() ? null : forms);
            restoreValue(REVEALED_ABILITIES, pokemon, ability);
            if (suppressed) SUPPRESSED_ABILITIES.add(pokemon); else SUPPRESSED_ABILITIES.remove(pokemon);
            restoreValue(LAST_SCANNED_FORMS, pokemon, scannedForm);
            if (aliases.isEmpty()) publicAliases(pokemon).remove(pokemon);
            else publicAliases(pokemon).put(pokemon, new java.util.HashSet<>(aliases));
            restoreValue(DYNAMIC_TYPES, pokemon, types);
            restoreValue(TERA_TYPES, pokemon, tera);
            restoreValue(LAST_SELECTED_MOVES, pokemon, lastMove);
            OBSERVED_STAT_STAGES.replace(pokemon, observedStages);
            if (statusCured) CONFIRMED_STATUS_CURES.add(pokemon); else CONFIRMED_STATUS_CURES.remove(pokemon);
        }
    }

    private static Set<UUID> activeIds(ClientBattleActor actor) {
        java.util.HashSet<UUID> ids = new java.util.HashSet<>();
        for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
            if (active.getBattlePokemon() != null) ids.add(active.getBattlePokemon().getUuid());
        }
        return ids;
    }

    static List<StatStageView> statStages(ClientBattlePokemon pokemon) {
        if (pokemon == null) return List.of();
        List<StatStageView> stages = new ArrayList<>();
        for (var entry : resolvedStatStageValues(pokemon.getUuid(), pokemon.getStatChanges()).entrySet()) {
            Stat stat = statById(entry.getKey());
            if (stat != null) stages.add(new StatStageView(stat.getDisplayName(), entry.getKey(), entry.getValue()));
        }
        stages.sort(java.util.Comparator.comparingInt(stage -> statOrder(stage.id())));
        return List.copyOf(stages);
    }

    /** One authoritative merge used by both the HUD and damage-calculation inputs. */
    static Map<String, Integer> resolvedStatStageValues(UUID pokemon, Map<Stat, Integer> nativeStages) {
        Map<String, Integer> baseline = new LinkedHashMap<>();
        if (nativeStages != null) {
            for (Map.Entry<Stat, Integer> entry : nativeStages.entrySet()) {
                int value = entry.getValue() == null ? 0 : entry.getValue();
                Stat stat = entry.getKey();
                if (stat == null) continue;
                if (value != 0) baseline.put(stat.getShowdownId(), Math.clamp(value, -6, 6));
            }
        }
        OBSERVED_STAT_STAGES.initializeIfAbsent(pokemon, baseline);
        return OBSERVED_STAT_STAGES.stages(pokemon);
    }

    private static void rememberStatStages(String key, Object[] args) {
        if (key == null) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        UUID subject = pokemonFromArgument(argument(args, 0));
        if (normalized.startsWith("cobblemon.battle.boost.") || normalized.startsWith("cobblemon.battle.unboost.")) {
            Stat stat = statFromArgument(argument(args, 1));
            if (subject == null || stat == null || normalized.contains(".cap.")) return;
            int amount = normalized.contains(".severe") ? 3 : normalized.contains(".sharp") ? 2 : 1;
            OBSERVED_STAT_STAGES.change(subject, stat.getShowdownId(),
                    normalized.startsWith("cobblemon.battle.unboost.") ? -amount : amount);
            return;
        }
        if (normalized.equals("cobblemon.battle.setboost.bellydrum")
                || normalized.equals("cobblemon.battle.setboost.angerpoint")) {
            OBSERVED_STAT_STAGES.set(subject, "atk", 6);
            return;
        }
        switch (normalized) {
            case "cobblemon.battle.clearboost" -> OBSERVED_STAT_STAGES.clear(subject);
            case "cobblemon.battle.clearallboost" -> OBSERVED_STAT_STAGES.clearAll();
            case "cobblemon.battle.clearallnegativeboost", "cobblemon.battle.clearallnegativeboost.zeffect" ->
                    OBSERVED_STAT_STAGES.clearNegative(subject);
            case "cobblemon.battle.invertboost" -> OBSERVED_STAT_STAGES.invert(subject);
            case "cobblemon.battle.copyboost.generic" ->
                    OBSERVED_STAT_STAGES.copy(subject, pokemonFromArgument(argument(args, 1)));
            case "cobblemon.battle.swapboost.guardswap" -> OBSERVED_STAT_STAGES.swap(subject,
                    statSwapTarget(subject, "guardswap", args), Set.of("def", "spd"));
            case "cobblemon.battle.swapboost.powerswap" -> OBSERVED_STAT_STAGES.swap(subject,
                    statSwapTarget(subject, "powerswap", args), Set.of("atk", "spa"));
            case "cobblemon.battle.swapboost.generic", "cobblemon.battle.swapboost.heartswap" ->
                    OBSERVED_STAT_STAGES.swap(subject, statSwapTarget(subject,
                                    normalized.endsWith("heartswap") ? "heartswap" : "", args),
                            Set.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion"));
            default -> { }
        }
    }

    private static UUID statSwapTarget(UUID subject, String expectedMove, Object[] args) {
        UUID explicit = pokemonFromArgument(argument(args, 1));
        if (explicit != null) return explicit;
        PendingMoveTarget pending = PENDING_MOVE_TARGETS.remove(subject);
        return pending != null && pending.turn() == turn
                && (expectedMove.isBlank() || pending.move().equals(expectedMove)) ? pending.target() : null;
    }

    private static Stat statFromArgument(Object argument) {
        if (!(argument instanceof Text text)) return null;
        String name = text.getString().trim();
        for (Stats stat : Stats.values()) {
            if (stat.getShowdownId().equalsIgnoreCase(name) || stat.getDisplayName().getString().equalsIgnoreCase(name)) return stat;
        }
        return null;
    }

    private static Stat statById(String id) {
        for (Stats stat : Stats.values()) if (stat.getShowdownId().equalsIgnoreCase(id)) return stat;
        return null;
    }

    private static List<TypeView> typeViews(Iterable<ElementalType> types) {
        List<ElementalType> input = new ArrayList<>();
        if (types != null) types.forEach(input::add);
        TypeKey key = new TypeKey(List.copyOf(input), net.minecraft.util.Language.getInstance(), UiResourceEpoch.current());
        List<TypeView> cached = TYPE_VIEWS.get(key);
        if (cached != null) return cached;
        List<TypeView> result = new ArrayList<>();
        if (types != null) {
            for (ElementalType type : types) result.add(new TypeView(type.getName(), type.getDisplayName()));
        }
        if (TYPE_VIEWS.size() >= 128) TYPE_VIEWS.clear();
        List<TypeView> snapshot = List.copyOf(result);
        TYPE_VIEWS.put(key, snapshot);
        return snapshot;
    }
    private record TypeKey(List<ElementalType> types, net.minecraft.util.Language language, long resources) { }

    static List<TypeView> currentTypeViews(UUID pokemon, Iterable<ElementalType> baseTypes) {
        DynamicTypeState override = DYNAMIC_TYPES.get(pokemon);
        return override == null ? typeViews(baseTypes) : override.types();
    }

    private static void acceptDynamicPokemonState(String key, Object[] args) {
        if (key == null || key.isBlank()) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        UUID subject = pokemonFromArgument(argument(args, 0));
        if (subject == null) return;

        if (normalized.equals("cobblemon.battle.start.typechange") || normalized.equals("cobblemon.battle.terastallize")) {
            List<TypeView> types = typesFromArgument(argument(args, 1));
            if (!types.isEmpty()) {
                boolean tera = normalized.equals("cobblemon.battle.terastallize");
                if (tera) TERA_TYPES.put(subject, types.getFirst().id());
                if (!tera || !types.getFirst().id().equalsIgnoreCase("stellar")) {
                    DYNAMIC_TYPES.put(subject, new DynamicTypeState(types, tera, baseTypesBeforeOverride(subject)));
                }
                updateKnownTypes(subject);
            }
            return;
        }
        if (normalized.equals("cobblemon.battle.start.typeadd")) {
            TypeView added = typeFromArgument(argument(args, 1));
            if (added == null) return;
            List<TypeView> types = new ArrayList<>(typesFor(subject));
            if (types.stream().noneMatch(type -> type.id().equalsIgnoreCase(added.id()))) types.add(added);
            DYNAMIC_TYPES.put(subject, new DynamicTypeState(types, false, baseTypesBeforeOverride(subject)));
            updateKnownTypes(subject);
            return;
        }
        if (normalized.equals("cobblemon.battle.start.reflecttype")) {
            UUID source = pokemonFromArgument(argument(args, 1));
            List<TypeView> copied = typesFor(source);
            if (!copied.isEmpty()) {
                DYNAMIC_TYPES.put(subject, new DynamicTypeState(copied, false, baseTypesBeforeOverride(subject)));
                updateKnownTypes(subject);
            }
        }
    }

    private static TypeView typeFromArgument(Object value) {
        if (!(value instanceof Text text)) return null;
        String translation = translationKey(text);
        if (!translation.isBlank()) {
            String id = translation.substring(translation.lastIndexOf('.') + 1)
                    .replace("_type", "").replace("type_", "");
            ElementalType type = ElementalTypes.get(id);
            if (type != null) return new TypeView(type.getName(), type.getDisplayName());
        }
        String label = text.getString();
        for (ElementalType type : ElementalTypes.all()) {
            if (type.getName().equalsIgnoreCase(label) ||
                    type.getDisplayName().getString().equalsIgnoreCase(label)) {
                return new TypeView(type.getName(), type.getDisplayName());
            }
        }
        return null;
    }

    private static List<TypeView> typesFromArgument(Object value) {
        TypeView single = typeFromArgument(value);
        if (single != null) return List.of(single);
        if (!(value instanceof Text text)) return List.of();
        var result = new ArrayList<TypeView>();
        for (String label : text.getString().split("[/,]")) {
            TypeView type = typeFromArgument(Text.literal(label.trim()));
            if (type != null && result.stream().noneMatch(existing -> existing.id().equals(type.id()))) result.add(type);
        }
        return List.copyOf(result);
    }

    static String teraType(UUID pokemon) { return TERA_TYPES.getOrDefault(pokemon, ""); }

    private static List<TypeView> typesFor(UUID pokemon) {
        if (pokemon == null) return List.of();
        DynamicTypeState dynamic = DYNAMIC_TYPES.get(pokemon);
        if (dynamic != null) return dynamic.types();
        for (TeamMemberView member : ownTeam) if (member.uuid().equals(pokemon)) return member.types();
        TeamMemberView opponent = OPPONENT_TEAM.get(pokemon);
        return opponent == null ? List.of() : opponent.types();
    }

    private static List<TypeView> baseTypesBeforeOverride(UUID pokemon) {
        DynamicTypeState existing = DYNAMIC_TYPES.get(pokemon);
        return existing == null ? typesFor(pokemon) : existing.originalTypes();
    }

    private static void updateKnownTypes(UUID pokemon) {
        DynamicTypeState dynamic = DYNAMIC_TYPES.get(pokemon);
        if (dynamic == null) return;
        updateKnownTypes(pokemon, dynamic.types());
    }

    private static void updateKnownTypes(UUID pokemon, List<TypeView> types) {
        TeamMemberView opponent = OPPONENT_TEAM.get(pokemon);
        if (opponent != null) OPPONENT_TEAM.put(pokemon, opponent.withTypes(types));
        List<TeamMemberView> updated = new ArrayList<>(ownTeam.size());
        for (TeamMemberView member : ownTeam) {
            updated.add(member.uuid().equals(pokemon) ? member.withTypes(types) : member);
        }
        ownTeam = List.copyOf(updated);
    }

    private static void clearSwitchedOutDynamicTypes(Set<UUID> activePokemon) {
        if (!PREVIOUSLY_ACTIVE.isEmpty()) {
            for (UUID previous : PREVIOUSLY_ACTIVE) {
                if (activePokemon.contains(previous)) continue;
                if (TRANSFORMED_MOVESETS.remove(previous) != null && OPPONENT_TEAM.containsKey(previous)) {
                    PENDING_FORM_EVENTS.put(previous, FormEventKind.TRANSFORM_REVERTED);
                    refreshOpponentMoves(previous);
                }
                clearTemporaryDynamicType(previous);
                if (SUPPRESSED_ABILITIES.remove(previous)) {
                    TeamMemberView member = OPPONENT_TEAM.get(previous);
                    if (member != null && member.ability() != null) {
                        OPPONENT_TEAM.put(previous, member.withAbility(member.ability().withSuppressed(false)));
                    }
                }
            }
        }
        PREVIOUSLY_ACTIVE.clear();
        PREVIOUSLY_ACTIVE.addAll(activePokemon);
    }

    private static void clearTemporaryDynamicType(UUID pokemon) {
        DynamicTypeState state = DYNAMIC_TYPES.get(pokemon);
        if (state == null || state.persistent()) return;
        DYNAMIC_TYPES.remove(pokemon);
        if (!state.originalTypes().isEmpty()) updateKnownTypes(pokemon, state.originalTypes());
    }

    private static int statOrder(String id) {
        return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
            case "atk" -> 0;
            case "def" -> 1;
            case "spa" -> 2;
            case "spd" -> 3;
            case "spe" -> 4;
            case "accuracy" -> 5;
            case "evasion" -> 6;
            default -> 7;
        };
    }

    private static TeamMemberView memberById(UUID uuid) {
        if (uuid == null) return null;
        if (OPPONENT_TEAM.containsKey(uuid)) return OPPONENT_TEAM.get(uuid);
        for (TeamMemberView member : ownTeam) if (member.uuid().equals(uuid)) return member;
        return OPPONENT_TEAM.get(uuid);
    }

    private static AbilityView abilityView(AbilityTemplate ability) {
        if (ability == null) return null;
        return new AbilityView(ability.getName(), translated(ability.getDisplayName()),
                translated(ability.getDescription()));
    }

    private static MoveView moveView(Move move) {
        MoveTemplate template = move.getTemplate();
        return new MoveView(template.getName(), template.getDisplayName(), template.getDescription(),
                template.getElementalType().getName(), move.getCurrentPp(), move.getMaxPp());
    }

    private static MoveView moveView(MoveTemplate move) {
        return new MoveView(move.getName(), move.getDisplayName(), move.getDescription(),
                move.getElementalType().getName(), -1, move.getMaxPp());
    }

    private static Text translated(String value) {
        if (value == null || value.isBlank()) return Text.empty();
        return value.indexOf('.') >= 0 ? Text.translatable(value) : Text.literal(value);
    }

    private static void rememberItemTransferContext(String key, Object[] args) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (pendingItemSwap != null && pendingItemSwap.turn() != turn) pendingItemSwap = null;
        if (normalized.startsWith("cobblemon.battle.fail") || normalized.equals("cobblemon.battle.missed")
                || normalized.equals("cobblemon.battle.immune")) {
            pendingItemSwap = null;
            return;
        }
        if (!(normalized.endsWith("used_move_on") || normalized.endsWith("used_move"))
                || !(argument(args, 1) instanceof Text moveText)) return;
        MoveTemplate move = moveTemplate(moveText);
        String moveId = move == null ? "" : normalizeMove(move.getName());
        if (!moveId.equals("trick") && !moveId.equals("switcheroo")) return;
        UUID user = pokemonFromArgument(argument(args, 0));
        UUID target = pokemonFromArgument(argument(args, 2));
        if (user == null || target == null || user.equals(target)) return;
        pendingItemSwap = new PendingItemSwap(user, target, knownHeldItem(user), knownHeldItem(target), turn);
    }

    private static ItemStack knownHeldItem(UUID owner) {
        TeamMemberView member = memberById(owner);
        return currentItem(owner, member == null ? ItemStack.EMPTY : member.heldItem()).copy();
    }

    static void rememberItemState(String key, String rendered, Object[] args) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        ItemStack spotted = ItemStack.EMPTY;
        for (Object arg : args) {
            if (arg instanceof Text text) {
                ItemStack candidate = itemStack(text);
                if (!candidate.isEmpty()) {
                    spotted = candidate;
                    break;
                }
            }
        }
        if (spotted.isEmpty() && (normalizedKey.startsWith("cobblemon.battle.enditem.")
                || normalizedKey.startsWith("cobblemon.battle.item."))) {
            spotted = itemFromBattleId(normalizedKey.substring(normalizedKey.lastIndexOf('.') + 1));
        }
        if (spotted.isEmpty()) {
            String itemId = switch (normalizedKey) {
                // Public effect keys are authoritative even when a server sends
                // the displayed item as a literal instead of an item translation.
                case "cobblemon.battle.heal.leftovers" -> "leftovers";
                case "cobblemon.battle.heal.blacksludge" -> "blacksludge";
                case "cobblemon.battle.damage.lifeorb" -> "lifeorb";
                case "cobblemon.battle.damage.rockyhelmet" -> "rockyhelmet";
                default -> "";
            };
            if (!itemId.isEmpty()) spotted = itemFromBattleId(itemId);
        }
        if (normalizedKey.startsWith("cobblemon.battle.enditem.") ||
                normalizedKey.equals("cobblemon.battle.item.eat")) {
            UUID owner = pokemonFromArgument(argument(args, 0));
            if (isLumBerry(spotted)) confirmStatusCure(owner);
            if (normalizedKey.endsWith(".focusband")) {
                setOpponentItem(owner, spotted, ItemEventKind.REVEALED);
                return;
            }
            ItemEventKind kind;
            if (normalizedKey.contains("knockoff")) {
                kind = ItemEventKind.KNOCKED_OFF;
            } else if (normalizedKey.contains("incinerate") || normalizedKey.contains("corrosivegas") ||
                    normalizedKey.contains("airballoon") || normalizedKey.contains("fling")) {
                kind = ItemEventKind.DESTROYED;
            } else {
                kind = ItemEventKind.CONSUMED;
            }
            clearOpponentItem(owner, spotted, kind);
            return;
        }
        if (spotted.isEmpty()) return;
        // An item mentioned by an unrelated effect is not evidence that the
        // subject holds it (for example the victim of Rocky Helmet damage).
        if (!(normalizedKey.startsWith("cobblemon.battle.item.")
                || normalizedKey.equals("cobblemon.battle.activate.poltergeist")
                || normalizedKey.equals("cobblemon.battle.damage.item")
                || normalizedKey.equals("cobblemon.battle.heal.item")
                || normalizedKey.equals("cobblemon.battle.damage.rockyhelmet")
                || normalizedKey.equals("cobblemon.battle.heal.leftovers")
                || normalizedKey.equals("cobblemon.battle.heal.blacksludge")
                || normalizedKey.equals("cobblemon.battle.damage.lifeorb"))) return;
        if (normalizedKey.equals("cobblemon.battle.item.thief")) {
            setOpponentItem(pokemonFromArgument(argument(args, 0)), spotted, ItemEventKind.STOLEN);
            clearOpponentItem(pokemonFromArgument(argument(args, 2)), spotted, ItemEventKind.STOLEN);
            return;
        }
        if (normalizedKey.equals("cobblemon.battle.item.bestow")) {
            setOpponentItem(pokemonFromArgument(argument(args, 0)), spotted, ItemEventKind.GIVEN);
            clearOpponentItem(pokemonFromArgument(argument(args, 2)), spotted, ItemEventKind.GIVEN);
            return;
        }
        UUID owner = pokemonFromArgument(argument(args,
                normalizedKey.equals("cobblemon.battle.damage.rockyhelmet") ? 1 : 0));
        if (normalizedKey.equals("cobblemon.battle.item.trick") && applyPendingItemSwap(owner, spotted)) return;
        ItemEventKind kind = normalizedKey.contains("trick") || normalizedKey.contains("switcheroo")
                ? ItemEventKind.SWAPPED : ItemEventKind.REVEALED;
        setOpponentItem(owner, spotted, kind);
    }

    private static boolean isLumBerry(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return Registries.ITEM.getId(item.getItem()).getPath().replace("_", "").equals("lumberry");
    }

    private static void confirmStatusCure(UUID pokemon) {
        if (pokemon == null) return;
        CONFIRMED_STATUS_CURES.add(pokemon);
        updateKnownStatus(pokemon, "");
        PokemonBattleEffects.clearCuredStatus(pokemon);
    }

    private static void rememberStatusApplication(String key, Object[] args) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("cobblemon.status.") || !normalized.endsWith(".apply")) return;
        CONFIRMED_STATUS_CURES.remove(pokemonFromArgument(argument(args, 0)));
    }

    static String visibleStatus(UUID pokemon, String nativeStatus) {
        return resolvedStatus(pokemon, nativeStatus);
    }

    static String resolvedStatus(UUID pokemon, String nativeStatus) {
        return CONFIRMED_STATUS_CURES.contains(pokemon) ? "" : nativeStatus == null ? "" : nativeStatus;
    }

    private static void updateKnownStatus(UUID owner, String status) {
        TeamMemberView member = OPPONENT_TEAM.get(owner);
        if (member != null) OPPONENT_TEAM.put(owner, member.withStatus(status));
        if (!spectating) {
            List<TeamMemberView> updated = new ArrayList<>(ownTeam.size());
            for (TeamMemberView own : ownTeam) updated.add(own.uuid().equals(owner) ? own.withStatus(status) : own);
            if (!updated.equals(ownTeam)) ownTeam = List.copyOf(updated);
        }
    }

    private static boolean applyPendingItemSwap(UUID announcedOwner, ItemStack announcedItem) {
        PendingItemSwap swap = pendingItemSwap;
        if (swap == null || swap.turn() != turn || announcedOwner == null
                || !announcedOwner.equals(swap.user()) && !announcedOwner.equals(swap.target())) return false;
        UUID other = announcedOwner.equals(swap.user()) ? swap.target() : swap.user();
        ItemStack otherNewItem = announcedOwner.equals(swap.user()) ? swap.userItem() : swap.targetItem();
        applySwappedItem(announcedOwner, announcedItem);
        applySwappedItem(other, otherNewItem);
        pendingItemSwap = null;
        return true;
    }

    private static void applySwappedItem(UUID owner, ItemStack item) {
        if (item == null || item.isEmpty()) clearOpponentItem(owner, ItemStack.EMPTY, ItemEventKind.SWAPPED);
        else setOpponentItem(owner, item, ItemEventKind.SWAPPED);
    }

    private static ItemStack itemFromBattleId(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        String normalized = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ITEM_BY_BATTLE_ID.isEmpty()) {
            for (Identifier identifier : Registries.ITEM.getIds()) {
                if (identifier.getNamespace().equals("cobblemon")) {
                    ITEM_BY_BATTLE_ID.putIfAbsent(identifier.getPath().replace("_", ""),
                            Registries.ITEM.get(identifier).getDefaultStack());
                }
            }
        }
        ItemStack item = ITEM_BY_BATTLE_ID.get(normalized);
        return item == null ? ItemStack.EMPTY : item.copy();
    }

    private static void rememberRevealedMove(String key, Object[] args) {
        if (!(key.endsWith("used_move") || key.endsWith("used_move_on")) || args.length < 2) return;
        UUID anyOwner = pokemonFromArgument(args[0]);
        if (anyOwner == null || !(args[1] instanceof Text moveText)) return;
        MoveTemplate move = moveTemplate(moveText);
        if (move == null) return;

        boolean called = consumesPendingCalledMove(anyOwner) || consumesFreeMove(anyOwner);
        if (!called && !normalizeMove(move.getName()).equals("struggle")) {
            LAST_SELECTED_MOVES.put(anyOwner, move.getName());
        }
        if (!called && OpponentPpRules.callsAnotherMove(move.getName())) {
            PENDING_CALLED_MOVES.put(anyOwner, new PendingMoveUse(turn));
        }

        UUID owner = opponentFromArgument(args[0]);
        if (owner == null) return;
        LinkedHashMap<String, MoveView> moves = activeOpponentMoveMap(owner);
        MoveView known = moves.get(move.getName());
        if (called) {
            if (known == null) moves.put(move.getName(), opponentMoveView(move).asCalled());
        } else if (!normalizeMove(move.getName()).equals("struggle")) {
            if (known == null || known.origin() == MoveOrigin.CALLED) known = opponentMoveView(move);
            moves.put(move.getName(), known.spendPp(ppCost(args, move)));
        }
        refreshOpponentMoves(owner);
    }

    private static boolean consumesPendingCalledMove(UUID owner) {
        PendingMoveUse pending = PENDING_CALLED_MOVES.get(owner);
        if (pending == null) return false;
        PENDING_CALLED_MOVES.remove(owner);
        return pending.turn() == turn;
    }

    private static boolean consumesFreeMove(UUID owner) {
        Integer pendingTurn = FREE_NEXT_MOVE_USE.remove(owner);
        return pendingTurn != null && pendingTurn == turn;
    }

    private static void rememberPpContext(String key, Object[] args) {
        if (key == null) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.equals("cobblemon.battle.activate.snatch")) {
            UUID owner = pokemonFromArgument(argument(args, 0));
            if (owner != null) FREE_NEXT_MOVE_USE.put(owner, turn);
        }
        if (OpponentPpRules.repeatsWithoutPp(normalized)) {
            // %1$s is the instructed Pokémon. Its repeated move is real, but
            // Showdown does not spend a second PP from that Pokémon's moveset.
            UUID instructed = pokemonFromArgument(argument(args, 0));
            if (instructed != null) FREE_NEXT_MOVE_USE.put(instructed, turn);
        }
    }

    private static LinkedHashMap<String, MoveView> activeOpponentMoveMap(UUID owner) {
        LinkedHashMap<String, MoveView> transformed = TRANSFORMED_MOVESETS.get(owner);
        if (transformed != null) return transformed;
        return REVEALED_MOVES.computeIfAbsent(owner,
                ignored -> new LinkedHashMap<>());
    }

    private static void rememberUsedMoveEffects(String key, Object[] args) {
        if (key == null || !(key.endsWith("used_move") || key.endsWith("used_move_on")) || args.length < 2) return;
        UUID owner = pokemonFromArgument(args[0]);
        if (owner == null || !(args[1] instanceof Text moveText)) return;
        MoveTemplate move = moveTemplate(moveText);
        if (move != null) {
            String moveId = normalizeMove(move.getName());
            CurrentMove sequence = currentMoveFor(owner, true);
            if (moveId.equals("batonpass")) {
                PENDING_BATON_PASSES.put(owner, new PendingBatonPass(turn, sequence.sequence()));
            }
            else PENDING_BATON_PASSES.remove(owner);
            UUID target = pokemonFromArgument(argument(args, 2));
            if (target == null) PENDING_MOVE_TARGETS.remove(owner);
            else PENDING_MOVE_TARGETS.put(owner, new PendingMoveTarget(moveId, target, turn));
            PokemonBattleEffects.acceptMove(owner, move.getName(), turn);
            BattleCalculationHistory.move(owner, move.getName(), move.getDamageCategory().getName(), turn);
        }
    }

    private static boolean consumePendingBatonPass(UUID pokemon) {
        PendingBatonPass pending = PENDING_BATON_PASSES.remove(pokemon);
        return pending != null && pending.turn() == turn;
    }

    private static CurrentMove currentMoveFor(UUID owner, boolean markEffectsRecorded) {
        CurrentMove sequence = currentMove;
        if (!markEffectsRecorded) {
            sequence = new CurrentMove(owner, turn, ++moveSequence, false);
        } else if (sequence == null || sequence.turn() != turn
                || !java.util.Objects.equals(sequence.user(), owner) || sequence.effectsRecorded()) {
            sequence = new CurrentMove(owner, turn, ++moveSequence, markEffectsRecorded);
        } else {
            sequence = new CurrentMove(sequence.user(), sequence.turn(), sequence.sequence(), true);
        }
        currentMove = sequence;
        return sequence;
    }

    private static boolean belongsToCurrentMove(UUID user, int moveTurn, long sequence) {
        return currentMove != null && currentMove.turn() == turn && currentMove.turn() == moveTurn
                && currentMove.sequence() == sequence && java.util.Objects.equals(currentMove.user(), user);
    }

    private static void rememberSlotEffects(String key, Object[] args) {
        if (key == null || args == null) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (pendingHazardMove != null && pendingHazardMove.turn() != turn) pendingHazardMove = null;
        if (pendingSideEffectMove != null && (pendingSideEffectMove.turn() != turn
                || pendingSideEffectMove.spectating() != spectating)) pendingSideEffectMove = null;
        if (pendingSideRemoval != null && (pendingSideRemoval.turn() != turn
                || pendingSideRemoval.spectating() != spectating)) pendingSideRemoval = null;
        confirmMultiTargetSideRemoval(normalized, args);
        if (moveWasBlocked(normalized)) {
            if (pendingHazardMove != null && belongsToCurrentMove(pendingHazardMove.user(),
                    pendingHazardMove.turn(), pendingHazardMove.sequence())) {
                BattleFieldEffects.cancelMoveHazard(pendingHazardMove.id(), pendingHazardMove.side(), turn,
                        pendingHazardMove.applied());
                pendingHazardMove = null;
            }
            if (pendingSideEffectMove != null && pendingSideEffectMove.spectating() == spectating
                    && belongsToCurrentMove(pendingSideEffectMove.user(), pendingSideEffectMove.turn(),
                    pendingSideEffectMove.sequence())) {
                BattleFieldEffects.cancelMoveSideEffect(pendingSideEffectMove.activation());
                pendingSideEffectMove = null;
            }
            if (pendingSideRemoval != null && pendingSideRemoval.spectating() == spectating
                    && belongsToCurrentMove(pendingSideRemoval.user(), pendingSideRemoval.turn(),
                    pendingSideRemoval.sequence())) {
                if (!pendingSideRemoval.needsHitConfirmation()) {
                    BattleFieldEffects.cancelObservedSideRemoval(pendingSideRemoval.activation());
                    pendingSideRemoval = null;
                } else if (normalized.startsWith("cobblemon.battle.fail")) {
                    pendingSideRemoval = null;
                }
            }
            if (currentMove != null && currentMove.turn() == turn) {
                PendingBatonPass batonPass = PENDING_BATON_PASSES.get(currentMove.user());
                if (batonPass != null && batonPass.turn() == turn
                        && batonPass.sequence() == currentMove.sequence()) {
                    PENDING_BATON_PASSES.remove(currentMove.user());
                }
            }
            return;
        }
        UUID subject = pokemonFromArgument(argument(args, 0));
        BattleFieldEffects.EffectSide subjectSide = effectSideFromArgument(argument(args, 0));
        if (normalized.contains(".sidestart.") && pendingHazardMove != null) {
            String announced = normalized.substring(normalized.indexOf(".sidestart.") + ".sidestart.".length());
            String announcedId = announced.substring(announced.lastIndexOf('.') + 1);
            if (pendingHazardMove.id().equals(announcedId)
                    && (subjectSide == BattleFieldEffects.EffectSide.FIELD || subjectSide == pendingHazardMove.side())) {
                pendingHazardMove = null;
            }
        }
        if (normalized.contains(".sidestart.") && pendingSideEffectMove != null
                && pendingSideEffectMove.spectating() == spectating) {
            String announced = normalized.substring(normalized.indexOf(".sidestart.") + ".sidestart.".length());
            String announcedId = announced.substring(announced.lastIndexOf('.') + 1);
            if (pendingSideEffectMove.id().equals(announcedId)
                    && (subjectSide == BattleFieldEffects.EffectSide.FIELD
                    || subjectSide == pendingSideEffectMove.side())) {
                pendingSideEffectMove = null;
            }
        }
        if (normalized.equals("cobblemon.battle.start.futuresight") ||
                normalized.equals("cobblemon.battle.start.doomdesire")) {
            String id = normalized.endsWith("futuresight") ? "futuresight" : "doomdesire";
            // Cobblemon names the caster in the start announcement ("foresaw an
            // attack"), not the targeted slot. The preceding used-move message
            // already provides the exact target; only add an opposite-side
            // fallback when that authoritative observation was unavailable.
            if (!BattleFieldEffects.active(id)) {
                BattleFieldEffects.startSlotEffect(id, opposite(subjectSide), turn);
            }
            return;
        }
        if ((normalized.endsWith("used_move") || normalized.endsWith("used_move_on")) &&
                argument(args, 1) instanceof Text moveText) {
            pendingHazardMove = null;
            pendingSideEffectMove = null;
            pendingSideRemoval = null;
            CurrentMove sequence = currentMoveFor(subject, false);
            MoveTemplate move = moveTemplate(moveText);
            if (move == null || subjectSide == BattleFieldEffects.EffectSide.FIELD) return;
            String id = normalizeMove(move.getName());
            UUID moveTarget = pokemonFromArgument(argument(args, 2));
            BattleFieldEffects.EffectSide targetSide = sideFor(moveTarget);
            if (targetSide == BattleFieldEffects.EffectSide.FIELD) targetSide = opposite(subjectSide);
            boolean reflectedByMagicCoat = spectating && moveTarget != null
                    && PokemonBattleEffects.active(moveTarget, "magiccoat", turn);
            if (spectating) {
                Map<BattleFieldEffects.EffectSide, Set<String>> removals = sideRemovalsForMove(
                        id, subject, subjectSide,
                        id.equals("defog") && reflectedByMagicCoat ? subjectSide : targetSide);
                boolean needsHitConfirmation = id.equals("mortalspin") || id.equals("gmaxwindrage");
                BattleFieldEffects.SideRemovalActivation activation = needsHitConfirmation || removals.isEmpty()
                        ? null : BattleFieldEffects.removeObservedSideConditions(removals);
                if ((needsHitConfirmation && !removals.isEmpty())
                        || (activation != null && activation.changed())) {
                    pendingSideRemoval = new PendingSideRemoval(subject, sequence.sequence(), id, turn,
                            removals, activation, needsHitConfirmation, false, true);
                }
            }
            if (SIDE_EFFECT_MOVES.contains(id)) {
                if (spectating) {
                    BattleFieldEffects.SideEffectMoveActivation activation =
                            BattleFieldEffects.startSideEffectFromMove(id, subjectSide, turn);
                    pendingSideEffectMove = new PendingSideEffectMove(subject, sequence.sequence(), id,
                            subjectSide, turn, activation, true);
                } else {
                    BattleFieldEffects.rememberSideEffectMove(id, subjectSide, turn);
                }
            }
            String hazard = hazardForMove(id);
            if (hazard != null) {
                if (reflectedByMagicCoat) targetSide = subjectSide;
                if (targetSide != BattleFieldEffects.EffectSide.FIELD) {
                    boolean applied = BattleFieldEffects.startHazardFromMove(hazard, targetSide, turn);
                    pendingHazardMove = new PendingHazardMove(subject, sequence.sequence(), hazard,
                            targetSide, turn, applied);
                }
            }
            if (id.equals("wish")) BattleFieldEffects.startSlotEffect(id, subjectSide, slotId(subject), turn);
            if (id.equals("futuresight") || id.equals("doomdesire")) {
                UUID target = moveTarget;
                BattleFieldEffects.EffectSide delayedTargetSide = sideFor(target);
                if (delayedTargetSide == BattleFieldEffects.EffectSide.FIELD) delayedTargetSide = opposite(subjectSide);
                BattleFieldEffects.startSlotEffect(id, delayedTargetSide, slotId(target), turn);
            }
            return;
        }
        if (normalized.equals("cobblemon.battle.heal.wish")) {
            BattleFieldEffects.endSlotEffect("wish", subjectSide, slotId(subject));
        } else if (normalized.equals("cobblemon.battle.end.futuresight")) {
            BattleFieldEffects.endSlotEffect("futuresight", subjectSide, slotId(subject));
        } else if (normalized.equals("cobblemon.battle.end.doomdesire")) {
            BattleFieldEffects.endSlotEffect("doomdesire", subjectSide, slotId(subject));
        }
    }

    static boolean moveWasBlocked(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("cobblemon.battle.fail") || normalized.contains(".miss")
                || normalized.endsWith(".immune") || normalized.startsWith("cobblemon.battle.block.")) return true;
        return Set.of("cobblemon.battle.activate.protect", "cobblemon.battle.activate.wideguard",
                "cobblemon.battle.activate.quickguard", "cobblemon.battle.activate.craftyshield",
                "cobblemon.battle.activate.matblock", "cobblemon.battle.activate.maxguard")
                .contains(normalized);
    }

    private static String slotId(UUID pokemon) {
        ActiveClientBattlePokemon active = activeBattlePokemon(pokemon);
        return active == null ? "" : active.getPNX();
    }

    private static BattleFieldEffects.EffectSide sideFor(UUID pokemon) {
        if (pokemon == null) return BattleFieldEffects.EffectSide.FIELD;
        if (spectating() && SPECTATED_LEFT.contains(pokemon)) return BattleFieldEffects.EffectSide.PLAYER_FIELD;
        return (isOpponentMember(pokemon) || teamPreview.opponentPlaceholder(pokemon))
                ? BattleFieldEffects.EffectSide.OPPONENT_FIELD
                : ownTeam().stream().anyMatch(member -> member.uuid().equals(pokemon))
                ? BattleFieldEffects.EffectSide.PLAYER_FIELD : BattleFieldEffects.EffectSide.FIELD;
    }

    static BattleFieldEffects.EffectSide effectSideFromArgument(Object argument) {
        UUID pokemon = pokemonFromArgument(argument);
        BattleFieldEffects.EffectSide pokemonSide = sideFor(pokemon);
        if (pokemonSide != BattleFieldEffects.EffectSide.FIELD) return pokemonSide;
        String rendered = argument instanceof Text text ? text.getString()
                : argument instanceof CharSequence chars ? chars.toString() : "";
        if (rendered.isBlank()) return BattleFieldEffects.EffectSide.FIELD;
        BattleLogSide side = BattleLogSide.resolve(rendered, "",
                List.copyOf(OWN_ACTOR_NAMES), List.copyOf(OPPONENT_ACTOR_NAMES));
        return side == BattleLogSide.PLAYER ? BattleFieldEffects.EffectSide.PLAYER_FIELD
                : side == BattleLogSide.OPPONENT ? BattleFieldEffects.EffectSide.OPPONENT_FIELD
                : BattleFieldEffects.EffectSide.FIELD;
    }

    private static BattleFieldEffects.EffectSide opposite(BattleFieldEffects.EffectSide side) {
        return side == BattleFieldEffects.EffectSide.PLAYER_FIELD
                ? BattleFieldEffects.EffectSide.OPPONENT_FIELD
                : side == BattleFieldEffects.EffectSide.OPPONENT_FIELD
                ? BattleFieldEffects.EffectSide.PLAYER_FIELD : BattleFieldEffects.EffectSide.FIELD;
    }

    private static final Set<String> SIDE_EFFECT_MOVES = Set.of("tailwind", "reflect", "lightscreen",
            "auroraveil", "safeguard", "mist", "luckychant");
    private static final Set<String> ENTRY_HAZARDS = Set.of("spikes", "toxicspikes", "stealthrock", "stickyweb");
    private static final Set<String> DAMAGE_SCREENS = Set.of("reflect", "lightscreen", "auroraveil");
    private static final Set<String> DEFOG_TARGET_EFFECTS = Set.of(
            "reflect", "lightscreen", "auroraveil", "safeguard", "mist");

    private static Map<BattleFieldEffects.EffectSide, Set<String>> sideRemovalsForMove(
            String moveId, UUID user, BattleFieldEffects.EffectSide userSide,
            BattleFieldEffects.EffectSide targetSide) {
        Map<BattleFieldEffects.EffectSide, Set<String>> removals = new LinkedHashMap<>();
        switch (moveId) {
            case "rapidspin", "mortalspin" -> {
                AbilityView ability = abilityFor(user);
                if (ability == null || !normalizeMove(ability.id()).equals("sheerforce")) {
                    addSideRemovals(removals, userSide, ENTRY_HAZARDS);
                }
            }
            case "defog" -> {
                addSideRemovals(removals, BattleFieldEffects.EffectSide.PLAYER_FIELD, ENTRY_HAZARDS);
                addSideRemovals(removals, BattleFieldEffects.EffectSide.OPPONENT_FIELD, ENTRY_HAZARDS);
                addSideRemovals(removals, targetSide, DEFOG_TARGET_EFFECTS);
            }
            case "tidyup" -> {
                addSideRemovals(removals, BattleFieldEffects.EffectSide.PLAYER_FIELD, ENTRY_HAZARDS);
                addSideRemovals(removals, BattleFieldEffects.EffectSide.OPPONENT_FIELD, ENTRY_HAZARDS);
            }
            case "gmaxwindrage" -> {
                addSideRemovals(removals, BattleFieldEffects.EffectSide.PLAYER_FIELD, ENTRY_HAZARDS);
                addSideRemovals(removals, BattleFieldEffects.EffectSide.OPPONENT_FIELD, ENTRY_HAZARDS);
                addSideRemovals(removals, opposite(userSide), DEFOG_TARGET_EFFECTS);
            }
            case "brickbreak", "psychicfangs", "ragingbull" ->
                    addSideRemovals(removals, targetSide, DAMAGE_SCREENS);
            default -> {
            }
        }
        return removals;
    }

    private static void addSideRemovals(Map<BattleFieldEffects.EffectSide, Set<String>> removals,
                                        BattleFieldEffects.EffectSide side, Set<String> effects) {
        if (side == null || side == BattleFieldEffects.EffectSide.FIELD || effects == null || effects.isEmpty()) return;
        removals.merge(side, effects, (previous, added) -> {
            Set<String> merged = new java.util.LinkedHashSet<>(previous);
            merged.addAll(added);
            return Set.copyOf(merged);
        });
    }

    private static void confirmMultiTargetSideRemoval(String key, Object[] args) {
        PendingSideRemoval pending = pendingSideRemoval;
        if (pending == null || !pending.needsHitConfirmation() || pending.hitConfirmed()
                || !belongsToCurrentMove(pending.user(), pending.turn(), pending.sequence())) return;
        boolean hitSignal = key.equals("cobblemon.battle.damage_dealt")
                || key.equals("cobblemon.battle.activate.substitute")
                || key.equals("cobblemon.battle.end.substitute")
                || key.equals("cobblemon.battle.ability.disguise")
                || key.equals("cobblemon.battle.ability.iceface")
                || key.endsWith(".faint") || key.endsWith(".fainted");
        if (!hitSignal) return;
        UUID affected = pokemonFromArgument(argument(args, 0));
        if (affected == null || affected.equals(pending.user()) || !opposingPokemon(pending.user(), affected)) return;
        BattleFieldEffects.SideRemovalActivation activation =
                BattleFieldEffects.removeObservedSideConditions(pending.removals());
        pendingSideRemoval = new PendingSideRemoval(pending.user(), pending.sequence(), pending.moveId(),
                pending.turn(), pending.removals(), activation, true, true, pending.spectating());
    }

    private static int ppCost(Object[] args, MoveTemplate move) {
        UUID user = pokemonFromArgument(argument(args, 0));
        UUID target = pokemonFromArgument(argument(args, 2));
        if (target != null) {
            AbilityView ability = abilityFor(target);
            return opposingPokemon(user, target) && ability != null
                    && "pressure".equalsIgnoreCase(ability.id()) ? 2 : 1;
        }
        if (move == null || !(move.getTarget() == MoveTarget.all ||
                move.getTarget() == MoveTarget.allAdjacent ||
                move.getTarget() == MoveTarget.allAdjacentFoes)) return 1;
        int pressureTargets = 0;
        ActiveClientBattlePokemon activeUser = activeBattlePokemon(user);
        List<UUID> targets;
        if (activeUser != null) {
            targets = BattleMultiTargeting.affected(activeUser, move.getTarget()).stream()
                    .filter(active -> !activeUser.isAllied(active))
                    .map(active -> active.getBattlePokemon().getUuid()).toList();
        } else {
            targets = (isOpponentMember(user) ? ownTeam() : opponentTeam()).stream()
                    .filter(member -> member.active() && !member.fainted()).map(TeamMemberView::uuid).toList();
        }
        for (UUID pokemon : targets) {
            AbilityView ability = abilityFor(pokemon);
            if (ability != null && "pressure".equalsIgnoreCase(ability.id())) pressureTargets++;
        }
        return 1 + pressureTargets;
    }

    static boolean opposingPokemon(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        ActiveClientBattlePokemon firstActive = activeBattlePokemon(first);
        ActiveClientBattlePokemon secondActive = activeBattlePokemon(second);
        if (firstActive != null && secondActive != null) return !firstActive.isAllied(secondActive);
        BattleFieldEffects.EffectSide firstSide = sideFor(first);
        BattleFieldEffects.EffectSide secondSide = sideFor(second);
        return firstSide != BattleFieldEffects.EffectSide.FIELD
                && secondSide != BattleFieldEffects.EffectSide.FIELD && firstSide != secondSide;
    }

    private static AbilityView abilityFor(UUID pokemon) {
        if (pokemon == null) return null;
        if (SUPPRESSED_ABILITIES.contains(pokemon)) return null;
        TeamMemberView member = memberById(pokemon);
        return REVEALED_ABILITIES.getOrDefault(pokemon, member == null ? null : member.ability());
    }

    private static void rememberPpChange(String key, Object[] args) {
        if (key == null) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        int ownerIndex;
        int moveIndex;
        if (normalized.equals("cobblemon.battle.activate.spite")) {
            ownerIndex = 0;
            moveIndex = 1;
        } else if (normalized.equals("cobblemon.battle.activate.grudge")) {
            ownerIndex = 0;
            moveIndex = 2;
        } else if (normalized.equals("cobblemon.battle.activate.leppaberry")) {
            ownerIndex = 0;
            moveIndex = 2;
        } else {
            return;
        }

        UUID owner = opponentFromArgument(argument(args, ownerIndex));
        Object moveArg = argument(args, moveIndex);
        if (owner == null || !(moveArg instanceof Text moveText)) return;
        MoveTemplate template = moveTemplate(moveText);
        if (template == null) return;
        LinkedHashMap<String, MoveView> moves = activeOpponentMoveMap(owner);
        MoveView known = moves.computeIfAbsent(template.getName(), ignored -> opponentMoveView(template));
        if (normalized.endsWith(".spite")) {
            moves.put(template.getName(), known.spendPp(integerArgument(argument(args, 2), 4)));
        } else if (normalized.endsWith(".grudge")) {
            moves.put(template.getName(), known.exhaustPp());
        } else {
            moves.put(template.getName(), known.restorePpFromEmpty(10));
        }
        refreshOpponentMoves(owner);
    }

    private static void rememberCopiedMove(String key, Object[] args) {
        if (key == null) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        int moveIndex = normalized.equals("cobblemon.battle.start.mimic") ? 1
                : normalized.equals("cobblemon.battle.activate.sketch") ? 2 : -1;
        if (moveIndex < 0) return;
        UUID owner = opponentFromArgument(argument(args, 0));
        Object moveArgument = argument(args, moveIndex);
        if (owner == null || !(moveArgument instanceof Text moveText)) return;
        MoveTemplate move = moveTemplate(moveText);
        if (move == null) return;
        LinkedHashMap<String, MoveView> moves = activeOpponentMoveMap(owner);
        moves.remove(normalized.endsWith(".mimic") ? "mimic" : "sketch");
        moves.put(move.getName(), opponentMoveView(move).asCopied(5));
        refreshOpponentMoves(owner);
    }

    private static MoveView opponentMoveView(MoveTemplate move) {
        int minimumMax = Math.max(1, move.getPp());
        int estimatedMax = Math.max(1, move.getMaxPp());
        return MoveView.estimatedRange(move.getName(), move.getDisplayName(), move.getDescription(),
                move.getElementalType().getName(), minimumMax, estimatedMax);
    }

    private static void refreshOpponentMoves(UUID owner) {
        TeamMemberView member = OPPONENT_TEAM.get(owner);
        if (member != null) {
            OPPONENT_TEAM.put(owner, new TeamMemberView(member.uuid(), member.name(), member.level(),
                    member.hpPercent(), member.status(), member.fainted(), member.active(), member.heldItem(),
                    member.portrait(), member.types(), member.ability(),
                    List.copyOf(activeOpponentMoveMap(owner).values()), member.statStages(), true));
        }
    }

    private static void setOpponentItem(UUID owner, ItemStack item, ItemEventKind kind) {
        if (owner == null || item == null || item.isEmpty()) return;
        ItemStack previous = REVEALED_ITEMS.get(owner);
        boolean changed = previous == null || previous.isEmpty() || !ItemStack.areItemsEqual(previous, item);
        REVEALED_NO_ITEM.remove(owner);
        REVEALED_ITEMS.put(owner, item.copy());
        if (changed || kind != ItemEventKind.REVEALED) rememberItemHistory(owner, item, kind);
        updateKnownItem(owner, item);
    }

    private static void clearOpponentItem(UUID owner, ItemStack announcedItem, ItemEventKind kind) {
        if (owner == null) return;
        TeamMemberView member = memberById(owner);
        ItemStack previous = announcedItem == null || announcedItem.isEmpty()
                ? currentItem(owner, member == null ? ItemStack.EMPTY : member.heldItem()) : announcedItem;
        if (!previous.isEmpty()) rememberItemHistory(owner, previous, kind);
        REVEALED_ITEMS.remove(owner);
        REVEALED_NO_ITEM.add(owner);
        updateKnownItem(owner, ItemStack.EMPTY);
    }

    private static void updateKnownItem(UUID owner, ItemStack item) {
        TeamMemberView member = OPPONENT_TEAM.get(owner);
        if (member != null) OPPONENT_TEAM.put(owner, member.withItem(item));
        if (!spectating) {
            List<TeamMemberView> updated = new ArrayList<>(ownTeam.size());
            for (TeamMemberView own : ownTeam) updated.add(own.uuid().equals(owner) ? own.withItem(item) : own);
            if (!updated.equals(ownTeam)) ownTeam = List.copyOf(updated);
        }
    }

    private static void rememberItemHistory(UUID owner, ItemStack item, ItemEventKind kind) {
        if (owner == null || item == null || item.isEmpty()) return;
        List<ItemHistoryView> history = new ArrayList<>(ITEM_HISTORY.getOrDefault(owner, List.of()));
        ItemHistoryView event = new ItemHistoryView(item, kind, turn);
        if (!history.isEmpty()) {
            ItemHistoryView last = history.getLast();
            if (last.kind() == event.kind() && last.turn() == event.turn() &&
                    ItemStack.areItemsEqual(last.item(), event.item())) return;
        }
        history.add(event);
        while (history.size() > 12) history.removeFirst();
        ITEM_HISTORY.put(owner, List.copyOf(history));
    }

    private static void rememberFormEvent(String key, Object[] args) {
        if (key == null || key.isBlank()) return;
        String normalized = key.toLowerCase(Locale.ROOT);
        UUID subject = pokemonFromArgument(argument(args, 0));
        if (subject == null || !OPPONENT_TEAM.containsKey(subject)) return;
        if (normalized.equals("cobblemon.battle.transform")) {
            PENDING_FORM_EVENTS.put(subject, FormEventKind.TRANSFORM);
            UUID copiedTarget = pokemonFromArgument(argument(args, 1));
            TeamMemberView target = memberById(copiedTarget);
            if (target != null && !target.knownMoves().isEmpty()) {
                LinkedHashMap<String, MoveView> transformed = new LinkedHashMap<>();
                for (MoveView move : target.knownMoves()) transformed.put(move.id(), move.asTransformed());
                TRANSFORMED_MOVESETS.put(subject, transformed);
                refreshOpponentMoves(subject);
            }
        } else if (normalized.equals("cobblemon.battle.end.illusion")) {
            PENDING_FORM_EVENTS.put(subject, FormEventKind.ILLUSION_REVEALED);
        } else if (normalized.startsWith("cobblemon.battle.formechange.")) {
            PENDING_FORM_EVENTS.put(subject, FormEventKind.FORM_CHANGE);
        } else if (normalized.equals("cobblemon.battle.terastallize")) {
            String current = LAST_SCANNED_FORMS.getOrDefault(subject,
                    Optional.ofNullable(OPPONENT_TEAM.get(subject)).map(TeamMemberView::name).orElse("?"));
            Object type = argument(args, 1);
            String destination = type instanceof Text text ? text.getString() : String.valueOf(type);
            rememberFormHistory(subject, current, destination, FormEventKind.TERASTALLIZED);
        }
    }

    private static void rememberScannedForm(UUID pokemon, String currentForm) {
        if (pokemon == null || currentForm == null || currentForm.isBlank()) return;
        String previous = LAST_SCANNED_FORMS.put(pokemon, currentForm);
        if (previous == null || previous.equalsIgnoreCase(currentForm)) return;
        FormEventKind kind = PENDING_FORM_EVENTS.remove(pokemon);
        rememberFormHistory(pokemon, previous, currentForm,
                kind == null ? FormEventKind.FORM_CHANGE : kind);
    }

    private static void rememberFormHistory(UUID pokemon, String from, String to, FormEventKind kind) {
        if (pokemon == null || to == null || to.isBlank()) return;
        List<FormHistoryView> history = new ArrayList<>(FORM_HISTORY.getOrDefault(pokemon, List.of()));
        FormHistoryView event = new FormHistoryView(from, to, kind, turn);
        if (!history.isEmpty() && history.getLast().equals(event)) return;
        history.add(event);
        while (history.size() > 12) history.removeFirst();
        FORM_HISTORY.put(pokemon, List.copyOf(history));
    }

    private static Object argument(Object[] args, int index) {
        return args != null && index >= 0 && index < args.length ? args[index] : null;
    }

    /** Some public effect messages name the source first and the affected Pokémon second. */
    static UUID pokemonEffectSubject(String key, Object[] args) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.equals("cobblemon.battle.start.curse")) {
            UUID target = pokemonFromArgument(argument(args, 1));
            if (target != null) return target;
        }
        return pokemonFromArgument(argument(args, 0));
    }

    static Text repairMalformedBattleMessage(Text message, String key, Object[] args) {
        if (!malformedRedCard(message, key)) return message;
        Text holder = readableArgument(argument(args, 0));
        Text attacker = readableArgument(argument(args, 2));
        if (attacker == null && (key == null || key.isBlank())) {
            Text possibleAttacker = readableArgument(argument(args, 1));
            if (!redCardItem(possibleAttacker)) attacker = possibleAttacker;
        }
        if (holder != null && attacker != null) {
            return Text.translatable("text.tropimon_ui_battle.red_card_against", holder, attacker);
        }
        return Text.translatable("text.tropimon_ui_battle.red_card_activated");
    }

    private static boolean malformedRedCard(Text message, String key) {
        if (message == null) return false;
        String rendered = message.getString();
        if (!FORMAT_PLACEHOLDER.matcher(rendered).find()) return false;
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        String lower = rendered.toLowerCase(Locale.ROOT);
        return normalizedKey.equals("cobblemon.battle.enditem.redcard")
                || lower.contains("red card") || lower.contains("carton rouge");
    }

    private static Text readableArgument(Object value) {
        Text text = value instanceof Text component ? component
                : value instanceof CharSequence sequence ? Text.literal(sequence.toString()) : null;
        return text == null || text.getString().isBlank() || FORMAT_PLACEHOLDER.matcher(text.getString()).find()
                ? null : text;
    }

    private static boolean redCardItem(Text text) {
        if (text == null) return false;
        String key = translationKey(text).replace("_", "");
        String label = text.getString().toLowerCase(Locale.ROOT);
        return key.endsWith("redcard") || label.equals("red card") || label.equals("carton rouge");
    }

    private static int integerArgument(Object value, int fallback) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        if (value instanceof Text text) value = text.getString();
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static void rememberRevealedAbility(String key, Object[] args) {
        if ("cobblemon.battle.endability".equals(key)) {
            UUID pokemon = pokemonFromArgument(argument(args, 0));
            if (pokemon != null) {
                SUPPRESSED_ABILITIES.add(pokemon);
                TeamMemberView member = OPPONENT_TEAM.get(pokemon);
                if (member != null && member.ability() != null) {
                    OPPONENT_TEAM.put(pokemon, member.withAbility(member.ability().withSuppressed(true)));
                }
            }
            return;
        }
        AbilityTemplate ability = revealedAbility(key, args);
        if (ability == null || args.length == 0) return;
        UUID owner = pokemonFromArgument(args[0]);
        if (owner == null) return;

        rememberAbility(owner, ability);
        if (spectating && key.equals("cobblemon.battle.ability.generic")
                && normalizeMove(ability.getName()).equals("screencleaner")) {
            Map<BattleFieldEffects.EffectSide, Set<String>> removals = new LinkedHashMap<>();
            addSideRemovals(removals, BattleFieldEffects.EffectSide.PLAYER_FIELD, DAMAGE_SCREENS);
            addSideRemovals(removals, BattleFieldEffects.EffectSide.OPPONENT_FIELD, DAMAGE_SCREENS);
            BattleFieldEffects.removeObservedSideConditions(removals);
        }
        if (key.equals("cobblemon.battle.ability.generic")
                && ability.getName().equalsIgnoreCase("toxicdebris")) {
            BattleFieldEffects.EffectSide ownerSide = sideFor(owner);
            if (ownerSide != BattleFieldEffects.EffectSide.FIELD) {
                BattleFieldEffects.startHazard("toxicspikes", opposite(ownerSide), turn);
            }
        }
        if (key.equals("cobblemon.battle.ability.magicbounce")) {
            Object reflectedArgument = argument(args, 1);
            MoveTemplate reflected = reflectedArgument instanceof Text moveText ? moveTemplate(moveText) : null;
            BattleFieldEffects.EffectSide ownerSide = sideFor(owner);
            if (reflected != null && ownerSide != BattleFieldEffects.EffectSide.FIELD) {
                if (normalizeMove(reflected.getName()).equals("defog") && pendingSideRemoval != null
                        && pendingSideRemoval.moveId().equals("defog") && pendingSideRemoval.turn() == turn
                        && belongsToCurrentMove(pendingSideRemoval.user(), pendingSideRemoval.turn(),
                        pendingSideRemoval.sequence())) {
                    PendingSideRemoval reflectedRemoval = pendingSideRemoval;
                    if (reflectedRemoval.activation() != null) {
                        BattleFieldEffects.cancelObservedSideRemoval(reflectedRemoval.activation());
                    }
                    BattleFieldEffects.EffectSide reflectedTarget = opposite(ownerSide);
                    Map<BattleFieldEffects.EffectSide, Set<String>> removals = sideRemovalsForMove(
                            "defog", reflectedRemoval.user(), opposite(ownerSide), reflectedTarget);
                    BattleFieldEffects.SideRemovalActivation activation =
                            BattleFieldEffects.removeObservedSideConditions(removals);
                    pendingSideRemoval = activation.changed()
                            ? new PendingSideRemoval(reflectedRemoval.user(), reflectedRemoval.sequence(), "defog",
                            turn, removals, activation, false, false, true) : null;
                }
                String hazard = hazardForMove(normalizeMove(reflected.getName()));
                if (hazard != null) {
                    PendingHazardMove reflectedHazard = pendingHazardMove;
                    if (reflectedHazard != null && reflectedHazard.turn() == turn
                            && reflectedHazard.id().equals(hazard) && reflectedHazard.side() == ownerSide) {
                        BattleFieldEffects.cancelMoveHazard(hazard, ownerSide, turn, reflectedHazard.applied());
                        pendingHazardMove = null;
                    }
                    BattleFieldEffects.startHazard(hazard, opposite(ownerSide), turn);
                }
            }
        }
        if ((key.equals("cobblemon.battle.ability.generic") || key.equals("cobblemon.battle.ability.dancer")
                || key.equals("cobblemon.battle.ability.magicbounce"))
                && Set.of("dancer", "magicbounce").contains(ability.getName().toLowerCase(Locale.ROOT))) {
            FREE_NEXT_MOVE_USE.put(owner, turn);
        }
        // Trace also explicitly identifies the copied ability's original owner.
        if (key.equals("cobblemon.battle.ability.trace") && args.length >= 3) {
            UUID source = pokemonFromArgument(args[1]);
            if (source != null) rememberAbility(source, ability);
        }
    }

    static AbilityTemplate revealedAbility(String key, Object[] args) {
        if (!key.startsWith("cobblemon.battle.ability.")) {
            // Only self-owned causes: Rough Skin/Iron Barbs damage names the
            // victim, not the ability owner, and is insufficient in doubles.
            String id = switch (key) {
                case "cobblemon.battle.heal.raindish" -> "raindish";
                case "cobblemon.battle.heal.poisonheal" -> "poisonheal";
                case "cobblemon.battle.heal.cheekpouch" -> "cheekpouch";
                case "cobblemon.battle.heal.dryskin", "cobblemon.battle.damage.dryskin" -> "dryskin";
                case "cobblemon.battle.heal.eartheater" -> "eartheater";
                case "cobblemon.battle.heal.icybody" -> "icebody";
                case "cobblemon.battle.heal.voltabsorb" -> "voltabsorb";
                case "cobblemon.battle.heal.waterabsorb" -> "waterabsorb";
                default -> "";
            };
            return id.isEmpty() ? null : Abilities.get(id);
        }
        String id = key.substring("cobblemon.battle.ability.".length());
        if (!Set.of("generic", "replace", "receiver", "trace").contains(id)) return Abilities.get(id);

        AbilityTemplate ability = null;
        for (int index = id.equals("trace") ? 2 : 1; index < args.length && ability == null; index++) {
            ability = abilityTemplate(args[index]);
        }
        return ability;
    }

    private static void rememberAbility(UUID owner, AbilityTemplate ability) {
        SUPPRESSED_ABILITIES.remove(owner);
        AbilityView view = abilityView(ability);
        REVEALED_ABILITIES.put(owner, view);
        TeamMemberView member = OPPONENT_TEAM.get(owner);
        if (member != null) {
            OPPONENT_TEAM.put(owner, new TeamMemberView(member.uuid(), member.name(), member.level(),
                    member.hpPercent(), member.status(), member.fainted(), member.active(), member.heldItem(),
                    member.portrait(), member.types(), view, member.knownMoves(), member.statStages(), true));
        }
    }

    private static BattleLogSide logSide(Object[] args, String message, BattleLogEntryType type) {
        if (type == BattleLogEntryType.TURN || type == BattleLogEntryType.HEADER || type == BattleLogEntryType.FIELD) {
            return BattleLogSide.NEUTRAL;
        }
        String subject = argument(args, 0) instanceof Text text ? text.getString() : "";
        BattleLogSide trainer = BattleLogSide.resolve(subject, "", List.copyOf(OWN_ACTOR_NAMES), List.copyOf(OPPONENT_ACTOR_NAMES));
        if (trainer != BattleLogSide.NEUTRAL) return trainer;
        UUID pokemon = pokemonFromArgument(argument(args, 0));
        if (pokemon != null) return isOpponentMember(pokemon) ? BattleLogSide.OPPONENT : BattleLogSide.PLAYER;
        List<String> own = new ArrayList<>(OWN_ACTOR_NAMES);
        List<String> opponents = new ArrayList<>(OPPONENT_ACTOR_NAMES);
        for (var identity : LOG_IDENTITIES.values()) (identity.opponent() ? opponents : own).add(identity.alias());
        return BattleLogSide.resolve(subject, message, own, opponents);
    }

    private static UUID opponentFromArgument(Object arg) {
        UUID pokemon = pokemonFromArgument(arg);
        return OPPONENT_TEAM.containsKey(pokemon) ? pokemon : null;
    }

    private static UUID pokemonFromArgument(Object arg) {
        if (!(arg instanceof Text text)) return null;
        String candidate = text.getString();
        if (isOpponentReference(candidate)) {
            UUID opponent = opponentFromName(candidate);
            if (opponent != null) return opponent;
        }
        UUID own = memberFromName(ownTeam(), OWN_POKEMON_ALIASES, candidate);
        UUID opponent = rawOpponentFromName(candidate);
        if (own != null && isOwnActorReference(candidate)) return own;
        if (own != null && opponent != null) return null;
        return own != null ? own : opponent;
    }

    private static UUID memberFromName(Iterable<TeamMemberView> members,
                                       Map<UUID, Set<String>> aliases, String candidate) {
        List<TeamMemberView> matches = new ArrayList<>();
        for (TeamMemberView member : members) {
            if (nameMatches(candidate, member.name()) || aliases.getOrDefault(member.uuid(), Set.of()).stream()
                    .anyMatch(alias -> nameMatches(candidate, alias))) {
                matches.add(member);
            }
        }
        return uniqueMatch(matches);
    }

    private static UUID opponentFromName(String candidate) {
        if (isOwnActorReference(candidate) && !isOpponentReference(candidate)) return null;
        UUID opponent = rawOpponentFromName(candidate);
        if (opponent == null) return null;

        // In a mirror match, a bare species name is not enough evidence to
        // mutate the opponent's item/PP/ability state. Cobblemon normally
        // supplies "opposing" or the trainer name; otherwise keep the public
        // information unknown instead of assigning it to the wrong Pokémon.
        UUID own = memberFromName(ownTeam(), OWN_POKEMON_ALIASES, candidate);
        if (own != null && !isOpponentReference(candidate)) return null;
        return opponent;
    }

    private static UUID rawOpponentFromName(String candidate) {
        List<TeamMemberView> matches = new ArrayList<>();
        for (TeamMemberView member : OPPONENT_TEAM.values()) {
            if (SPECTATED_LEFT.contains(member.uuid())) continue;
            if (nameMatches(candidate, member.name()) ||
                    OPPONENT_POKEMON_ALIASES.getOrDefault(member.uuid(), Set.of()).stream()
                            .anyMatch(alias -> nameMatches(candidate, alias))) {
                matches.add(member);
            }
        }
        if (matches.isEmpty()) return null;
        return uniqueMatch(matches);
    }

    private static UUID uniqueMatch(List<TeamMemberView> matches) {
        if (matches.size() == 1) return matches.getFirst().uuid();
        List<TeamMemberView> active = matches.stream().filter(TeamMemberView::active).toList();
        return active.size() == 1 ? active.getFirst().uuid() : null;
    }

    static boolean nameMatches(String candidate, String name) {
        if (candidate == null || name == null || name.isBlank()) return false;
        return namePattern(name).matcher(candidate).find();
    }

    static Pattern namePattern(String name) {
        String safeName = name == null ? "" : name;
        return NAME_PATTERNS.computeIfAbsent(safeName, value -> Pattern.compile(
                "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(value) + "(?![\\p{L}\\p{N}])"));
    }

    static boolean isOpponentReference(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("opposing") || lower.contains("adverse") ||
                lower.contains("wild ") || lower.contains(" sauvage")) return true;
        for (String name : OPPONENT_ACTOR_NAMES) if (nameMatches(value, name)) return true;
        return false;
    }

    private static boolean isOwnActorReference(String value) {
        for (String name : OWN_ACTOR_NAMES) if (nameMatches(value, name)) return true;
        return false;
    }

    private static MoveTemplate moveTemplate(Text text) {
        String key = translationKey(text);
        if (!key.isBlank()) {
            String id = key.substring(key.lastIndexOf('.') + 1);
            MoveTemplate move = Moves.getByName(id);
            if (move == null) move = Moves.getByName(id.replace("_", ""));
            if (move != null) return move;
        }
        String label = text.getString();
        for (MoveTemplate move : Moves.all()) {
            if (move.getDisplayName().getString().equalsIgnoreCase(label)) return move;
        }
        return null;
    }

    private static AbilityTemplate abilityTemplate(Object value) {
        String label;
        if (value instanceof Text text) {
            String key = translationKey(text);
            if (!key.isBlank()) {
                String id = key.substring(key.lastIndexOf('.') + 1);
                AbilityTemplate ability = Abilities.get(id);
                if (ability != null) return ability;
            }
            label = text.getString();
        } else if (value instanceof CharSequence text) {
            // Cobblemon 1.7.2's public ability.generic message passes the
            // typeless ability label as a raw String, not as a Text argument.
            label = text.toString();
        } else {
            return null;
        }
        AbilityTemplate direct = Abilities.get(normalizeMove(label));
        if (direct != null) return direct;
        for (AbilityTemplate ability : Abilities.all()) {
            if (normalizeMove(ability.getName()).equals(normalizeMove(label))
                    || translated(ability.getDisplayName()).getString().equalsIgnoreCase(label)) return ability;
        }
        return null;
    }

    private static ItemStack itemStack(Text text) {
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if (key.startsWith("item.")) {
                String[] parts = key.split("\\.", 3);
                if (parts.length == 3) {
                    Identifier id = Identifier.tryParse(parts[1] + ":" + parts[2]);
                    if (id != null) return Registries.ITEM.getOrEmpty(id)
                            .map(item -> item.getDefaultStack()).orElse(ItemStack.EMPTY);
                }
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Text nested) {
                    ItemStack found = itemStack(nested);
                    if (!found.isEmpty()) return found;
                }
            }
        }
        for (Text sibling : text.getSiblings()) {
            ItemStack found = itemStack(sibling);
            if (!found.isEmpty()) return found;
        }
        return ItemStack.EMPTY;
    }

    private static void updateKnownHealth(UUID uuid, float health, boolean opponent) {
        if (opponent || SPECTATED_LEFT.contains(uuid)) {
            TeamMemberView member = OPPONENT_TEAM.get(uuid);
            if (member != null) OPPONENT_TEAM.put(uuid, member.withHealth(health));
            return;
        }
        List<TeamMemberView> updated = new ArrayList<>(ownTeam.size());
        for (TeamMemberView member : ownTeam) {
            updated.add(member.uuid().equals(uuid) ? member.withHealth(health) : member);
        }
        ownTeam = List.copyOf(updated);
    }

    private static boolean isOpponent(ClientBattleActor actor, ClientBattle battle) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientBattleSide left = leftSide(battle, client.player == null ? null : client.player.getUuid());
        return actor != null && left != null && actor.getSide() != left;
    }

    static ClientBattleSide leftSide(ClientBattle battle, UUID viewer) {
        if (battle == null) return null;
        // Cobblemon's overlay uses side 2 on the left when the viewer is not an actor.
        if (battle.getSpectating()) return battle.getSide2();
        ClientBattleActor local = viewer == null ? null : battle.getParticipatingActor(viewer);
        return local == null ? null : local.getSide();
    }

    private static void acceptTurnTimer(String rendered) {
        String lower = rendered == null ? "" : rendered.toLowerCase(Locale.ROOT);
        if (lower.contains("battle timer is off") || lower.contains("battle timer is disabled") ||
                lower.contains("minuteur de combat est désactivé") ||
                lower.contains("minuteur de combat est desactive")) {
            turnDeadline = null;
            timedPlayer = "";
            return;
        }
        Matcher matcher = TURN_TIMER.matcher(rendered == null ? "" : rendered);
        if (!matcher.find()) return;
        try {
            int seconds = Integer.parseInt(matcher.group(1));
            turnDeadline = Instant.now().plusSeconds(Math.max(0, seconds));
            int marker = rendered.toLowerCase(Locale.ROOT).indexOf(" has ");
            timedPlayer = marker > 0 ? rendered.substring(0, marker).trim() : "";
        } catch (NumberFormatException ignored) {
        }
    }

    private static void addEntry(BattleLogEntry entry) {
        // Keep the turn as data: translated/wrapped text is not a navigation index.
        LOG.add(entry.atTurn(turn));
    }

    public static List<BattleLogEntry> logSnapshot() {
        synchronized (LOG) {
            return LOG.snapshot();
        }
    }

    public static long logRevision() {
        return LOG.revision();
    }

    static RevisionedJournal.Delta<BattleLogEntry> logChanges(long epoch, int entries, boolean force) {
        return LOG.since(epoch, entries, force);
    }

    public static boolean active() {
        return battleId != null && CobblemonClient.INSTANCE.getBattle() != null;
    }

    public static int turn() {
        return turn;
    }

    public static Duration elapsed() {
        if (startedAt == null) return Duration.ZERO;
        Instant end = endedAt == null ? Instant.now() : endedAt;
        return Duration.between(startedAt, end).isNegative() ? Duration.ZERO : Duration.between(startedAt, end);
    }

    public static Duration elapsedAt(Instant timestamp) {
        if (startedAt == null || timestamp == null) return Duration.ZERO;
        Duration value = Duration.between(startedAt, timestamp);
        return value.isNegative() ? Duration.ZERO : value;
    }

    public static Optional<String> combatTime() {
        if (turnDeadline != null) {
            long millis = Duration.between(Instant.now(), turnDeadline).toMillis();
            if (millis <= 0) {
                turnDeadline = null;
                timedPlayer = "";
                return Optional.empty();
            }
            long seconds = Math.max(0, (millis + 999) / 1000);
            return Optional.of(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
        }
        return Optional.empty();
    }

    public static String timedPlayer() {
        return timedPlayer;
    }

    public static List<BattleFieldEffects.EffectView> effects() {
        return BattleFieldEffects.snapshot(turn);
    }

    public static List<TeamMemberView> ownTeam() {
        if (spectating) ownTeam = publicTeamSnapshot(ownTeam, true);
        return ownTeam;
    }

    public static List<TeamMemberView> opponentTeam() {
        opponentSnapshot = publicTeamSnapshot(opponentSnapshot, false);
        return opponentSnapshot;
    }

    static List<TeamMemberView> ownHudTeam() {
        if (spectating || teamPreview.player().isEmpty()) return ownTeam();
        ownHudSnapshot = teamPreview.merge(true, ownTeam(), ownHudSnapshot);
        return ownHudSnapshot;
    }

    static List<TeamMemberView> opponentHudTeam() {
        List<TeamMemberView> observed = opponentTeam();
        if (spectating || teamPreview.opponent().isEmpty()) return observed;
        opponentHudSnapshot = teamPreview.merge(false, observed, opponentHudSnapshot);
        return opponentHudSnapshot;
    }

    private static List<TeamMemberView> publicTeamSnapshot(List<TeamMemberView> previous, boolean left) {
        boolean same = true;
        int index = 0;
        for (TeamMemberView value : OPPONENT_TEAM.values()) {
            if (SPECTATED_LEFT.contains(value.uuid()) != left) continue;
            if (index >= previous.size() || previous.get(index) != value) same = false;
            index++;
        }
        if (same && index == previous.size()) return previous;
        return OPPONENT_TEAM.values().stream().filter(value -> SPECTATED_LEFT.contains(value.uuid()) == left).toList();
    }

    private static Map<UUID, Set<String>> publicAliases(UUID pokemon) {
        return SPECTATED_LEFT.contains(pokemon) ? OWN_POKEMON_ALIASES : OPPONENT_POKEMON_ALIASES;
    }

    static boolean publicInformationOnly(UUID pokemon) {
        return OPPONENT_TEAM.containsKey(pokemon) || teamPreview.opponentPlaceholder(pokemon);
    }

    static boolean spectating() { return spectating; }

    static OpponentKnowledgeView opponentKnowledge(UUID uuid) {
        TeamMemberView member = uuid == null ? null : memberById(uuid);
        if (member == null) member = teamPreview.placeholder(uuid);
        if (member == null || !member.known()) return OpponentKnowledgeView.empty();
        List<AbilityView> possibleAbilities = publicInformationOnly(uuid) ? possibleAbilities(member) : List.of();
        SpeedRangeView speed = publicInformationOnly(uuid) ? speedRange(member) : SpeedRangeView.unknown();
        return new OpponentKnowledgeView(possibleAbilities, speed,
                ITEM_HISTORY.getOrDefault(uuid, List.of()),
                FORM_HISTORY.getOrDefault(uuid, List.of()));
    }

    static boolean opponentItemKnownAbsent(UUID uuid) {
        return uuid != null && REVEALED_NO_ITEM.contains(uuid);
    }

    private static List<AbilityView> possibleAbilities(TeamMemberView member) {
        if (member == null || member.portrait() == null || member.portrait().getForm() == null) return List.of();
        return possibleAbilities(member.portrait().getForm().getAbilities(), member.portrait().getAspects());
    }

    static List<AbilityView> possibleAbilities(Iterable<PotentialAbility> pool, Set<String> aspects) {
        LinkedHashMap<String, AbilityView> result = new LinkedHashMap<>();
        for (PotentialAbility potential : pool) {
            if (potential == null || potential.getTemplate() == null) continue;
            // HiddenAbility.isSatisfiedBy is always false: it controls random
            // generation, not whether a revealed species can have this ability.
            boolean hidden = potential instanceof HiddenAbility;
            if (!hidden && aspects != null && !potential.isSatisfiedBy(aspects)) continue;
            AbilityView view = abilityView(potential.getTemplate()).withHidden(hidden);
            result.merge(view.id().toLowerCase(Locale.ROOT), view,
                    (previous, next) -> previous.hidden() ? next : previous);
        }
        return List.copyOf(result.values());
    }

    static SpeedRangeView speedRange(TeamMemberView member) {
        if (member == null || member.level() <= 0 || member.portrait() == null || member.portrait().getForm() == null) {
            return SpeedRangeView.unknown();
        }
        Integer baseSpeed = member.portrait().getForm().getBaseStats().get(Stats.SPEED);
        int stage = member.statStages().stream().filter(value -> value.id().equalsIgnoreCase("spe"))
                .mapToInt(StatStageView::stage).findFirst().orElse(0);
        String ability = member.ability() == null || member.ability().suppressed() ? "" : member.ability().id();
        PokemonSpeedRange.PublicEffects effects = new PokemonSpeedRange.PublicEffects(
                BattleFieldEffects.active("raindance") || BattleFieldEffects.active("primordialsea"),
                BattleFieldEffects.active("sunnyday") || BattleFieldEffects.active("desolateland"),
                BattleFieldEffects.active("sandstorm"),
                BattleFieldEffects.active("hail") || BattleFieldEffects.active("snow"),
                BattleFieldEffects.active("electricterrain"),
                BattleFieldEffects.activeOnSide("tailwind", sideFor(member.uuid()), turn));
        return PokemonSpeedRange.calculate(baseSpeed == null ? 0 : baseSpeed, member.level(), stage,
                member.status(), ability, heldItemId(member.heldItem()), effects);
    }

    public static boolean isOpponentMember(UUID uuid) {
        return uuid != null && OPPONENT_TEAM.containsKey(uuid) && !SPECTATED_LEFT.contains(uuid);
    }

    static TeamMemberView knownOpponent(UUID uuid) { return OPPONENT_TEAM.get(uuid); }
    static TeamMemberView member(UUID uuid) { return memberById(uuid); }
    static String trainerName(UUID uuid) { return uuid == null ? "" : TRAINER_BY_POKEMON.getOrDefault(uuid, ""); }

    static String positionLabel(UUID uuid) {
        ActiveClientBattlePokemon active = activeBattlePokemon(uuid);
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (active == null || battle == null || battle.getBattleFormat().getBattleType().getPokemonPerSide() != 3) return "";
        return TriplePosition.label(active.getDigit(true), isOpponentMember(uuid));
    }
    static AbilityView currentAbility(UUID uuid, AbilityView fallback) {
        AbilityView value = REVEALED_ABILITIES.getOrDefault(uuid, fallback);
        return value == null ? null : value.withSuppressed(SUPPRESSED_ABILITIES.contains(uuid));
    }
    static ItemStack currentItem(UUID uuid, ItemStack fallback) {
        return REVEALED_NO_ITEM.contains(uuid) ? ItemStack.EMPTY : REVEALED_ITEMS.getOrDefault(uuid, fallback);
    }

    public static List<PokemonBattleEffects.PokemonEffectView> pokemonEffects(UUID uuid) {
        return PokemonBattleEffects.snapshot(uuid, turn);
    }

    static BattleStatsView ownBattleStats(UUID uuid) {
        if (uuid == null || spectating || publicInformationOnly(uuid)) return BattleStatsView.UNKNOWN;
        return OWN_BATTLE_STATS.getOrDefault(uuid, BattleStatsView.UNKNOWN);
    }

    public static List<TypeView> activeOpponentTypes() {
        List<ActiveTargetView> targets = activeOpponentTargets();
        return targets.isEmpty() ? List.of() : targets.getFirst().types();
    }

    public static List<ActiveTargetView> activeOpponentTargets() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientBattleSide left = leftSide(battle, client.player == null ? null : client.player.getUuid());
        if (left == null) return List.of();
        ClientBattleSide opposing = left == battle.getSide1() ? battle.getSide2() : battle.getSide1();
        List<ActiveTargetView> targets = new ArrayList<>();
        // Read actual battlefield slots, not the order in which species were first revealed.
        // Form/HP/slot changes are reflected immediately rather than after the five-tick team scan.
        for (ClientBattleActor actor : opposing.getActors()) {
            for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                ActiveTargetView view = activeTargetView(active);
                if (view != null) targets.add(view);
            }
        }
        return List.copyOf(targets);
    }

    static ActiveTargetView activeTargetView(ActiveClientBattlePokemon active) {
        if (active == null || active.getBattlePokemon() == null || active.getBattlePokemon().getHpValue() <= 0) return null;
        ClientBattlePokemon pokemon = active.getBattlePokemon();
        RenderablePokemon portrait = opponentRenderable(pokemon);
        TeamMemberView known = memberById(pokemon.getUuid());
        AbilityView ability = REVEALED_ABILITIES.getOrDefault(pokemon.getUuid(), known == null ? null : known.ability());
        if (ability != null) ability = ability.withSuppressed(SUPPRESSED_ABILITIES.contains(pokemon.getUuid()));
        ItemStack item = REVEALED_NO_ITEM.contains(pokemon.getUuid()) ? ItemStack.EMPTY
                : REVEALED_ITEMS.getOrDefault(pokemon.getUuid(), known == null ? ItemStack.EMPTY : known.heldItem());
        return new ActiveTargetView(pokemon.getUuid(), speciesName(pokemon.getSpecies(), portrait.getForm()),
                currentTypeViews(pokemon.getUuid(), portrait.getForm().getTypes()), ability, heldItemId(item),
                resolvedStatus(pokemon.getUuid(), pokemon.getStatus() == null ? "" : pokemon.getStatus().getShowdownName()),
                statStages(pokemon),
                BattleHealthFormatting.percent(pokemon.isHpFlat(), pokemon.getHpValue(), pokemon.getMaxHp()));
    }

    static boolean canUsePrivateData(ActiveClientBattlePokemon active) {
        MinecraftClient client = MinecraftClient.getInstance();
        return active != null && client.player != null && !spectating()
                && active.getActor().getUuid().equals(client.player.getUuid());
    }

    static BattleFieldEffects.EffectSide effectSide(UUID pokemon) {
        return sideFor(pokemon);
    }

    static TeamMemberView activeOwnMember() {
        return ownTeam().stream().filter(member -> member.active() && !member.fainted()).findFirst().orElse(null);
    }

    static List<TeamMemberView> activeOwnMembers() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientBattleSide left = leftSide(battle, client.player == null ? null : client.player.getUuid());
        if (left == null) return List.of();
        List<TeamMemberView> result = new ArrayList<>();
        for (ClientBattleActor actor : left.getActors()) {
            for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                if (active.getBattlePokemon() == null || active.getBattlePokemon().getHpValue() <= 0) continue;
                var member = ownMember(active.getBattlePokemon().getUuid());
                if (member != null) result.add(member);
            }
        }
        return List.copyOf(result);
    }

    static TeamMemberView ownMember(UUID uuid) {
        if (uuid == null) return activeOwnMember();
        TeamMemberView member = ownTeam().stream().filter(value -> value.uuid().equals(uuid)).findFirst().orElse(null);
        ActiveClientBattlePokemon active = activeBattlePokemon(uuid);
        if (member == null || active == null || active.getBattlePokemon() == null) return member;
        ClientBattlePokemon pokemon = active.getBattlePokemon();
        RenderablePokemon portrait = opponentRenderable(pokemon);
        AbilityView ability = REVEALED_ABILITIES.getOrDefault(uuid, member.ability());
        if (ability != null) ability = ability.withSuppressed(SUPPRESSED_ABILITIES.contains(uuid));
        ItemStack item = REVEALED_NO_ITEM.contains(uuid) ? ItemStack.EMPTY : REVEALED_ITEMS.getOrDefault(uuid, member.heldItem());
        TeamMemberView next = TeamMemberView.snapshot(LIVE_OWN.getOrDefault(uuid, member), uuid, speciesName(pokemon.getSpecies(), portrait.getForm()), pokemon.getLevel(),
                BattleHealthFormatting.percent(pokemon.isHpFlat(), pokemon.getHpValue(), pokemon.getMaxHp()),
                resolvedStatus(uuid, pokemon.getStatus() == null ? "" : pokemon.getStatus().getShowdownName()),
                pokemon.getHpValue() <= 0,
                true, item, portrait, currentTypeViews(uuid, portrait.getForm().getTypes()), ability,
                member.knownMoves(), statStages(pokemon), true);
        LIVE_OWN.put(uuid, next);
        return next;
    }

    static String lastSelectedMove(UUID pokemon) {
        return pokemon == null ? "" : LAST_SELECTED_MOVES.getOrDefault(pokemon, "");
    }

    static boolean activeOpponentHasRevealedMove(String moveId) {
        String normalized = normalizeMove(moveId);
        if (normalized.isBlank()) return false;
        return opponentTeam().stream().filter(TeamMemberView::active)
                .flatMap(member -> member.knownMoves().stream())
                .anyMatch(move -> normalizeMove(move.id()).equals(normalized));
    }

    static boolean activeOpponentHasEffect(String effectId) {
        return opponentTeam().stream().filter(TeamMemberView::active)
                .anyMatch(member -> PokemonBattleEffects.active(member.uuid(), effectId, turn));
    }

    static Float animatedHealthTarget(ClientBattlePokemon pokemon) {
        if (pokemon == null) return null;
        Float target = LAST_HEALTH.get(pokemon.getUuid());
        if (target == null || Math.abs(target - pokemon.getHpValue()) < 0.0001F) return null;
        return BattleHealthFormatting.percent(pokemon.isHpFlat(), target, pokemon.getMaxHp());
    }

    static ActiveClientBattlePokemon activeBattlePokemon(UUID pokemon) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || pokemon == null) return null;
        for (ClientBattleSide side : battle.getSides()) {
            for (ClientBattleActor actor : side.getActors()) {
                for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                    if (active.getBattlePokemon() != null && pokemon.equals(active.getBattlePokemon().getUuid())) {
                        return active;
                    }
                }
            }
        }
        return null;
    }

    public static String ownSideName() {
        return ownSideName;
    }

    public static String opponentSideName() {
        return opponentSideName;
    }

    public static int opponentSlotCount() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return 0;
        ClientBattleActor wild = battle.getWildActor();
        if (wild != null && wild.getType() == ActorType.WILD && isOpponent(wild, battle)) return Math.max(1, opponentTeam().size());
        return Math.max(6, opponentTeam().size());
    }

    public static Phase phase() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return Phase.WAITING;
        if (battle.getSpectating()) return Phase.SPECTATING;
        if (battle.getMustChoose()) {
            SingleActionRequest request = battle.getFirstUnansweredRequest();
            if (request != null && request.getForceSwitch()) return Phase.FORCE_SWITCH;
            return Phase.YOUR_TURN;
        }
        return Phase.WAITING;
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

    private static String sideName(ClientBattleSide side) {
        List<String> names = new ArrayList<>();
        for (ClientBattleActor actor : side.getActors()) names.add(actor.getDisplayName().getString());
        return String.join(" & ", names);
    }

    private static void registerActorIdentities(ClientBattleSide side, boolean opponent) {
        for (ClientBattleActor actor : side.getActors()) {
            String name = actor.getDisplayName().getString();
            // A wild actor is often named exactly like its Pokémon. Treating
            // that as a trainer prefix would misclassify the player's Pokémon
            // in a mirror wild battle; "wild/opposing" in the message remains
            // the reliable side marker in that case.
            if (actor.getType() != ActorType.WILD) {
                (opponent ? OPPONENT_ACTOR_NAMES : OWN_ACTOR_NAMES).add(name);
            }
            registerLogIdentity(name, name, opponent, false);
        }
    }

    private static void registerPokemonIdentity(String alias, String speciesName, boolean opponent) {
        registerLogIdentity(alias, speciesName, opponent, true);
        registerLogIdentity(speciesName, speciesName, opponent, true);
    }

    private static void registerPokemonAlias(UUID pokemon, String alias, boolean opponent) {
        if (pokemon == null || alias == null || alias.isBlank()) return;
        Map<UUID, Set<String>> aliases = opponent ? OPPONENT_POKEMON_ALIASES : OWN_POKEMON_ALIASES;
        aliases.computeIfAbsent(pokemon, ignored -> new java.util.LinkedHashSet<>()).add(alias);
    }

    static RenderablePokemon opponentRenderable(ClientBattlePokemon pokemon) {
        Set<String> aspects = Set.of();
        // FloatingState is updated by ClientBattlePokemon.updateAspects and is
        // therefore authoritative after Transform and forme changes. Merging
        // it with the original properties can create impossible combinations
        // (for example two regional/form aspects at once) and select the wrong
        // Cobblemon model.
        if (pokemon.getState() != null && pokemon.getState().getCurrentAspects() != null) {
            aspects = pokemon.getState().getCurrentAspects();
        }
        if (pokemon.getState() == null && pokemon.getProperties().getAspects() != null) {
            aspects = pokemon.getProperties().getAspects();
        }
        RenderablePokemon previous = PORTRAITS.get(pokemon.getUuid());
        if (previous != null && previous.getSpecies() == pokemon.getSpecies() && previous.getAspects().equals(aspects)) return previous;
        if (PORTRAITS.size() > 64) PORTRAITS.clear();
        RenderablePokemon next = new RenderablePokemon(pokemon.getSpecies(), Set.copyOf(aspects), ItemStack.EMPTY);
        PORTRAITS.put(pokemon.getUuid(), next);
        return next;
    }

    private static String speciesName(Species species, FormData form) {
        String base = species.getTranslatedName().getString();
        if (form == null || form == species.getStandardForm()) return base;
        String formName = form.getName();
        if (formName == null || formName.isBlank() ||
                Set.of("normal", "standard", "base").contains(formName.toLowerCase(Locale.ROOT))) return base;
        return base + "-" + formName;
    }

    private static void registerLogIdentity(String alias, String displayName, boolean opponent, boolean pokemon) {
        if (alias == null || alias.isBlank() || displayName == null || displayName.isBlank()) return;
        String side = opponent ? "opponent:" : "player:";
        LOG_IDENTITIES.put(side + alias.toLowerCase(Locale.ROOT),
                new LogIdentity(alias, displayName, opponent, pokemon));
    }

    static List<LogIdentity> logIdentities() {
        List<LogIdentity> result = new ArrayList<>(LOG_IDENTITIES.values());
        result.sort(java.util.Comparator.comparingInt((LogIdentity identity) -> identity.alias().length()).reversed());
        return List.copyOf(result);
    }

    static List<LogIdentity> logIdentities(String context) {
        boolean opponentContext = isOpponentReference(context);
        Map<String, List<LogIdentity>> byAlias = new LinkedHashMap<>();
        for (LogIdentity identity : logIdentities()) {
            byAlias.computeIfAbsent(identity.alias().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .add(identity);
        }
        List<LogIdentity> resolved = new ArrayList<>();
        for (List<LogIdentity> candidates : byAlias.values()) {
            LogIdentity selected = candidates.stream()
                    .filter(identity -> identity.opponent() == opponentContext)
                    .findFirst()
                    .orElse(candidates.getFirst());
            resolved.add(selected);
        }
        resolved.sort(java.util.Comparator.comparingInt((LogIdentity identity) -> identity.alias().length()).reversed());
        return List.copyOf(resolved);
    }

    static boolean isRandomFormat(String mod, Set<String> rules) {
        return isRandomFormat(mod, rules, "");
    }

    static boolean isRandomFormat(String mod, Set<String> rules, String battleTypeName) {
        if (mod != null && mod.toLowerCase(Locale.ROOT).contains("random")) return true;
        for (String rule : rules) if (normalizeRule(rule).contains("random")) return true;
        return normalizeRule(battleTypeName).contains("random");
    }

    private static String normalizeRule(String rule) {
        return rule == null ? "" : rule.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalizeMove(String move) {
        return move == null ? "" : move.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String hazardForMove(String move) {
        return switch (move) {
            case "spikes", "ceaselessedge" -> "spikes";
            case "toxicspikes" -> "toxicspikes";
            case "stealthrock", "stoneaxe" -> "stealthrock";
            case "stickyweb" -> "stickyweb";
            default -> null;
        };
    }

    public enum Phase {
        YOUR_TURN("text.tropimon_ui_battle.your_turn", 0xFF55D5DE),
        FORCE_SWITCH("text.tropimon_ui_battle.force_switch", 0xFFFFC857),
        WAITING("text.tropimon_ui_battle.waiting", 0xFFB5BDC5),
        SPECTATING("text.tropimon_ui_battle.spectating", 0xFFB68CFF);

        private final String translationKey;
        private final int color;

        Phase(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public Text text() {
            return Text.translatable(translationKey);
        }

        public int color() {
            return color;
        }
    }

    record LogIdentity(String alias, String displayName, boolean opponent, boolean pokemon) {
    }

    static String heldItemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).getPath();
    }

    public record ActiveTargetView(UUID uuid, String name, List<TypeView> types, AbilityView ability,
                                   String heldItemId, String status, List<StatStageView> statStages, float hpPercent) {
        public ActiveTargetView(UUID uuid, String name, List<TypeView> types, AbilityView ability,
                                String heldItemId, String status, List<StatStageView> statStages) {
            this(uuid, name, types, ability, heldItemId, status, statStages, Float.NaN);
        }
        public ActiveTargetView(UUID uuid, String name, List<TypeView> types, AbilityView ability) {
            this(uuid, name, types, ability, "", "", List.of());
        }

        public ActiveTargetView(UUID uuid, String name, List<TypeView> types, AbilityView ability,
                                String heldItemId) {
            this(uuid, name, types, ability, heldItemId, "", List.of());
        }

        public ActiveTargetView {
            name = name == null ? "?" : name;
            types = types == null ? List.of() : List.copyOf(types);
            heldItemId = heldItemId == null ? "" : heldItemId;
            status = status == null ? "" : status;
            statStages = statStages == null ? List.of() : List.copyOf(statStages);
        }
    }

    private record DynamicTypeState(List<TypeView> types, boolean persistent, List<TypeView> originalTypes) {
        private DynamicTypeState {
            types = types == null ? List.of() : List.copyOf(types);
            originalTypes = originalTypes == null ? List.of() : List.copyOf(originalTypes);
        }
    }

    private record PendingMoveUse(int turn) {
    }

    private record PendingMoveTarget(String move, UUID target, int turn) {
    }

    private record PendingBatonPass(int turn, long sequence) {
    }

    private record PendingItemSwap(UUID user, UUID target, ItemStack userItem, ItemStack targetItem, int turn) {
        private PendingItemSwap {
            userItem = userItem == null ? ItemStack.EMPTY : userItem.copy();
            targetItem = targetItem == null ? ItemStack.EMPTY : targetItem.copy();
        }
    }

    private record PendingHazardMove(UUID user, long sequence, String id,
                                     BattleFieldEffects.EffectSide side, int turn, boolean applied) {
    }

    private record PendingSideEffectMove(UUID user, long sequence, String id,
                                         BattleFieldEffects.EffectSide side, int turn,
                                         BattleFieldEffects.SideEffectMoveActivation activation,
                                         boolean spectating) {
    }

    private record PendingSideRemoval(UUID user, long sequence, String moveId, int turn,
                                      Map<BattleFieldEffects.EffectSide, Set<String>> removals,
                                      BattleFieldEffects.SideRemovalActivation activation,
                                      boolean needsHitConfirmation, boolean hitConfirmed,
                                      boolean spectating) {
        private PendingSideRemoval {
            removals = Map.copyOf(removals);
        }
    }

    private record CurrentMove(UUID user, int turn, long sequence, boolean effectsRecorded) {
    }
}
