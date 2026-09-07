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
  void intentionallyRejectsLineModes() {
    assertNull(Indices.triangles(VertexFormat.Mode.LINES, 4));
    assertNull(Indices.triangles(VertexFormat.Mode.LINE_STRIP, 4));
  }
}
