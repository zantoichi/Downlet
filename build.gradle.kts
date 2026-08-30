import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

private val downletVersion = "0.1.0"
private val strongDownloadTargetMiB = 65L
private val maxDownloadSizeMiB = 90L

abstract class PackageWindowsSingleExeTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputDirectory
        abstract val appImageDirectory: DirectoryProperty

        @get:InputFile
        abstract val launcherSource: RegularFileProperty

        @get:InputFile
        abstract val iconFile: RegularFileProperty

        @get:Input
        abstract val productVersion: org.gradle.api.provider.Property<String>

        @get:Input
        abstract val strongTargetMiB: org.gradle.api.provider.Property<Long>

        @get:Input
        abstract val maximumSizeMiB: org.gradle.api.provider.Property<Long>

        @get:LocalState
        abstract val workDirectory: DirectoryProperty

        @get:OutputDirectory
        abstract val distributionDirectory: DirectoryProperty

        @TaskAction
        fun packageSingleExe() {
            require(System.getProperty("os.name").startsWith("Windows")) {
                "packageWindowsSingleExe requires Windows."
            }

            val appImage = appImageDirectory.get().asFile
            val work = workDirectory.get().asFile
            val staging = work.resolve("staging")
            val output = distributionDirectory.get().asFile
            val finalExecutable = output.resolve("Downlet.exe")

            work.deleteRecursively()
            output.deleteRecursively()
            staging.mkdirs()
            output.mkdirs()
            check(appImage.copyRecursively(staging, overwrite = true)) {
                "Could not stage the Compose application image."
            }
            check(staging.resolve("Downlet.exe").isFile) { "Staged application launcher is missing." }
            check(staging.resolve("runtime").isDirectory) { "Staged jlink runtime is missing." }

            val stagedNames =
                staging
                    .walkTopDown()
                    .filter(File::isFile)
                    .map { it.name.lowercase() }
                    .toSet()
            check("yt-dlp.exe" !in stagedNames) { "yt-dlp must remain external to the distribution." }
            check("ffmpeg.exe" !in stagedNames) { "FFmpeg must remain external to the distribution." }
            val downletJar =
                staging.resolve("app").listFiles()?.singleOrNull {
                    it.name.startsWith("Downlet-") &&
                        it.extension == "jar"
                }
            check(
                downletJar != null &&
                    ZipFile(downletJar).use { it.getEntry("tools/quickjs-ng/0.16.2/qjs.exe") != null },
            ) {
                "Bundled QuickJS is missing from the staged payload."
            }

            val manifest = work.resolve("payload.sha256")
            val stagedFiles =
                Files.walk(staging.toPath()).use { paths ->
                    paths
                        .filter(Files::isRegularFile)
                        .sorted()
                        .toList()
                }
            val manifestText =
                stagedFiles.joinToString(separator = "\n", postfix = "\n") { path ->
                    val relative =
                        staging
                            .toPath()
                            .relativize(path)
                            .toString()
                            .replace(File.separatorChar, '/')
                    "${sha256(path.toFile())}\t$relative"
                }
            manifest.writeText(manifestText, StandardCharsets.UTF_8)

            val cabinet = work.resolve("payload.cab")
            val cabinetDdf = work.resolve("payload.ddf")
            cabinetDdf.writeText(
                buildString {
                    appendLine(".OPTION EXPLICIT")
                    appendLine(".Set Cabinet=ON")
                    appendLine(".Set Compress=ON")
                    appendLine(".Set CabinetNameTemplate=payload.cab")
                    appendLine(".Set DiskDirectoryTemplate=${ddfQuote(work.absolutePath)}")
                    appendLine(".Set CompressionType=LZX")
                    appendLine(".Set CompressionMemory=21")
                    appendLine(".Set MaxDiskSize=0")
                    for (path in stagedFiles) {
                        val relative = staging.toPath().relativize(path).toString()
                        appendLine("${ddfQuote(path.toFile().absolutePath)} ${ddfQuote(relative)}")
                    }
                },
                StandardCharsets.UTF_8,
            )
            execOperations.exec {
                commandLine("makecab.exe", "/F", cabinetDdf.absolutePath)
                workingDir(work)
                standardOutput = ByteArrayOutputStream()
            }
            check(cabinet.isFile) { "Windows cabinet creation did not produce payload.cab." }

            val configHeader = work.resolve("launcher_config.h")
            configHeader.writeText(
                "#pragma once\n#define DOWNLET_VERSION L\"${productVersion.get()}\"\n",
                StandardCharsets.UTF_8,
            )
            val resourceScript = work.resolve("launcher.rc")
            resourceScript.writeText(
                windowsResourceScript(
                    version = productVersion.get(),
                    cabinet = cabinet,
                    manifest = manifest,
                    icon = iconFile.get().asFile,
                ),
                StandardCharsets.UTF_8,
            )

            val visualStudio = findVisualStudioInstallation()
            val vcVars = visualStudio.resolve("VC/Auxiliary/Build/vcvars64.bat")
            check(vcVars.isFile) { "Visual Studio x64 build environment is unavailable." }
            val resource = work.resolve("launcher.res")
            val objectFile = work.resolve("launcher.obj")
            val buildScript = work.resolve("build-launcher.cmd")
            buildScript.writeText(
                """
                @echo off
                call "${vcVars.absolutePath}" >nul
                if errorlevel 1 exit /b %errorlevel%
                rc.exe /nologo /fo"${resource.absolutePath}" "${resourceScript.absolutePath}"
                if errorlevel 1 exit /b %errorlevel%
                cl.exe /nologo /std:c++20 /permissive- /W4 /WX /O2 /GL /MT /EHsc /DUNICODE /D_UNICODE /I"${work.absolutePath}" /Fo"${objectFile.absolutePath}" "${launcherSource.get().asFile.absolutePath}" "${resource.absolutePath}" /link /SUBSYSTEM:WINDOWS /LTCG /OPT:REF /OPT:ICF setupapi.lib shell32.lib ole32.lib bcrypt.lib user32.lib /OUT:"${finalExecutable.absolutePath}"
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            execOperations.exec {
                commandLine("cmd.exe", "/d", "/c", buildScript.absolutePath)
                workingDir(work)
            }

            check(finalExecutable.isFile) { "Launcher compilation did not produce Downlet.exe." }
            check(output.listFiles()?.map { it.name } == listOf("Downlet.exe")) {
                "Windows distribution directory must contain only Downlet.exe."
            }
            check(readPeMachine(finalExecutable) == 0x8664) { "Downlet.exe is not an x64 PE image." }

            val sizeMiB = finalExecutable.length().toDouble() / (1024.0 * 1024.0)
            check(sizeMiB <= maximumSizeMiB.get()) {
                "Downlet.exe is ${"%.2f".format(sizeMiB)} MiB; release ceiling is ${maximumSizeMiB.get()} MiB."
            }
            if (sizeMiB > strongTargetMiB.get()) {
                logger.warn(
                    "Downlet.exe is ${"%.2f".format(
                        sizeMiB,
                    )} MiB, above the ${strongTargetMiB.get()} MiB strong target.",
                )
            }
            logger.lifecycle(
                "Created ${finalExecutable.absolutePath} (${"%.2f".format(sizeMiB)} MiB; " +
                    "${"%.2f".format(
                        staging
                            .walkTopDown()
                            .filter(File::isFile)
                            .sumOf(File::length)
                            .toDouble() / 1024 / 1024,
                    )} MiB extracted).",
            )
        }

        private fun findVisualStudioInstallation(): File {
            val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: error("ProgramFiles(x86) is unavailable.")
            val vsWhere = File(programFilesX86, "Microsoft Visual Studio/Installer/vswhere.exe")
            check(vsWhere.isFile) { "vswhere.exe is unavailable; install Visual Studio Build Tools with MSVC x64." }
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine(
                    vsWhere.absolutePath,
                    "-latest",
                    "-products",
                    "*",
                    "-requires",
                    "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                    "-property",
                    "installationPath",
                )
                standardOutput = output
            }
            return File(output.toString(StandardCharsets.UTF_8).trim()).also {
                check(it.isDirectory) { "Visual Studio with MSVC x64 was not found." }
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun ddfQuote(value: String) = "\"${value.replace("\"", "\"\"")}\""

        private fun windowsResourceScript(
            version: String,
            cabinet: File,
            manifest: File,
            icon: File,
        ): String {
            val numericVersion = version.split('.').joinToString(",") + ",0"

            fun rcPath(file: File) = file.absolutePath.replace('\\', '/')
            return """
                #include <windows.h>
                1 ICON "${rcPath(icon)}"
                101 RCDATA "${rcPath(cabinet)}"
                102 RCDATA "${rcPath(manifest)}"
                1 VERSIONINFO
                FILEVERSION $numericVersion
                PRODUCTVERSION $numericVersion
                FILEFLAGSMASK 0x3fL
                FILEFLAGS 0x0L
                FILEOS 0x40004L
                FILETYPE 0x1L
                FILESUBTYPE 0x0L
                BEGIN
                    BLOCK "StringFileInfo"
                    BEGIN
                        BLOCK "040904b0"
                        BEGIN
                            VALUE "FileDescription", "Downlet"
                            VALUE "FileVersion", "$version"
                            VALUE "InternalName", "Downlet"
                            VALUE "OriginalFilename", "Downlet.exe"
                            VALUE "ProductName", "Downlet"
                            VALUE "ProductVersion", "$version"
                        END
                    END
                    BLOCK "VarFileInfo"
                    BEGIN
                        VALUE "Translation", 0x0409, 1200
                    END
                END
                """.trimIndent()
        }

        private fun readPeMachine(file: File): Int {
            file.inputStream().use { input ->
                val header = input.readNBytes(64)
                check(header.size == 64 && header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte()) {
                    "Downlet.exe has no DOS header."
                }
                val peOffset = ByteBuffer.wrap(header, 0x3c, 4).order(ByteOrder.LITTLE_ENDIAN).int
                input.skipNBytes((peOffset - 64).toLong())
                val peHeader = input.readNBytes(6)
                check(
                    peHeader.copyOfRange(0, 4).contentEquals(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0)),
                ) {
                    "Downlet.exe has no PE header."
                }
                return ByteBuffer
                    .wrap(peHeader, 4, 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                    .toInt() and 0xffff
            }
        }
    }

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

repositories {
    google()
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.39.1-262.9437.29") {
        exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.39.1-262.9437.29") {
        exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    runtimeOnly("com.jetbrains.intellij.platform:icons:262.9437.136")
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

val jetBrainsJdk25 =
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xms64m", "-Xmx256m")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "downlet.MainKt"
        javaHome =
            jetBrainsJdk25
                .get()
                .metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xms64m", "-Xmx256m")
        nativeDistributions {
            packageName = "Downlet"
            packageVersion = downletVersion
            modules("java.instrument", "java.net.http", "jdk.unsupported")
            windows {
                iconFile.set(project.file("src/launcher/windows/downlet.ico"))
            }
        }
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

tasks.register<PackageWindowsSingleExeTask>("packageWindowsSingleExe") {
    description = "Builds the portable single-file Windows x64 distribution."
    group = "distribution"
    dependsOn("createDistributable")
    appImageDirectory.set(layout.buildDirectory.dir("compose/binaries/main/app/Downlet"))
    launcherSource.set(layout.projectDirectory.file("src/launcher/windows/launcher.cpp"))
    iconFile.set(layout.projectDirectory.file("src/launcher/windows/downlet.ico"))
    productVersion.set(downletVersion)
    strongTargetMiB.set(strongDownloadTargetMiB)
    maximumSizeMiB.set(maxDownloadSizeMiB)
    workDirectory.set(layout.buildDirectory.dir("windows-single-exe/work"))
    distributionDirectory.set(layout.buildDirectory.dir("compose/binaries/main/windows-single-exe"))
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        javaLauncher = jetBrainsJdk25
        @Suppress("UsePropertyAccessSyntax")
        setExecutable(javaLauncher.map { it.executablePath.asFile.absolutePath }.get())
    }
}
