package fr.tropimon.battleui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageAssetsTest {
    private static final String ROOT = "assets/tropimon_ui_battle/lang/";

    @Test
    void languageFilesAreValidObjectsWithMatchingKeys() throws IOException {
        JsonObject english = readLanguage("en_us.json");
        JsonObject french = readLanguage("fr_fr.json");

        assertEquals(english.keySet(), french.keySet(),
                "Les traductions anglaises et françaises doivent exposer les mêmes clés");
        assertStringValues(english.keySet(), english);
        assertStringValues(french.keySet(), french);
    }

    private static JsonObject readLanguage(String fileName) throws IOException {
        try (InputStream stream = LanguageAssetsTest.class.getClassLoader().getResourceAsStream(ROOT + fileName)) {
            assertNotNull(stream, "Fichier de langue introuvable : " + fileName);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                assertTrue(parsed.isJsonObject(), "Le fichier de langue doit contenir un objet JSON : " + fileName);
                return parsed.getAsJsonObject();
            }
        }
    }

    private static void assertStringValues(Set<String> keys, JsonObject language) {
        for (String key : keys) {
            JsonElement value = language.get(key);
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                    "La traduction doit être une chaîne : " + key);
        }
    }
}
