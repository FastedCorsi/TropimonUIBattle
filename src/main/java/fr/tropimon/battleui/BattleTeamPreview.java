package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.item.components.PokemonItemComponent;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Captures only the species and forms publicly exposed by the pre-battle inventory. */
final class BattleTeamPreview {
    private static final long TTL_MILLIS = 600_000L;
    private static final long CAPTURE_INTERVAL_TICKS = 10L;
    private static Pending pending;
    private static Screen lastScreen;
    private static long tick;
    private static long nextCaptureTick;

    private BattleTeamPreview() { }

    static void tick(MinecraftClient client, boolean battleActive) {
        long now = System.currentTimeMillis();
        if (client == null || client.player == null || client.world == null) {
            reset();
            return;
        }
        expire(now);
        Screen screen = client.currentScreen;
        tick++;
        if (battleActive) {
            lastScreen = screen;
            return;
        }
        boolean changed = screen != lastScreen;
        if (!changed && (screen == null || tick < nextCaptureTick)) return;
        lastScreen = screen;
        nextCaptureTick = tick + CAPTURE_INTERVAL_TICKS;
        captureVisible(screen, now);
    }

    private static void captureVisible(Screen screen, long now) {
        if (!(screen instanceof HandledScreen<?> handled) || !recognizedTitle(screen.getTitle().getString())) return;
        List<net.minecraft.screen.slot.Slot> slots = handled.getScreenHandler().slots;
        int containerSlots = Math.max(0, slots.size() - 36);
        if (containerSlots == 0) return;
        List<PreviewSlot> visible = slots.stream()
                .map(slot -> new PreviewSlot(slot.id, slot.getStack()))
                .toList();
        capture(screen, screen.getTitle().getString(), visible, containerSlots, now);
    }

    static boolean capture(Object screen, String title, List<PreviewSlot> slots, int containerSlots, long now) {
        if (screen == null || !recognizedTitle(title) || slots == null || containerSlots <= 0) return false;
        boolean doubles = normalize(title).contains("select2pokemonfordoubles");
        List<PreviewMember> player = roster(slots, containerSlots, doubles, true);
        List<PreviewMember> opponent = roster(slots, containerSlots, doubles, false);
        if (player.isEmpty() && opponent.isEmpty()) return false;
        remember(screen, new Snapshot(player, opponent), now);
        return true;
    }

    static void remember(Object screen, Snapshot snapshot, long now) {
        List<PreviewMember> player = snapshot == null ? List.of() : snapshot.player();
        List<PreviewMember> opponent = snapshot == null ? List.of() : snapshot.opponent();
        if (pending != null && pending.screen() == screen) {
            if (player.isEmpty()) player = pending.snapshot().player();
            if (opponent.isEmpty()) opponent = pending.snapshot().opponent();
        }
        pending = new Pending(screen, new Snapshot(player, opponent), now + TTL_MILLIS);
    }

    private static List<PreviewMember> roster(List<PreviewSlot> slots, int containerSlots,
                                               boolean doubles, boolean player) {
        List<PreviewMember> result = new ArrayList<>();
        for (PreviewSlot slot : slots) {
            if (!(player ? playerSlot(slot.id(), containerSlots, doubles)
                    : opponentSlot(slot.id(), containerSlots, doubles))) continue;
            PreviewMember member = fromStack(slot.id(), player, slot.stack());
            if (member != null) result.add(member);
        }
        return List.copyOf(result);
    }

    static PreviewMember fromStack(int slot, boolean player, ItemStack stack) {
        PokemonItemComponent pokemonItem = pokemonItemComponent(stack);
        if (pokemonItem == null) return null;
        Species species = PokemonSpecies.getByIdentifier(pokemonItem.getSpecies());
        if (species == null) return null;
        Set<String> aspects = Set.copyOf(pokemonItem.getAspects());
        RenderablePokemon portrait = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
        FormData form = portrait.getForm();
        String name = displayName(species, form);
        List<TypeView> types = new ArrayList<>();
        if (form != null) form.getTypes().forEach(type -> types.add(new TypeView(type.getName(), type.getDisplayName())));
        String identity = (player ? "player:" : "opponent:") + slot + ":" + pokemonItem.getSpecies()
                + ":" + String.join(",", aspects.stream().sorted().toList());
        UUID uuid = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        TeamMemberView view = new TeamMemberView(uuid, name, 0, 100.0F, "", false, false,
                ItemStack.EMPTY, portrait, types, null, List.of(), List.of(), true);
        return new PreviewMember(slot, pokemonItem.getSpecies(), formId(form), view);
    }

    static PokemonItemComponent pokemonItemComponent(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(CobblemonItemComponents.POKEMON_ITEM);
    }

    private static String displayName(Species species, FormData form) {
        String base = species.getTranslatedName().getString();
        if (form == null || form == species.getStandardForm()) return base;
        String formName = form.getName();
        if (formName == null || formName.isBlank()
                || Set.of("normal", "standard", "base").contains(formName.toLowerCase(Locale.ROOT))) return base;
        return base + "-" + formName;
    }

