import java.net.URI
import java.security.MessageDigest
import java.util.Locale

plugins {
    id("fabric-loom")
    `maven-publish`
    signing
}

fun prop(name: String): String = property(name) as String

evaluationDependsOn(":api")

version = "${prop("mod_version")}+mc${prop("minecraft_version")}"
group = prop("maven_group")
base {
    archivesName.set(prop("archives_base_name"))
}

repositories {
    mavenCentral()
}

val minecraftVersion = prop("minecraft_version")
val loaderVersion = prop("loader_version")
val lwjglVersion = prop("lwjgl_version")
val javaVersion = prop("java_version").toInt()
val githubUrl = prop("github_url")
val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY").orElse(prop("github_repository"))
val signingKeyCandidates = prop("signing_key_candidates").split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun gpgHasSecretKey(keyName: String): Boolean {
    return try {
        ProcessBuilder("gpg", "--batch", "--list-secret-keys", keyName)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Throwable) {
        false
    }
}

val signingKeyName: String? =
    if (gradle.startParameter.taskNames.any { it.contains("publish", ignoreCase = true) || it.contains("sign", ignoreCase = true) }) {
        signingKeyCandidates.firstOrNull { gpgHasSecretKey(it) }
    } else {
        null
    }
extra["dx12mcSigningKey"] = signingKeyName
val bgfxWindowsNatives by configurations.creating
val javaFormatter by configurations.creating
val shadercCache = File(
    gradle.gradleUserHomeDir,
    "caches/dx12mc/bgfx-$lwjglVersion/windows-x64/shaderc.exe"
)

val shadercProfile = prop("shaderc_profile")
val shadercOptimization = prop("shaderc_optimization")
val shadercDownloadBase = prop("lwjgl_release_url")
val formatterMaxHeap = prop("formatter_max_heap")

val shaderSourceDir = layout.projectDirectory.dir("src/main/shaders")
val generatedShaderRoot = layout.buildDirectory.dir("generated/dx12mc-shaders")
val generatedShaderDir = generatedShaderRoot.map { it.dir("assets/dx12mc/shaders/d3d12") }
data class ShaderSpec(val name: String, val type: String, val magic: Char, val source: String = name, val defines: String? = null)
val shaderSpecs = listOf(
    ShaderSpec("vs_position_color", "vertex", 'V'),
    ShaderSpec("vs_position_color_light", "vertex", 'V'),
    ShaderSpec("fs_position_color", "fragment", 'F'),
    ShaderSpec("fs_position_color_alpha", "fragment", 'F'),
    ShaderSpec("fs_logic_invert", "fragment", 'F'),
    ShaderSpec("fs_logic_invert_alpha", "fragment", 'F'),
    ShaderSpec("vs_position_color_tex", "vertex", 'V'),
    ShaderSpec("vs_position_color_tex_light", "vertex", 'V'),
    ShaderSpec("fs_position_color_tex", "fragment", 'F'),
    ShaderSpec("fs_position_color_tex_cutout", "fragment", 'F'),
    ShaderSpec("vs_entity", "vertex", 'V'),
    ShaderSpec("vs_line", "vertex", 'V'),
    ShaderSpec("vs_end_portal", "vertex", 'V'),
    ShaderSpec("fs_end_portal", "fragment", 'F'),
    ShaderSpec("fs_entity", "fragment", 'F'),
    ShaderSpec("fs_entity_cutout", "fragment", 'F'),
    ShaderSpec("vs_post", "vertex", 'V'),
    ShaderSpec("fs_post_box_blur", "fragment", 'F'),
    ShaderSpec("fs_post_entity_sobel", "fragment", 'F'),
    ShaderSpec("fs_post_outline_blur", "fragment", 'F'),
    ShaderSpec("fs_post_blit", "fragment", 'F'),
    ShaderSpec("fs_post_invert", "fragment", 'F'),
    ShaderSpec("fs_post_color_convolve", "fragment", 'F'),
    ShaderSpec("fs_post_bits", "fragment", 'F'),
    ShaderSpec("fs_post_spider", "fragment", 'F')
) + listOf(
    "fs_position_color", "fs_position_color_alpha", "fs_logic_invert", "fs_logic_invert_alpha",
    "fs_position_color_tex", "fs_position_color_tex_cutout", "fs_entity", "fs_entity_cutout", "fs_end_portal"
).map { ShaderSpec("${it}_mrt", "fragment", 'F', it, "DX12MC_SCENE_MRT") }

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
        val expectedSha256 = findProperty("shaderc_sha256_$lwjglVersion") as String?
            ?: throw GradleException(
                "No pinned SHA-256 for bgfx shaderc $lwjglVersion. " +
                    "Add shaderc_sha256_$lwjglVersion to gradle.properties."
            )
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun requirePinned(file: File, origin: String) {
            val actual = sha256(file)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw GradleException(
                    "shaderc from $origin does not match the pinned SHA-256 for LWJGL $lwjglVersion.\n" +
                        "  expected: $expectedSha256\n" +
                        "  actual:   $actual"
                )
            }
        }
        if (looksExecutable(shadercCache) && sha256(shadercCache).equals(expectedSha256, ignoreCase = true)) {
            return@doLast
        }
        shadercCache.delete()
        shadercCache.parentFile.mkdirs()
        val overridePath = providers.environmentVariable("DX12MC_SHADERC").orNull
        if (!overridePath.isNullOrBlank()) {
            val source = file(overridePath)
            if (!looksExecutable(source)) {
                throw GradleException("DX12MC_SHADERC is not a usable Windows shaderc executable: $source")
            }
            requirePinned(source, "DX12MC_SHADERC ($source)")
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
            "$shadercDownloadBase/$lwjglVersion/windows/x64/bgfx-tools/shaderc.exe"
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
                requirePinned(temp, candidate)
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
                "Unable to obtain bgfx shaderc $lwjglVersion matching the pinned SHA-256. " +
                    "Download it from LWJGL bgfx-tools and set DX12MC_SHADERC.",
                lastError
            )
        }
    }
}

