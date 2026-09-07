package net.dx12mc.bridge.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;

public final class Image {
  private static final int MAX_DIMENSION =
      Integer.getInteger("dx12mc.texture.maxDimension", 16_384);
  private static final long MAX_BYTES_PER_TEXTURE =
      Long.getLong("dx12mc.texture.maxBytes", 256L * 1024L * 1024L);
  private int width;
  private int height;
  private int maxLevel;
  private Level[] levels;
  private long revision;
  private boolean blur;
  private boolean clamp;
  private boolean mipmap;

  public Image(int width, int height, int maxLevel) {
    validateBaseSize(width, height);
    this.width = width;
    this.height = height;
    this.maxLevel = Math.min(Math.max(0, maxLevel), maximumMipLevel(width, height));
    this.levels = new Level[this.maxLevel + 1];
    for (int level = 0; level <= this.maxLevel; level++) {
      levels[level] = new Level(computedLevelWidth(level), computedLevelHeight(level));
    }
  }

  public boolean upload(
      NativeImage source,
      int level,
      int offsetX,
      int offsetY,
      int skipPixels,
      int skipRows,
      int uploadWidth,
      int uploadHeight,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    if (source == null || level < 0 || level > maxLevel) return false;
    if (uploadWidth <= 0
        || uploadHeight <= 0
        || offsetX < 0
        || offsetY < 0
        || skipPixels < 0
        || skipRows < 0) return false;
    Level target = levels[level];
    if ((long) offsetX + uploadWidth > target.width
        || (long) offsetY + uploadHeight > target.height) return false;
    if ((long) skipPixels + uploadWidth > source.getWidth()
        || (long) skipRows + uploadHeight > source.getHeight()) return false;
    byte[] rgba = target.ensurePixels();
    for (int y = 0; y < uploadHeight; y++) {
      for (int x = 0; x < uploadWidth; x++) {
        int dst = ((offsetY + y) * target.width + offsetX + x) * 4;
        Pixels.writeNativeImageAsRgba(source, skipPixels + x, skipRows + y, rgba, dst);
      }
    }
    target.markDirty(offsetX, offsetY, uploadWidth, uploadHeight);
    this.blur = blur;
    this.clamp = clamp;
    this.mipmap = mipmap;
    revision++;
    return true;
  }

  public boolean growToFit(int minimumWidth, int minimumHeight) {
    int newWidth = Math.max(width, minimumWidth);
    int newHeight = Math.max(height, minimumHeight);
    if (newWidth == width && newHeight == height) return false;
    validateBaseSize(newWidth, newHeight);
    long projected = projectedAllocatedCpuBytes(newWidth, newHeight);
    if (projected > MAX_BYTES_PER_TEXTURE) {
      throw new IllegalArgumentException(
          "grown texture CPU mirror would require "
              + projected
              + " bytes; per-texture safety limit is "
              + MAX_BYTES_PER_TEXTURE
              + " (override with -Ddx12mc.texture.maxBytes=...)");
    }
    Level[] resized = new Level[levels.length];
    for (int level = 0; level < levels.length; level++) {
      Level old = levels[level];
      Level next = new Level(Math.max(1, newWidth >> level), Math.max(1, newHeight >> level));
      if (old.pixels != null) {
        next.pixels = new byte[checkedBytes(next.width, next.height)];
        int copyWidth = Math.min(old.width, next.width);
        int copyHeight = Math.min(old.height, next.height);
        int rowBytes = Math.multiplyExact(copyWidth, 4);
        for (int row = 0; row < copyHeight; row++) {
          System.arraycopy(
              old.pixels, row * old.width * 4, next.pixels, row * next.width * 4, rowBytes);
        }
        next.dirty.addAll(old.dirty);
      }
      resized[level] = next;
    }
    width = newWidth;
    height = newHeight;
    levels = resized;
    revision++;
    return true;
  }

