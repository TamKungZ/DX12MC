import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

val packLwjglVersion = property("lwjgl_version") as String
val packShadercProfile = property("shaderc_profile") as String
val packContract = (property("shader_contract") as String).toInt()
val packProfileName = property("shader_pack_profile") as String
val packShaderc = File(gradle.gradleUserHomeDir, "caches/dx12mc/bgfx-$packLwjglVersion/windows-x64/shaderc.exe")
val packInclude = file("tools/shaderpack/include")
val packShaderPath = Regex("assets/[a-z0-9_.-]+/dx12mc/shaders/.*")

fun dxsh(bgfx: ByteArray): ByteArray {
    if (bgfx.size < 4 || bgfx[1] != 'S'.code.toByte() || bgfx[2] != 'H'.code.toByte()) {
        throw GradleException("shaderc output is not a bgfx shader")
    }
    val profile = packProfileName.toByteArray(Charsets.US_ASCII)
    val out = ByteBuffer.allocate(4 + 2 + 2 + 1 + 1 + 1 + profile.size + 4 + bgfx.size).order(ByteOrder.LITTLE_ENDIAN)
    out.put("DXSH".toByteArray(Charsets.US_ASCII)).putShort(1).putShort(packContract.toShort())
    out.put(bgfx[0]).put(bgfx[3]).put(profile.size.toByte()).put(profile)
    out.putInt(bgfx.size).put(bgfx)
    return out.array()
}

fun compileShaderPack(source: File, output: File) {
    if (!File(source, "pack.mcmeta").isFile) throw GradleException("$source has no pack.mcmeta")
    output.deleteRecursively()
    output.mkdirs()
    val temp = File(output.parentFile, "${output.name}.tmp")
    temp.mkdirs()
    val failures = mutableListOf<String>()
    source.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
        val relative = file.relativeTo(source).invariantSeparatorsPath
        val shaderDir = packShaderPath.matches(relative)
        val stage = when {
            !shaderDir || !file.name.endsWith(".sc") -> null
            file.name.startsWith("vs_") -> "vertex"
            file.name.startsWith("fs_") -> "fragment"
            else -> null
        }
        when {
            stage != null -> {
                val compiled = File(temp, file.nameWithoutExtension + ".bin")
                compiled.delete()
                val varyings = File(file.parentFile, "varying.def.sc").takeIf { it.isFile } ?: File(packInclude, "varying.def.sc")
                val command = listOf(
                    packShaderc.absolutePath, "-f", file.absolutePath, "-o", compiled.absolutePath,
                    "--type", stage, "--platform", "windows", "-p", packShadercProfile,
                    "--varyingdef", varyings.absolutePath,
                    "-i", packInclude.absolutePath, "-i", file.parentFile.absolutePath,
                    "-O", "3", "--define", "DX12MC_DEFINE_SPLAT"
                )
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val log = process.inputStream.bufferedReader().use { it.readText() }
                if (process.waitFor() != 0 || !compiled.isFile) {
                    failures += "--- $relative ---\n${log.trimEnd()}"
                } else {
                    val target = File(output, relative.removeSuffix(".sc") + ".dxsh")
                    target.parentFile.mkdirs()
                    target.writeBytes(dxsh(compiled.readBytes()))
                }
            }
            shaderDir && (file.name.endsWith(".sh") || file.name.endsWith(".sc")) -> {}
            else -> {
                val target = File(output, relative)
                target.parentFile.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
    }
    temp.deleteRecursively()
    if (failures.isNotEmpty()) {
        throw GradleException("${failures.size} pack shader(s) failed to compile:\n\n" + failures.joinToString("\n\n"))
    }
}

fun zipDirectory(directory: File, zip: File) {
    zip.parentFile.mkdirs()
    ZipOutputStream(zip.outputStream()).use { out ->
        directory.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
            val entry = ZipEntry(file.relativeTo(directory).invariantSeparatorsPath)
            entry.time = 315532800000L
            out.putNextEntry(entry)
            file.inputStream().use { it.copyTo(out) }
            out.closeEntry()
        }
    }
}

tasks.register("compileShaderPack") {
    group = "dx12mc shader packs"
    description = "Compiles a shader pack (-PpackDir=<dir>) with the pinned shaderc into build/shaderpacks."
    dependsOn("prepareShaderc")
    doLast {
        val source = file(findProperty("packDir") as String? ?: throw GradleException("Set -PpackDir=<pack source directory>"))
        val output = layout.buildDirectory.dir("shaderpacks/${source.name}").get().asFile
        compileShaderPack(source, output)
        val zip = File(output.parentFile, "${source.name}.zip")
        zipDirectory(output, zip)
        logger.lifecycle("DX12MC shader pack written to $zip")
    }
}

val testPackSources = file("src/test/packs")
val testPackOutput = layout.buildDirectory.dir("test-packs")
tasks.register("compileTestShaderPacks") {
    group = "dx12mc shader packs"
    description = "Compiles the renderBench test packs and the sample pack."
    dependsOn("prepareShaderc")
    inputs.dir(testPackSources)
    inputs.dir(file("examples/sample-pack"))
    inputs.dir(packInclude)
    inputs.property("shadercSha256", findProperty("shaderc_sha256_$packLwjglVersion") as String?)
    outputs.dir(testPackOutput)
    doLast {
        val root = testPackOutput.get().asFile
        root.deleteRecursively()
        testPackSources.listFiles { file -> file.isDirectory }?.sortedBy { it.name }?.forEach {
            compileShaderPack(it, File(root, it.name))
        }
        compileShaderPack(file("examples/sample-pack"), File(root, "sample-pack"))
    }
}
