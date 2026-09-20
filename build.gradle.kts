import java.util.zip.ZipFile
import javax.imageio.ImageIO
import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://api.modrinth.com/maven")
}

val defaultTropimonRoot = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
    ?.let { file(it).resolve(".tropimon") }
    ?: file(System.getProperty("user.home")).resolve(".tropimon")
val managedInstances = defaultTropimonRoot.resolve("profiles").listFiles()
    ?.filter { it.isDirectory && it.resolve("instance/mods").isDirectory }.orEmpty()
val localMods = providers.gradleProperty("tropimon_mods_dir")
    .orElse(providers.environmentVariable("TROPIMON_MODS_DIR"))
    .orNull?.let { file(it) }
    ?: when (managedInstances.size) {
        0 -> defaultTropimonRoot.resolve("mods")
        1 -> managedInstances.single().resolve("instance/mods")
        else -> throw GradleException("Plusieurs profils : précisez -Ptropimon_mods_dir=<mods du profil actif>.")
    }
val configuredCobblemonJar = providers.gradleProperty("cobblemon_jar_path")
    .orElse(providers.environmentVariable("COBBLEMON_JAR"))
    .orNull?.let { file(it) }

fun supportedCobblemonJar(candidate: File): Boolean {
    if (!candidate.isFile) return false
    return runCatching {
        ZipFile(candidate).use { zip ->
            val manifest = zip.getEntry("fabric.mod.json") ?: return@use false
            val parsed = zip.getInputStream(manifest).use(JsonSlurper()::parse)
            val metadata = parsed as? Map<*, *> ?: return@use false
            if (metadata["id"] != "cobblemon") return@use false
            val version = metadata["version"]?.toString().orEmpty()
            val components = version.substringBefore('+').substringBefore('-').split('.')
            val major = components.getOrNull(0)?.toIntOrNull() ?: return@use false
            val minor = components.getOrNull(1)?.toIntOrNull() ?: return@use false
            val patch = components.getOrNull(2)?.toIntOrNull() ?: 0
            val supportedVersion = major == 1 && (minor > 7 || minor == 7 && patch >= 2)
            val minecraft = (metadata["depends"] as? Map<*, *>)?.get("minecraft")?.toString().orEmpty()
            supportedVersion && minecraft.contains(property("minecraft_version").toString())
        }
    }.getOrDefault(false)
}

val officialDependenciesOnly = providers.gradleProperty("officialDependenciesOnly").isPresent
val cobblemonJar = if (officialDependenciesOnly) {
    null
} else if (configuredCobblemonJar != null) {
    if (!supportedCobblemonJar(configuredCobblemonJar)) {
        throw GradleException("Le JAR Cobblemon configuré doit être une version 1.x >= 1.7.2 pour Minecraft " +
                property("minecraft_version") + ".")
    }
    configuredCobblemonJar
} else {
    val candidates = localMods.listFiles { candidate ->
        candidate.isFile && candidate.name.endsWith(".jar")
    }?.filter(::supportedCobblemonJar).orEmpty()
    when (candidates.size) {
        1 -> candidates.single()
        0 -> throw GradleException("Aucun JAR Cobblemon 1.x compatible trouvé. " +
                "Utilisez -Pcobblemon_jar_path=<jar> ou la variable COBBLEMON_JAR.")
        else -> throw GradleException("Plusieurs JAR Cobblemon compatibles sont présents. " +
                "Sélectionnez-en un avec -Pcobblemon_jar_path=<jar>.")
    }
}
val privacy = sourceSets.create("privacy") {
    java.setSrcDirs(listOf("tools/privacy"))
}

dependencies {
    // Build-only verifier, never packaged or added to the mod runtime.
    add(privacy.implementationConfigurationName, "com.google.code.gson:gson:2.11.0")
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    if (cobblemonJar != null) {
        modImplementation(files(cobblemonJar))
    } else {
        modImplementation("maven.modrinth:MdwFAVRL:${property("cobblemon_modrinth_version")}")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("net.fabricmc:fabric-loader-junit:${property("fabric_loader_version")}")
    // Explicit opt-in verification only; none of these mods is a production dependency.
    if (providers.gradleProperty("coexistence").isPresent) {
        modRuntimeOnly(fileTree(localMods) {
            include("Tropi*.jar", "XaerosWorldMap_*.jar", "geckolib-fabric-*.jar",
                "mega_showdown-*.jar", "architectury-*.jar")
            exclude("TropimonUIBattle-*.jar", "Cobblemon-fabric-*.jar",
                "fabric-api-*.jar", "fabric-language-kotlin-*.jar")
        })
        // Loom's development classpath does not expose this client's nested core automatically.
        // Test-only: mirror the installed client without adding any UI Battle dependency.
        fileTree(localMods) { include("TropimodClient-*.jar") }.forEach { clientJar ->
            modRuntimeOnly(files(zipTree(clientJar).matching {
                include("META-INF/jars/TropimonCore-*.jar")
            }))
        }
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("battleui.test.coexistence", providers.gradleProperty("coexistence").isPresent)
    val reportSuite = if (providers.gradleProperty("coexistence").isPresent) "coexistence" else "isolated"
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$reportSuite"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$reportSuite"))
    // Registry-backed ItemStack tests need Fabric's transformations and Cobblemon's Java 21 runtime.
    val configuredJava = providers.gradleProperty("tropimon_java_path")
        .orElse(providers.environmentVariable("TROPIMON_JAVA"))
        .orNull?.let { file(it) }
    val bundledJava = defaultTropimonRoot.resolve("runtime/x64/jdk-21.0.6+7/bin/java.exe")
    if (configuredJava?.isFile == true) executable = configuredJava.absolutePath
    else if (bundledJava.isFile) executable = bundledJava.absolutePath
}

val privacySelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Teste le contrôle de confidentialité avec des données fictives."
    dependsOn(privacy.classesTaskName)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.battleui.buildprivacy.PrivacyGuardSelfTest")
    workingDir(projectDir)
}

val verifyPrivacySources by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Contrôle les fichiers publiables sans afficher les valeurs privées."
    dependsOn(privacySelfTest)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.battleui.buildprivacy.PrivacyGuard")
    args("--root", projectDir.absolutePath, "--sources")
}

val privateDistributionPaths = listOf(
    "**/.git/**", "**/.gradle/**", "**/.fabric/**", "**/.idea/**", "**/.vscode/**",
    "**/logs/**", "**/config/**", "**/configs/**", "**/private/**", "**/backups/**",
    "**/screenshots/**", "**/captures/**", "**/previous-icons/**", "**/.env*",
    "**/*.log", "**/*.log.gz", "**/*.bak", "**/*.tmp", "**/*.local.*",
    "**/codex-clipboard-*", "**/hs_err_pid*", "**/replay_pid*", "**/credentials.json",
    "**/secrets.json", "**/*.p12", "**/*.pfx", "**/*.keystore"
)
tasks.processResources {
    dependsOn(verifyPrivacySources)
    exclude(privateDistributionPaths)
}
tasks.withType<Jar>().configureEach {
    dependsOn(verifyPrivacySources)
    exclude(privateDistributionPaths)
}

val verifyPrivacyArtifacts by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Contrôle les JAR finaux, leurs constantes, ressources et archives imbriquées."
    dependsOn(tasks.remapJar, tasks.remapSourcesJar)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.battleui.buildprivacy.PrivacyGuard")
    doFirst {
        setArgs(listOf("--root", projectDir.absolutePath,
            "--artifact", tasks.remapJar.get().archiveFile.get().asFile.absolutePath,
            "--artifact", tasks.remapSourcesJar.get().archiveFile.get().asFile.absolutePath))
    }
}
tasks.check { dependsOn(verifyPrivacyArtifacts) }

