package net.dx12mc.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dx12mc.bridge.shader.ShaderSemantics;
import org.junit.jupiter.api.Test;

final class ShaderContractSyncTest {
  private static final Pattern ROW =
      Pattern.compile("^\\| `([a-z_A-Z0-9]+)` \\| ([^|]+) \\|(?: ([^|]+) \\|)?");

  @Test
  void theDocumentListsExactlyTheOverridablePrograms() throws IOException {
    Map<String, String> documented = new LinkedHashMap<>();
    for (String[] row : table("### Overridable programs")) documented.put(row[0], row[1]);
    Map<String, String> actual = new LinkedHashMap<>();
    ShaderContract.PROGRAMS.forEach(
        (name, family) -> actual.put(name, family.name().toLowerCase(Locale.ROOT)));
    assertEquals(actual, documented);
  }

  @Test
  void theDocumentListsExactlyTheContractUniforms() throws IOException {
    List<String> documented = new ArrayList<>();
    for (String[] row : table("### Uniforms")) documented.add(row[0] + " " + row[1] + " " + row[2]);
    List<String> actual = new ArrayList<>();
    for (ShaderContract.UniformSpec spec : ShaderContract.UNIFORMS) {
      actual.add(spec.name() + " " + spec.type() + " " + scope(spec.scope()));
    }
    assertEquals(actual, documented);
  }

  @Test
  void theDocumentListsExactlyTheContractSamplers() throws IOException {
    List<String> documented = new ArrayList<>();
    for (String[] row : table("### Samplers")) documented.add(row[0] + " " + row[1] + " " + row[2]);
    List<String> actual = new ArrayList<>();
    for (ShaderContract.SamplerSpec spec : ShaderContract.SAMPLERS) {
      actual.add(spec.name() + " " + spec.slot() + " " + scope(spec.scope()));
    }
    assertEquals(actual, documented);
  }

  @Test
  void theDocumentListsExactlyTheStages() throws IOException {
    List<String> documented = new ArrayList<>();
    for (String[] row : table("Stages:")) documented.add(row[0]);
    assertEquals(ShaderContract.STAGES, documented);
  }

  @Test
  void thePackHeaderDeclaresTheContractUniformsAndSlots() throws IOException {
    String header = Files.readString(Path.of("tools/shaderpack/include/dx12mc.sh"));
    List<String> declared = new ArrayList<>();
    Matcher uniforms = Pattern.compile("(?m)^uniform (vec4|mat4) (u_\\w+);").matcher(header);
    while (uniforms.find()) declared.add(uniforms.group(2) + " " + uniforms.group(1));
    List<String> expected = new ArrayList<>();
    for (ShaderContract.UniformSpec spec : ShaderContract.UNIFORMS)
      expected.add(spec.name() + " " + spec.type());
    assertEquals(expected, declared);
    for (ShaderContract.SamplerSpec spec : ShaderContract.SAMPLERS) {
      String macro = "DX12MC_SLOT_" + spec.name().substring(2).toUpperCase(Locale.ROOT);
      assertTrue(header.contains("#define " + macro + " " + spec.slot()), macro);
    }
    for (String name : ShaderContract.BGFX_PREDEFINED) {
      assertFalse(header.contains(" " + name + ";"), name + " is a bgfx predefined uniform");
    }
  }

  @Test
  void versionsAgreeAcrossBuildDocumentAndCode() throws IOException {
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
      properties.load(reader);
    }
    assertEquals(String.valueOf(ShaderContract.VERSION), properties.getProperty("shader_contract"));
    assertEquals(ShaderContract.PROFILE, properties.getProperty("shader_pack_profile"));
    String document = Files.readString(Path.of("docs/API.md"));
    assertTrue(
        document.contains(
            "API version "
                + properties.getProperty("api_version")
                + ", shader contract "
                + ShaderContract.VERSION));
    assertTrue(document.contains("## Shader contract " + ShaderContract.VERSION));
    assertTrue(document.contains("profile `" + ShaderContract.PROFILE + "`"));
    assertTrue(
        document.contains("bgfx shader binary version " + ShaderContract.BGFX_SHADER_VERSION));
  }

  @Test
  void overridableProgramsMatchTheirVanillaSemantics() {
    ShaderContract.PROGRAMS.forEach(
        (name, family) -> {
          assertTrue(ShaderSemantics.isVanilla(name), name);
          ShaderSemantics semantics = ShaderSemantics.of(name);
          if (family == ShaderContract.Family.BLOCK) {
            assertEquals(ShaderSemantics.Lightmap.FILTERED, semantics.lightmap(), name);
            assertFalse(semantics.diffuseLighting(), name);
          } else {
            assertTrue(semantics.diffuseLighting(), name);
            assertEquals(ShaderSemantics.Lightmap.FETCH, semantics.lightmap(), name);
          }
        });
  }

  private static String scope(ShaderContract.Scope scope) {
    return switch (scope) {
      case DRAW -> "draw";
      case ENTITY_DRAW -> "entity draw";
      case FRAME -> "frame";
      case PASS -> "pass";
    };
  }

  private static List<String[]> table(String heading) throws IOException {
    List<String> lines = Files.readAllLines(Path.of("docs/API.md"));
    int start = lines.indexOf(heading);
    assertTrue(start >= 0, heading);
    List<String[]> rows = new ArrayList<>();
    boolean inTable = false;
    for (int i = start + 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.startsWith("|")) {
        inTable = true;
        Matcher row = ROW.matcher(line);
        if (row.find()) {
          rows.add(
              new String[] {
                row.group(1), row.group(2).trim(), row.group(3) == null ? "" : row.group(3).trim()
              });
        }
      } else if (inTable) {
        break;
      }
    }
    return rows;
  }
}
