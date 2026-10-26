package net.dx12mc.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.bgfx.BGFX.BGFX_STATE_PT_LINES;
import static org.lwjgl.bgfx.BGFX.BGFX_STATE_PT_LINESTRIP;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderSemantics;
import org.junit.jupiter.api.Test;

class LinePrimitiveTest {

  @Test
  void debugLineModesReachBgfxAsLinePrimitives() {
    assertEquals(BGFX_STATE_PT_LINES, DrawContextFactory.primitive(VertexFormat.Mode.DEBUG_LINES));
    assertEquals(
        BGFX_STATE_PT_LINESTRIP, DrawContextFactory.primitive(VertexFormat.Mode.DEBUG_LINE_STRIP));
    assertEquals(0L, DrawContextFactory.primitive(VertexFormat.Mode.LINES));
    assertEquals(0L, DrawContextFactory.primitive(VertexFormat.Mode.QUADS));
  }

  @Test
  void onlyVanillaQuadExpandedLineModesUseTheScreenSpaceLineProgram() {
    assertTrue(DrawContextFactory.expandsLines(batch(VertexFormat.Mode.LINES)));
    assertTrue(DrawContextFactory.expandsLines(batch(VertexFormat.Mode.LINE_STRIP)));
    assertFalse(DrawContextFactory.expandsLines(batch(VertexFormat.Mode.DEBUG_LINES)));
    assertTrue(ShaderSemantics.of("rendertype_lines").screenSpaceLines());
    assertFalse(ShaderSemantics.of("position_color").screenSpaceLines());
  }

  @Test
  void lineParametersCarryVanillaLineWidthAndScreenSize() {
    float[] params = new float[4];
    DrawContextFactory.lineParameters(2.5f, 1920, 1009, params);
    assertArrayEquals(new float[] {2.5f, 1920.0f, 1009.0f, 0.0f}, params);
  }

  private static Batch batch(VertexFormat.Mode mode) {
    return new Batch(
        new float[12],
        new int[4],
        new float[0],
        false,
        new short[0],
        false,
        new short[0],
        false,
        new byte[12],
        true,
        new int[] {0, 1, 2, 3, 2, 1},
        4,
        mode);
  }
}
