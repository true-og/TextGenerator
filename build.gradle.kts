/* This is free and unencumbered software released into the public domain */

import org.gradle.kotlin.dsl.provideDelegate

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "7.0.4" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.6" // Import Shadow plugin.
    id("checkstyle") // Import Checkstyle plugin.
    eclipse // Import Eclipse plugin.
}

/* --------------------------- JDK Config ------------------------------ */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

/* ----------------------------- Metadata ------------------------------ */
group = "de.philw" // Declare bundle identifier.

version = "1.0.5" // Declare plugin version (will be in .jar).

val apiVersion = "1.19" // Declare minecraft server target version.

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let {
        val dir = file(it)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $it")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("net.wesjd:anvilgui:1.9.2-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    archiveBaseName.set("TextGenerator")
    archiveClassifier.set("") // Use empty string instead of null.
    relocate("org.bstats", "de.philw")
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters") // Enable reflection for java code.
    options.isFork = true // Run javac in its own process.
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    java {
        eclipse().configFile("config/formatter/eclipse-java-formatter.xml") // Eclipse java formatting.
        leadingTabsToSpaces() // Convert leftover leading tabs to spaces.
        removeUnusedImports() // Remove imports that aren't being called.
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

checkstyle {
    toolVersion = "10.18.1" // Declare checkstyle version to use.
    configFile = file("config/checkstyle/checkstyle.xml") // Point checkstyle to config file.
    isIgnoreFailures = true // Don't fail the build if checkstyle does not pass.
    isShowViolations = true // Show the violations in any IDE with the checkstyle plugin.
}

tasks.named("compileJava") {
    dependsOn("spotlessApply") // Run spotless before compiling with the JDK.
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}

/* --------------------------- Remap (SpecialSource) -------------------- */
val specialSource by configurations.creating
val mojangMappings by configurations.creating
val spigotMappings by configurations.creating
val spigotRemappedMojang by configurations.creating
val spigotRemappedObf by configurations.creating

dependencies {
    specialSource("net.md-5:SpecialSource:1.11.5")
    mojangMappings("org.spigotmc:minecraft-server:1.19.4-R0.1-SNAPSHOT:maps-mojang@txt")
    spigotMappings("org.spigotmc:minecraft-server:1.19.4-R0.1-SNAPSHOT:maps-spigot@csrg")
    spigotRemappedMojang("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT:remapped-mojang@jar")
    spigotRemappedObf("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT:remapped-obf@jar")
}

val remapObf =
    tasks.register<JavaExec>("remapObf") {
        dependsOn(tasks.shadowJar)
        classpath = specialSource
        mainClass.set("net.md_5.specialsource.SpecialSource")
        val inJar = tasks.shadowJar.get().archiveFile.get().asFile
        val outJar = layout.buildDirectory.file("libs/TextGenerator-${project.version}-remapped-obf.jar").get().asFile
        inputs.file(inJar)
        outputs.file(outJar)
        args(
            "--in-jar",
            inJar.absolutePath,
            "--out-jar",
            outJar.absolutePath,
            "--srg-in",
            mojangMappings.singleFile.absolutePath,
            "--reverse",
        )
    }

val remapSpigot =
    tasks.register<JavaExec>("remapSpigot") {
        dependsOn(remapObf)
        classpath = specialSource
        mainClass.set("net.md_5.specialsource.SpecialSource")
        val inJar = layout.buildDirectory.file("libs/TextGenerator-${project.version}-remapped-obf.jar").get().asFile
        val outJar = layout.buildDirectory.file("libs/TextGenerator-${project.version}.jar").get().asFile
        inputs.file(inJar)
        outputs.file(outJar)
        args(
            "--in-jar",
            inJar.absolutePath,
            "--out-jar",
            outJar.absolutePath,
            "--srg-in",
            spigotMappings.singleFile.absolutePath,
        )
    }

tasks.build { dependsOn(tasks.spotlessApply, remapSpigot) } // Build depends on spotless and final remap.

/* ------------------------------ Eclipse SHIM ------------------------- */

// This can't be put in eclipse.gradle.kts because Gradle is weird.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    eclipse.project.name = "${project.name}-${rootProject.name}"
    tasks.withType<Jar>().configureEach { archiveBaseName.set("${project.name}-${rootProject.name}") }
}
