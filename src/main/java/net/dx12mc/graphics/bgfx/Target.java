package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ShortBuffer;
import org.lwjgl.system.MemoryStack;

public final class Target implements AutoCloseable {
  private static final long COLOR_FLAGS =
      BGFX_TEXTURE_RT | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
  private short frameBuffer;
  private short color;
  private final int width;
  private final int height;
  private final boolean depth;
  private boolean needsClear = true;
  private short depthTexture = BGFX_INVALID_HANDLE;
  private short[] extras = new short[0];
  private short mrtFrameBuffer = BGFX_INVALID_HANDLE;
  private short extrasFrameBuffer = BGFX_INVALID_HANDLE;
  private Target colorOnly;
  private boolean ownsTextures;

  private Target(short frameBuffer, short color, int width, int height, boolean depth) {
    this.frameBuffer = frameBuffer;
    this.color = color;
    this.width = width;
    this.height = height;
    this.depth = depth;
  }

  public static Target createColor(int width, int height, int format) {
    checkSize(width, height);
    short color = bgfx_create_texture_2d(width, height, false, 1, format, COLOR_FLAGS, null);
    if (!BGFX_HANDLE_IS_VALID(color)) {
      throw new IllegalStateException("bgfx rejected pack target " + width + "x" + height);
    }
    short frameBuffer = frameBuffer(false, color);
    if (!BGFX_HANDLE_IS_VALID(frameBuffer)) {
      bgfx_destroy_texture(color);
      throw new IllegalStateException("bgfx rejected pack target frame buffer");
    }
    Target target = new Target(frameBuffer, color, width, height, false);
    target.ownsTextures = true;
    return target;
  }

  public static Target createScene(int width, int height, int[] extraFormats) {
    checkSize(width, height);
    Target target = null;
    short color = BGFX_INVALID_HANDLE;
    short depthTexture = BGFX_INVALID_HANDLE;
    short[] extras = new short[extraFormats.length];
    java.util.Arrays.fill(extras, BGFX_INVALID_HANDLE);
    try {
      color =
          bgfx_create_texture_2d(
              width, height, false, 1, BGFX_TEXTURE_FORMAT_RGBA8, COLOR_FLAGS, null);
      depthTexture =
          bgfx_create_texture_2d(
              width, height, false, 1, BGFX_TEXTURE_FORMAT_D24S8, COLOR_FLAGS, null);
      for (int i = 0; i < extras.length; i++) {
        extras[i] =
            bgfx_create_texture_2d(width, height, false, 1, extraFormats[i], COLOR_FLAGS, null);
      }
      requireValid(color, depthTexture);
      for (short extra : extras) requireValid(extra);
      target = new Target(frameBuffer(false, color, depthTexture), color, width, height, true);
      target.depthTexture = depthTexture;
      target.extras = extras;
      target.ownsTextures = true;
      color = depthTexture = BGFX_INVALID_HANDLE;
      requireValid(target.frameBuffer);
      Target colorOnly =
          new Target(frameBuffer(false, target.color), target.color, width, height, false);
      target.colorOnly = colorOnly;
      requireValid(colorOnly.frameBuffer);
      if (extras.length > 0) {
        short[] all = new short[extras.length + 2];
        all[0] = target.color;
        System.arraycopy(extras, 0, all, 1, extras.length);
        all[all.length - 1] = target.depthTexture;
        target.mrtFrameBuffer = frameBuffer(false, all);
        target.extrasFrameBuffer = frameBuffer(false, extras);
        requireValid(target.mrtFrameBuffer, target.extrasFrameBuffer);
      }
      return target;
    } catch (Throwable throwable) {
      if (target != null) {
        target.close();
      } else {
        for (short handle : extras) if (BGFX_HANDLE_IS_VALID(handle)) bgfx_destroy_texture(handle);
      }
      if (BGFX_HANDLE_IS_VALID(color)) bgfx_destroy_texture(color);
      if (BGFX_HANDLE_IS_VALID(depthTexture)) bgfx_destroy_texture(depthTexture);
      throw throwable;
    }
  }

  private static void checkSize(int width, int height) {
    if (width <= 0 || height <= 0 || width > 0xFFFF || height > 0xFFFF) {
      throw new IllegalArgumentException("invalid bgfx render target size " + width + "x" + height);
    }
  }

  private static void requireValid(short first, short... more) {
    if (!BGFX_HANDLE_IS_VALID(first))
      throw new IllegalStateException("bgfx rejected a scene target");
    for (short handle : more) {
      if (!BGFX_HANDLE_IS_VALID(handle))
        throw new IllegalStateException("bgfx rejected a scene target");
    }
  }

