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

  static boolean isQuadPattern(int[] indices, int indexCount, int vertexCount) {
    if (vertexCount <= 0 || vertexCount % 4 != 0 || indexCount != vertexCount / 4 * 6) return false;
    for (int quad = 0; quad < vertexCount / 4; quad++) {
      int v = quad * 4;
      int i = quad * 6;
      if (indices[i] != v
          || indices[i + 1] != v + 1
          || indices[i + 2] != v + 2
          || indices[i + 3] != v + 2
          || indices[i + 4] != v + 3
          || indices[i + 5] != v) return false;
    }
    return true;
  }

  static int triangleIndexCount(VertexFormat.Mode mode, int vertexCount) {
    if (mode == null || vertexCount <= 0) return 0;
    return switch (mode) {
      case TRIANGLES -> vertexCount - vertexCount % 3;
      case QUADS, LINES -> vertexCount / 4 * 6;
      case TRIANGLE_STRIP, TRIANGLE_FAN, LINE_STRIP -> vertexCount < 3 ? 0 : (vertexCount - 2) * 3;
      case DEBUG_LINES -> vertexCount - vertexCount % 2;
      case DEBUG_LINE_STRIP -> vertexCount < 2 ? 0 : vertexCount;
    };
  }

  static boolean completePrimitives(VertexFormat.Mode mode, int indexCount) {
    if (indexCount <= 0) return false;
    if (mode == VertexFormat.Mode.DEBUG_LINES) return indexCount % 2 == 0;
    if (mode == VertexFormat.Mode.DEBUG_LINE_STRIP) return indexCount >= 2;
    return indexCount % 3 == 0;
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
      case LINES -> {
        for (int line = 0; line < vertexCount / 4; line++) {
          int v = line * 4;
          int i = line * 6;
          out[i] = v;
          out[i + 1] = v + 1;
          out[i + 2] = v + 2;
          out[i + 3] = v + 3;
          out[i + 4] = v + 2;
          out[i + 5] = v + 1;
        }
      }
      case TRIANGLE_STRIP, LINE_STRIP -> {
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
      case DEBUG_LINES, DEBUG_LINE_STRIP -> {
        for (int i = 0; i < required; i++) out[i] = i;
      }
    }
  }
}
