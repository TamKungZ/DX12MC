package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_COLOR;
import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_DEPTH;
import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_NONE;

public final class ViewSequence {
  public static final int GL_DEPTH_BUFFER_BIT = 0x0100;
  public static final int GL_COLOR_BUFFER_BIT = 0x4000;
  static final int MAX_VIEWS = 255;
  private static int current;
  private static boolean used;

  private ViewSequence() {}

  public static int current() {
    return current;
  }

  public static void used() {
    used = true;
  }

  static void reset() {
    current = Backend.MAIN_VIEW;
    used = false;
  }

  static boolean advanceIfUsed() {
    if (!used) return false;
    advance();
    return true;
  }

  private static void advance() {
    if (current + 1 >= MAX_VIEWS) {
      throw new IllegalStateException(
          "DX12MC needs more than " + MAX_VIEWS + " bgfx views for the clears of one frame");
    }
    current++;
    used = false;
  }

  public static int clearFlags(int glMask) {
    int flags = BGFX_CLEAR_NONE;
    if ((glMask & GL_COLOR_BUFFER_BIT) != 0) flags |= BGFX_CLEAR_COLOR;
    if ((glMask & GL_DEPTH_BUFFER_BIT) != 0) flags |= BGFX_CLEAR_DEPTH;
    return flags;
  }

  public static int rgba(float red, float green, float blue, float alpha) {
    return channel(red) << 24 | channel(green) << 16 | channel(blue) << 8 | channel(alpha);
  }

  private static int channel(float value) {
    return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
  }
}
