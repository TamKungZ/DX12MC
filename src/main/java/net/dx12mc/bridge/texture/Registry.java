package net.dx12mc.bridge.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.dx12mc.bridge.Stats;
import net.dx12mc.graphics.bgfx.Texture;

public final class Registry implements AutoCloseable {
  private static final long MAX_TOTAL_CPU_BYTES =
      Long.getLong("dx12mc.texture.maxTotalBytes", 512L * 1024L * 1024L);
  private final Map<Integer, TextureEntry> entries = new HashMap<>();
  private static final int DELETED_HISTORY = 32;
  private final int[] deletedIds = new int[DELETED_HISTORY];
  private final long[] deletedFrames = new long[DELETED_HISTORY];
  private int deletedCursor;
  private final Stats stats;
  private int boundId;
  private boolean started;
  private long cpuBytes;
  private long frameBoundary;
  private int cachedResolvedId;
  private TextureEntry cachedResolvedEntry;

  public Registry(Stats stats) {
    this.stats = stats;
  }

  public void start() {
    started = true;
  }

  public void bind(int id) {
    boundId = id;
  }

  public void allocate(int id, int maxLevel, int width, int height) {
    if (id <= 0 || width <= 0 || height <= 0) return;
    if (cachedResolvedId == id) clearResolvedCache();
    TextureEntry existing = entries.get(id);
    if (existing == null) {
      entries.put(id, new TextureEntry(new Image(width, height, maxLevel), true, stats));
      return;
    }
    if (existing.image.matchesDimensions(width, height)) {
      if (existing.image.ensureMaxLevel(maxLevel)) existing.close();
      existing.authoritativeDimensions = true;
      return;
    }
    if (!existing.authoritativeDimensions) {
      if (width < existing.image.width() || height < existing.image.height()) {
        throw new IllegalStateException(
            "authoritative texture allocation is smaller than previously mirrored upload extent:"
                + " textureId="
                + id
                + ", inferred="
                + existing.image.width()
                + "x"
                + existing.image.height()
                + ", authoritative="
                + width
                + "x"
                + height);
      }
      growInferred(existing, width, height, id, 0);
      if (existing.image.ensureMaxLevel(maxLevel)) existing.close();
      existing.authoritativeDimensions = true;
      return;
    }
    existing.close();
    cpuBytes -= existing.image.allocatedCpuBytes();
    entries.put(id, new TextureEntry(new Image(width, height, maxLevel), true, stats));
  }

  public void allocateBoundLevel(int level, int width, int height) {
    if (boundId <= 0) {
      throw new IllegalStateException("OpenGL texture allocation has no tracked bound texture id");
    }
    if (level < 0 || width <= 0 || height <= 0) {
      throw new IllegalArgumentException(
          "invalid raw texture allocation: level=" + level + ", size=" + width + "x" + height);
    }
    if (cachedResolvedId == boundId) clearResolvedCache();
    TextureEntry entry = entries.get(boundId);
    if (entry == null) {
      if (level != 0) {
        throw new IllegalStateException(
            "raw mip allocation arrived before base level for textureId="
                + boundId
                + ", level="
                + level
                + ", size="
                + width
                + "x"
                + height);
      }
      entries.put(boundId, new TextureEntry(new Image(width, height, 0), true, stats));
      return;
    }
    if (entry.image.ensureMaxLevel(level)) entry.close();
    if (entry.image.levelWidth(level) != width || entry.image.levelHeight(level) != height) {
      if (level == 0) {
        entry.close();
        cpuBytes -= entry.image.allocatedCpuBytes();
        entries.put(boundId, new TextureEntry(new Image(width, height, 0), true, stats));
        return;
      }
      throw new IllegalStateException(
          "raw mip allocation dimensions disagree with mirrored base texture: textureId="
              + boundId
              + ", level="
              + level
              + ", expected="
              + entry.image.levelWidth(level)
              + "x"
              + entry.image.levelHeight(level)
              + ", actual="
              + width
              + "x"
              + height);
    }
  }

  public boolean upload(
      NativeImage source,
      int level,
      int offsetX,
      int offsetY,
      int skipPixels,
      int skipRows,
      int width,
      int height,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    if (boundId <= 0 || source == null) return false;
    if (level < 0 || offsetX < 0 || offsetY < 0 || width <= 0 || height <= 0) return false;
    if (skipPixels < 0
        || skipRows < 0
        || (long) skipPixels + width > source.getWidth()
        || (long) skipRows + height > source.getHeight()) return false;
    TextureEntry entry = entries.get(boundId);
    if (entry == null) {
      if (level != 0) {
        throw new IllegalStateException(
            "mip upload arrived before base dimensions were known: textureId="
                + boundId
                + ", level="
                + level
                + ", dst="
                + offsetX
                + ","
                + offsetY
                + " "
                + width
                + "x"
                + height);
      }
      int inferredWidth = checkedExtent(offsetX, width, "width");
      int inferredHeight = checkedExtent(offsetY, height, "height");
      entry = new TextureEntry(new Image(inferredWidth, inferredHeight, 0), false, stats);
      entries.put(boundId, entry);
    } else {
      if (level > entry.image.maxLevel()) {
        if (entry.authoritativeDimensions) return false;
        int minimumAxis = minimumBaseAxisForMipLevel(level);
        if (entry.image.width() >= entry.image.height()) {
          growInferred(entry, minimumAxis, entry.image.height(), boundId, level);
        } else {
          growInferred(entry, entry.image.width(), minimumAxis, boundId, level);
        }
      }
      if (entry.image.ensureMaxLevel(level)) entry.close();
    }
    int requiredLevelWidth = checkedExtent(offsetX, width, "width");
    int requiredLevelHeight = checkedExtent(offsetY, height, "height");
    if (requiredLevelWidth > entry.image.levelWidth(level)
        || requiredLevelHeight > entry.image.levelHeight(level)) {
      if (entry.authoritativeDimensions) return false;
      int minimumBaseWidth = minimumBaseDimensionForLevelExtent(requiredLevelWidth, level);
      int minimumBaseHeight = minimumBaseDimensionForLevelExtent(requiredLevelHeight, level);
      growInferred(entry, minimumBaseWidth, minimumBaseHeight, boundId, level);
    }
    long additional = entry.image.additionalBytesForUpload(level);
    ensureRegistryBudget(additional, boundId, level);
    long before = entry.image.allocatedCpuBytes();
    try {
      return entry.image.upload(
          source,
          level,
          offsetX,
          offsetY,
          skipPixels,
          skipRows,
          width,
          height,
          blur,
          clamp,
          mipmap);
    } finally {
      cpuBytes += entry.image.allocatedCpuBytes() - before;
    }
  }

