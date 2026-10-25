package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.dx12mc.bridge.Slot;
import org.junit.jupiter.api.Test;

class QuadPatternTest {
  @Test
  void generatedQuadIndicesMatchThePattern() {
    int[] indices = new int[18];
    Indices.writeTriangles(VertexFormat.Mode.QUADS, 12, indices);
    assertTrue(Indices.isQuadPattern(indices, 18, 12));
  }

  @Test
  void sortedOrReorderedIndicesAreNotThePattern() {
    int[] indices = new int[12];
    Indices.writeTriangles(VertexFormat.Mode.QUADS, 8, indices);
    int swap = indices[0];
    indices[0] = indices[6];
    indices[6] = swap;
    assertFalse(Indices.isQuadPattern(indices, 12, 8));
  }

  @Test
  void partialQuadsAreNotThePattern() {
    int[] indices = new int[6];
    Indices.writeTriangles(VertexFormat.Mode.QUADS, 4, indices);
    assertFalse(Indices.isQuadPattern(indices, 6, 5));
    assertFalse(Indices.isQuadPattern(indices, 3, 4));
    assertFalse(Indices.isQuadPattern(indices, 0, 0));
  }

  @Test
  void assemblerKeepsThePatternOnlyWhenEveryMemberHasIt() {
    SectionRegionBatcher.Assembler assembler = new SectionRegionBatcher.Assembler();
    Batch quads = quadBatch(8, false);
    assembler.assemble(members(quads, quads), 0, 2, 0, 0, 0);
    assertTrue(assembler.quadPattern());
    Batch sorted = quadBatch(8, true);
    assembler.assemble(members(quads, sorted), 0, 2, 0, 0, 0);
    assertFalse(assembler.quadPattern());
  }

  @Test
  void assembledQuadPagesRemainThePatternAcrossMembers() {
    SectionRegionBatcher.Assembler assembler = new SectionRegionBatcher.Assembler();
    Batch quads = quadBatch(8, false);
    Batch combined = assembler.assemble(members(quads, quads, quads), 0, 3, 0, 0, 0);
    assertTrue(
        Indices.isQuadPattern(combined.indices(), combined.indexCount(), combined.vertexCount()));
  }

  private static List<SectionRegionBatcher.Member> members(Batch... batches) {
    java.util.ArrayList<SectionRegionBatcher.Member> members = new java.util.ArrayList<>();
    for (int i = 0; i < batches.length; i++) {
      members.add(new SectionRegionBatcher.Member(new Slot(), batches[i], i * 16, 0, 0));
    }
    return members;
  }

  private static Batch quadBatch(int vertexCount, boolean reorder) {
    int[] indices = new int[vertexCount / 4 * 6];
    Indices.writeTriangles(VertexFormat.Mode.QUADS, vertexCount, indices);
    if (reorder) {
      int swap = indices[0];
      indices[0] = indices[indices.length - 1];
      indices[indices.length - 1] = swap;
    }
    return new Batch(
        new float[vertexCount * 3],
        new int[vertexCount],
        new float[vertexCount * 2],
        true,
        new short[0],
        false,
        new short[vertexCount * 2],
        true,
        new byte[0],
        false,
        indices,
        vertexCount,
        VertexFormat.Mode.QUADS);
  }
}
