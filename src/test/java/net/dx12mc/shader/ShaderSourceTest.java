package net.dx12mc.shader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;

class ShaderSourceTest {
  private static final Path ROOT = Path.of("src", "main", "shaders");
  private static final List<String> VERTEX =
      List.of(
          "vs_position_color.sc",
          "vs_position_color_light.sc",
          "vs_position_color_tex.sc",
          "vs_position_color_tex_light.sc",
          "vs_entity.sc");
  private static final List<String> FRAGMENT =
      List.of(
          "fs_position_color.sc",
          "fs_position_color_alpha.sc",
          "fs_position_color_tex.sc",
          "fs_position_color_tex_cutout.sc",
          "fs_entity.sc",
          "fs_entity_cutout.sc");

  @Test
  void everyShaderGetsTheCommonShadercHelpers() throws IOException {
    for (String name : VERTEX) assertTrue(includesHelpers(read(name)), name);
    for (String name : FRAGMENT) assertTrue(includesHelpers(read(name)), name);
    assertTrue(read("texture.sh").contains("#include \"common.sh\""));
  }

  @Test
  void commonHeaderDefinesShadercFragmentSplats() throws IOException {
    String common = read("common.sh");
    assertTrue(common.contains("vec2 vec2_splat(float _x)"));
    assertTrue(common.contains("vec3 vec3_splat(float _x)"));
    assertTrue(common.contains("vec4 vec4_splat(float _x)"));
  }

  @Test
  void d3dSamplerUsesSeparateSamplerAndTextureResources() throws IOException {
    String texture = read("texture.sh");
    assertTrue(texture.contains("SamplerState m_sampler;"));
    assertTrue(texture.contains("Texture2D m_texture;"));
    assertTrue(texture.contains("m_texture.Sample(_sampler.m_sampler, _coord)"));
    assertTrue(texture.contains("CalculateLevelOfDetailUnclamped"));
    assertTrue(texture.contains("SampleLevel(_sampler.m_sampler, _coord, min(lod, _maxLod))"));
    assertFalse(texture.contains("uniform sampler2D"));
    assertFalse(texture.contains("SamplerState sampler;"));
    assertFalse(texture.contains("Texture2D texture;"));
  }

  @Test
  void lightAndLightmapMathMatchesVanillaLightGlsl() throws IOException {
    String vanilla = vanilla("include/light.glsl");
    String common = read("common.sh");
    assertTrue(vanilla.contains("#define MINECRAFT_LIGHT_POWER   (0.6)"));
    assertTrue(vanilla.contains("#define MINECRAFT_AMBIENT_LIGHT (0.4)"));
    assertTrue(common.contains("(light0 + light1) * 0.6 + 0.4"));
    assertTrue(
        vanilla.contains("clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))"),
        "vanilla terrain lightmap coordinate changed");
    assertTrue(
        common.contains("clamp(_uv2 / 256.0, vec2_splat(0.5 / 16.0), vec2_splat(15.5 / 16.0))"));
    String entity = vanilla("core/rendertype_entity_solid.vsh");
    assertTrue(entity.contains("texelFetch(Sampler2, UV2 / 16, 0)"));
    assertTrue(common.contains("(floor(_uv2 / 16.0) + vec2_splat(0.5)) / 16.0"));
    assertFalse(read("vs_position_color_tex_light.sc").contains("vec2_splat(8.0)"));
  }

  @Test
  void fogMathMatchesVanillaFogGlsl() throws IOException {
    String vanilla = vanilla("include/fog.glsl");
    String common = read("common.sh");
    assertTrue(vanilla.contains("smoothstep(fogStart, fogEnd, vertexDistance)"));
    assertTrue(common.contains("smoothstep(_params.x, _params.y, _distance)"));
    assertTrue(vanilla.contains("mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a)"));
    assertTrue(common.contains("lerp(_color.rgb, _fogColor.rgb, fogValue * _fogColor.a)"));
    assertTrue(vanilla.contains("smoothstep(fogEnd, fogStart, vertexDistance)"));
    assertTrue(common.contains("smoothstep(_params.y, _params.x, _distance)"));
    assertTrue(vanilla.contains("float distXZ = length(pos.xz);"));
    assertTrue(common.contains("max(length(_pos.xz), abs(_pos.y))"));
  }

  @Test
  void onlyAlphaTestVariantsDiscardSoOpaqueTerrainKeepsEarlyDepth() throws IOException {
    for (String name : FRAGMENT) {
      boolean alphaVariant = name.endsWith("_cutout.sc") || name.endsWith("_alpha.sc");
      assertEquals(alphaVariant, read(name).contains("discard"), name);
      if (alphaVariant) assertTrue(read(name).contains("< u_material.x) discard;"), name);
    }
  }

  @Test
  void entityShadersApplyDiffuseLightOverlayAndLightmapInVanillaOrder() throws IOException {
    String vertex = read("vs_entity.sc");
    assertTrue(vertex.contains("SAMPLER2D(s_overlay, 1)"));
    assertTrue(vertex.contains("SAMPLER2D(s_lightmap, 2)"));
    assertTrue(
        vertex.contains("dx12mcMixLight(u_lightDir0.xyz, u_lightDir1.xyz, normal, a_color0)"));
    for (String name : List.of("fs_entity.sc", "fs_entity_cutout.sc")) {
      String fragment = read(name);
      int multiply = fragment.indexOf("vec4 color = base * v_color0 * u_color;");
      int overlay = fragment.indexOf("color.rgb = lerp(v_overlay.rgb, color.rgb, v_overlay.a);");
      int lightmap = fragment.indexOf("color * v_light");
      assertTrue(multiply >= 0 && overlay > multiply && lightmap > overlay, name);
    }
  }

  private static boolean includesHelpers(String source) {
    return source.contains("#include \"common.sh\"") || source.contains("#include \"texture.sh\"");
  }

  private static String read(String name) throws IOException {
    return Files.readString(ROOT.resolve(name));
  }

  static String vanilla(String path) throws IOException {
    try (InputStream input =
        Options.class.getClassLoader().getResourceAsStream("assets/minecraft/shaders/" + path)) {
      assertNotNull(input, path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
