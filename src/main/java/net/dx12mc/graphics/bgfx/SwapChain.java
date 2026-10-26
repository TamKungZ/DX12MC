package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.BGFX_RESET_NONE;
import static org.lwjgl.bgfx.BGFX.BGFX_RESET_VSYNC;

import net.dx12mc.graphics.FrameSize;

public final class SwapChain {
  private FrameSize size = new FrameSize(1, 1);
  private boolean vsync;
  private boolean resetPending;
  private boolean presentedSinceReset;

  public void vsync(boolean enabled) {
    if (vsync == enabled) return;
    vsync = enabled;
    resetPending = true;
  }

  public boolean vsync() {
    return vsync;
  }

  public void size(FrameSize newSize) {
    if (newSize.equals(size)) return;
    size = newSize;
    resetPending = true;
  }

  public FrameSize size() {
    return size;
  }

  public int resetFlags() {
    return vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
  }

  public boolean takeResetPending() {
    boolean pending = resetPending;
    resetPending = false;
    if (pending) presentedSinceReset = false;
    return pending;
  }

  public void presented() {
    presentedSinceReset = true;
  }

  public boolean holdsPresentedFrame() {
    return presentedSinceReset;
  }
}
