package net.dx12mc.bridge.shader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Options;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ShaderSemanticsTest {
  private static final String CORE = "assets/minecraft/shaders/core/";
  private static final Pattern ALPHA_TEST =
      Pattern.compile("if \\(color\\.a (<|==) ([A-Za-z0-9.]+)\\)");

  @Test
  void tableCoversExactlyTheVanillaCorePrograms() throws Exception {
    assertEquals(
        vanillaPrograms().keySet(), new java.util.TreeSet<>(ShaderSemantics.VANILLA.keySet()));
  }

  @TestFactory
  Stream<DynamicTest> everyVanillaProgramMatchesItsShaderSource() throws Exception {
    return vanillaPrograms().entrySet().stream()
        .map(
            program ->
                DynamicTest.dynamicTest(
                    program.getKey(),
                    () ->
                        assertEquals(
                            derive(
                                program.getValue()[0],
                                program.getValue()[1],
                                program.getValue()[2]),
                            ShaderSemantics.VANILLA.get(program.getKey()))));
  }

  @Test
  void unknownProgramsFallBackByName() {
    assertEquals(
        ShaderSemantics.AlphaTest.TENTH, ShaderSemantics.of("mymod:fancy_cutout").alphaTest());
    assertFalse(ShaderSemantics.of("mymod:fancy").discards());
    assertSame(
        ShaderSemantics.VANILLA.get("rendertype_cutout_mipped"),
        ShaderSemantics.of("minecraft:rendertype_cutout_mipped"));
  }

  @Test
  void endPortalAndGatewayShareOneShaderAndDifferOnlyInLayerCount() {
    ShaderSemantics portal = ShaderSemantics.of("rendertype_end_portal");
    ShaderSemantics gateway = ShaderSemantics.of("rendertype_end_gateway");
    assertTrue(portal.endPortal());
    assertTrue(gateway.endPortal());
    assertEquals(15, portal.endPortalLayers());
    assertEquals(16, gateway.endPortalLayers());
    assertEquals(ShaderSemantics.FogMode.NONE, portal.fogMode());
    assertFalse(portal.discards());
    assertFalse(ShaderSemantics.of("rendertype_solid").endPortal());
    assertTrue(ShaderSemantics.isVanilla("rendertype_end_gateway"));
  }

  @Test
  void cutoutMippedUsesTheVanillaHalfThreshold() {
    assertEquals(0.5f, ShaderSemantics.of("rendertype_cutout_mipped").alphaTest().threshold());
    assertEquals(0.1f, ShaderSemantics.of("rendertype_cutout").alphaTest().threshold());
  }

  static ShaderSemantics derive(String vertex, String fragment, String json) {
    Matcher matcher = ALPHA_TEST.matcher(fragment);
    ShaderSemantics.AlphaTest alpha = ShaderSemantics.AlphaTest.NONE;
    boolean testsTexture = false;
    if (matcher.find()) {
      alpha =
          switch (matcher.group(2)) {
            case "0.0" -> ShaderSemantics.AlphaTest.ZERO;
            case "0.1" -> ShaderSemantics.AlphaTest.TENTH;
            case "0.5" -> ShaderSemantics.AlphaTest.HALF;
            case "vertexColor.a" -> ShaderSemantics.AlphaTest.VERTEX_ALPHA;
            default -> throw new AssertionError("unknown alpha test " + matcher.group());
          };
      String before = fragment.substring(0, matcher.start());
      String assignment = before.substring(before.lastIndexOf("vec4 color ="));
      assignment = assignment.substring(0, assignment.indexOf(';'));
      testsTexture = assignment.contains("texture(") && !assignment.contains("*");
    }
    ShaderSemantics.FogMode fog = ShaderSemantics.FogMode.NONE;
    if (fragment.contains("linear_fog(")) fog = ShaderSemantics.FogMode.LINEAR;
    else if (fragment.contains("linear_fog_fade")) {
      fog =
          fragment.contains("color.rgb * fade")
              ? ShaderSemantics.FogMode.FADE_RGB
              : ShaderSemantics.FogMode.FADE;
    }
    ShaderSemantics.FogSpace space = ShaderSemantics.FogSpace.POSITION;
    if (fragment.contains("gl_FragCoord.z")) space = ShaderSemantics.FogSpace.VIEW_DEPTH;
    else if (vertex.contains("length((ModelViewMat")) space = ShaderSemantics.FogSpace.VIEW_SPHERE;
    else if (vertex.contains("fog_distance(pos.xyz")) space = ShaderSemantics.FogSpace.VIEW;
    ShaderSemantics.Lightmap lightmap = ShaderSemantics.Lightmap.NONE;
    if (vertex.contains("minecraft_sample_lightmap(Sampler2, UV2)")) {
      lightmap = ShaderSemantics.Lightmap.FILTERED;
    } else if (vertex.contains("texelFetch(Sampler2, UV2 / 16, 0)")) {
      lightmap = ShaderSemantics.Lightmap.FETCH;
    }
    return new ShaderSemantics(
        alpha,
        testsTexture,
        fog,
        space,
        lightmap,
        vertex.contains("minecraft_mix_light("),
        vertex.contains("texelFetch(Sampler1, UV1, 0)") && fragment.contains("overlayColor"),
        fragment.contains(".rrrr"),
        fragment.contains("GlintAlpha"),
        vertex.contains("uniform float LineWidth;"),
        endPortalLayers(fragment, json));
  }

  private static int endPortalLayers(String fragment, String json) {
    if (!fragment.contains("uniform int EndPortalLayers;")) return 0;
    Matcher layers =
        Pattern.compile("\"EndPortalLayers\".*?\"values\": [\\[] *([0-9]+)").matcher(json);
    assertTrue(layers.find(), "EndPortalLayers value in the program json");
    return Integer.parseInt(layers.group(1));
  }

  private static Map<String, String[]> vanillaPrograms() throws Exception {
    Map<String, String[]> programs = new TreeMap<>();
    Path jar = Path.of(Options.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (JarFile file = new JarFile(jar.toFile())) {
      Enumeration<JarEntry> entries = file.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        if (!name.startsWith(CORE) || !name.endsWith(".json")) continue;
        String program = name.substring(CORE.length(), name.length() - 5);
        String json = read(file, name);
        String vertex = shaderFile(json, "vertex");
        String fragment = shaderFile(json, "fragment");
        programs.put(
            program,
            new String[] {
              read(file, CORE + vertex + ".vsh"), read(file, CORE + fragment + ".fsh"), json
            });
      }
    }
    return programs;
  }

  private static String shaderFile(String json, String stage) {
    Matcher matcher = Pattern.compile("\"" + stage + "\": \"([a-z_0-9]+)\"").matcher(json);
    assertTrue(matcher.find(), stage + " in the program json");
    return matcher.group(1);
  }

  private static String read(JarFile file, String name) throws IOException {
    try (InputStream input = file.getInputStream(file.getEntry(name))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