    static Snapshot consume(long now) {
        expire(now);
        if (pending == null) return Snapshot.EMPTY;
        Snapshot result = pending.snapshot();
        pending = null;
        return result;
    }

    private static void expire(long now) {
        if (pending != null && now > pending.expiresAt()) pending = null;
    }

    static boolean recognizedTitle(String title) {
        String normalized = normalize(title);
        return normalized.contains("selectyourleadpokemon")
                || normalized.contains("select2pokemonfordoubles")
                || normalized.contains("selectiondelequipe");
    }

    static boolean playerSlot(int slot, int containerSlots, boolean doubles) {
        if (slot < 0 || slot >= containerSlots) return false;
        if (!doubles) return slot % 9 == 0;
        return slot >= 18 && slot < 45 && (slot % 9 == 1 || slot % 9 == 2);
    }

    static boolean opponentSlot(int slot, int containerSlots, boolean doubles) {
        if (slot < 0 || slot >= containerSlots) return false;
        if (!doubles) return slot % 9 == 8;
        return slot >= 18 && slot < 45 && (slot % 9 == 6 || slot % 9 == 7);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static void reset() {
        pending = null;
        lastScreen = null;
        tick = 0L;
        nextCaptureTick = 0L;
    }

    record PreviewSlot(int id, ItemStack stack) { }

    record PreviewMember(int slot, Identifier species, String form, TeamMemberView view) {
        private boolean exact(TeamMemberView observed) {
            return sameSpecies(observed) && form.equals(formId(observed.portrait() == null
                    ? null : observed.portrait().getForm()));
        }

        private boolean sameSpecies(TeamMemberView observed) {
            if (observed == null) return false;
            if (observed.portrait() != null) {
                return species.equals(observed.portrait().getSpecies().getResourceIdentifier());
            }
            return normalize(species.getPath()).equals(normalize(observed.name()))
                    || normalize(view.name()).equals(normalize(observed.name()));
        }
    }

    record Snapshot(List<PreviewMember> player, List<PreviewMember> opponent) {
        static final Snapshot EMPTY = new Snapshot(List.of(), List.of());

        Snapshot {
            player = player == null ? List.of() : List.copyOf(player);
            opponent = opponent == null ? List.of() : List.copyOf(opponent);
        }

        boolean empty() { return player.isEmpty() && opponent.isEmpty(); }

        boolean opponentPlaceholder(UUID uuid) {
            return uuid != null && opponent.stream().anyMatch(member -> member.view().uuid().equals(uuid));
        }

        TeamMemberView placeholder(UUID uuid) {
            if (uuid == null) return null;
            for (PreviewMember member : player) if (member.view().uuid().equals(uuid)) return member.view();
            for (PreviewMember member : opponent) if (member.view().uuid().equals(uuid)) return member.view();
            return null;
        }

        List<TeamMemberView> merge(boolean playerSide, List<TeamMemberView> observed,
                                   List<TeamMemberView> previous) {
            List<PreviewMember> preview = playerSide ? player : opponent;
            if (preview.isEmpty()) return observed;
            List<TeamMemberView> actual = observed == null ? List.of() : observed;
            boolean[] used = new boolean[actual.size()];
            List<TeamMemberView> result = new ArrayList<>(Math.max(preview.size(), actual.size()));
            for (int previewIndex = 0; previewIndex < preview.size(); previewIndex++) {
                PreviewMember member = preview.get(previewIndex);
                int match = previousMatch(previous, previewIndex, actual, used);
                if (match < 0) match = find(member, actual, used, true);
                if (match < 0) match = find(member, actual, used, false);
                if (match >= 0) {
                    used[match] = true;
                    result.add(actual.get(match));
                } else {
                    result.add(member.view());
                }
            }
            for (int index = 0; index < actual.size(); index++) if (!used[index]) result.add(actual.get(index));
            if (same(previous, result)) return previous;
            return List.copyOf(result);
        }

        private static int previousMatch(List<TeamMemberView> previous, int slot,
                                         List<TeamMemberView> actual, boolean[] used) {
            if (previous == null || slot >= previous.size()) return -1;
            UUID uuid = previous.get(slot).uuid();
            for (int index = 0; index < actual.size(); index++) {
                if (!used[index] && uuid.equals(actual.get(index).uuid())) return index;
            }
            return -1;
        }

        private static int find(PreviewMember preview, List<TeamMemberView> actual,
                                boolean[] used, boolean exact) {
            for (int index = 0; index < actual.size(); index++) {
                if (used[index]) continue;
                if (exact ? preview.exact(actual.get(index)) : preview.sameSpecies(actual.get(index))) return index;
            }
            return -1;
        }

        private static boolean same(List<TeamMemberView> previous, List<TeamMemberView> current) {
            if (previous == null || previous.size() != current.size()) return false;
            for (int index = 0; index < current.size(); index++) {
                if (!TeamMemberView.sameData(previous.get(index), current.get(index))) return false;
            }
            return true;
        }
    }

    private static String formId(FormData form) {
        return form == null || form.getName() == null ? "" : form.getName().toLowerCase(Locale.ROOT);
    }

    private record Pending(Object screen, Snapshot snapshot, long expiresAt) { }
}
