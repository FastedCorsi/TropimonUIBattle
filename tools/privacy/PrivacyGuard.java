package fr.tropimon.battleui.buildprivacy;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.zip.*;

/** Build-only verifier. Reports contain locations and rule IDs, never matched values. */
public final class PrivacyGuard {
    static final String AUTHOR = "By FastedCorsi";
    static final long ENTRY_LIMIT = 32L * 1024 * 1024;
    static final long EXPANDED_LIMIT = 256L * 1024 * 1024;
    static final Set<String> PRIVATE_DIRS = Set.of(".git", ".gradle", ".fabric", ".idea", ".vscode",
            "logs", "config", "configs", "private", "backups", "screenshots", "captures", "previous-icons");
    static final Set<String> LOCAL_ROOTS = Set.of("build", "run", "mods", "releases", "local");
    private static final Set<String> GENERIC_USERS = Set.of("root", "runner", "user", "admin", "administrator",
            "developer", "system", "fastedcorsi", "by fastedcorsi");
    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();
    static {
        RULES.put("personal-path", Pattern.compile("(?i)(?:[a-z]:[\\\\/]+(?:users|documents and settings)[\\\\/]+[^\\\\/\\s\"'<>]+|/(?:home|Users)/[^/\\s\"'<>]+)"));
        RULES.put("email", Pattern.compile("(?i)(?<![\\w.+-])[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"));
        RULES.put("private-key", Pattern.compile("-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"));
        RULES.put("credential-token", Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}|sk-(?:proj-)?[A-Za-z0-9_-]{24,}|(?:AKIA|ASIA)[A-Z0-9]{16}|xox[baprs]-[A-Za-z0-9-]{20,})\\b"));
        RULES.put("jwt-token", Pattern.compile("\\beyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b"));
        RULES.put("credential-assignment", Pattern.compile("(?i)[\"']?(?:api[_-]?key|secret[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|password)[\"']?\\s*[:=]\\s*[\"']([^\"'\\r\\n]{8,})[\"']"));
        RULES.put("author-credit", Pattern.compile("(?im)^\\s*(?:(?://|\\*|#)\\s*)?(?:@author|author|developer|développeur)\\s*[:=]\\s*([^\\r\\n]+)$"));
    }

    record Finding(String location, String rule) { }
    record Review(String path, String sha256, String rule) { }
    private final List<Pattern> privateTerms;
    private final Set<Review> reviews;
    private final LinkedHashSet<Finding> findings = new LinkedHashSet<>();
    private long expanded;
    private int files;

    PrivacyGuard(Collection<String> terms) { this(terms, Set.of()); }
    PrivacyGuard(Collection<String> terms, Set<Review> reviews) {
        privateTerms = terms.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> s.length() >= 3 && !GENERIC_USERS.contains(s.toLowerCase(Locale.ROOT)))
                .distinct().map(s -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(s) + "(?![\\p{L}\\p{N}_])"))
                .toList();
        this.reviews = Set.copyOf(reviews);
    }

    public static void main(String[] args) {
        try {
            Path root = null;
            boolean sources = false;
            List<Path> artifacts = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--root" -> root = Path.of(args[++i]).toRealPath();
                    case "--sources" -> sources = true;
                    case "--artifact" -> artifacts.add(Path.of(args[++i]));
                    default -> throw new IllegalArgumentException();
                }
            }
            if (root == null || (!sources && artifacts.isEmpty())) throw new IllegalArgumentException();
            var guard = new PrivacyGuard(runtimeTerms(root), loadReviews(root));
            if (sources) guard.scanSources(root);
            for (Path artifact : artifacts) {
                guard.expanded = 0;
                guard.scanArchive(artifact.getFileName().toString(), readBounded(Files.newInputStream(artifact)), 0, true);
            }
            if (!guard.findings.isEmpty()) {
                for (Finding finding : guard.findings) System.err.println(finding.location + " : " + finding.rule + " [value masked]");
                System.err.println("Privacy verification FAILED: " + guard.findings.size() + " finding(s). No matched values are printed.");
                System.exit(1);
            }
            System.out.println("Privacy verification OK: " + guard.files + " files/entries; "
                    + guard.privateTerms.size() + " private runtime search terms; attribution " + AUTHOR + ".");
        } catch (Exception exception) {
            // Exception messages from file/JSON libraries can contain paths or secret text.
            System.err.println("Privacy verification could not complete (input, archive, or configuration error; details masked).");
            System.exit(2);
        }
    }

    List<Finding> findings() { return List.copyOf(findings); }

    static boolean forbiddenPath(String name) {
        String normalized = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String part : normalized.split("/")) {
            if (PRIVATE_DIRS.contains(part) || part.startsWith(".env") || part.endsWith(".log")
                    || part.endsWith(".log.gz") || part.endsWith(".bak") || part.endsWith(".tmp")
                    || part.contains(".local.") || part.startsWith("codex-clipboard-")
                    || part.startsWith("hs_err_pid") || part.startsWith("replay_pid")
                    || part.equals("credentials.json") || part.equals("secrets.json")
                    || part.endsWith(".p12") || part.endsWith(".pfx") || part.endsWith(".keystore")) return true;
        }
        return false;
    }

    static boolean excludedSource(String relative) {
        String normalized = relative.replace('\\', '/');
        String first = normalized.split("/")[0].toLowerCase(Locale.ROOT);
        return LOCAL_ROOTS.contains(first) || forbiddenPath(normalized);
    }

    void scanSources(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && excludedSource(root.relativize(dir).toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (excludedSource(relative)) return FileVisitResult.CONTINUE;
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)) add(relative, "symlink-not-publishable");
                else scanEntry(relative, readBounded(Files.newInputStream(file)), 0);
                return FileVisitResult.CONTINUE;
            }
        });
        Path manifest = root.resolve("src/main/resources/fabric.mod.json");
        validateManifest("src/main/resources/fabric.mod.json", Files.readAllBytes(manifest), true);
    }

    void scanArchive(String location, byte[] bytes, int depth, boolean requireOwnManifest) throws IOException {
        if (depth > 5) { add(location, "nested-archive-limit"); return; }
        scanZipMetadata(location, bytes);
        boolean ownManifest = false;
        int entries = 0;
        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new IOException();
                String name = entry.getName().replace('\\', '/');
                String child = location + "!/" + name;
                scanText(child + ":name", name, bytes);
                if (entry.getExtra() != null)
                    scanText(child + ":zip-extra", new String(entry.getExtra(), StandardCharsets.UTF_8), bytes);
                if (!names.add(name)) add(child, "duplicate-archive-entry");
                if (name.startsWith("/") || Arrays.asList(name.split("/")).contains("..")
                        || name.matches("(?i)^[a-z]:.*")) add(child, "unsafe-archive-path");
                if (forbiddenPath(name)) add(child, "private-file-in-artifact");
                if (!entry.isDirectory()) {
                    byte[] data = readEntry(zip);
                    expanded += data.length;
                    if (expanded > EXPANDED_LIMIT) throw new IOException();
                    if (name.equals("fabric.mod.json")) ownManifest = validateManifest(child, data, requireOwnManifest);
                    scanEntry(child, data, depth);
                }
                zip.closeEntry();
            }
        }
        if (entries == 0) add(location, "unreadable-or-empty-archive");
        if (requireOwnManifest && !ownManifest) add(location, "missing-ui-battle-manifest");
    }

    /** ZipInputStream does not expose central-directory comments or the archive comment. */
    private void scanZipMetadata(String location, byte[] bytes) throws IOException {
        int footer = -1;
        for (int at = bytes.length - 22; at >= Math.max(0, bytes.length - 65557); at--) {
            if (little(bytes, at, 4) == 0x06054b50L && at + 22 + little(bytes, at + 20, 2) == bytes.length) {
                footer = at;
                break;
            }
        }
        if (footer < 0) throw new IOException();
        if (little(bytes, footer + 4, 4) != 0) throw new IOException(); // No multi-disk archives.
        long centralOffset = little(bytes, footer + 16, 4);
        long centralSize = little(bytes, footer + 12, 4);
        if (centralOffset + centralSize > footer || centralOffset > Integer.MAX_VALUE) throw new IOException();
        scanText(location + ":zip-comment", new String(bytes, footer + 22, bytes.length - footer - 22, StandardCharsets.UTF_8), bytes);
        int cursor = (int) centralOffset;
        int count = (int) little(bytes, footer + 10, 2);
        if (count > 10000) throw new IOException();
        for (int index = 0; index < count; index++) {
            if (cursor + 46 > footer || little(bytes, cursor, 4) != 0x02014b50L) throw new IOException();
            int name = (int) little(bytes, cursor + 28, 2);
            int extra = (int) little(bytes, cursor + 30, 2);
            int comment = (int) little(bytes, cursor + 32, 2);
            int end = cursor + 46 + name + extra + comment;
            if (end > footer) throw new IOException();
            int fieldOffset = cursor + 46;
            for (int fieldLength : new int[]{name, extra, comment}) {
                scanText(location + ":zip-metadata#" + index,
                        new String(bytes, fieldOffset, fieldLength, StandardCharsets.UTF_8), bytes);
                fieldOffset += fieldLength;
            }
            cursor = end;
        }
        if (cursor != centralOffset + centralSize) throw new IOException();
    }

    private static long little(byte[] bytes, int offset, int size) throws IOException {
        if (offset < 0 || offset + size > bytes.length) throw new IOException();
        long result = 0;
        for (int i = 0; i < size; i++) result |= (long) (bytes[offset + i] & 255) << (8 * i);
        return result;
    }

    private void scanEntry(String location, byte[] bytes, int depth) throws IOException {
        files++;
        scanText(location + ":name", location, bytes);
        if (starts(bytes, 0x50, 0x4b, 0x03, 0x04) || location.endsWith(".jar") || location.endsWith(".zip")) {
            scanArchive(location, bytes, depth + 1, false);
        } else if (starts(bytes, 0xca, 0xfe, 0xba, 0xbe)) {
            scanClass(location, bytes);
        } else if (starts(bytes, 0x1f, 0x8b)) {
            if (depth > 5) { add(location, "nested-archive-limit"); return; }
            byte[] unpacked = readBounded(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            expanded += unpacked.length;
            if (expanded > EXPANDED_LIMIT) throw new IOException();
            scanEntry(location + "!/content", unpacked, depth + 1);
        } else if (starts(bytes, 0x89, 0x50, 0x4e, 0x47)) {
            scanPng(location, bytes);
        } else {
            String text;
            if (starts(bytes, 0xff, 0xfe)) text = new String(bytes, StandardCharsets.UTF_16LE);
            else if (starts(bytes, 0xfe, 0xff)) text = new String(bytes, StandardCharsets.UTF_16BE);
            else text = new String(bytes, StandardCharsets.UTF_8);
            scanText(location, text, bytes);
            if (location.endsWith(".json") || location.endsWith(".mcmeta")) {
                try { scanJson(location, JsonParser.parseString(text), bytes); }
                catch (RuntimeException ignored) { add(location, "invalid-json-review-required"); }
            }
            if (location.endsWith("fabric.mod.json")) validateManifest(location, bytes, false);
        }
    }

    private boolean validateManifest(String location, byte[] bytes, boolean requireOwn) {
        try {
            var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean own = json.has("id") && json.get("id").getAsString().equals("tropimon_ui_battle");
            if (own) {
                var authors = json.getAsJsonArray("authors");
                if (authors == null || authors.size() != 1 || !authors.get(0).isJsonPrimitive()
                        || !authors.get(0).getAsString().equals(AUTHOR)) add(location, "developer-attribution");
            } else if (requireOwn) add(location, "wrong-mod-manifest");
            return own;
        } catch (RuntimeException ignored) { add(location, "invalid-mod-manifest"); return false; }
    }

    private void scanJson(String location, com.google.gson.JsonElement json, byte[] file) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) scanText(location + ":json-string", json.getAsString(), file);
        else if (json.isJsonArray()) for (var value : json.getAsJsonArray()) scanJson(location, value, file);
        else if (json.isJsonObject()) for (var entry : json.getAsJsonObject().entrySet()) {
            scanText(location + ":json-key", entry.getKey(), file);
            scanJson(location, entry.getValue(), file);
        }
    }

    private void scanClass(String location, byte[] bytes) throws IOException {
        try (var data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            data.readInt(); data.readUnsignedShort(); data.readUnsignedShort();
            int count = data.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                switch (data.readUnsignedByte()) {
                    case 1 -> scanText(location + ":constant#" + index, data.readUTF(), bytes);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> data.skipNBytes(4);
                    case 5, 6 -> { data.skipNBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> data.skipNBytes(2);
                    case 15 -> data.skipNBytes(3);
                    default -> throw new IOException();
                }
            }
        }
    }

    private void scanPng(String location, byte[] bytes) throws IOException {
        try (var data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            data.skipNBytes(8);
            int chunkIndex = 0;
            while (data.available() > 0) {
                int size = data.readInt();
                if (size < 0 || size > ENTRY_LIMIT) throw new IOException();
                String type = new String(data.readNBytes(4), StandardCharsets.US_ASCII);
                byte[] chunk = data.readNBytes(size);
                if (chunk.length != size) throw new IOException();
                data.skipNBytes(4);
                String child = location + ":png-metadata#" + (++chunkIndex);
                if (type.equals("tEXt")) scanText(child, new String(chunk, StandardCharsets.ISO_8859_1), bytes);
                else if (type.equals("zTXt")) {
                    int end = zero(chunk, 0);
                    scanText(child, new String(chunk, 0, end, StandardCharsets.ISO_8859_1), bytes);
                    scanText(child, new String(inflate(chunk, end + 2), StandardCharsets.ISO_8859_1), bytes);
                } else if (type.equals("iTXt")) {
                    int end = zero(chunk, 0);
                    if (end + 2 >= chunk.length) throw new IOException();
                    boolean compressed = chunk[end + 1] == 1;
                    int languageEnd = zero(chunk, end + 3);
                    int translatedEnd = zero(chunk, languageEnd + 1);
                    scanText(child, new String(chunk, 0, translatedEnd, StandardCharsets.UTF_8), bytes);
                    byte[] content = compressed ? inflate(chunk, translatedEnd + 1)
                            : Arrays.copyOfRange(chunk, translatedEnd + 1, chunk.length);
                    scanText(child, new String(content, StandardCharsets.UTF_8), bytes);
                } else if (type.equals("eXIf")) {
                    scanText(child, new String(chunk, StandardCharsets.UTF_8), bytes);
                    scanText(child, new String(chunk, StandardCharsets.UTF_16LE), bytes);
                }
                if (type.equals("IEND")) break;
            }
        }
    }

    void scanText(String location, String text, byte[] file) {
        for (Pattern term : privateTerms) if (term.matcher(text).find()) add(location, "private-runtime-term");
        for (var rule : RULES.entrySet()) {
            Matcher matcher = rule.getValue().matcher(text);
            while (matcher.find()) {
                if (rule.getKey().equals("author-credit") && matcher.group(1).strip().equals(AUTHOR)) continue;
                if (reviewed(location, file, rule.getKey())) continue;
                int line = 1;
                for (int i = 0; i < matcher.start(); i++) if (text.charAt(i) == '\n') line++;
                add(location + ":" + line, rule.getKey());
            }
        }
    }

    private boolean reviewed(String location, byte[] bytes, String rule) {
        if (!(rule.equals("email") || rule.equals("author-credit"))) return false;
        String base = location.replaceFirst(":(?:json-string|json-key|constant#.*|png-metadata#.*|name)$", "");
        try { return reviews.contains(new Review(base, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), rule)); }
        catch (Exception ignored) { return false; }
    }

    private void add(String location, String rule) { findings.add(new Finding(maskLocation(location), rule)); }
    private String maskLocation(String value) {
        for (Pattern pattern : privateTerms) value = pattern.matcher(value).replaceAll("[masked]");
        for (String rule : List.of("personal-path", "email", "credential-token", "jwt-token"))
            value = RULES.get(rule).matcher(value).replaceAll("[masked]");
        return value.replace('\r', '_').replace('\n', '_');
    }

    private static Set<Review> loadReviews(Path root) throws IOException {
        Path file = root.resolve("tools/privacy/reviewed-public.tsv");
        if (!Files.isRegularFile(file)) return Set.of();
        Set<Review> result = new HashSet<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", 4);
            if (fields.length != 4 || !fields[1].matches("[0-9a-f]{64}") || fields[3].isBlank()
                    || !Set.of("email", "author-credit").contains(fields[2])) throw new IOException();
            result.add(new Review(fields[0], fields[1], fields[2]));
        }
        return result;
    }

    private static Set<String> runtimeTerms(Path root) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (String value : new String[]{System.getProperty("user.name"), System.getenv("USERNAME"),
                System.getenv("GIT_AUTHOR_NAME"), System.getenv("GIT_COMMITTER_NAME"),
                System.getenv("GIT_AUTHOR_EMAIL"), System.getenv("GIT_COMMITTER_EMAIL")}) {
            if (value != null) result.add(value);
        }
        for (String role : List.of("GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT")) {
            Matcher identity = Pattern.compile("^(.*?)\\s+<([^>]+)>").matcher(git(root, "var", role));
            if (identity.find()) {
                String name = identity.group(1).strip();
                if (!GENERIC_USERS.contains(name.toLowerCase(Locale.ROOT))) {
                    result.add(name);
                    for (String part : name.split("\\s+")) if (part.length() > 2) result.add(part);
                }
                if (!identity.group(2).endsWith("@" + "users.noreply.github.com")) result.add(identity.group(2));
            }
        }
        String termsFile = System.getenv("TROPIMON_PRIVACY_TERMS_FILE");
        if (termsFile != null && !termsFile.isBlank()) {
            Path external = Path.of(termsFile).toRealPath();
            String gitRoot = git(root, "rev-parse", "--show-toplevel").strip();
            if (external.startsWith(root) || (!gitRoot.isBlank() && external.startsWith(Path.of(gitRoot).toRealPath()))) throw new IOException();
            for (String line : Files.readAllLines(external)) if (!line.isBlank() && !line.startsWith("#")) result.add(line.strip());
        }
        return result;
    }

    private static String git(Path root, String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); return ""; }
            return process.exitValue() == 0 ? new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip() : "";
        } catch (Exception ignored) { return ""; }
    }

    static byte[] readBounded(InputStream input) throws IOException {
        try (input) { return readEntry(input); }
    }
    private static byte[] readEntry(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes((int) ENTRY_LIMIT + 1);
        if (bytes.length > ENTRY_LIMIT) throw new IOException();
        return bytes;
    }
    private static byte[] inflate(byte[] data, int offset) throws IOException {
        if (offset > data.length) throw new IOException();
        return readBounded(new InflaterInputStream(new ByteArrayInputStream(data, offset, data.length - offset)));
    }
    private static int zero(byte[] bytes, int from) throws IOException {
        for (int i = from; i < bytes.length; i++) if (bytes[i] == 0) return i;
        throw new IOException();
    }
    private static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if ((bytes[i] & 255) != prefix[i]) return false;
        return true;
    }
}
