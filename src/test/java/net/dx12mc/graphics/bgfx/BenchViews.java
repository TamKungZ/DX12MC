package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

public final class BenchViews {
  private BenchViews() {}

  public static int current() {
    return ViewSequence.current();
  }

  public static void split(int width, int height) {
    if (!ViewSequence.advanceIfUsed()) return;
    int view = ViewSequence.current();
    bgfx_set_view_mode(view, BGFX_VIEW_MODE_SEQUENTIAL);
    bgfx_set_view_rect(view, 0, 0, width, height);
    bgfx_set_view_clear(view, BGFX_CLEAR_NONE, 0, 1.0f, 0);
    bgfx_touch(view);
  }
}
