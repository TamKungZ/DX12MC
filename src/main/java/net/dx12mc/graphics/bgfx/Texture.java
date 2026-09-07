package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import org.lwjgl.bgfx.BGFXMemory;

public final class Texture implements AutoCloseable {
  private short handle;
  private int samplerFlags;
  private final int width;
  private final int height;
  private final int maxLevel;

  private Texture(short handle, int width, int height, int maxLevel, int samplerFlags) {
    this.handle = handle;
    this.width = width;
    this.height = height;
    this.maxLevel = maxLevel;
    this.samplerFlags = samplerFlags;
  }

  public static Texture create(
      int width, int height, int maxLevel, boolean blur, boolean clamp, boolean mipmap) {
    if (width <= 0 || height <= 0 || width > 0xFFFF || height > 0xFFFF) {
      throw new IllegalArgumentException("invalid bgfx texture size " + width + "x" + height);
    }
    int safeMaxLevel = Math.max(0, maxLevel);
    int sampler = samplerFlags(blur, clamp, mipmap);
    short handle =
        bgfx_create_texture_2d(
            width, height, safeMaxLevel > 0, 1, BGFX_TEXTURE_FORMAT_RGBA8, sampler, null);
    if (!BGFX_HANDLE_IS_VALID(handle)) {
      throw new IllegalStateException(
          "bgfx rejected mutable RGBA8 texture " + width + "x" + height + " mips=" + safeMaxLevel);
    }
    return new Texture(handle, width, height, safeMaxLevel, sampler);
  }

  public void update(
      int level,
      int x,
      int y,
      int width,
      int height,
      byte[] rgba,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    update(level, x, y, width, height, rgba, 0, blur, clamp, mipmap);
  }

  public void update(
      int level,
      int x,
      int y,
      int width,
      int height,
      byte[] rgba,
      int sourceOffset,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    if (!valid()) throw new IllegalStateException("texture is closed");
    if (level < 0 || level > maxLevel)
      throw new IllegalArgumentException("invalid mip level " + level + " > " + maxLevel);
    int bytes = validateRegion(level, x, y, width, height, rgba, sourceOffset);
    samplerFlags = samplerFlags(blur, clamp, mipmap);
    BGFXMemory memory =
        StagingMemory.allocate(bytes, target -> target.put(rgba, sourceOffset, bytes));
    bgfx_update_texture_2d(handle, 0, level, x, y, width, height, memory, 0xFFFF);
  }

  public short handle() {
    if (!valid()) throw new IllegalStateException("texture is closed");
    return handle;
  }

  public int samplerFlags() {
    return samplerFlags;
  }

  public boolean valid() {
    return BGFX_HANDLE_IS_VALID(handle);
  }

  @Override
  public void close() {
    if (BGFX_HANDLE_IS_VALID(handle)) {
      bgfx_destroy_texture(handle);
      handle = BGFX_INVALID_HANDLE;
    }
  }

  private int validateRegion(
      int level, int x, int y, int regionWidth, int regionHeight, byte[] rgba, int sourceOffset) {
    int levelWidth = Math.max(1, width >> level);
    int levelHeight = Math.max(1, height >> level);
    long expected = (long) regionWidth * regionHeight * 4L;
    if (x < 0
        || y < 0
        || regionWidth <= 0
        || regionHeight <= 0
        || (long) x + regionWidth > levelWidth
        || (long) y + regionHeight > levelHeight
        || rgba == null
        || expected > Integer.MAX_VALUE
        || sourceOffset < 0
        || (long) sourceOffset + expected > (rgba == null ? 0 : rgba.length)) {
      throw new IllegalArgumentException(
          "invalid RGBA texture update: "
              + describe(level, x, y, regionWidth, regionHeight, rgba == null ? -1 : rgba.length)
              + ", sourceOffset="
              + sourceOffset
              + ", levelSize="
              + levelWidth
              + "x"
              + levelHeight);
    }
    return (int) expected;
  }

  private String describe(int level, int x, int y, int width, int height, int bytes) {
    return "texture="
        + this.width
        + "x"
        + this.height
        + ", level="
        + level
        + ", region="
        + x
        + ","
        + y
        + " "
        + width
        + "x"
        + height
        + ", bytes="
        + bytes;
  }

  private static int samplerFlags(boolean blur, boolean clamp, boolean mipmap) {
    int flags = BGFX_SAMPLER_NONE;
    if (clamp) flags |= BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
    if (!blur) flags |= BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT;
    if (mipmap && !blur) flags |= BGFX_SAMPLER_MIP_POINT;
    return flags;
  }
}
