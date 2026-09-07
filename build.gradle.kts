import java.net.URI
import java.util.Locale

plugins {
    id("fabric-loom") version "1.11.8"
    `maven-publish`
    signing
}

version = property("mod_version") as String
group = property("maven_group") as String
base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
}

val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String
val lwjglVersion = property("lwjgl_version") as String
val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY").orElse("TamKungZ/DX12MC")
val signingKeyName = "dev@tamkungz.me"
val bgfxWindowsNatives by configurations.creating
val javaFormatter by configurations.creating
val shadercCache = File(
    gradle.gradleUserHomeDir,
    "caches/dx12mc/bgfx-$lwjglVersion/windows-x64/shaderc.exe"
)

val shaderSourceDir = layout.projectDirectory.dir("src/main/shaders")
val generatedShaderRoot = layout.buildDirectory.dir("generated/dx12mc-shaders")
val generatedShaderDir = generatedShaderRoot.map { it.dir("assets/dx12mc/shaders/d3d12") }
data class ShaderSpec(val name: String, val type: String, val magic: Char)
val shaderSpecs = listOf(
    ShaderSpec("vs_position_color", "vertex", 'V'),
    ShaderSpec("fs_position_color", "fragment", 'F'),
    ShaderSpec("vs_position_color_tex", "vertex", 'V'),
    ShaderSpec("fs_position_color_tex", "fragment", 'F'),
    ShaderSpec("fs_position_color_tex_cutout", "fragment", 'F'),
    ShaderSpec("vs_position_color_tex_light", "vertex", 'V'),
    ShaderSpec("fs_position_color_tex_light", "fragment", 'F'),
    ShaderSpec("fs_position_color_tex_light_cutout", "fragment", 'F')
)

val prepareShaderc by tasks.registering {
    group = "dx12mc verification"
    description = "Obtains bgfx shaderc matching the LWJGL/bgfx runtime version."
    outputs.file(shadercCache)
    doLast {
        fun looksExecutable(file: File): Boolean {
            if (!file.isFile || file.length() < 64 * 1024) return false
            return file.inputStream().use { input ->
                input.read() == 'M'.code && input.read() == 'Z'.code
            }
        }
        if (looksExecutable(shadercCache)) return@doLast
        shadercCache.delete()
        shadercCache.parentFile.mkdirs()
        val overridePath = providers.environmentVariable("DX12MC_SHADERC").orNull
        if (!overridePath.isNullOrBlank()) {
            val source = file(overridePath)
            if (!looksExecutable(source)) {
                throw GradleException("DX12MC_SHADERC is not a usable Windows shaderc executable: $source")
            }
            source.copyTo(shadercCache, overwrite = true)
            logger.lifecycle("DX12MC: cached shaderc from DX12MC_SHADERC -> $shadercCache")
            return@doLast
        }
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        if (!os.contains("windows")) {
            throw GradleException(
                "DX12MC's D3D12 shaders are compiled with bgfx shaderc for Windows. " +
                    "Build on Windows or set DX12MC_SHADERC to a compatible shaderc executable."
            )
        }
        val candidates = listOf(
            "https://build.lwjgl.org/release/$lwjglVersion/windows/x64/bgfx-tools/shaderc.exe",
            "https://build.lwjgl.org/release/$lwjglVersion/windows/x64/bgfx-tools/shadercRelease.exe"
        )
        val temp = File(shadercCache.parentFile, "shaderc.download")
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                logger.lifecycle("DX12MC: downloading bgfx shaderc $lwjglVersion from $candidate")
                URI(candidate).toURL().openStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!looksExecutable(temp)) {
                    throw GradleException("Downloaded shaderc is not a PE executable: $candidate")
                }
                temp.copyTo(shadercCache, overwrite = true)
                temp.delete()
                lastError = null
                break
            } catch (throwable: Throwable) {
                lastError = throwable
                temp.delete()
            }
        }
        if (!looksExecutable(shadercCache)) {
            throw GradleException(
                "Unable to obtain bgfx shaderc $lwjglVersion. " +
                    "Download it from LWJGL bgfx-tools and set DX12MC_SHADERC.",
                lastError
            )
        }
    }
}

