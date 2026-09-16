package fr.tropimon.battleui.buildprivacy;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** All identity/credential fixtures are synthetic and assembled only in memory. */
public final class PrivacyGuardSelfTest {
    private static final String PRIVATE = "Fixture" + " Person";
    private static int checks;

    public static void main(String[] args) throws Exception {
        textRule(PRIVATE, "private-runtime-term");
        textRule("C:" + "\\" + "Users" + "\\" + "FixtureAccount" + "\\code", "personal-path");
        textRule("/" + "home/" + "fixture-account" + "/code", "personal-path");
        String email = "fixture" + "@" + "example.test";
        textRule(email, "email");
        textRule("-----BEGIN " + "PRIVATE KEY-----", "private-key");
        textRule("gh" + "p_" + "A".repeat(32), "credential-token");
        textRule("sk" + "-proj-" + "A".repeat(40), "credential-token");
        textRule("AK" + "IA" + "A".repeat(16), "credential-token");
        textRule("ey" + "J" + "A".repeat(14) + "." + "B".repeat(18) + "." + "C".repeat(18), "jwt-token");
        textRule("pass" + "word = \"" + "synthetic-fixture" + "\"", "credential-assignment");
        textRule("Author: " + "Synthetic Identity", "author-credit");
        safeText("By FastedCorsi\nAuthor: By FastedCorsi\nFastedCorsi used Surf!");
        safeText("https://github.com/smogon/pokemon-showdown\nhttps://fabricmc.net/");
        safeText("private int secretCounter; String authorLabel; access_token = null;");

        PrivacyGuard masked = new PrivacyGuard(List.of(PRIVATE));
        masked.scanText("docs/" + PRIVATE + ".txt", PRIVATE, utf8(PRIVATE));
        check(!masked.findings().toString().contains(PRIVATE), "Private names are masked in locations");
        String privatePath = "C:" + "/" + "Users/" + "FixtureAccount" + "/code.txt";
        masked.scanText(privatePath, privatePath, utf8(privatePath));
        check(!masked.findings().toString().contains("FixtureAccount"), "Private paths are masked in locations");

        byte[] own = manifest(PrivacyGuard.AUTHOR);
        Map<String, byte[]> clean = Map.of("fabric.mod.json", own, "NOTICE.txt", utf8("Public upstream license"));
        check(archive(zip(clean, null, null)).findings().isEmpty(), "Valid final manifest and public license");
        check(has(archive(zip(Map.of("fabric.mod.json", manifest("Synthetic Identity")), null, null)),
                "developer-attribution"), "Wrong developer attribution rejected");
        check(has(archive(zip(Map.of("data.txt", utf8("content")), null, null)), "missing-ui-battle-manifest"), "Manifest required");
        check(has(archive(zip(Map.of("fabric.mod.json", own, "../escape", utf8("x")), null, null)), "unsafe-archive-path"), "Traversal rejected");
        check(has(archive(zip(clean, PRIVATE, null)), "private-runtime-term"), "Archive comment inspected");
        check(has(archive(zip(clean, null, PRIVATE)), "private-runtime-term"), "Entry comment inspected");
        byte[] nested = zip(Map.of("Fixture.class", constantClass(PRIVATE)), null, null);
        PrivacyGuard nestedGuard = archive(zip(Map.of("fabric.mod.json", own, "META-INF/jars/helper.jar", nested), null, null));
        check(has(nestedGuard, "private-runtime-term"), "Nested compiled constants inspected");
        check(nestedGuard.findings().stream().anyMatch(f -> f.location().contains("constant#")), "Class constant location identified");
        check(has(archive(zip(Map.of("fabric.mod.json", own, "compressed.bin", gzip(PRIVATE)), null, null)), "private-runtime-term"), "Gzip content inspected");
        String escaped = "{\"name\":\"" + PRIVATE.replace("F", "\\" + "u0046") + "\"}";
        check(has(archive(zip(Map.of("fabric.mod.json", own, "value.json", utf8(escaped)), null, null)), "private-runtime-term"), "Escaped JSON inspected");
        check(has(archive(zip(Map.of("fabric.mod.json", own, "value.txt", utf16(PRIVATE)), null, null)), "private-runtime-term"), "UTF-16 inspected");
        check(has(archive(zip(Map.of("fabric.mod.json", own, "bad.json", utf8("{")), null, null)), "invalid-json-review-required"), "Invalid JSON fails closed");

        for (String path : List.of(".env", ".env.production", ".git/config", "logs/run.log", "config/player.json",
                "captures/image.png", "backups/archive.dat", "settings.local.json", "credentials.json",
                "secrets.json", "certificate.p12", "identity.pfx", "identity.keystore", "codex-clipboard-fixture.png")) {
            check(PrivacyGuard.forbiddenPath(path), "Private distribution path recognized");
            check(PrivacyGuard.excludedSource(path), "Local original excluded from source scan");
            check(has(archive(zip(Map.of("fabric.mod.json", own, path, utf8("{}")), null, null)), "private-file-in-artifact"), "Private path rejected in final artifact");
        }
        for (String path : List.of("build/out.jar", ".gradle/cache.bin", ".fabric/cache.jar", "run/state.json",
                "mods/dependency.jar", "art/effects-v2/previous-icons/rain.png", "releases/old.jar"))
            check(PrivacyGuard.excludedSource(path), "Local build/backup remains outside publication");
        for (String path : List.of("src/main/resources/fabric.mod.json", "docs/privacy.md",
                "src/main/resources/assets/tropimon_ui_battle/textures/gui/effects/rain.png",
                "art/effects-v2/sources/rain.png", "tools/privacy/PrivacyGuard.java", ".gitignore"))
            check(!PrivacyGuard.excludedSource(path), "Publishable file remains checked");

        check(has(archive(zip(Map.of("fabric.mod.json", own, "image.png", png("tEXt", utf8("Author\0" + PRIVATE))), null, null)),
                "private-runtime-term"), "PNG text metadata inspected");
        ByteArrayOutputStream zText = new ByteArrayOutputStream();
        zText.write(utf8("Author")); zText.write(0); zText.write(0);
        try (var deflate = new DeflaterOutputStream(zText)) { deflate.write(utf8(PRIVATE)); }
        check(has(archive(zip(Map.of("fabric.mod.json", own, "image.png", png("zTXt", zText.toByteArray())), null, null)),
                "private-runtime-term"), "Compressed PNG metadata inspected");
        check(archive(zip(Map.of("fabric.mod.json", own, "image.png", png("tEXt", utf8("Author\0By FastedCorsi"))), null, null))
                .findings().isEmpty(), "Public image attribution preserved");

        byte[] credit = utf8(email);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(credit));
        var reviews = Set.of(new PrivacyGuard.Review("LICENSE-upstream", digest, "email"));
        PrivacyGuard reviewed = new PrivacyGuard(List.of(), reviews);
        reviewed.scanText("LICENSE-upstream", email, credit);
        check(reviewed.findings().isEmpty(), "Explicit exact third-party review honored");
        reviewed.scanText("LICENSE-upstream", email + " changed", utf8(email + " changed"));
        check(has(reviewed, "email"), "Content changes invalidate review");
        PrivacyGuard privateReview = new PrivacyGuard(List.of(email), reviews);
        privateReview.scanText("LICENSE-upstream", email, credit);
        check(has(privateReview, "private-runtime-term"), "Private term cannot be bypassed by public review");

