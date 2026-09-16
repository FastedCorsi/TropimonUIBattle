package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PokemonPortraitRenderer {
    private static final int MAX_STATES = 64;
    private static final Map<UUID, PortraitState> STATES = new LinkedHashMap<>();
    private static final Map<UUID, FailedPortrait> FAILED = new LinkedHashMap<>();
    private static final ProfileDrawBridge PROFILE_DRAW = ProfileDrawBridge.resolve();

    private PokemonPortraitRenderer() {
    }

    static boolean draw(DrawContext context, UUID uuid, RenderablePokemon pokemon, int x, int y, int size) {
        if (context == null || uuid == null || pokemon == null || size <= 0) return false;
        if (failed(uuid, pokemon, UiResourceEpoch.current())) return false;
        boolean clipped = false;
        boolean pushed = false;
        try {
            PortraitState portrait = state(uuid, pokemon);
            context.enableScissor(x, y, x + size, y + size);
            clipped = true;
            context.getMatrices().push();
            pushed = true;

            // Cobblemon's per-species profile transform fits a full-body view. Leave
            // breathing room inside the portrait bounds, without a minimum zoom on tiny GUIs.
            float scale = size / 44.0F;
            context.getMatrices().translate(x + size / 2.0D, y, 1000.0D);
            context.getMatrices().scale(scale, scale, scale);
            Quaternionf rotation = portrait.rotation().rotationXYZ(
                    (float) Math.toRadians(13.0D), (float) Math.toRadians(25.0D), 0.0F);
            MinecraftClient client = MinecraftClient.getInstance();
            float frameTicks = animationStep(client.getRenderTickCounter().getLastFrameDuration(), client.isPaused());
            PROFILE_DRAW.draw(pokemon, context.getMatrices(), rotation, portrait.state(), frameTicks);
            return true;
        } catch (RuntimeException | LinkageError error) {
            rememberFailure(uuid, pokemon, UiResourceEpoch.current());
            TropimonUIBattleClient.LOGGER.debug("Portrait rendering disabled until the next resource reload: {}",
                    pokemon.getSpecies().getName(), error);
            return false;
        } finally {
            if (pushed) context.getMatrices().pop();
            if (clipped) context.disableScissor();
        }
    }

    static float animationStep(float frameTicks, boolean paused) {
        // FloatingState adds this delta; an absolute tick/partial time would accelerate with FPS.
        return paused || !Float.isFinite(frameTicks) ? 0.0F : Math.max(0.0F, Math.min(2.0F, frameTicks));
    }

    static String profileApiGeneration() {
        return PROFILE_DRAW.generation().name();
    }

    private static synchronized PortraitState state(UUID uuid, RenderablePokemon pokemon) {
        PortraitState cached = STATES.get(uuid);
        if (cached != null && cached.species() == pokemon.getSpecies() &&
                cached.aspects().equals(pokemon.getAspects())) return cached;
        Set<String> aspects = Set.copyOf(pokemon.getAspects());
        if (STATES.size() >= MAX_STATES) STATES.remove(STATES.keySet().iterator().next());
        FloatingState state = new FloatingState();
        state.setCurrentAspects(aspects);
        PortraitState created = new PortraitState(pokemon.getSpecies(), aspects, state, new Quaternionf());
        STATES.put(uuid, created);
        return created;
    }

    private static synchronized boolean failed(UUID uuid, RenderablePokemon pokemon, long resources) {
        FailedPortrait failure = FAILED.get(uuid);
        return failure != null && failure.resources() == resources && failure.species() == pokemon.getSpecies()
                && failure.aspects().equals(pokemon.getAspects());
    }

    private static synchronized void rememberFailure(UUID uuid, RenderablePokemon pokemon, long resources) {
        if (FAILED.size() >= MAX_STATES && !FAILED.containsKey(uuid))
            FAILED.remove(FAILED.keySet().iterator().next());
        FAILED.put(uuid, new FailedPortrait(pokemon.getSpecies(), Set.copyOf(pokemon.getAspects()), resources));
    }

    static synchronized void clear() {
        STATES.clear();
        FAILED.clear();
    }

    private record PortraitState(com.cobblemon.mod.common.pokemon.Species species, Set<String> aspects,
                                 FloatingState state, Quaternionf rotation) {
    }
    private record FailedPortrait(com.cobblemon.mod.common.pokemon.Species species, Set<String> aspects,
                                  long resources) {
    }

    /** Keeps one JAR compatible with both Cobblemon 1.7.x and its 1.8 profile-render API. */
    private record ProfileDrawBridge(MethodHandle method, Object profileTransform,
                                     ProfileApiGeneration generation, Throwable resolutionFailure) {
        private static ProfileDrawBridge resolve() {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try {
                Class<?> transformType = Class.forName(
                        "com.cobblemon.mod.common.client.gui.ProfileTransformType");
                Object profile = enumConstant(transformType, "PROFILE");
                Method modern = PokemonGuiUtilsKt.class.getMethod("drawProfilePokemon",
                        RenderablePokemon.class, MatrixStack.class, Quaternionf.class, PoseType.class,
                        PosableState.class, float.class, float.class, transformType, boolean.class,
                        float.class, float.class, float.class, float.class, float.class, float.class, int.class);
                return new ProfileDrawBridge(lookup.unreflect(modern), profile,
                        ProfileApiGeneration.MODERN, null);
            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
                // Cobblemon 1.7.x has no ProfileTransformType and uses two booleans instead.
            } catch (IllegalAccessException | LinkageError error) {
                return unavailable(error);
            }

            try {
                Method legacy = PokemonGuiUtilsKt.class.getMethod("drawProfilePokemon",
                        RenderablePokemon.class, MatrixStack.class, Quaternionf.class, PoseType.class,
                        PosableState.class, float.class, float.class, boolean.class, boolean.class,
                        float.class, float.class, float.class, float.class, float.class, float.class);
                return new ProfileDrawBridge(lookup.unreflect(legacy), null,
                        ProfileApiGeneration.LEGACY, null);
            } catch (ReflectiveOperationException | LinkageError error) {
                return unavailable(error);
            }
        }

        private static ProfileDrawBridge unavailable(Throwable error) {
            return new ProfileDrawBridge(null, null, ProfileApiGeneration.UNAVAILABLE, error);
        }

        private static Object enumConstant(Class<?> type, String name) throws NoSuchFieldException {
            Object[] constants = type.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (constant instanceof Enum<?> value && value.name().equals(name)) return value;
                }
            }
            throw new NoSuchFieldException(type.getName() + "." + name);
        }

        void draw(RenderablePokemon pokemon, MatrixStack matrices, Quaternionf rotation,
                  FloatingState state, float frameTicks) {
            if (method == null) {
                throw new IllegalStateException("Unsupported Cobblemon profile-render API", resolutionFailure);
            }
            try {
                if (generation == ProfileApiGeneration.MODERN) {
                    method.invoke(pokemon, matrices, rotation, PoseType.PROFILE, state,
                            frameTicks, 20.0F, profileTransform, false,
                            1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 13);
                } else {
                    method.invoke(pokemon, matrices, rotation, PoseType.PROFILE, state,
                            frameTicks, 20.0F, true, false,
                            1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                }
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable error) {
                throw new IllegalStateException("Cobblemon profile rendering failed", error);
            }
        }
    }

    private enum ProfileApiGeneration {
        LEGACY,
        MODERN,
        UNAVAILABLE
    }
}