val verifyAssetPolicy by tasks.registering {
    group = "verification"
    description = "Vérifie les PNG originaux indépendants des effets de combat."
    doLast {
        val embeddedImages = fileTree("src/main/resources") {
            include("**/*.png", "**/*.jpg", "**/*.jpeg", "**/*.webp", "**/*.gif")
        }.files
        val effectDirectory = file("src/main/resources/assets/tropimon_ui_battle/textures/gui/effects")
        val expectedNames = setOf(
            "rain.png", "primordial_sea.png", "water_sport.png",
            "sun.png", "desolate_land.png", "sandstorm.png", "mud_sport.png", "hail.png", "snow.png",
            "electric_terrain.png", "grassy_terrain.png", "misty_terrain.png", "psychic_terrain.png",
            "light_screen.png", "reflect.png", "safeguard.png", "aurora_veil.png", "mist.png", "lucky_chant.png",
            "trick_room.png", "magic_room.png", "wonder_room.png", "tailwind.png", "delta_stream.png", "gravity.png",
            "stealth_rock.png", "spikes.png", "toxic_spikes.png", "sticky_web.png", "generic_effect.png"
        )
        val skinDirectory = file("src/main/resources/assets/tropimon_ui_battle/textures/gui/skin")
        val themeDirectory = file("src/main/resources/assets/tropimon_ui_battle/textures/gui/theme")
        val expectedImages = expectedNames.map { effectDirectory.resolve(it).canonicalFile }.toSet() +
                setOf(
                    skinDirectory.resolve("history_frame.png").canonicalFile,
                    themeDirectory.resolve("theme_day.png").canonicalFile,
                    themeDirectory.resolve("theme_night.png").canonicalFile
                )
        val actualImages = embeddedImages.map { it.canonicalFile }.toSet()
        check(actualImages == expectedImages) {
            val missing = expectedImages - actualImages
            val unexpected = actualImages - expectedImages
            "Assets d'effets incohérents. Manquants : $missing ; inattendus : $unexpected"
        }
        embeddedImages.forEach { imageFile ->
            val image = ImageIO.read(imageFile)
            if (imageFile.parentFile.canonicalFile == skinDirectory.canonicalFile) {
                val expected = 345 to 205
                check(image != null && image.width == expected.first && image.height == expected.second && image.colorModel.hasAlpha())
                return@forEach
            }
            if (imageFile.parentFile.canonicalFile == themeDirectory.canonicalFile) {
                check(image != null && image.width == 12 && image.height == 12 && image.colorModel.hasAlpha()) {
                    "${imageFile.name} doit rester un PNG ARGB transparent de 12 x 12."
                }
                check((image.getRGB(0, 0) ushr 24) == 0) {
                    "${imageFile.name} contient un fond opaque au lieu d'une vraie transparence."
                }
                return@forEach
            }
            check(image != null && image.width == 128 && image.height == 128 &&
                      image.colorModel.hasAlpha()) {
                "${imageFile.name} doit rester un PNG ARGB transparent de 128 x 128."
            }
            val cornerAlphas = listOf(
                image.getRGB(0, 0) ushr 24,
                image.getRGB(image.width - 1, 0) ushr 24,
                image.getRGB(0, image.height - 1) ushr 24,
                image.getRGB(image.width - 1, image.height - 1) ushr 24
            )
            check(cornerAlphas.all { it <= 1 }) {
                "${imageFile.name} contient un fond opaque au lieu d'une vraie transparence."
            }
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            val fullyTransparentPixels = pixels.count { (it ushr 24) == 0 }
            check(fullyTransparentPixels >= pixels.size / 10) {
                "${imageFile.name} ne contient pas assez de pixels réellement transparents ; " +
                        "un fond semble avoir été incrusté."
            }
        }
    }
}

tasks.check {
    dependsOn(verifyAssetPolicy)
}

val cobblemonMinimumVersion = property("cobblemon_min_version") as String
val verifyCobblemonCompatibility = tasks.register("verifyCobblemonCompatibility") {
    group = "verification"
    description = "Refuse les anciennes bornes Cobblemon avant de fabriquer un JAR."
    inputs.property("cobblemonMinimumVersion", cobblemonMinimumVersion)
    inputs.file("src/main/resources/fabric.mod.json")
    doLast {
        val expected = "\"cobblemon\": \">=$cobblemonMinimumVersion\""
        check(file("src/main/resources/fabric.mod.json").readText().contains(expected)) {
            "fabric.mod.json doit déclarer Cobblemon >=$cobblemonMinimumVersion sans borne maximale artificielle."
        }
    }
}

tasks.processResources {
    dependsOn(verifyCobblemonCompatibility)
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.register("installTropimonUIBattleLocal") {
    group = "distribution"
    description = "Installe dans le profil géré après arrêt du jeu et vérification des deux copies."
    dependsOn("armReleaseLocal")
}


val prepareReleaseDelivery = tasks.register("prepareReleaseDelivery") {
    group = "distribution"
    description = "Produit les JAR local et partageable vérifiés de la même version."
    dependsOn(tasks.build)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        val deliveryRoot = layout.buildDirectory.dir("release").get().asFile
        val shareDirectory = deliveryRoot.resolve("shareable")
        val localDirectory = deliveryRoot.resolve("local")
        shareDirectory.deleteRecursively()
        localDirectory.deleteRecursively()
        shareDirectory.mkdirs()
        localDirectory.mkdirs()

        fun copyAndHash(target: File) {
            source.copyTo(target, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            target.resolveSibling(target.name + ".sha256").writeText(hash + System.lineSeparator())
        }

        copyAndHash(shareDirectory.resolve("TropimonUIBattle-${project.version}+1.21.1.jar"))
        copyAndHash(localDirectory.resolve("TropimonUIBattle-${project.version}+1.21.1-LOCAL.jar"))
        file("tools/install-local-deferred.ps1")
            .copyTo(localDirectory.resolve("install-local-deferred.ps1"), overwrite = true)
        file("tools/InstallManagedLocalMod.ps1")
            .copyTo(localDirectory.resolve("InstallManagedLocalMod.ps1"), overwrite = true)
    }
}

tasks.register("armReleaseLocal") {
    group = "distribution"
    description = "Arme l'installation locale différée sans arrêter Minecraft ni le launcher."
    dependsOn(prepareReleaseDelivery)
    doLast {
        val script = layout.buildDirectory.file("release/local/install-local-deferred.ps1").get().asFile
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .directory(script.parentFile)
            .start()
    }
}

