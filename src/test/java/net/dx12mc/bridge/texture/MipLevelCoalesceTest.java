package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.dx12mc.bridge.texture.MipLevel.DirtyRegion;
import org.junit.jupiter.api.Test;

class MipLevelCoalesceTest {
  private static final long CALL_COST = 131_072L;

  @Test
  void keepsSingleRegionUntouched() {
    DirtyRegion region = new DirtyRegion(0, 0, 16, 16);
    assertEquals(List.of(region), MipLevel.coalesce(List.of(region), CALL_COST));
  }

  @Test
  void mergesNearbySmallRegionsIntoOneUpdate() {
    List<DirtyRegion> merged =
        MipLevel.coalesce(
            List.of(new DirtyRegion(0, 0, 16, 16), new DirtyRegion(32, 0, 48, 16)), CALL_COST);
    assertEquals(List.of(new DirtyRegion(0, 0, 48, 16)), merged);
  }

  @Test
  void keepsDistantLargeRegionsSeparate() {
    List<DirtyRegion> regions =
        List.of(new DirtyRegion(0, 0, 512, 512), new DirtyRegion(2000, 2000, 2512, 2512));
    assertEquals(2, MipLevel.coalesce(regions, CALL_COST).size());
  }

  @Test
  void collapsesManyScatteredTilesIntoFewUpdates() {
    java.util.ArrayList<DirtyRegion> tiles = new java.util.ArrayList<>();
    for (int i = 0; i < 40; i++) {
      int x = i % 8 * 120;
      int y = i / 8 * 200;
      tiles.add(new DirtyRegion(x, y, x + 16, y + 16));
    }
    List<DirtyRegion> merged = MipLevel.coalesce(tiles, CALL_COST);
    assertTrue(merged.size() <= 8, "updates: " + merged.size());
    long mergedCost = 0L;
    for (DirtyRegion region : merged) mergedCost += CALL_COST + region.area();
    long separateCost = 0L;
    for (DirtyRegion tile : tiles) separateCost += CALL_COST + tile.area();
    assertTrue(mergedCost < separateCost);
  }

  @Test
  void neverDropsCoverageOfInputRegions() {
    java.util.Random random = new java.util.Random(3);
    for (int round = 0; round < 50; round++) {
      java.util.ArrayList<DirtyRegion> regions = new java.util.ArrayList<>();
      for (int i = 0; i < 1 + random.nextInt(30); i++) {
        int x = random.nextInt(1000);
        int y = random.nextInt(1000);
        regions.add(new DirtyRegion(x, y, x + 1 + random.nextInt(64), y + 1 + random.nextInt(64)));
      }
      List<DirtyRegion> merged = MipLevel.coalesce(regions, CALL_COST);
      for (DirtyRegion region : regions) {
        assertTrue(
            merged.stream()
                .anyMatch(
                    m ->
                        m.minX() <= region.minX()
                            && m.maxX() >= region.maxX()
                            && m.minY() <= region.minY()
                            && m.maxY() >= region.maxY()));
      }
    }
  }
}