val validateD3D12ShaderSources by tasks.registering {
    group = "dx12mc verification"
    description = "Validates the small shader compatibility layer before invoking shaderc."
    inputs.dir(shaderSourceDir)
    doLast {
        val common = shaderSourceDir.file("common.sh").asFile.readText()
        val texture = shaderSourceDir.file("texture.sh").asFile.readText()
        val requiredSplats = listOf(
            "vec2 vec2_splat(float _x)",
            "vec3 vec3_splat(float _x)",
            "vec4 vec4_splat(float _x)"
        )
        for (signature in requiredSplats) {
            if (!common.contains(signature)) {
                throw GradleException("common.sh is missing shaderc-required helper definition: $signature")
            }
        }
        val textureContract = listOf(
            "SamplerState m_sampler;",
            "Texture2D m_texture;",
            "m_texture.Sample(_sampler.m_sampler, _coord)",
            "SAMPLER2D(_name, _reg)",
            "DX12MC_REGISTER"
        )
        for (needle in textureContract) {
            if (!texture.contains(needle)) {
                throw GradleException("texture.sh no longer satisfies the D3D12 sampler contract: $needle")
            }
        }
        val forbidden = listOf(
            "uniform sampler2D",
            "SamplerState sampler;",
            "Texture2D texture;"
        )
        for (needle in forbidden) {
            if (texture.contains(needle)) {
                throw GradleException("texture.sh contains an invalid HLSL sampler form: $needle")
            }
        }
        val expectedIncludes = mapOf(
            "vs_position_color.sc" to "#include \"common.sh\"",
            "fs_position_color.sc" to "#include \"common.sh\"",
            "vs_position_color_tex.sc" to "#include \"common.sh\"",
            "fs_position_color_tex.sc" to "#include \"texture.sh\"",
            "fs_position_color_tex_cutout.sc" to "#include \"texture.sh\"",
            "vs_position_color_tex_light.sc" to "#include \"common.sh\"",
            "fs_position_color_tex_light.sc" to "#include \"texture.sh\"",
            "fs_position_color_tex_light_cutout.sc" to "#include \"texture.sh\""
        )
        expectedIncludes.forEach { (name, include) ->
            val text = shaderSourceDir.file(name).asFile.readText()
            if (!text.contains(include)) {
                throw GradleException("$name must contain $include")
            }
        }
        val texturedFragment = shaderSourceDir.file("fs_position_color_tex.sc").asFile.readText()
        if (!texturedFragment.contains("SAMPLER2D(s_texColor, 0)")) {
            throw GradleException("Textured fragment shader is missing s_texColor")
        }
        val lightFragment = shaderSourceDir.file("fs_position_color_tex_light.sc").asFile.readText()
        if (!lightFragment.contains("SAMPLER2D(s_texColor, 0)") ||
            !lightFragment.contains("SAMPLER2D(s_lightmap, 2)")) {
            throw GradleException("Lightmapped fragment shader must bind Sampler0 and Sampler2")
        }
        for (name in listOf("fs_position_color_tex_cutout.sc", "fs_position_color_tex_light_cutout.sc")) {
            val cutout = shaderSourceDir.file(name).asFile.readText()
            if (!cutout.contains("discard") || !cutout.contains("color.a < 0.1") && !cutout.contains("base.a < 0.1")) {
                throw GradleException("$name must preserve vanilla alpha-cutout semantics")
            }
        }
    }
}

