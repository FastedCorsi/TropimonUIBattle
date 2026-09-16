package fr.tropimon.battleui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Each battle effect owns an independent original Tropimon UI Battle PNG.
 * Keeping the files separate prevents one icon replacement from affecting
 * the sampling, padding or sharpness of any other effect.
 */
final class BattleEffectIcon {
    private static final int GENERATED_ICON_SIZE = 128;
    private static final Icon GENERIC = generated("generic_effect");
    private static final Map<String, Icon> ICONS = icons();

    private BattleEffectIcon() {
    }

    static Icon forEffect(String effectId) {
        return ICONS.getOrDefault(simpleId(effectId), GENERIC);
    }

    private static Map<String, Icon> icons() {
        Map<String, Icon> icons = new LinkedHashMap<>();
        icons.put("raindance", generated("rain"));
        icons.put("primordialsea", generated("primordial_sea"));
        icons.put("watersport", generated("water_sport"));
        icons.put("sunnyday", generated("sun"));
        icons.put("desolateland", generated("desolate_land"));
        icons.put("sandstorm", generated("sandstorm"));
        icons.put("mudsport", generated("mud_sport"));
        icons.put("hail", generated("hail"));
        icons.put("snow", generated("snow"));
        icons.put("electricterrain", generated("electric_terrain"));
        icons.put("grassyterrain", generated("grassy_terrain"));
        icons.put("mistyterrain", generated("misty_terrain"));
        icons.put("psychicterrain", generated("psychic_terrain"));
        icons.put("lightscreen", generated("light_screen"));
        icons.put("safeguard", generated("safeguard"));
        icons.put("mist", generated("mist"));
        icons.put("luckychant", generated("lucky_chant"));
        icons.put("reflect", generated("reflect"));
        icons.put("auroraveil", generated("aurora_veil"));
        icons.put("trickroom", generated("trick_room"));
        icons.put("magicroom", generated("magic_room"));
        icons.put("wonderroom", generated("wonder_room"));
        icons.put("tailwind", generated("tailwind"));
        icons.put("deltastream", generated("delta_stream"));
        icons.put("gravity", generated("gravity"));
        icons.put("stealthrock", generated("stealth_rock"));
        icons.put("spikes", generated("spikes"));
        icons.put("toxicspikes", generated("toxic_spikes"));
        icons.put("stickyweb", generated("sticky_web"));
        return Map.copyOf(icons);
    }

    private static Icon generated(String name) {
        return full(name, GENERATED_ICON_SIZE, GENERATED_ICON_SIZE);
    }

    private static Icon full(String name, int width, int height) {
        Identifier texture = Identifier.of("tropimon_ui_battle", "textures/gui/effects/" + name + ".png");
        return new Icon(texture, 0, 0, width, height, width, height);
    }

    private static String simpleId(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('.');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    record Icon(Identifier texture, int u, int v, int width, int height,
                int textureWidth, int textureHeight) {
        void draw(DrawContext context, int x, int y, int size) {
            // Explicit blending matters here: the generated icons use real
            // alpha and must never render their transparent canvas as black.
            BattleUiSkin.drawScaledRegionAlpha(context, texture, x, y, size, size,
                    u, v, width, height, textureWidth, textureHeight, 1.0F);
        }
    }
}
