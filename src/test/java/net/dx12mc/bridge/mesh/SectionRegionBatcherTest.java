package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Arrays;
import java.util.List;
import net.dx12mc.bridge.Slot;
import org.junit.jupiter.api.Test;

class SectionRegionBatcherTest {
  @Test
  void assemblerTranslatesVerticesAndRebasesIndicesIntoRegionSpace() {
    Batch first = batch(new float[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, new int[] {0, 1, 2}, true);
    Batch second = batch(new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[] {0, 2, 1}, false);
    List<SectionRegionBatcher.Member> members =
        List.of(
            new SectionRegionBatcher.Member(new Slot(), first, 0, 0, 32),
            new SectionRegionBatcher.Member(new Slot(), second, 16, 32, 48));

    Batch combined = new SectionRegionBatcher.Assembler().assemble(members, 0, 2, 0, 0, 32);

    assertEquals(6, combined.vertexCount());
    assertEquals(6, combined.indexCount());
    assertArrayEquals(
        new float[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 34, 19, 20, 37, 22, 23, 40, 25},
        Arrays.copyOf(combined.positions(), 18));
    assertArrayEquals(new int[] {0, 1, 2, 3, 5, 4}, Arrays.copyOf(combined.indices(), 6));
    assertArrayEquals(
        new float[] {0, 1, 2, 3, 4, 5, 0, 0, 0, 0, 0, 0}, Arrays.copyOf(combined.uv0(), 12));
  }

  private static Batch batch(float[] positions, int[] indices, boolean textured) {
    return new Batch(
        positions,
        new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF},
        textured ? new float[] {0, 1, 2, 3, 4, 5} : new float[0],
        textured,
        new short[0],
        false,
        new short[] {1, 2, 3, 4, 5, 6},
        true,
        new byte[0],
        false,
        indices,
        3,
        VertexFormat.Mode.TRIANGLES);
  }
}