apply(from = "tools/shaderpack.gradle.kts")
apply(from = "tools/harness.gradle.kts")

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
        val vanillaConstants = listOf(
            "(light0 + light1) * 0.6 + 0.4",
            "clamp(_uv2 / 256.0, vec2_splat(0.5 / 16.0), vec2_splat(15.5 / 16.0))",
            "(floor(_uv2 / 16.0) + vec2_splat(0.5)) / 16.0",
            "smoothstep(_params.x, _params.y, _distance)",
            "smoothstep(_params.y, _params.x, _distance)",
            "max(length(_pos.xz), abs(_pos.y))"
        )
        for (needle in vanillaConstants) {
            if (!common.contains(needle)) {
                throw GradleException("common.sh no longer matches vanilla fog/light/lightmap math: $needle")
            }
        }
        val expectedIncludes = mapOf(
            "vs_position_color.sc" to "#include \"common.sh\"",
            "vs_position_color_light.sc" to "#include \"texture.sh\"",
            "fs_position_color.sc" to "#include \"common.sh\"",
            "fs_position_color_alpha.sc" to "#include \"common.sh\"",
            "fs_logic_invert.sc" to "#include \"common.sh\"",
            "fs_logic_invert_alpha.sc" to "#include \"common.sh\"",
            "vs_position_color_tex.sc" to "#include \"common.sh\"",
            "vs_position_color_tex_light.sc" to "#include \"texture.sh\"",
            "fs_position_color_tex.sc" to "#include \"texture.sh\"",
            "fs_position_color_tex_cutout.sc" to "#include \"texture.sh\"",
            "vs_entity.sc" to "#include \"texture.sh\"",
            "vs_line.sc" to "#include \"common.sh\"",
            "vs_end_portal.sc" to "#include \"common.sh\"",
            "fs_end_portal.sc" to "#include \"texture.sh\"",
            "fs_entity.sc" to "#include \"texture.sh\"",
            "fs_entity_cutout.sc" to "#include \"texture.sh\"",
            "vs_post.sc" to "#include \"common.sh\"",
            "post.sh" to "#include \"texture.sh\"",
            "fs_post_box_blur.sc" to "#include \"post.sh\"",
            "fs_post_entity_sobel.sc" to "#include \"post.sh\"",
            "fs_post_outline_blur.sc" to "#include \"post.sh\"",
            "fs_post_blit.sc" to "#include \"post.sh\"",
            "fs_post_invert.sc" to "#include \"post.sh\"",
            "fs_post_color_convolve.sc" to "#include \"post.sh\"",
            "fs_post_bits.sc" to "#include \"post.sh\"",
            "fs_post_spider.sc" to "#include \"post.sh\""
        )
        expectedIncludes.forEach { (name, include) ->
            val text = shaderSourceDir.file(name).asFile.readText()
            if (!text.contains(include)) {
                throw GradleException("$name must contain $include")
            }
        }
        fun requireAll(name: String, needles: List<String>) {
            val text = shaderSourceDir.file(name).asFile.readText()
            for (needle in needles) {
                if (!text.contains(needle)) throw GradleException("$name is missing $needle")
            }
        }
        for (name in listOf("vs_position_color.sc", "vs_position_color_light.sc", "vs_position_color_tex.sc",
                "vs_position_color_tex_light.sc", "vs_entity.sc", "vs_line.sc")) {
            requireAll(name, listOf("uniform mat4 u_fogModel;", "dx12mcFogDistance(mul(u_fogModel, clip).xyz"))
        }
        for (name in listOf("vs_position_color_light.sc", "vs_position_color_tex_light.sc", "vs_entity.sc")) {
            requireAll(name, listOf("SAMPLER2D(s_lightmap, 2)",
                "dx12mcLightmapUv(dx12mcUnsigned16(a_texcoord2), u_lightParams.x)"))
        }
        requireAll("vs_line.sc", listOf(
            "uniform vec4 u_lineParams;",
            "vec2(-screenDirection.y, screenDirection.x) * u_lineParams.x / u_lineParams.yz",
            "if (lineOffset.x < 0.0) lineOffset *= -1.0;",
            "lineOffset * a_texcoord0.x"))
        requireAll("vs_end_portal.sc", listOf(
            "uniform mat4 u_mvp;", "v_texProj = vec4(clip.xy * 0.5 + vec2_splat(clip.w * 0.5), clip.zw);"))
        requireAll("fs_end_portal.sc", listOf(
            "SAMPLER2D(s_texColor, 0)", "SAMPLER2D(s_overlay, 1)", "uniform vec4 u_portal;",
            "for (int i = 0; i < layers; i++)", "(4.5 - layer / 4.0) * 2.0", "17.0 / layer * p.w",
            "(2.0 + layer / 1.5) * (u_portal.x * 1.5) * p.w", "xy * 0.5 + vec2_splat(0.25 * p.w)"))
        requireAll("vs_entity.sc", listOf(
            "SAMPLER2D(s_overlay, 1)", "dx12mcMixLight(u_lightDir0.xyz, u_lightDir1.xyz, normal, a_color0)"))
        requireAll("post.sh", listOf("vec2(_glCoord.x, 1.0 - _glCoord.y)", "dx12mcTexture2DLevel("))
        for (name in listOf("fs_post_box_blur.sc", "fs_post_entity_sobel.sc", "fs_post_outline_blur.sc",
                "fs_post_blit.sc", "fs_post_invert.sc", "fs_post_color_convolve.sc", "fs_post_bits.sc",
                "fs_post_spider.sc")) {
            if (shaderSourceDir.file(name).asFile.readText().contains("discard")) {
                throw GradleException("$name must not discard")
            }
        }
        val fragments = listOf(
            "fs_position_color.sc", "fs_position_color_alpha.sc", "fs_position_color_tex.sc",
            "fs_position_color_tex_cutout.sc", "fs_entity.sc", "fs_entity_cutout.sc")
        for (name in fragments) {
            requireAll(name, listOf("uniform vec4 u_fogParams;", "uniform vec4 u_fogColor;", "dx12mcApplyFog("))
            val discards = shaderSourceDir.file(name).asFile.readText().contains("discard")
            val alphaVariant = name.endsWith("_cutout.sc") || name.endsWith("_alpha.sc")
            if (discards != alphaVariant) {
                throw GradleException("$name: only alpha-test variants may discard, so opaque programs keep early depth")
            }
            if (alphaVariant) requireAll(name, listOf("uniform vec4 u_material;", "< u_material.x) discard;"))
        }
        requireAll("fs_logic_invert.sc", listOf("\$input v_color0, v_fog", "dx12mcOutput(vec4_splat(1.0));"))
        if (shaderSourceDir.file("fs_logic_invert.sc").asFile.readText().contains("discard")) {
            throw GradleException("fs_logic_invert.sc: only alpha-test variants may discard")
        }
        requireAll("fs_logic_invert_alpha.sc", listOf(
            "\$input v_color0, v_fog", "uniform vec4 u_material;", "< u_material.x) discard;",
            "dx12mcOutput(vec4_splat(1.0));"))
        for (name in listOf("fs_position_color_tex.sc", "fs_position_color_tex_cutout.sc", "fs_entity.sc", "fs_entity_cutout.sc")) {
            requireAll(name, listOf("SAMPLER2D(s_texColor, 0)", "dx12mcTexture2DMaxLod(s_texColor, v_texcoord0, u_texLod.x)"))
        }
        for (name in listOf("fs_entity.sc", "fs_entity_cutout.sc")) {
            requireAll(name, listOf("color.rgb = lerp(v_overlay.rgb, color.rgb, v_overlay.a);", "color * v_light"))
        }
    }
}

