@file:Suppress("UnstableApiUsage", "UsePropertyAccessSyntax")

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

afterEvaluate {
    tasks.named<JavaExec>("run") {
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }
        setExecutable(javaLauncher.map { it.executablePath.asFile.absolutePath }.get())
    }
}
