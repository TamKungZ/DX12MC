package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

class IndicesTest {

  @Test
  void convertsOneQuadToTwoTriangles() {
    assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0}, Indices.triangles(VertexFormat.Mode.QUADS, 4));
  }

  @Test
  void keepsTriangleStripWindingConsistent() {
    assertArrayEquals(
        new int[] {0, 1, 2, 2, 1, 3}, Indices.triangles(VertexFormat.Mode.TRIANGLE_STRIP, 4));
  }

  @Test
  void buildsTriangleFan() {
    assertArrayEquals(
        new int[] {0, 1, 2, 0, 2, 3}, Indices.triangles(VertexFormat.Mode.TRIANGLE_FAN, 4));
  }

  @Test
  void linesUseVanillaSharedSequentialLinesPattern() {
    assertArrayEquals(
        new int[] {0, 1, 2, 3, 2, 1, 4, 5, 6, 7, 6, 5},
        Indices.triangles(VertexFormat.Mode.LINES, 8));
  }

  @Test
  void lineStripIsTheTriangleStripOfDuplicatedVertices() {
    assertArrayEquals(
        Indices.triangles(VertexFormat.Mode.TRIANGLE_STRIP, 6),
        Indices.triangles(VertexFormat.Mode.LINE_STRIP, 6));
  }

  @Test
  void debugLineModesAreSequentialLinePrimitives() {
    assertArrayEquals(new int[] {0, 1, 2, 3}, Indices.triangles(VertexFormat.Mode.DEBUG_LINES, 5));
    assertArrayEquals(
        new int[] {0, 1, 2}, Indices.triangles(VertexFormat.Mode.DEBUG_LINE_STRIP, 3));
    assertTrue(Indices.completePrimitives(VertexFormat.Mode.DEBUG_LINES, 4));
    assertFalse(Indices.completePrimitives(VertexFormat.Mode.DEBUG_LINES, 3));
    assertTrue(Indices.completePrimitives(VertexFormat.Mode.DEBUG_LINE_STRIP, 2));
    assertFalse(Indices.completePrimitives(VertexFormat.Mode.TRIANGLES, 4));
  }
}
