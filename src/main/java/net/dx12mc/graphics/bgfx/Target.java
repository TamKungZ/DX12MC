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

  private Target(short frameBuffer, short color, int width, int height, boolean depth) {
    this.frameBuffer = frameBuffer;
    this.color = color;
    this.width = width;
    this.height = height;
    this.depth = depth;
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
    if (BGFX_HANDLE_IS_VALID(frameBuffer)) {
      bgfx_destroy_frame_buffer(frameBuffer);
      frameBuffer = BGFX_INVALID_HANDLE;
      color = BGFX_INVALID_HANDLE;
    }
  }
}
