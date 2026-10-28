package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import net.dx12mc.graphics.bgfx.Mesh;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class SectionLoadVisibilityTest {
  private static final Object LAYER = new Object();
  private static final int FRAMES = 40;
  private static final int ARRIVALS_PER_FRAME = 40;
  private static final int SECTIONS = 1200;
  private static final int VERTICES = 3000;
  private static final long STREAM_BYTES = 6L << 20;

  private record Result(int[] missing, int maxWaitFrames) {
    int total() {
      int total = 0;
      for (int value : missing) total += value;
      return total;
    }
  }

  @Test
  void theLargerRebuildBudgetDrawsEverySectionThatTheOldBudgetDrew() {
    Result before = simulate(6, Long.MAX_VALUE);
    Result after = simulate(32, 4L << 20);
    for (int frame = 0; frame < FRAMES; frame++) {
      assertTrue(
          after.missing()[frame] <= before.missing()[frame],
          "frame "
              + frame
              + ": "
              + after.missing()[frame]
              + " missing now, "
              + before.missing()[frame]
              + " before");
    }
    assertTrue(before.total() > 0, "the simulated load must reproduce the old holes");
    assertEquals(0, after.total(), "no arrived section may be left undrawn");
    assertTrue(after.maxWaitFrames() < before.maxWaitFrames());
  }

  @Test
  void theRebuildByteCapStillBoundsOneFrameOfRebuilds() {
    List<Long> perFrame = new ArrayList<>();
    simulate(1000, 1L << 20, perFrame);
    for (long bytes : perFrame) assertTrue(bytes < (1L << 20) + regionBytes(), "rebuilt " + bytes);
  }

  private static Result simulate(int rebuilds, long rebuildBytes) {
    return simulate(rebuilds, rebuildBytes, new ArrayList<>());
  }

  private static Result simulate(int rebuilds, long rebuildBytes, List<Long> rebuiltPerFrame) {
    long[] frameBytes = new long[1];
    SectionRegionBatcher batcher =
        new SectionRegionBatcher(
            new Stats(),
            (combined, quads, layout) -> {
              frameBytes[0] += combined.vertexCount() * 28L;
              return new SectionRegionBatcher.Page(
                  null,
                  combined.vertexCount(),
                  combined.indexCount(),
                  combined.vertexCount() * 28L);
            },
            rebuilds,
            rebuildBytes,
            STREAM_BYTES);
    List<Slot> loaded = new ArrayList<>();
    List<Integer> arrivedAt = new ArrayList<>();
    int[] missing = new int[FRAMES];
    int maxWait = 0;
    MeshStore meshes = new MeshStore(new Stats());
    for (int frame = 0; frame < FRAMES; frame++) {
      for (int i = 0; i < ARRIVALS_PER_FRAME && loaded.size() < SECTIONS; i++) {
        loaded.add(slot());
        arrivedAt.add(frame);
      }
      frameBytes[0] = 0L;
      batcher.begin(LAYER, true, new Matrix4f(), 0, 0, 0);
      for (int i = 0; i < loaded.size(); i++) {
        int region = i / 4;
        batcher.collect(
            loaded.get(i),
            loaded.get(i).batch(),
            region * 32 + (i % 2) * 16,
            0,
            ((i / 2) % 2) * 16);
      }
      batcher.prepare(null, meshes);
      rebuiltPerFrame.add(frameBytes[0]);
      for (int r = 0; r < batcher.touchedCount(); r++) {
        SectionRegionBatcher.Region region = batcher.touched(r);
        int waiting = region.unbuiltVisible();
        for (int m = 0; m < waiting; m++) {
          if (!batcher.takeStreamBudget(Mesh.estimatedBytes(region.unbuilt(m).batch())))
            missing[frame]++;
        }
      }
      batcher.end();
      batcher.onFrameBoundary();
      if (missing[frame] > 0) maxWait = Math.max(maxWait, frame - arrivedAt.get(0));
    }
    batcher.close();
    return new Result(missing, maxWait);
  }

  private static long regionBytes() {
    return 4L * VERTICES * 28;
  }

  private static Slot slot() {
    Slot slot = new Slot();
    float[] positions = new float[VERTICES * 3];
    int[] colors = new int[VERTICES];
    int[] indices = new int[VERTICES / 4 * 6];
    for (int quad = 0; quad < VERTICES / 4; quad++) {
      int vertex = quad * 4;
      int base = quad * 6;
      indices[base] = vertex;
      indices[base + 1] = vertex + 1;
      indices[base + 2] = vertex + 2;
      indices[base + 3] = vertex + 2;
      indices[base + 4] = vertex + 3;
      indices[base + 5] = vertex;
    }
    Batch batch =
        new Batch(
            positions,
            colors,
            new float[VERTICES * 2],
            true,
            new short[0],
            false,
            new short[VERTICES * 2],
            true,
            new byte[0],
            false,
            indices,
            VERTICES,
            VertexFormat.Mode.QUADS);
    slot.capture(batch, false, VertexFormat.IndexType.SHORT, indices.length);
    return slot;
  }
}