  public long projectedAllocatedCpuBytes(int newWidth, int newHeight) {
    if (newWidth < width || newHeight < height) {
      throw new IllegalArgumentException(
          "texture mirror growth cannot shrink "
              + width
              + "x"
              + height
              + " to "
              + newWidth
              + "x"
              + newHeight);
    }
    validateBaseSize(newWidth, newHeight);
    long total = 0;
    for (int level = 0; level < levels.length; level++) {
      if (levels[level].pixels == null) continue;
      long levelBytes =
          (long) Math.max(1, newWidth >> level) * Math.max(1, newHeight >> level) * 4L;
      total = Math.addExact(total, levelBytes);
    }
    return total;
  }

  public byte[] rgba() {
    return levels[0].ensurePixels();
  }

  public byte[] copyRegion(int x, int y, int regionWidth, int regionHeight) {
    return copyRegion(0, x, y, regionWidth, regionHeight);
  }

  public byte[] copyRegion(int level, int x, int y, int regionWidth, int regionHeight) {
    Level source = level(level);
    if (x < 0
        || y < 0
        || regionWidth <= 0
        || regionHeight <= 0
        || (long) x + regionWidth > source.width
        || (long) y + regionHeight > source.height) {
      throw new IllegalArgumentException("invalid texture region");
    }
    byte[] pixels = source.ensurePixels();
    byte[] region = new byte[checkedBytes(regionWidth, regionHeight)];
    int rowBytes = Math.multiplyExact(regionWidth, 4);
    for (int row = 0; row < regionHeight; row++) {
      int src = ((y + row) * source.width + x) * 4;
      int dst = row * rowBytes;
      System.arraycopy(pixels, src, region, dst, rowBytes);
    }
    return region;
  }

  public List<Upload> fullUploads() {
    List<Upload> uploads = new ArrayList<>();
    for (int level = 0; level <= maxLevel; level++) {
      Level source = levels[level];
      if (!source.hasPixels()) continue;
      uploads.add(new Upload(level, 0, 0, source.width, source.height, source.pixels));
    }
    return uploads;
  }

  public List<Upload> dirtyUploads() {
    List<Upload> uploads = new ArrayList<>();
    for (int level = 0; level <= maxLevel; level++) {
      Level source = levels[level];
      if (source.dirty.isEmpty() || !source.hasPixels()) continue;
      for (Dirty dirty : source.dirty) {
        uploads.add(
            new Upload(
                level,
                dirty.minX,
                dirty.minY,
                dirty.maxX - dirty.minX,
                dirty.maxY - dirty.minY,
                copyRegion(
                    level,
                    dirty.minX,
                    dirty.minY,
                    dirty.maxX - dirty.minX,
                    dirty.maxY - dirty.minY)));
      }
    }
    return uploads;
  }

  public void clearDirty() {
    for (Level level : levels) level.dirty.clear();
  }

  public boolean ensureMaxLevel(int requestedMaxLevel) {
    int target = Math.min(Math.max(0, requestedMaxLevel), maximumMipLevel(width, height));
    if (target <= maxLevel) return false;
    Level[] expanded = new Level[target + 1];
    System.arraycopy(levels, 0, expanded, 0, levels.length);
    for (int level = levels.length; level < expanded.length; level++) {
      expanded[level] = new Level(Math.max(1, width >> level), Math.max(1, height >> level));
    }
    levels = expanded;
    maxLevel = target;
    return true;
  }

  public long additionalBytesForUpload(int level) {
    if (level < 0) throw new IllegalArgumentException("invalid mip level: " + level);
    ensureMaxLevel(level);
    Level target = levels[level];
    long additional = target.pixels == null ? (long) target.width * target.height * 4L : 0L;
    long total = allocatedCpuBytes() + additional;
    if (total > MAX_BYTES_PER_TEXTURE) {
      throw new IllegalArgumentException(
          "texture CPU mirror including mip levels would require "
              + total
              + " bytes; per-texture safety limit is "
              + MAX_BYTES_PER_TEXTURE
              + " (override with -Ddx12mc.texture.maxBytes=...)");
    }
    return additional;
  }

  public boolean matchesDimensions(int width, int height) {
    return this.width == width && this.height == height;
  }

  public boolean matches(int width, int height, int requestedMaxLevel) {
    return matchesDimensions(width, height)
        && this.maxLevel
            >= Math.min(Math.max(0, requestedMaxLevel), maximumMipLevel(width, height));
  }

