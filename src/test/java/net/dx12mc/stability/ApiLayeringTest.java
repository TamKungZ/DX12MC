package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ApiLayeringTest {
  private static final Path API = Path.of("api/src/main/java/net/dx12mc/api");

  @Test
  void apiSourcesImportOnlyJavaAndTheApiPackage() throws IOException {
    for (Path file : javaFiles(API)) {
      String source = Files.readString(file);
      assertTrue(source.startsWith("package net.dx12mc.api;"), file.toString());
      for (String line : source.split("\n")) {
        if (!line.startsWith("import ")) continue;
        String imported = line.substring(7).replace("static ", "");
        assertTrue(
            imported.startsWith("java.") || imported.startsWith("net.dx12mc.api."),
            file + " imports " + imported);
      }
      assertFalse(source.contains("net.minecraft"), file.toString());
      assertFalse(source.contains("org.lwjgl"), file.toString());
      assertFalse(source.contains("bgfx_"), file.toString());
    }
  }

  @Test
  void apiBytecodeReferencesNoInternalMinecraftOrBgfxType() throws IOException {
    Path classes = Path.of("api/build/classes/java/main/net/dx12mc/api");
    if (!Files.isDirectory(classes)) return;
    for (Path file : files(classes, ".class")) {
      String constants = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
      for (String forbidden :
          List.of("net/minecraft/", "org/lwjgl/", "com/mojang/", "net/fabricmc/")) {
        assertFalse(constants.contains(forbidden), file + " references " + forbidden);
      }
      int index = 0;
      while ((index = constants.indexOf("net/dx12mc/", index)) >= 0) {
        assertTrue(
            constants.startsWith("net/dx12mc/api/", index), file + " references an internal type");
        index++;
      }
    }
  }

  @Test
  void internalsAddNothingToTheApiPackage() throws IOException {
    assertFalse(Files.exists(Path.of("src/main/java/net/dx12mc/api")));
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      long declaring =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .filter(path -> read(path).startsWith("package net.dx12mc.api"))
              .count();
      assertEquals(0, declaring);
    }
  }

  @Test
  void theModBundlesTheApiAndPublishesItsVersion() throws IOException {
    String build = Files.readString(Path.of("build.gradle.kts"));
    assertTrue(build.contains("implementation(project(\":api\"))"));
    assertTrue(build.contains("include(project(\":api\"))"));
    String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
    assertTrue(metadata.contains("\"dx12mc:api\": \"${apiVersion}\""));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    return files(root, ".java");
  }

  private static List<Path> files(Path root, String suffix) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(suffix)).sorted().toList();
    }
  }
}
