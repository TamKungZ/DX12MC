package net.dx12mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.dx12mc.bridge.mesh.Batch;
import org.junit.jupiter.api.Test;

class SlotTest {

  @Test
  void sortedIndicesStayPendingUntilNativeUploadSucceeds() {
    Slot slot = new Slot();
    slot.capture(batch(new int[] {0, 1, 2}), false, VertexFormat.IndexType.SHORT, 3);
    assertFalse(slot.indicesDirty());
    assertTrue(slot.replaceIndices(new int[] {2, 1, 0}));
    assertTrue(slot.indicesDirty());
    assertArrayEquals(new int[] {2, 1, 0}, slot.batch().indices());
    assertTrue(slot.replaceIndices(new int[] {1, 2, 0}));
    assertTrue(slot.indicesDirty());
    assertArrayEquals(new int[] {1, 2, 0}, slot.batch().indices());
    slot.indicesUploaded();
    assertFalse(slot.indicesDirty());
  }

  private static Batch batch(int[] indices) {
    return new Batch(
        new float[9],
        new int[3],
        new float[0],
        false,
        new short[0],
        false,
        new short[0],
        false,
        new byte[0],
        false,
        indices,
        3,
        VertexFormat.Mode.TRIANGLES);
  }
}