val compileD3D12Shaders by tasks.registering {
    group = "dx12mc verification"
    description = "Compiles every DX12MC D3D12 shader and reports all shader failures together."
    dependsOn(prepareShaderc, validateD3D12ShaderSources)
    inputs.property("shadercSha256", findProperty("shaderc_sha256_$lwjglVersion") as String?)
    inputs.property("shadercProfile", shadercProfile)
    inputs.property("shadercOptimization", shadercOptimization)
    for (spec in shaderSpecs.distinctBy { it.source }) {
        inputs.file(shaderSourceDir.file("${spec.source}.sc"))
    }
    inputs.file(shaderSourceDir.file("varying.def.sc"))
    inputs.file(shaderSourceDir.file("common.sh"))
    inputs.file(shaderSourceDir.file("texture.sh"))
    inputs.file(shaderSourceDir.file("post.sh"))
    outputs.dir(generatedShaderDir)
    doLast {
        val varyingFile = shaderSourceDir.file("varying.def.sc").asFile
        val outputDir = generatedShaderDir.get().asFile
        outputDir.mkdirs()
        outputDir.listFiles { file -> file.extension == "bin" && shaderSpecs.none { it.name == file.nameWithoutExtension } }
            ?.forEach { it.delete() }
        val failures = mutableListOf<String>()
        for (spec in shaderSpecs) {
            val inputFile = shaderSourceDir.file("${spec.source}.sc").asFile
            val outputFile = File(outputDir, "${spec.name}.bin")
            outputFile.delete()
            val command = listOf(
                shadercCache.absolutePath,
                "-f", inputFile.absolutePath,
                "-o", outputFile.absolutePath,
                "--type", spec.type,
                "--platform", "windows",
                "-p", shadercProfile,
                "--varyingdef", varyingFile.absolutePath,
                "-i", shaderSourceDir.asFile.absolutePath,
                "-O", shadercOptimization
            ) + (spec.defines?.let { listOf("--define", it) } ?: emptyList())
            logger.lifecycle("DX12MC shaderc: ${spec.name}")
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
    implementation(project(":api"))
    include(project(":api"))
    implementation("org.lwjgl:lwjgl-bgfx:$lwjglVersion")
    include("org.lwjgl:lwjgl-bgfx:$lwjglVersion")
    runtimeOnly("org.lwjgl:lwjgl-bgfx:$lwjglVersion:natives-windows")
    bgfxWindowsNatives("org.lwjgl:lwjgl-bgfx:$lwjglVersion:natives-windows")
    javaFormatter("com.google.googlejavaformat:google-java-format:${prop("google_java_format_version")}")
    testImplementation(platform("org.junit:junit-bom:${prop("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
    withJavadocJar()
}

loom {
    runs {
        named("client") {
            client()
            val mode = providers.systemProperty("dx12mc.mode").orElse(prop("run_mode"))
            val clear = providers.systemProperty("dx12mc.clear").orElse(prop("run_clear"))
            val overlay = providers.systemProperty("dx12mc.overlay").orElse(prop("run_overlay"))
            vmArg("-Ddx12mc.mode=${mode.get()}")
            vmArg("-Ddx12mc.clear=${clear.get()}")
            vmArg("-Ddx12mc.overlay=${overlay.get()}")
        }
        create("harness") {
            client()
            configName = "DX12MC flight harness"
            runDir = "build/harness-run"
            source(sourceSets["harness"])
            vmArg("-Ddx12mc.harness=flight")
            vmArg("-Ddx12mc.mode=" + (findProperty("harnessMode") as String? ?: "native"))
            (findProperty("harnessJvm") as String?)?.split(Regex("[ ,]"))?.filter { it.isNotEmpty() }?.forEach { vmArg(it) }
            programArgs("--width", "1920", "--height", "1009")
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("apiVersion", prop("api_version"))
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "apiVersion" to prop("api_version"))
    }
    from({ bgfxWindowsNatives.map { zipTree(it) } }) {
        include("**/*.dll")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val installedShaderRoot = layout.buildDirectory.dir("generated/dx12mc-installed-resources")
val installD3D12Shaders by tasks.registering(Sync::class) {
    group = "dx12mc verification"
    description = "Stages verified D3D12 shader binaries for inclusion in the distributable JAR."
    dependsOn(verifyD3D12Shaders)
    from(generatedShaderRoot)
    into(installedShaderRoot)
}

sourceSets.main {
    output.dir(mapOf("builtBy" to installD3D12Shaders), installedShaderRoot)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

fun javaSourceFiles() = files(fileTree("src"), fileTree("api/src"), fileTree("examples")).asFileTree.matching {
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

fun javaFormatArgumentFile(name: String): String {
    val file = layout.buildDirectory.file("tmp/java-format/$name.args").get().asFile
    file.parentFile.mkdirs()
    file.writeText(javaSourceFiles().joinToString("\n", postfix = "\n") { it.absolutePath })
    return "@" + file.absolutePath
}

tasks.register<JavaExec>("formatJava") {
    group = "formatting"
    description = "Formats Java source files with google-java-format."
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    maxHeapSize = formatterMaxHeap
    jvmArgs(googleJavaFormatJvmArgs)
    args("--replace")
    doFirst { args(javaFormatArgumentFile("format")) }
}

tasks.register<JavaExec>("checkJavaFormat") {
    group = "verification"
    description = "Checks Java source formatting with google-java-format."
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    maxHeapSize = formatterMaxHeap
    jvmArgs(googleJavaFormatJvmArgs)
    args("--dry-run", "--set-exit-if-changed")
    doFirst { args(javaFormatArgumentFile("check")) }
}

tasks.test {
    useJUnitPlatform()
    dependsOn("compileTestShaderPacks")
    systemProperty("dx12mc.testPacks", layout.buildDirectory.dir("test-packs").get().asFile.absolutePath)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.register<JavaExec>("cpuBench") {
    group = "verification"
    description = "Times the CPU-side mesh encoding, packing and region assembly paths."
    dependsOn("testClasses")
    classpath = files(sourceSets["test"].runtimeClasspath)
    mainClass.set("net.dx12mc.bridge.mesh.CpuBench")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("renderBench") {
    group = "verification"
    description = "Measures bgfx D3D12 submission cost on the local GPU with synthetic terrain meshes."
    dependsOn(verifyD3D12Shaders, "testClasses", "compileTestShaderPacks")
    classpath = files(sourceSets["test"].runtimeClasspath, generatedShaderRoot)
    mainClass.set("net.dx12mc.bridge.RenderBench")
    systemProperty("dx12mc.testPacks", layout.buildDirectory.dir("test-packs").get().asFile.absolutePath)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs(providers.gradleProperty("benchJvm").orElse("").get().split(" ").filter { it.isNotEmpty() })
    args(providers.gradleProperty("benchArgs").orElse("").get().split(" ").filter { it.isNotEmpty() })
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
    archiveFileName.set("${prop("archives_base_name")}-${project.version}.jar")
}

extra["signing.gnupg.keyName"] = signingKeyName ?: signingKeyCandidates.first()

fun hasSigningKey(): Boolean = signingKeyName != null

val requireSigning = providers.gradleProperty("dx12mc.requireSigning").map { it.toBoolean() }.orElse(false).get()
if (requireSigning && signingKeyName == null &&
    gradle.startParameter.taskNames.any { it.contains("publish", ignoreCase = true) }) {
    throw GradleException(
        "dx12mc.requireSigning is set but gpg has none of the signing keys: ${signingKeyCandidates.joinToString()}")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = prop("archives_base_name")
            from(components["java"])
            pom {
                name.set(prop("pom_name"))
                description.set(prop("pom_description"))
                url.set("$githubUrl/${githubRepository.get()}")
                licenses {
                    license {
                        name.set(prop("license_name"))
                        url.set(prop("license_url"))
                    }
                }
                developers {
                    developer {
                        id.set(prop("developer_id"))
                        name.set(prop("developer_name"))
                        email.set(prop("developer_email"))
                        roles.add("maintainer")
                    }
                }
                scm {
                    connection.set("scm:git:$githubUrl/${githubRepository.get()}.git")
                    developerConnection.set("scm:git:ssh://git@${githubUrl.removePrefix("https://")}/${githubRepository.get()}.git")
                    url.set("$githubUrl/${githubRepository.get()}")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("${prop("github_packages_url")}/${githubRepository.get()}")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse(prop("developer_id"))
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
