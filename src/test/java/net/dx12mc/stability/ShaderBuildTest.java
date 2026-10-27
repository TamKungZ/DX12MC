package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShaderBuildTest {
  @Test
  void compileTaskRerunsWhenTheCompilerOrItsSettingsChange() throws IOException {
    String compile = task("val compileD3D12Shaders");
    assertTrue(compile.contains("inputs.property(\"shadercSha256\""));
    assertTrue(compile.contains("shaderc_sha256_$lwjglVersion"));
    assertTrue(compile.contains("inputs.property(\"shadercProfile\", shadercProfile)"));
    assertTrue(compile.contains("inputs.property(\"shadercOptimization\", shadercOptimization)"));
  }

  @Test
  void compiledShadersAreOnTheMainOutputSoTheDevRunLoadsThem() throws IOException {
    String build = build();
    assertTrue(
        build.contains(
            "sourceSets.main {\n    output.dir(mapOf(\"builtBy\" to installD3D12Shaders),"
                + " installedShaderRoot)\n}"));
    assertFalse(build.contains("tasks.jar {"));
  }

  private static String task(String declaration) throws IOException {
    String build = build();
    int start = build.indexOf(declaration);
    assertTrue(start >= 0, declaration);
    int end = build.indexOf("\nval ", start + 1);
    return build.substring(start, end < 0 ? build.length() : end);
  }

  private static String build() throws IOException {
    return Files.readString(Path.of("build.gradle.kts")).replace("\r\n", "\n");
  }
}
