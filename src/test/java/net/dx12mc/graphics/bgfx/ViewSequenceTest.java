package net.dx12mc.graphics.bgfx;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_COLOR;
import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_DEPTH;
import static org.lwjgl.bgfx.BGFX.BGFX_CLEAR_NONE;

import org.junit.jupiter.api.Test;

class ViewSequenceTest {

  @Test
  void handDepthClearAfterWorldDrawsStartsANewViewSoTheClearRunsBetweenThem() {
    ViewSequence.reset();
    assertFalse(ViewSequence.advanceIfUsed(), "clear before any draw reuses the frame view");
    assertEquals(Backend.MAIN_VIEW, ViewSequence.current());
    ViewSequence.used();
    assertTrue(ViewSequence.advanceIfUsed(), "world drawn: hand depth clear needs a new view");
    assertEquals(Backend.MAIN_VIEW + 1, ViewSequence.current());
    assertFalse(ViewSequence.advanceIfUsed(), "two clears without draws share one view");
    ViewSequence.used();
    assertTrue(ViewSequence.advanceIfUsed());
    assertEquals(Backend.MAIN_VIEW + 2, ViewSequence.current());
    ViewSequence.reset();
    assertEquals(Backend.MAIN_VIEW, ViewSequence.current());
  }

  @Test
  void runawayClearsFailInsteadOfWrappingViewIds() {
    ViewSequence.reset();
    for (int i = 1; i < ViewSequence.MAX_VIEWS; i++) {
      ViewSequence.used();
      ViewSequence.advanceIfUsed();
    }
    ViewSequence.used();
    assertThrows(IllegalStateException.class, ViewSequence::advanceIfUsed);
    ViewSequence.reset();
  }

  @Test
  void glClearMasksMapToBgfxClearFlags() {
    assertEquals(BGFX_CLEAR_DEPTH, ViewSequence.clearFlags(0x0100));
    assertEquals(BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH, ViewSequence.clearFlags(0x4100));
    assertEquals(BGFX_CLEAR_COLOR, ViewSequence.clearFlags(0x4000));
    assertEquals(BGFX_CLEAR_NONE, ViewSequence.clearFlags(0x0400));
  }

  @Test
  void clearColorIsPackedAsBgfxRgba() {
    assertEquals(0xFF8000FF, ViewSequence.rgba(1.0f, 0.5f, 0.0f, 1.0f));
    assertEquals(0x00000000, ViewSequence.rgba(-1.0f, 0.0f, 0.0f, 0.0f));
  }
}
