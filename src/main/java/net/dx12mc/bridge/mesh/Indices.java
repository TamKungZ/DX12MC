package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.VertexFormat;

final class Indices {

  private Indices() {}

  static int[] triangles(VertexFormat.Mode mode, int vertexCount) {
    int count = triangleIndexCount(mode, vertexCount);
    if (count == 0) return null;
    int[] out = new int[count];
    writeTriangles(mode, vertexCount, out);
    return out;
  }

  static int triangleIndexCount(VertexFormat.Mode mode, int vertexCount) {
    if (mode == null || vertexCount <= 0) return 0;
    return switch (mode) {
      case TRIANGLES -> vertexCount - vertexCount % 3;
      case QUADS -> vertexCount / 4 * 6;
      case TRIANGLE_STRIP, TRIANGLE_FAN -> vertexCount < 3 ? 0 : (vertexCount - 2) * 3;
      case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP -> 0;
    };
  }

  static void writeTriangles(VertexFormat.Mode mode, int vertexCount, int[] out) {
    int required = triangleIndexCount(mode, vertexCount);
    if (required == 0 || out == null || out.length < required) {
      throw new IllegalArgumentException("triangle index target is too small");
    }
    switch (mode) {
      case TRIANGLES -> {
        for (int i = 0; i < required; i++) out[i] = i;
      }
      case QUADS -> {
        for (int quad = 0; quad < vertexCount / 4; quad++) {
          int v = quad * 4;
          int i = quad * 6;
          out[i] = v;
          out[i + 1] = v + 1;
          out[i + 2] = v + 2;
          out[i + 3] = v + 2;
          out[i + 4] = v + 3;
          out[i + 5] = v;
        }
      }
      case TRIANGLE_STRIP -> {
        for (int triangle = 0; triangle < vertexCount - 2; triangle++) {
          int i = triangle * 3;
          if ((triangle & 1) == 0) {
            out[i] = triangle;
            out[i + 1] = triangle + 1;
          } else {
            out[i] = triangle + 1;
            out[i + 1] = triangle;
          }
          out[i + 2] = triangle + 2;
        }
      }
      case TRIANGLE_FAN -> {
        for (int triangle = 0; triangle < vertexCount - 2; triangle++) {
          int i = triangle * 3;
          out[i] = 0;
          out[i + 1] = triangle + 1;
          out[i + 2] = triangle + 2;
        }
      }
      case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP ->
          throw new IllegalArgumentException("line modes cannot be converted here");
    }
  }
}
