package fr.tropimon.battleui;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class IndependentRuntimeTest {
    @Test void cobblemonRangeHasMinimumWithoutArtificialMinorMaximum() throws Exception {
        var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        String range=manifest.getAsJsonObject("depends").get("cobblemon").getAsString();
        VersionPredicate predicate=VersionPredicate.parse(range);

        for(String version:List.of("1.7.2","1.8.1+1.21.1","1.9.0","1.99.0","2.0.0"))
            assertTrue(predicate.test(Version.parse(version)),version);
        for(String version:List.of("1.7.1","1.6.9"))
            assertFalse(predicate.test(Version.parse(version)),version);
    }

    @Test void loadsWithOnlyOfficialDependenciesOrWithTheOtherMods() throws Exception {
        var loader=FabricLoader.getInstance();
        assertTrue(loader.isModLoaded("cobblemon"));
        if (Boolean.getBoolean("battleui.test.coexistence")) {
            assertTrue(loader.isModLoaded("tropimodclient"));
            assertTrue(loader.isModLoaded("tropimon_damage_calc"));
        } else {
            for (String id:List.of("tropimodclient","tropimon_damage_calc","tropimon_chat_filter","tropimon_catch_preview")) {
                assertFalse(loader.isModLoaded(id),id);
            }
        }
        var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        for(String id:manifest.getAsJsonObject("depends").keySet()) {
            assertTrue(List.of("fabricloader","minecraft","fabric-api","cobblemon","java","fabric-language-kotlin").contains(id),id);
        }
        assertEquals("tropimon_ui_battle",BattleUiSkin.TROPIMON_NAVIGATOR_FRAME.getNamespace());
    }
    @Test void noProductionLinksToOtherTropimonPackagesOrResourceNamespaces() throws Exception {
        try(var sources=Files.walk(Path.of("src/main/java"))) {
            for(Path source:sources.filter(p->p.toString().endsWith(".java")).toList()) {
                String text=Files.readString(source);
                assertFalse(java.util.regex.Pattern.compile("fr\\.tropimon\\.(?!battleui\\b)[a-zA-Z0-9_.]+").matcher(text).find(),source.toString());
                assertFalse(text.contains("fr.tropimon.damagecalc"),source.toString());
                assertFalse(text.contains("fr.tropimon.chatfilter"),source.toString());
                assertFalse(text.contains("fr.tropimon.catchpreview"),source.toString());
                assertFalse(text.contains("config/tropimon_damage_calc"),source.toString());
                assertFalse(text.contains('"'+"tropimodclient"+'"'),source.toString());
            }
        }
    }
    @Test void skinBytesAreIdenticalAndTransparent() throws Exception {
        var root=Path.of("src/main/resources/assets/tropimon_ui_battle/textures/gui/skin");
        for(var entry:java.util.Map.of(
                "history_frame.png","314929311012f3688597ff5fe160ade70acb3080c74431f70a64e21be0ec875b").entrySet()) {
            byte[] bytes=Files.readAllBytes(root.resolve(entry.getKey()));
            assertEquals(entry.getValue(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertTrue(image.getColorModel().hasAlpha());
        }
    }
}
