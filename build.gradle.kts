import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.compose.hot-reload") version "1.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.39.1-262.9437.29")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.39.1-262.9437.29")
    implementation("io.github.nsk90:kstatemachine-coroutines:0.38.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.jetbrains.compose.ui:ui-test:1.12.0")
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "downlet.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
    }
}

compose.resources {
    generateResClass = always
}

tasks.named<ProcessResources>("processResources") {
    from(rootDir) {
        include("LICENSE", "THIRD_PARTY_NOTICES.md")
    }
}

ktlint {
    version.set("1.8.0")
    additionalEditorconfig.set(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt")
}

tasks.named<Test>("test") {
    exclude("**/ProductSmokeTest.class")
}

tasks.register<Test>("smokeTest") {
    description = "Runs the focused in-process Compose product smoke test."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath =
        sourceSets.test
            .get()
            .runtimeClasspath
    include("**/ProductSmokeTest.class")
    testLogging.showStandardStreams = true
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
                @Suppress("UnstableApiUsage")
                vendor = JvmVendorSpec.JETBRAINS
            }
        @Suppress("UsePropertyAccessSyntax")
        setExecutable(javaLauncher.map { it.executablePath.asFile.absolutePath }.get())
    }
}