  private static short frameBuffer(boolean destroyTextures, short... handles) {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ShortBuffer buffer = stack.mallocShort(handles.length);
      buffer.put(handles).flip();
      return bgfx_create_frame_buffer_from_handles(buffer, destroyTextures);
    }
  }

  public static Target create(int width, int height, boolean depth) {
    if (width <= 0 || height <= 0 || width > 0xFFFF || height > 0xFFFF) {
      throw new IllegalArgumentException("invalid bgfx render target size " + width + "x" + height);
    }
    short color =
        bgfx_create_texture_2d(
            width, height, false, 1, BGFX_TEXTURE_FORMAT_RGBA8, COLOR_FLAGS, null);
    if (!BGFX_HANDLE_IS_VALID(color)) {
      throw new IllegalStateException("bgfx rejected render target colour " + width + "x" + height);
    }
    short depthTexture = BGFX_INVALID_HANDLE;
    if (depth) {
      depthTexture =
          bgfx_create_texture_2d(
              width, height, false, 1, BGFX_TEXTURE_FORMAT_D24S8, BGFX_TEXTURE_RT_WRITE_ONLY, null);
      if (!BGFX_HANDLE_IS_VALID(depthTexture)) {
        bgfx_destroy_texture(color);
        throw new IllegalStateException(
            "bgfx rejected render target depth " + width + "x" + height);
      }
    }
    short frameBuffer;
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ShortBuffer handles = stack.mallocShort(depth ? 2 : 1);
      handles.put(color);
      if (depth) handles.put(depthTexture);
      handles.flip();
      frameBuffer = bgfx_create_frame_buffer_from_handles(handles, true);
    }
    if (!BGFX_HANDLE_IS_VALID(frameBuffer)) {
      bgfx_destroy_texture(color);
      if (depth) bgfx_destroy_texture(depthTexture);
      throw new IllegalStateException("bgfx rejected render target frame buffer");
    }
    return new Target(frameBuffer, color, width, height, depth);
  }

  public short frameBuffer() {
    if (!valid()) throw new IllegalStateException("render target is closed");
    return frameBuffer;
  }

  public short colorTexture() {
    if (!valid()) throw new IllegalStateException("render target is closed");
    return color;
  }

  public short depthTexture() {
    if (!valid() || !BGFX_HANDLE_IS_VALID(depthTexture)) {
      throw new IllegalStateException("render target has no readable depth");
    }
    return depthTexture;
  }

  public short attachment(int index) {
    if (!valid()) throw new IllegalStateException("render target is closed");
    return extras[index];
  }

  public int attachments() {
    return extras.length;
  }

  public boolean hasMrt() {
    return BGFX_HANDLE_IS_VALID(mrtFrameBuffer);
  }

  public short mrtFrameBuffer() {
    if (!valid() || !hasMrt()) throw new IllegalStateException("render target has no G-buffer");
    return mrtFrameBuffer;
  }

  public short extrasFrameBuffer() {
    if (!valid() || !hasMrt()) throw new IllegalStateException("render target has no G-buffer");
    return extrasFrameBuffer;
  }

  public Target colorOnly() {
    if (colorOnly == null) throw new IllegalStateException("render target has no colour-only view");
    return colorOnly;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public boolean hasDepth() {
    return depth;
  }

  public boolean takeNeedsClear() {
    boolean pending = needsClear;
    needsClear = false;
    return pending;
  }

  public boolean valid() {
    return BGFX_HANDLE_IS_VALID(frameBuffer);
  }

  @Override
  public void close() {
    if (colorOnly != null) {
      colorOnly.close();
      colorOnly = null;
    }
    if (BGFX_HANDLE_IS_VALID(mrtFrameBuffer)) bgfx_destroy_frame_buffer(mrtFrameBuffer);
    if (BGFX_HANDLE_IS_VALID(extrasFrameBuffer)) bgfx_destroy_frame_buffer(extrasFrameBuffer);
    mrtFrameBuffer = extrasFrameBuffer = BGFX_INVALID_HANDLE;
    if (BGFX_HANDLE_IS_VALID(frameBuffer)) bgfx_destroy_frame_buffer(frameBuffer);
    frameBuffer = BGFX_INVALID_HANDLE;
    if (ownsTextures) {
      if (BGFX_HANDLE_IS_VALID(color)) bgfx_destroy_texture(color);
      if (BGFX_HANDLE_IS_VALID(depthTexture)) bgfx_destroy_texture(depthTexture);
      for (short extra : extras) if (BGFX_HANDLE_IS_VALID(extra)) bgfx_destroy_texture(extra);
    }
    color = BGFX_INVALID_HANDLE;
    depthTexture = BGFX_INVALID_HANDLE;
    extras = new short[0];
  }
}
