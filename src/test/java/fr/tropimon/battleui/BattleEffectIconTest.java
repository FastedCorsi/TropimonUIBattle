package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleEffectIconTest {
    @Test
    void usesIndependentOriginalTextures() {
        var rain = BattleEffectIcon.forEffect("raindance");
        assertEquals("tropimon_ui_battle", rain.texture().getNamespace());
        assertEquals("textures/gui/effects/rain.png", rain.texture().getPath());
        assertEquals(0, rain.u());
        assertEquals(0, rain.v());
    }

    @Test
    void stripsBattleSideBeforeSelectingHazardIcon() {
        var web = BattleEffectIcon.forEffect("opponent.stickyweb");
        assertEquals("textures/gui/effects/sticky_web.png", web.texture().getPath());
        assertEquals(0, web.u());
        assertEquals(0, web.v());
    }

    @Test
    void screensAndTrickRoomHaveDedicatedIcons() {
        assertEquals("textures/gui/effects/light_screen.png",
                BattleEffectIcon.forEffect("ally.lightscreen").texture().getPath());
        assertEquals("textures/gui/effects/reflect.png",
                BattleEffectIcon.forEffect("opponent.reflect").texture().getPath());
        assertEquals("textures/gui/effects/aurora_veil.png",
                BattleEffectIcon.forEffect("ally.auroraveil").texture().getPath());
        assertEquals("textures/gui/effects/trick_room.png",
                BattleEffectIcon.forEffect("trickroom").texture().getPath());
    }

    @Test
    void everyDisplayedEffectUsesItsOwnPixelArtTexture() {
        var expected = Map.ofEntries(
                Map.entry("raindance", "rain.png"),
                Map.entry("primordialsea", "primordial_sea.png"),
                Map.entry("watersport", "water_sport.png"),
                Map.entry("sunnyday", "sun.png"),
                Map.entry("desolateland", "desolate_land.png"),
                Map.entry("sandstorm", "sandstorm.png"),
                Map.entry("mudsport", "mud_sport.png"),
                Map.entry("hail", "hail.png"),
                Map.entry("snow", "snow.png"),
                Map.entry("electricterrain", "electric_terrain.png"),
                Map.entry("grassyterrain", "grassy_terrain.png"),
                Map.entry("mistyterrain", "misty_terrain.png"),
                Map.entry("psychicterrain", "psychic_terrain.png"),
                Map.entry("lightscreen", "light_screen.png"),
                Map.entry("reflect", "reflect.png"),
                Map.entry("safeguard", "safeguard.png"),
                Map.entry("auroraveil", "aurora_veil.png"),
                Map.entry("mist", "mist.png"),
                Map.entry("luckychant", "lucky_chant.png"),
                Map.entry("trickroom", "trick_room.png"),
                Map.entry("magicroom", "magic_room.png"),
                Map.entry("wonderroom", "wonder_room.png"),
                Map.entry("tailwind", "tailwind.png"),
                Map.entry("deltastream", "delta_stream.png"),
                Map.entry("gravity", "gravity.png"),
                Map.entry("stealthrock", "stealth_rock.png"),
                Map.entry("spikes", "spikes.png"),
                Map.entry("toxicspikes", "toxic_spikes.png"),
                Map.entry("stickyweb", "sticky_web.png")
        );

        expected.forEach((effect, fileName) -> {
            var icon = BattleEffectIcon.forEffect(effect);
            assertEquals("textures/gui/effects/" + fileName, icon.texture().getPath());
            assertEquals(128, icon.width());
            assertEquals(128, icon.height());
            assertEquals(128, icon.textureWidth());
            assertEquals(128, icon.textureHeight());
        });
    }

    @Test
    void unknownEffectsUseANeutralFallback() {
        assertEquals("textures/gui/effects/generic_effect.png",
                BattleEffectIcon.forEffect("future_custom_effect").texture().getPath());
        assertSame(BattleEffectIcon.forEffect("future_custom_effect"),
                BattleEffectIcon.forEffect("another_unknown_effect"));
    }

    @Test
    void immutableIconsAreReusedAcrossFrames() {
        assertSame(BattleEffectIcon.forEffect("raindance"), BattleEffectIcon.forEffect("raindance"));
        assertSame(BattleEffectIcon.forEffect("ally.reflect"), BattleEffectIcon.forEffect("opponent.reflect"));
    }
}
