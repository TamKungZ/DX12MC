package net.dx12mc.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PackPlanTest {
  private static final String LINE = "1.21.1";

  private static byte[] dxsh(String builtIn) {
    return ShaderContainer.write(
        ShaderBinaryTest.builtIn(builtIn), ShaderContract.VERSION, ShaderContract.PROFILE);
  }

  private static PackPlan plan(String json, Map<String, byte[]> shaders) {
    return PackPlan.validate(
        new PackInput("test", "file/test", PackManifest.parse(json), shaders, Map.of()),
        ShaderBinary.parse(ShaderBinaryTest.builtIn("vs_post")),
        LINE);
  }

  private static String message(String json, Map<String, byte[]> shaders) {
    return assertThrows(InvalidPackException.class, () -> plan(json, shaders)).getMessage();
  }

  private static Map<String, byte[]> files(String... pairs) {
    Map<String, byte[]> files = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) files.put(pairs[i], dxsh(pairs[i + 1]));
    return files;
  }

  private static final String SOLID =
      """
      {"format": 1, "contract": 1,
       "programs": {"lit": {"vertex": "v.dxsh", "fragment": "f.dxsh"}},
       "overrides": {"rendertype_solid": "lit"}}
      """;

  @Test
  void aBlockOverrideBuiltFromContractUniformsIsAccepted() {
    PackPlan plan =
        plan(
            SOLID,
            files("v.dxsh", "vs_position_color_tex_light", "f.dxsh", "fs_position_color_tex"));
    PackPlan.Program program = plan.programs().get("lit");
    assertEquals(ShaderContract.Family.BLOCK, program.family());
    assertEquals(0, program.samplers().get("s_texColor"));
    assertEquals(2, program.samplers().get("s_lightmap"));
    assertFalse(plan.needsScene());
  }

  @Test
  void anOverrideWhoseVertexShaderReadsEntityAttributesIsRejected() {
    assertTrue(
        message(SOLID, files("v.dxsh", "vs_entity", "f.dxsh", "fs_entity"))
            .contains("reads attribute 0x2 that block vertices do not have"));
  }

  @Test
  void mismatchedVaryingsAreRejectedBeforeBgfxLinksThem() {
    assertTrue(
        message(SOLID, files("v.dxsh", "vs_position_color_tex_light", "f.dxsh", "fs_post_blit"))
            .contains("fragment inputs do not match the vertex outputs"));
  }

  @Test
  void shaderFilesInTheWrongStageOrMissingAreRejected() {
    assertTrue(
        message(SOLID, files("v.dxsh", "fs_position_color_tex", "f.dxsh", "fs_position_color_tex"))
            .contains("is not a vertex shader"));
    assertTrue(
        message(SOLID, files("v.dxsh", "vs_position_color_tex_light"))
            .contains("missing shader file f.dxsh"));
  }

  @Test
  void onlyContractProgramsCanBeOverridden() {
    String json = SOLID.replace("rendertype_solid", "rendertype_gui");
    assertTrue(
        message(
                json,
                files("v.dxsh", "vs_position_color_tex_light", "f.dxsh", "fs_position_color_tex"))
            .contains("rendertype_gui cannot be overridden in shader contract 1"));
  }

  @Test
  void undeclaredUniformsAndReservedNamesAreRejected() {
    String pass =
        """
        {"format": 1, "contract": 1,
         "programs": {"copy": {"fragment": "f.dxsh"}},
         "targets": {"half": {"format": "rgba8", "scale": 0.5}},
         "passes": [{"stage": "before_gui", "program": "copy",
                     "inputs": {"s_post0": "scene:color"}, "output": "target:half"}]}
        """;
    assertTrue(
        message(pass, files("f.dxsh", "fs_post_blit"))
            .contains("uniform u_postA is neither in the contract nor declared in pack.json"));
    String reserved = pass.replace("\"targets\"", "\"uniforms\": {\"u_postA\": {}}, \"targets\"");
    assertTrue(message(reserved, files("f.dxsh", "fs_post_blit")).contains("u_postA is reserved"));
  }

  @Test
  void bgfxPredefinedUniformsAreRejectedBecauseDx12mcNeverSetsThem() {
    assertTrue(
        assertThrows(InvalidPackException.class, () -> PackDirectory.plan("predefined"))
            .getMessage()
            .contains("u_viewProj is a bgfx predefined uniform"));
    String json =
        "{\"format\": 1, \"contract\": 1, \"uniforms\": {\"u_invViewProj\": {\"type\": \"mat4\"}}}";
    assertTrue(message(json, Map.of()).contains("u_invViewProj is reserved"));
  }

  @Test
  void passSourcesMustBeDeclaredAndMustNotFeedBack() {
    String json =
        """
        {"format": 1, "contract": 1,
         "programs": {"p": {"fragment": "f.dxsh"}},
         "targets": {"a": {}},
         "passes": [{"stage": "after_world", "program": "p",
                     "inputs": {"s_x": "SOURCE"}, "output": "OUTPUT"}]}
        """;
    Map<String, byte[]> files = files("f.dxsh", "fs_post_blit");
    assertTrue(
        message(json.replace("SOURCE", "scene:depth").replace("OUTPUT", "target:a"), files)
            .contains("scene.depth is not true"));
    assertTrue(
        message(json.replace("SOURCE", "target:a").replace("OUTPUT", "target:a"), files)
            .contains("reads the target it writes"));
    assertTrue(
        message(json.replace("SOURCE", "target:b").replace("OUTPUT", "target:a"), files)
            .contains("not a scene, target or texture source"));
    assertTrue(
        message(json.replace("SOURCE", "scene:color").replace("OUTPUT", "target:zz"), files)
            .contains("is not scene:color or a declared target"));
  }

  @Test
  void manifestErrorsNameTheProblem() {
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () -> PackManifest.parse("{\"format\": 1, \"contract\": 2}"))
            .getMessage()
            .contains("shader contract 2"));
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () -> PackManifest.parse("{\"format\": 1, \"contract\": 1, \"shaders\": {}}"))
            .getMessage()
            .contains("unknown key shaders"));
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () ->
                    PackManifest.parse(
                        "{\"format\": 1, \"contract\": 1, \"programs\": {\"p\": {\"fragment\":"
                            + " \"../x.dxsh\"}}}"))
            .getMessage()
            .contains("not a plain relative path"));
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () ->
                    PackManifest.parse(
                        "{\"format\": 1, \"contract\": 1, \"targets\": {\"t\": {\"scale\": 4}}}"))
            .getMessage()
            .contains("scale must be between"));
    assertThrows(InvalidPackException.class, () -> PackManifest.parse("not json"));
  }

  @Test
  void perLineVariantsReplaceTheDefaultFiles() {
    PackManifest manifest =
        PackManifest.parse(
            """
            {"format": 1, "contract": 1, "programs": {"p": {"vertex": "a.dxsh", "fragment": "b.dxsh",
              "variants": {"1.20.1": {"vertex": "c.dxsh", "fragment": "d.dxsh"}}}}}
            """);
    assertEquals("a.dxsh", manifest.programs().get("p").forLine("1.21.1").vertex());
    assertEquals("d.dxsh", manifest.programs().get("p").forLine("1.20.1").fragment());
  }

  @Test
  void vramIsCountedForTargetsSceneAndGbuffer() {
    String json =
        """
        {"format": 1, "contract": 1,
         "targets": {"bloom": {"format": "rgba16f", "scale": 0.5}},
         "scene": {"gbuffer": [{"name": "normal", "format": "rgba8"}], "depth": true}}
        """;
    PackPlan plan = plan(json, Map.of());
    assertTrue(plan.needsScene());
    long expected = 960L * 540 * 8 + 1920L * 1080 * 8 + 1920L * 1080 * 4;
    assertEquals(expected, plan.vramBytes(1920, 1080));
  }
}
