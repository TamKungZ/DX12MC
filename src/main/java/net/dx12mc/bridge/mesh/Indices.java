package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.VertexFormat;

final class Indices {

  private Indices() {}

  static int[] triangles(VertexFormat.Mode mode, int vertexCount) {
    return switch (mode) {
      case TRIANGLES -> triangles(vertexCount);
      case QUADS -> quads(vertexCount);
      case TRIANGLE_STRIP -> strip(vertexCount);
      case TRIANGLE_FAN -> fan(vertexCount);
      case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP -> null;
    };
  }

  private static int[] triangles(int vertexCount) {
    int count = vertexCount - vertexCount % 3;
    if (count < 3) {
      return null;
    }
    int[] out = new int[count];
    for (int i = 0; i < count; i++) {
      out[i] = i;
    }
    return out;
  }

  private static int[] quads(int vertexCount) {
    int quadCount = vertexCount / 4;
    if (quadCount == 0) {
      return null;
    }
    int[] out = new int[quadCount * 6];
    for (int quad = 0; quad < quadCount; quad++) {
      int v = quad * 4;
      int i = quad * 6;
      out[i] = v;
      out[i + 1] = v + 1;
      out[i + 2] = v + 2;
      out[i + 3] = v + 2;
      out[i + 4] = v + 3;
      out[i + 5] = v;
    }
    return out;
  }

  private static int[] strip(int vertexCount) {
    if (vertexCount < 3) {
      return null;
    }
    int[] out = new int[(vertexCount - 2) * 3];
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
    return out;
  }

  private static int[] fan(int vertexCount) {
    if (vertexCount < 3) {
      return null;
    }
    int[] out = new int[(vertexCount - 2) * 3];
    for (int triangle = 0; triangle < vertexCount - 2; triangle++) {
      int i = triangle * 3;
      out[i] = 0;
      out[i + 1] = triangle + 1;
      out[i + 2] = triangle + 2;
    }
    return out;
  }
}
