package net.dx12mc.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShaderBinaryTest {
  static final List<String> BUILT_IN =
      List.of(
          "vs_position_color",
          "vs_position_color_light",
          "fs_position_color",
          "fs_position_color_alpha",
          "fs_logic_invert",
          "fs_logic_invert_alpha",
          "vs_position_color_tex",
          "vs_position_color_tex_light",
          "fs_position_color_tex",
          "fs_position_color_tex_cutout",
          "vs_entity",
          "vs_line",
          "vs_end_portal",
          "fs_end_portal",
          "fs_entity",
          "fs_entity_cutout",
          "vs_post",
          "fs_post_box_blur",
          "fs_post_entity_sobel",
          "fs_post_outline_blur",
          "fs_post_blit",
          "fs_post_invert",
          "fs_post_color_convolve",
          "fs_post_bits",
          "fs_post_spider",
          "fs_position_color_tex_mrt",
          "fs_entity_mrt");

  static byte[] builtIn(String name) {
    try (InputStream input =
        ShaderBinaryTest.class.getResourceAsStream(
            "/assets/dx12mc/shaders/d3d12/" + name + ".bin")) {
      assertNotNull(input, name);
      return input.readAllBytes();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Test
  void everyCompiledBuiltInShaderParsesAndItsChecksumMatches() {
    for (String name : BUILT_IN) {
      ShaderBinary binary = ShaderBinary.parse(builtIn(name));
      assertEquals(name.startsWith("vs_") ? 'V' : 'F', binary.stage(), name);
      assertEquals(ShaderContract.BGFX_SHADER_VERSION, binary.version(), name);
    }
  }

  @Test
  void samplersReportTheirSlotAndEntityVerticesTheirAttributes() {
    ShaderBinary entity = ShaderBinary.parse(builtIn("vs_entity"));
    assertEquals(2, entity.uniform("s_lightmap").register());
    assertTrue(entity.uniform("s_lightmap").sampler());
    assertTrue(entity.hasAttribute(ShaderBinary.ATTRIB_NORMAL));
    assertEquals(ShaderBinary.TYPE_MAT4, entity.uniform("u_mvp").baseType());
    ShaderBinary mrt = ShaderBinary.parse(builtIn("fs_entity_mrt"));
    assertEquals(ShaderBinary.parse(builtIn("fs_entity")).hashIn(), mrt.hashIn());
  }

  @Test
  void aCorruptedShaderIsRejectedBeforeBgfxSeesIt() {
    byte[] bytes = builtIn("fs_position_color_tex");
    byte[] flipped = bytes.clone();
    flipped[flipped.length - 40] ^= 0x01;
    assertEquals(
        "DXBC checksum does not match",
        assertThrows(InvalidPackException.class, () -> ShaderBinary.parse(flipped)).getMessage());
    assertThrows(
        InvalidPackException.class,
        () -> ShaderBinary.parse(Arrays.copyOf(bytes, bytes.length - 3)));
    byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
    assertThrows(InvalidPackException.class, () -> ShaderBinary.parse(trailing));
    byte[] magic = bytes.clone();
    magic[1] = 'X';
    assertThrows(InvalidPackException.class, () -> ShaderBinary.parse(magic));
    assertThrows(InvalidPackException.class, () -> ShaderBinary.parse(new byte[8]));
  }

  @Test
  void aShaderLabelledWithTheWrongStageIsRejected() {
    byte[] relabelled = builtIn("fs_position_color_tex").clone();
    relabelled[0] = 'V';
    assertEquals(
        "DXBC stage does not match the bgfx shader stage",
        assertThrows(InvalidPackException.class, () -> ShaderBinary.parse(relabelled))
            .getMessage());
  }

  @Test
  void checksumMatchesTheStoredValueOfEveryBuiltInShader() {
    for (String name : BUILT_IN) {
      byte[] bytes = builtIn(name);
      ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      int offset = 14;
      int count = Short.toUnsignedInt(in.getShort(12));
      for (int i = 0; i < count; i++) offset += 1 + Byte.toUnsignedInt(bytes[offset]) + 10;
      int size = in.getInt(offset);
      byte[] code = Arrays.copyOfRange(bytes, offset + 4, offset + 4 + size);
      int[] stored = new int[4];
      ByteBuffer header = ByteBuffer.wrap(code).order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < 4; i++) stored[i] = header.getInt(4 + i * 4);
      assertArrayEquals(stored, DxbcChecksum.compute(code), name);
    }
  }

  @Test
  void containerRoundTripsAndRejectsMismatchedBuilds() {
    byte[] bgfx = builtIn("fs_position_color_tex");
    byte[] file = ShaderContainer.write(bgfx, ShaderContract.VERSION, ShaderContract.PROFILE);
    assertEquals('F', ShaderContainer.read(file).stage());
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () -> ShaderContainer.read(ShaderContainer.write(bgfx, 2, ShaderContract.PROFILE)))
            .getMessage()
            .contains("built for shader contract 2"));
    assertTrue(
        assertThrows(
                InvalidPackException.class,
                () ->
                    ShaderContainer.read(
                        ShaderContainer.write(bgfx, ShaderContract.VERSION, "vulkan_spirv")))
            .getMessage()
            .contains("built for profile vulkan_spirv"));
    byte[] version = file.clone();
    version[9] = 10;
    assertTrue(
        assertThrows(InvalidPackException.class, () -> ShaderContainer.read(version))
            .getMessage()
            .contains("bgfx shader binary version 10"));
    assertTrue(
        assertThrows(InvalidPackException.class, () -> ShaderContainer.read(bgfx))
            .getMessage()
            .contains("not a DX12MC shader file"));
  }
}
