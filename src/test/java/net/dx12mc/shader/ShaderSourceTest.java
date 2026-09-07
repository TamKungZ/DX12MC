package net.dx12mc.shader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShaderSourceTest {
  private static final Path ROOT = Path.of("src", "main", "shaders");

  @Test
  void everyShaderGetsTheCommonShadercHelpers() throws IOException {
    assertTrue(read("vs_position_color.sc").contains("#include \"common.sh\""));
    assertTrue(read("fs_position_color.sc").contains("#include \"common.sh\""));
    assertTrue(read("vs_position_color_tex.sc").contains("#include \"common.sh\""));
    assertTrue(read("fs_position_color_tex.sc").contains("#include \"texture.sh\""));
    assertTrue(read("fs_position_color_tex_cutout.sc").contains("#include \"texture.sh\""));
    assertTrue(read("vs_position_color_tex_light.sc").contains("#include \"common.sh\""));
    assertTrue(read("fs_position_color_tex_light.sc").contains("#include \"texture.sh\""));
    assertTrue(read("fs_position_color_tex_light_cutout.sc").contains("#include \"texture.sh\""));
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
    assertFalse(texture.contains("uniform sampler2D"));
    assertFalse(texture.contains("SamplerState sampler;"));
    assertFalse(texture.contains("Texture2D texture;"));
  }

  @Test
  void lightmapShaderUsesDedicatedSampler2AndVanillaUv2Domain() throws IOException {
    String vertex = read("vs_position_color_tex_light.sc");
    String fragment = read("fs_position_color_tex_light.sc");
    String varying = read("varying.def.sc");
    assertTrue(vertex.contains("a_texcoord2"));
    assertTrue(fragment.contains("SAMPLER2D(s_lightmap, 2)"));
    assertTrue(fragment.contains("v_texcoord2 + vec2_splat(8.0)"));
    assertTrue(fragment.contains("/ 256.0"));
    assertTrue(varying.contains("a_texcoord2 : TEXCOORD2"));
    assertTrue(varying.contains("a_normal : NORMAL"));
  }

  @Test
  void cutoutShadersDiscardTransparentAtlasTexelsBeforeWritingDepth() throws IOException {
    String base = read("fs_position_color_tex_cutout.sc");
    String light = read("fs_position_color_tex_light_cutout.sc");
    assertTrue(base.contains("color.a < 0.1"));
    assertTrue(base.contains("discard"));
    assertTrue(light.contains("base.a < 0.1"));
    assertTrue(light.contains("discard"));
  }

  private static String read(String name) throws IOException {
    return Files.readString(ROOT.resolve(name));
  }
}
