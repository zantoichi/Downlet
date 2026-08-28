import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.11.0"
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
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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

compose.desktop {
    application {
        mainClass = "downlet.MainKt"
    }
}

compose.resources {
    generateResClass = always
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