  public boolean hasBasePixels() {
    return levels[0].hasPixels();
  }

  public boolean hasCompleteMipChain() {
    if (maxLevel == 0) return false;
    for (Level level : levels) if (!level.hasPixels()) return false;
    return true;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int maxLevel() {
    return maxLevel;
  }

  public int levelWidth(int level) {
    return level(level).width;
  }

  public int levelHeight(int level) {
    return level(level).height;
  }

  public long revision() {
    return revision;
  }

  public boolean blur() {
    return blur;
  }

  public boolean clamp() {
    return clamp;
  }

  public boolean mipmap() {
    return mipmap && hasCompleteMipChain();
  }

  public long allocatedCpuBytes() {
    long total = 0;
    for (Level level : levels) if (level.pixels != null) total += level.pixels.length;
    return total;
  }

  private Level level(int level) {
    if (level < 0 || level > maxLevel)
      throw new IllegalArgumentException("invalid mip level: " + level);
    return levels[level];
  }

  private int computedLevelWidth(int level) {
    return Math.max(1, width >> level);
  }

  private int computedLevelHeight(int level) {
    return Math.max(1, height >> level);
  }

  private static int maximumMipLevel(int width, int height) {
    int size = Math.max(width, height);
    return 31 - Integer.numberOfLeadingZeros(size);
  }

  private static void validateBaseSize(int width, int height) {
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("invalid texture size " + width + "x" + height);
    if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
      throw new IllegalArgumentException(
          "texture size "
              + width
              + "x"
              + height
              + " exceeds DX12MC safety limit "
              + MAX_DIMENSION
              + " (override with -Ddx12mc.texture.maxDimension=...)");
    }
    long bytes = (long) width * height * 4L;
    if (bytes > MAX_BYTES_PER_TEXTURE || bytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "texture base level requires "
              + bytes
              + " bytes; safety limit is "
              + MAX_BYTES_PER_TEXTURE
              + " (override with -Ddx12mc.texture.maxBytes=...)");
    }
  }

  private static int checkedBytes(int width, int height) {
    long bytes = (long) width * height * 4L;
    if (bytes <= 0 || bytes > Integer.MAX_VALUE || bytes > MAX_BYTES_PER_TEXTURE) {
      throw new IllegalArgumentException(
          "RGBA region is too large: " + width + "x" + height + " (" + bytes + " bytes)");
    }
    return (int) bytes;
  }

  public record Upload(int level, int x, int y, int width, int height, byte[] rgba) {}

  private static final class Level {
    private final int width;
    private final int height;
    private static final int MAX_DIRTY_REGIONS = 64;
    private byte[] pixels;
    private final List<Dirty> dirty = new ArrayList<>();

    private Level(int width, int height) {
      this.width = width;
      this.height = height;
    }

    private boolean hasPixels() {
      return pixels != null;
    }

    private byte[] ensurePixels() {
      if (pixels == null) pixels = new byte[checkedBytes(width, height)];
      return pixels;
    }

    private void markDirty(int x, int y, int width, int height) {
      Dirty merged = new Dirty(x, y, x + width, y + height);
      for (int i = dirty.size() - 1; i >= 0; i--) {
        Dirty existing = dirty.get(i);
        if (!touches(existing, merged)) continue;
        merged = union(existing, merged);
        dirty.remove(i);
      }
      dirty.add(merged);
      if (dirty.size() > MAX_DIRTY_REGIONS) {
        Dirty all = dirty.get(0);
        for (int i = 1; i < dirty.size(); i++) all = union(all, dirty.get(i));
        dirty.clear();
        dirty.add(all);
      }
    }

    private static boolean touches(Dirty a, Dirty b) {
      return a.minX <= b.maxX + 1
          && a.maxX + 1 >= b.minX
          && a.minY <= b.maxY + 1
          && a.maxY + 1 >= b.minY;
    }

    private static Dirty union(Dirty a, Dirty b) {
      return new Dirty(
          Math.min(a.minX, b.minX),
          Math.min(a.minY, b.minY),
          Math.max(a.maxX, b.maxX),
          Math.max(a.maxY, b.maxY));
    }
  }

  private record Dirty(int minX, int minY, int maxX, int maxY) {}
}
