package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

class BatchTest {

  @Test
  void acceptsTriangleBatch() {
    assertEquals(3, batch(new int[] {0, 1, 2}, 3).indexCount());
  }

  @Test
  void rejectsIndicesWithoutVertices() {
    assertThrows(IllegalArgumentException.class, () -> batch(new int[] {0, 0, 0}, 0));
  }

  private static Batch batch(int[] indices, int vertexCount) {
    return new Batch(
        new float[vertexCount * 3],
        new int[vertexCount],
        new float[0],
        false,
        new short[0],
        false,
        new short[0],
        false,
        new byte[0],
        false,
        indices,
        vertexCount,
        VertexFormat.Mode.TRIANGLES);
  }
}
