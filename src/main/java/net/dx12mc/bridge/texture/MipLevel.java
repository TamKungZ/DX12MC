package net.dx12mc.bridge.texture;

import java.util.ArrayList;
import java.util.List;

final class MipLevel {
  private static final int MAX_DIRTY_REGIONS = 64;

  private final int width;
  private final int height;
  private byte[] pixels;
  private final List<DirtyRegion> dirtyRegions = new ArrayList<>();

  MipLevel(int width, int height) {
    this.width = width;
    this.height = height;
  }

  int width() {
    return width;
  }

  int height() {
    return height;
  }

  boolean hasPixels() {
    return pixels != null;
  }

  byte[] pixels() {
    return pixels;
  }

  void pixels(byte[] pixels) {
    this.pixels = pixels;
  }

  byte[] ensurePixels() {
    if (pixels == null) pixels = new byte[Image.checkedBytes(width, height)];
    return pixels;
  }

  List<DirtyRegion> dirtyRegions() {
    return dirtyRegions;
  }

  void copyDirtyRegionsFrom(MipLevel source) {
    dirtyRegions.addAll(source.dirtyRegions);
  }

  void clearDirty() {
    dirtyRegions.clear();
  }

  void markDirty(int x, int y, int width, int height) {
    DirtyRegion merged = new DirtyRegion(x, y, x + width, y + height);
    for (int i = dirtyRegions.size() - 1; i >= 0; i--) {
      DirtyRegion existing = dirtyRegions.get(i);
      if (!existing.touches(merged)) continue;
      merged = existing.union(merged);
      dirtyRegions.remove(i);
    }
    dirtyRegions.add(merged);
    if (dirtyRegions.size() > MAX_DIRTY_REGIONS) mergeAllDirtyRegions();
  }

  private void mergeAllDirtyRegions() {
    DirtyRegion merged = dirtyRegions.get(0);
    for (int i = 1; i < dirtyRegions.size(); i++) merged = merged.union(dirtyRegions.get(i));
    dirtyRegions.clear();
    dirtyRegions.add(merged);
  }

  record DirtyRegion(int minX, int minY, int maxX, int maxY) {
    boolean touches(DirtyRegion other) {
      return minX <= other.maxX + 1
          && maxX + 1 >= other.minX
          && minY <= other.maxY + 1
          && maxY + 1 >= other.minY;
    }

    DirtyRegion union(DirtyRegion other) {
      return new DirtyRegion(
          Math.min(minX, other.minX),
          Math.min(minY, other.minY),
          Math.max(maxX, other.maxX),
          Math.max(maxY, other.maxY));
    }
  }
}
