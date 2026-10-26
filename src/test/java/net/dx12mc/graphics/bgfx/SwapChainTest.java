package net.dx12mc.graphics.bgfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.bgfx.BGFX.BGFX_RESET_NONE;
import static org.lwjgl.bgfx.BGFX.BGFX_RESET_VSYNC;

import net.dx12mc.graphics.FrameSize;
import org.junit.jupiter.api.Test;

class SwapChainTest {

  @Test
  void vsyncChangeRequestsOneResetWithTheMatchingFlag() {
    SwapChain swapChain = new SwapChain();
    swapChain.size(new FrameSize(1280, 720));
    swapChain.takeResetPending();

    swapChain.vsync(true);
    assertTrue(swapChain.takeResetPending());
    assertEquals(BGFX_RESET_VSYNC, swapChain.resetFlags());
    assertFalse(swapChain.takeResetPending());

    swapChain.vsync(false);
    assertTrue(swapChain.takeResetPending());
    assertEquals(BGFX_RESET_NONE, swapChain.resetFlags());
  }

  @Test
  void screenCaptureWaitsUntilTheResetSwapChainHasPresentedAFrame() {
    SwapChain swapChain = new SwapChain();
    swapChain.size(new FrameSize(1280, 720));
    swapChain.takeResetPending();
    assertFalse(swapChain.holdsPresentedFrame());
    swapChain.presented();
    assertTrue(swapChain.holdsPresentedFrame());
    swapChain.vsync(true);
    swapChain.takeResetPending();
    assertFalse(swapChain.holdsPresentedFrame(), "VSync reset recreates the presented buffers");
    swapChain.presented();
    swapChain.size(new FrameSize(1920, 1080));
    swapChain.takeResetPending();
    assertFalse(swapChain.holdsPresentedFrame(), "resize and fullscreen recreate them too");
    swapChain.takeResetPending();
    swapChain.presented();
    assertTrue(swapChain.holdsPresentedFrame());
  }

  @Test
  void reapplyingTheSameVsyncValueDoesNotResetTheSwapChain() {
    SwapChain swapChain = new SwapChain();
    swapChain.vsync(true);
    swapChain.takeResetPending();

    swapChain.vsync(true);
    assertFalse(swapChain.takeResetPending());
  }

  @Test
  void vsyncRequestedBeforeInitializationIsKeptForTheInitialResetFlags() {
    SwapChain swapChain = new SwapChain();
    swapChain.vsync(true);
    swapChain.size(new FrameSize(800, 600));
    swapChain.takeResetPending();
    assertEquals(BGFX_RESET_VSYNC, swapChain.resetFlags());
    assertTrue(swapChain.vsync());
  }

  @Test
  void resizeStillRequestsAResetAndKeepsVsync() {
    SwapChain swapChain = new SwapChain();
    swapChain.vsync(true);
    swapChain.size(new FrameSize(800, 600));
    swapChain.takeResetPending();

    swapChain.size(new FrameSize(800, 600));
    assertFalse(swapChain.takeResetPending());
    swapChain.size(new FrameSize(1920, 1080));
    assertTrue(swapChain.takeResetPending());
    assertEquals(new FrameSize(1920, 1080), swapChain.size());
    assertEquals(BGFX_RESET_VSYNC, swapChain.resetFlags());
  }
}