        var artRoot = Path.of("art/effects-v2");
        var art = JsonParser.parseString(Files.readString(artRoot.resolve("generation-manifest.json"))).getAsJsonObject();
        Path source = Path.of(art.get("sourceDirectory").getAsString());
        check(!source.isAbsolute(), "Art source is portable");
        check(art.getAsJsonObject("icons").size() == 30, "All original effect mappings preserved");
        for (var icon : art.getAsJsonObject("icons").entrySet())
            check(Files.isRegularFile(artRoot.resolve(source).resolve(icon.getValue().getAsString())), "Original source remains available");
        System.out.println("Privacy self-tests OK: " + checks + " checks, synthetic fixtures only.");
    }

    private static void textRule(String value, String rule) {
        PrivacyGuard guard = new PrivacyGuard(List.of(PRIVATE));
        guard.scanText("fixture.txt", value, utf8(value));
        check(has(guard, rule), "Synthetic rule fixture: " + rule);
        check(!guard.findings().toString().contains(value), "Matched value not printed");
    }
    private static void safeText(String value) {
        PrivacyGuard guard = new PrivacyGuard(List.of(PRIVATE));
        guard.scanText("fixture.txt", value, utf8(value));
        check(guard.findings().isEmpty(), "Public/technical text preserved");
    }
    private static boolean has(PrivacyGuard guard, String rule) {
        return guard.findings().stream().anyMatch(f -> f.rule().equals(rule));
    }
    private static PrivacyGuard archive(byte[] bytes) throws IOException {
        var guard = new PrivacyGuard(List.of(PRIVATE));
        guard.scanArchive("fixture.jar", bytes, 0, true);
        return guard;
    }
    private static byte[] manifest(String author) {
        return utf8("{\"id\":\"tropimon_ui_battle\",\"authors\":[\"" + author + "\"]}");
    }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] utf16(String value) throws IOException {
        var output = new ByteArrayOutputStream();
        output.write(0xff); output.write(0xfe);
        output.write(value.getBytes(StandardCharsets.UTF_16LE));
        return output.toByteArray();
    }
    private static byte[] zip(Map<String, byte[]> entries, String comment, String entryComment) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            if (comment != null) zip.setComment(comment);
            for (var value : entries.entrySet()) {
                var entry = new ZipEntry(value.getKey());
                if (entryComment != null) entry.setComment(entryComment);
                zip.putNextEntry(entry);
                zip.write(value.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    private static byte[] constantClass(String value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            data.writeInt(0xcafebabe); data.writeShort(0); data.writeShort(65);
            data.writeShort(2); data.writeByte(1); data.writeUTF(value);
        }
        return output.toByteArray();
    }
    private static byte[] gzip(String value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(utf8(value)); }
        return output.toByteArray();
    }
    private static byte[] png(String type, byte[] value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            data.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            chunk(data, type, value);
            chunk(data, "IEND", new byte[0]);
        }
        return output.toByteArray();
    }
    private static void chunk(DataOutputStream data, String type, byte[] value) throws IOException {
        data.writeInt(value.length); data.write(utf8(type)); data.write(value);
        var crc = new CRC32(); crc.update(utf8(type)); crc.update(value);
        data.writeInt((int) crc.getValue());
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
