package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PixelsTest {

  @Test
  void convertsPackedAbgrToRgbaBytes() {
    byte[] out = new byte[4];
    Pixels.writeAbgrAsRgba(out, 0, 0x44332211);
    assertArrayEquals(new byte[] {0x11, 0x22, 0x33, 0x44}, out);
  }

  @Test
  void rejectsOutOfRangeDestination() {
    assertThrows(
        IndexOutOfBoundsException.class, () -> Pixels.writeAbgrAsRgba(new byte[3], 0, 0xFFFFFFFF));
  }
}