  public Texture resolve(int id) {
    if (!started || id <= 0) return null;
    TextureEntry entry =
        id == cachedResolvedId && cachedResolvedEntry != null
            ? cachedResolvedEntry
            : entries.get(id);
    if (entry == null) return null;
    if (!entry.image.hasBasePixels()) throw new TextureNotReadyException(id);
    cachedResolvedId = id;
    cachedResolvedEntry = entry;
    Texture ready = entry.currentIfReady();
    if (ready != null) return ready;
    return entry.resolve(id, frameBoundary, allocatedCpuBytes());
  }

  public String describeMissing(int id) {
    StringBuilder text = new StringBuilder("started=").append(started);
    text.append(", mirrored=").append(entries.size()).append(", bound=").append(boundId);
    for (int i = 0; i < DELETED_HISTORY; i++) {
      if (deletedIds[i] == id && id > 0) {
        text.append(", deleted ").append(frameBoundary - deletedFrames[i]).append(" frames ago");
        break;
      }
    }
    return text.toString();
  }

  public void delete(int id) {
    TextureEntry entry = entries.remove(id);
    deletedIds[deletedCursor] = id;
    deletedFrames[deletedCursor] = frameBoundary;
    deletedCursor = (deletedCursor + 1) % DELETED_HISTORY;
    if (entry != null) {
      entry.close();
      cpuBytes -= entry.image.allocatedCpuBytes();
    }
    if (cachedResolvedId == id) clearResolvedCache();
    if (boundId == id) boundId = 0;
  }

  public int mirroredCount() {
    return entries.size();
  }

  public long allocatedCpuBytes() {
    return Math.max(0, cpuBytes);
  }

  public void onFrameBoundary() {
    frameBoundary++;
  }

  @Override
  public void close() {
    for (TextureEntry entry : entries.values()) entry.close();
    entries.clear();
    boundId = 0;
    started = false;
    cpuBytes = 0;
    frameBoundary = 0;
    clearResolvedCache();
  }

  private void clearResolvedCache() {
    cachedResolvedId = 0;
    cachedResolvedEntry = null;
  }

  private void growInferred(
      TextureEntry entry, int minimumWidth, int minimumHeight, int textureId, int level) {
    if (entry.authoritativeDimensions) {
      throw new IllegalStateException(
          "attempted to grow authoritative texture dimensions: textureId=" + textureId);
    }
    int newWidth = Math.max(entry.image.width(), minimumWidth);
    int newHeight = Math.max(entry.image.height(), minimumHeight);
    if (newWidth == entry.image.width() && newHeight == entry.image.height()) return;
    long before = entry.image.allocatedCpuBytes();
    long projected = entry.image.projectedAllocatedCpuBytes(newWidth, newHeight);
    ensureRegistryBudget(projected - before, textureId, level);
    entry.close();
    entry.image.growToFit(newWidth, newHeight);
    cpuBytes += entry.image.allocatedCpuBytes() - before;
  }

  private void ensureRegistryBudget(long additional, int textureId, int level) {
    if (additional <= 0) return;
    long projected;
    try {
      projected = Math.addExact(cpuBytes, additional);
    } catch (ArithmeticException overflow) {
      throw new IllegalStateException("DX12MC texture CPU mirror byte counter overflow", overflow);
    }
    if (projected > MAX_TOTAL_CPU_BYTES) {
      throw new IllegalStateException(
          "DX12MC texture CPU mirror budget exceeded: current="
              + cpuBytes
              + ", additional="
              + additional
              + ", limit="
              + MAX_TOTAL_CPU_BYTES
              + ", textureId="
              + textureId
              + ", level="
              + level
              + ". Override with -Ddx12mc.texture.maxTotalBytes=... if intentional.");
    }
  }

  private static int checkedExtent(int offset, int size, String axis) {
    long extent = (long) offset + size;
    if (offset < 0 || size <= 0 || extent <= 0 || extent > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "invalid texture " + axis + " extent: offset=" + offset + ", size=" + size);
    }
    return (int) extent;
  }

  private static int minimumBaseAxisForMipLevel(int level) {
    if (level < 0 || level > 30) throw new IllegalArgumentException("invalid mip level: " + level);
    long size = 1L << level;
    if (size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("mip level is too large: " + level);
    return (int) size;
  }

  static int minimumBaseDimensionForLevelExtent(int levelExtent, int level) {
    if (levelExtent <= 0 || level < 0) throw new IllegalArgumentException("invalid mip extent");
    if (level == 0 || levelExtent == 1) return levelExtent;
    long base = (long) levelExtent << Math.min(level, 30);
    if (base <= 0 || base > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "cannot infer base dimension from level=" + level + ", extent=" + levelExtent);
    }
    return (int) base;
  }
}
