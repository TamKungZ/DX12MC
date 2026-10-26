package net.dx12mc.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ScreenImagesTest {

  @Test
  void bgfxBgraTexelsBecomeOpaqueNativeImageAbgr() {
    ByteBuffer bgra = ByteBuffer.wrap(new byte[] {0x10, 0x20, 0x30, 0x00});
    assertEquals(0xFF102030, ScreenImages.opaqueAbgr(bgra, 0));
  }

  @Test
  void rowsStayTopDownUnlessBgfxReportsAFlippedCapture() {
    assertEquals(0, ScreenImages.sourceRow(0, 720, false));
    assertEquals(719, ScreenImages.sourceRow(719, 720, false));
    assertEquals(719, ScreenImages.sourceRow(0, 720, true));
    assertEquals(0, ScreenImages.sourceRow(719, 720, true));
  }
}
