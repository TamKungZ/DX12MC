package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionRebuildTest {
  private static final int REBUILD_BUDGET = 6;

  private final Stats stats = new Stats();
  private final List<Boolean> builtQuadPatterns = new ArrayList<>();
  private final List<Integer> builtVertexCounts = new ArrayList<>();
  private final Object layer = new Object();
  private boolean failNextBuild;
  private SectionRegionBatcher batcher;
  private MeshStore meshes;

  @BeforeEach
  void setUp() {
    batcher =
        new SectionRegionBatcher(
            stats,
            (combined, quadPattern, layout) -> {
              if (failNextBuild) {
                failNextBuild = false;
                throw new IllegalStateException("simulated page failure");
              }
              builtQuadPatterns.add(quadPattern);
              builtVertexCounts.add(combined.vertexCount());
              return new SectionRegionBatcher.Page(
                  null,
                  combined.vertexCount(),
                  combined.indexCount(),
                  combined.vertexCount() * 32L);
            });
    meshes = new MeshStore(stats);
  }

  @Test
  void rebuildsAreLimitedPerFrameAndResumeOnTheNextFrame() {
    int total = REBUILD_BUDGET + 2;
    List<Slot> slots = slots(total);
    frame(slots, 32);
    assertEquals(REBUILD_BUDGET, stats.regionRebuilds());
    assertEquals(2, stats.regionRebuildDeferrals());
    frame(slots, 32);
    assertEquals(total, stats.regionRebuilds());
    assertEquals(total, builtVertexCounts.size());
  }

  @Test
  void cleanRegionsAreNotRebuiltAgain() {
    List<Slot> slots = slots(2);
    frame(slots, 32);
    frame(slots, 32);
    frame(slots, 32);
    assertEquals(2, stats.regionRebuilds());
  }

  @Test
  void changedSectionRebuildsOnlyItsRegion() {
    List<Slot> slots = slots(3);
    frame(slots, 32);
    long before = stats.regionRebuilds();
    slots.get(1).capture(quads(8), false, VertexFormat.IndexType.INT, 12);
    frame(slots, 32);
    assertEquals(before + 1, stats.regionRebuilds());
  }

  @Test
  void membersOfOneRegionShareAPageAndMergeIntoOneRun() {
    List<Slot> slots = slots(2);
    begin();
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(1), slots.get(1).batch(), 16, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(0);
    assertTrue(region.ready());
    assertEquals(1, region.pageCount());
    assertEquals(1, region.visibleRuns());
    assertEquals(0, region.runFirstIndex(0));
    assertEquals(24, region.runIndexCount(0));
    assertTrue(builtQuadPatterns.get(0));
    batcher.end();
  }

  @Test
  void hiddenMemberOnlyAffectsTheDrawnRange() {
    List<Slot> slots = slots(3);
    int[] originsX = {0, 16, 0};
    int[] originsZ = {0, 0, 16};
    begin();
    for (int i = 0; i < 3; i++) {
      batcher.collect(slots.get(i), slots.get(i).batch(), originsX[i], 0, originsZ[i]);
    }
    batcher.prepare(null, meshes);
    batcher.end();
    frameBoundary();

    begin();
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(2), slots.get(2).batch(), 0, 0, 16);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(0);
    assertTrue(region.ready());
    int drawn = 0;
    for (int run = 0; run < region.visibleRuns(); run++) drawn += region.runIndexCount(run);
    assertTrue(drawn >= 24);
    assertTrue(drawn <= 36);
    batcher.end();
  }

  @Test
  void releasedSlotsLeaveTheRegionAndTriggerARebuild() {
    List<Slot> slots = slots(2);
    frame(slots, 16);
    long before = stats.regionRebuilds();
    batcher.invalidate(slots.get(1));
    frame(slots.subList(0, 1), 16);
    assertEquals(before + 1, stats.regionRebuilds());
  }

  @Test
  void pagesReportDrawnVertexCountsForWholePageRuns() {
    List<Slot> slots = slots(2);
    begin();
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(1), slots.get(1).batch(), 16, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(0);
    assertEquals(1, region.visibleRuns());
    assertEquals(16, region.runVertexCount(0));
    batcher.end();
  }

  @Test
  void deferredRegionKeepsDrawingItsBuiltMembers() {
    List<Slot> slots = slots(1);
    frame(slots, 32);
    slots.get(0).capture(quads(12), false, VertexFormat.IndexType.INT, 18);
    List<Slot> others = slots(REBUILD_BUDGET);
    begin();
    for (int i = 0; i < others.size(); i++) {
      batcher.collect(others.get(i), others.get(i).batch(), 1000 + i * 64, 0, 0);
    }
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(REBUILD_BUDGET);
    assertFalse(region.ready());
    assertEquals(1, region.visibleRuns());
    assertEquals(12, region.runIndexCount(0));
    assertEquals(0, region.unbuiltVisible());
    batcher.end();
  }

  @Test
  void membersAddedAfterTheLastRebuildAreStreamedNotDrawnFromThePage() {
    List<Slot> slots = slots(2);
    frame(slots.subList(0, 1), 16);
    List<Slot> fillers = slots(REBUILD_BUDGET);
    begin();
    for (int i = 0; i < fillers.size(); i++) {
      batcher.collect(fillers.get(i), fillers.get(i).batch(), 1000 + i * 64, 0, 0);
    }
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(1), slots.get(1).batch(), 16, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(REBUILD_BUDGET);
    assertFalse(region.ready());
    assertEquals(1, region.visibleRuns());
    assertEquals(12, region.runIndexCount(0));
    assertEquals(1, region.unbuiltVisible());
    batcher.end();
  }

  @Test
  void regionsWithoutPagesStreamEveryVisibleMember() {
    List<Slot> slots = slots(2);
    List<Slot> fillers = slots(REBUILD_BUDGET);
    begin();
    for (int i = 0; i < fillers.size(); i++) {
      batcher.collect(fillers.get(i), fillers.get(i).batch(), 1000 + i * 64, 0, 0);
    }
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(1), slots.get(1).batch(), 16, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(REBUILD_BUDGET);
    assertEquals(0, region.pageCount());
    assertEquals(0, region.visibleRuns());
    assertEquals(2, region.unbuiltVisible());
    batcher.end();
  }

  @Test
  void failedPageCreationKeepsPreviousPagesAndMemberRanges() {
    List<Slot> slots = slots(2);
    frame(slots, 16);
    failNextBuild = true;
    slots.get(0).capture(quads(16), false, VertexFormat.IndexType.INT, 24);
    begin();
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.collect(slots.get(1), slots.get(1).batch(), 16, 0, 0);
    batcher.prepare(null, meshes);
    SectionRegionBatcher.Region region = batcher.touched(0);
    assertFalse(region.ready());
    assertEquals(1, stats.regionRebuildFailures());
    assertEquals(1, region.pageCount());
    assertEquals(1, region.visibleRuns());
    assertEquals(24, region.runIndexCount(0));
    batcher.end();
  }

  @Test
  void closingTwiceReleasesEverythingOnce() {
    List<Slot> slots = slots(3);
    frame(slots, 32);
    batcher.close();
    batcher.close();
    begin();
    batcher.collect(slots.get(0), slots.get(0).batch(), 0, 0, 0);
    batcher.prepare(null, meshes);
    assertEquals(1, batcher.touchedCount());
    batcher.end();
  }

  private void frame(List<Slot> slots, int spacing) {
    begin();
    for (int i = 0; i < slots.size(); i++) {
      batcher.collect(slots.get(i), slots.get(i).batch(), i * spacing, 0, 0);
    }
    batcher.prepare(null, meshes);
    batcher.end();
    frameBoundary();
  }

  private void begin() {
    batcher.begin(layer, true, new Matrix4f(), 0.0, 0.0, 0.0);
  }

  private void frameBoundary() {
    batcher.onFrameBoundary();
    meshes.onFrameBoundary();
  }

  private static List<Slot> slots(int count) {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Slot slot = new Slot();
      slot.capture(quads(8), false, VertexFormat.IndexType.INT, 12);
      slots.add(slot);
    }
    return slots;
  }

  private static Batch quads(int vertexCount) {
    int[] indices = new int[vertexCount / 4 * 6];
    Indices.writeTriangles(VertexFormat.Mode.QUADS, vertexCount, indices);
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