val compileD3D12Shaders by tasks.registering {
    group = "dx12mc verification"
    description = "Compiles every DX12MC D3D12 shader and reports all shader failures together."
    dependsOn(prepareShaderc, validateD3D12ShaderSources)
    for (spec in shaderSpecs) {
        inputs.file(shaderSourceDir.file("${spec.name}.sc"))
    }
    inputs.file(shaderSourceDir.file("varying.def.sc"))
    inputs.file(shaderSourceDir.file("common.sh"))
    inputs.file(shaderSourceDir.file("texture.sh"))
    outputs.dir(generatedShaderDir)
    doLast {
        val varyingFile = shaderSourceDir.file("varying.def.sc").asFile
        val outputDir = generatedShaderDir.get().asFile
        outputDir.mkdirs()
        val failures = mutableListOf<String>()
        for (spec in shaderSpecs) {
            val inputFile = shaderSourceDir.file("${spec.name}.sc").asFile
            val outputFile = File(outputDir, "${spec.name}.bin")
            outputFile.delete()
            val command = listOf(
                shadercCache.absolutePath,
                "-f", inputFile.absolutePath,
                "-o", outputFile.absolutePath,
                "--type", spec.type,
                "--platform", "windows",
                "-p", "s_5_0",
                "--varyingdef", varyingFile.absolutePath,
                "-i", shaderSourceDir.asFile.absolutePath,
                "-O", "3"
            )
            logger.lifecycle("DX12MC shaderc: ${spec.name}.sc")
            val process = ProcessBuilder(command)
                .directory(project.projectDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            if (exit != 0) {
                failures += buildString {
                    appendLine("--- ${spec.name}.sc (${spec.type}) ---")
                    appendLine("exit code: $exit")
                    append(output.trimEnd())
                }
                continue
            }
            if (!outputFile.isFile || outputFile.length() < 8) {
                failures += "--- ${spec.name}.sc (${spec.type}) ---\nshaderc exited 0 but did not create a usable output file"
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "${failures.size} D3D12 shader(s) failed to compile:\n\n" +
                    failures.joinToString("\n\n")
            )
        }
    }
}

val verifyD3D12Shaders by tasks.registering {
    group = "verification"
    description = "Compiles all D3D12 shaders and verifies bgfx shader containers were produced."
    dependsOn(compileD3D12Shaders)
    doLast {
        for (spec in shaderSpecs) {
            val file = generatedShaderDir.get().file("${spec.name}.bin").asFile
            if (!file.isFile || file.length() < 8) {
                throw GradleException("shaderc did not produce a usable ${spec.name}.bin")
            }
            val header = file.inputStream().use { it.readNBytes(3) }
            if (header.size != 3 || header[0].toInt().toChar() != spec.magic ||
                header[1].toInt().toChar() != 'S' || header[2].toInt().toChar() != 'H') {
                throw GradleException(
                    "${spec.name}.bin is not a bgfx ${spec.type} shader container " +
                        "(expected ${spec.magic}SH magic)"
                )
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("org.lwjgl:lwjgl-bgfx:$lwjglVersion")
    include("org.lwjgl:lwjgl-bgfx:$lwjglVersion")
    runtimeOnly("org.lwjgl:lwjgl-bgfx:$lwjglVersion:natives-windows")
    bgfxWindowsNatives("org.lwjgl:lwjgl-bgfx:$lwjglVersion:natives-windows")
    javaFormatter("com.google.googlejavaformat:google-java-format:1.36.1")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

loom {
    runs {
        named("client") {
            client()
            val mode = providers.systemProperty("dx12mc.mode").orElse("native")
            val vsync = providers.systemProperty("dx12mc.vsync").orElse("true")
            val clear = providers.systemProperty("dx12mc.clear").orElse("18202AFF")
            val overlay = providers.systemProperty("dx12mc.overlay").orElse("true")
            vmArg("-Ddx12mc.mode=${mode.get()}")
            vmArg("-Ddx12mc.vsync=${vsync.get()}")
            vmArg("-Ddx12mc.clear=${clear.get()}")
            vmArg("-Ddx12mc.overlay=${overlay.get()}")
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    from({ bgfxWindowsNatives.map { zipTree(it) } }) {
        include("**/*.dll")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val installedShaderRoot = layout.buildDirectory.dir("generated/dx12mc-installed-resources")
val installD3D12Shaders by tasks.registering(Copy::class) {
    group = "dx12mc verification"
    description = "Stages verified D3D12 shader binaries for inclusion in the distributable JAR."
    dependsOn(verifyD3D12Shaders)
    from(generatedShaderRoot)
    into(installedShaderRoot)
}

tasks.jar {
    dependsOn(installD3D12Shaders)
    from(installedShaderRoot)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

fun javaSourceFiles() = fileTree("src") {
    include("**/*.java")
}.files.sortedBy { it.path }

val googleJavaFormatJvmArgs = listOf(
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
)

tasks.register<JavaExec>("formatJava") {
    group = "formatting"
    description = "Formats Java source files with google-java-format."
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    jvmArgs(googleJavaFormatJvmArgs)
    args("--replace")
    args(javaSourceFiles())
}

tasks.register<JavaExec>("checkJavaFormat") {
    group = "verification"
    description = "Checks Java source formatting with google-java-format."
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    jvmArgs(googleJavaFormatJvmArgs)
    args("--dry-run", "--set-exit-if-changed")
    args(javaSourceFiles())
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(verifyD3D12Shaders)
    dependsOn("checkJavaFormat")
}

tasks.register("verifyDX12MC") {
    group = "verification"
    description = "Runs Java tests and the real shaderc D3D12 shader smoke tests."
    dependsOn("test", verifyD3D12Shaders)
}

tasks.remapJar {
    archiveFileName.set("dx12mc-${project.version}.jar")
}

extra["signing.gnupg.keyName"] = signingKeyName

fun hasSigningKey(): Boolean {
    if (!gradle.startParameter.taskNames.any { it.contains("publish", ignoreCase = true) || it.contains("sign", ignoreCase = true) }) {
        return false
    }
    return try {
        ProcessBuilder("gpg", "--batch", "--list-secret-keys", signingKeyName)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Throwable) {
        false
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = property("archives_base_name") as String
            from(components["java"])
            pom {
                name.set("DX12MC")
                description.set("Client-side Fabric mod that routes selected Minecraft Java rendering work through Direct3D 12 using bgfx.")
                url.set("https://github.com/${githubRepository.get()}")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                    }
                }
                developers {
                    developer {
                        id.set("tamkungz")
                        name.set("TamKungZ_")
                        email.set("dev@tamkungz.me")
                        roles.add("maintainer")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/${githubRepository.get()}.git")
                    developerConnection.set("scm:git:ssh://git@github.com/${githubRepository.get()}.git")
                    url.set("https://github.com/${githubRepository.get()}")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${githubRepository.get()}")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse("tamkungz")
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orElse(providers.environmentVariable("GH_TOKEN"))
                    .orElse("")
                    .get()
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = hasSigningKey()
    sign(publishing.publications["mavenJava"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { hasSigningKey() }
}

tasks.register("publishLocalMaven") {
    group = "publishing"
    description = "Publishes DX12MC to the local Maven repository."
    dependsOn("publishToMavenLocal")
}

tasks.register("publishGitHubPackage") {
    group = "publishing"
    description = "Publishes DX12MC to GitHub Packages."
    dependsOn("publishMavenJavaPublicationToGitHubPackagesRepository")
}
