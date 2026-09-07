package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImageTest {

  @Test
  void copyRegionPacksRowsWithoutFullTexturePadding() {
    Image image = new Image(3, 2, 0);
    byte[] pixels = image.rgba();
    for (int i = 0; i < pixels.length; i++) pixels[i] = (byte) i;
    byte[] region = image.copyRegion(1, 0, 2, 2);
    assertArrayEquals(
        new byte[] {
          4, 5, 6, 7, 8, 9, 10, 11,
          16, 17, 18, 19, 20, 21, 22, 23
        },
        region);
  }

  @Test
  void copyRegionRejectsOutOfBoundsBlocks() {
    Image image = new Image(4, 4, 0);
    assertThrows(IllegalArgumentException.class, () -> image.copyRegion(3, 3, 2, 2));
  }

  @Test
  void mipCapacityCanExpandWithoutDiscardingBasePixels() {
    Image image = new Image(8, 8, 0);
    byte[] base = image.rgba();
    base[0] = 42;
    assertTrue(image.ensureMaxLevel(3));
    assertEquals(3, image.maxLevel());
    assertSame(base, image.rgba());
    assertEquals(42, Byte.toUnsignedInt(image.rgba()[0]));
    assertEquals(4L * 4L * 4L, image.additionalBytesForUpload(1));
    assertFalse(image.ensureMaxLevel(2));
  }

  @Test
  void inferredTextureCanGrowWithoutLosingExistingPixels() {
    Image image = new Image(5, 8, 0);
    byte[] original = image.rgba();
    original[0] = 11;
    original[(7 * 5 + 4) * 4] = 22;
    assertTrue(image.growToFit(5, 17));
    assertEquals(5, image.width());
    assertEquals(17, image.height());
    assertEquals(11, Byte.toUnsignedInt(image.rgba()[0]));
    assertEquals(22, Byte.toUnsignedInt(image.rgba()[(7 * 5 + 4) * 4]));
  }

  @Test
  void growthProjectionCountsOnlyLevelsThatAlreadyOwnPixels() {
    Image image = new Image(8, 8, 3);
    image.rgba();
    assertEquals(16L * 8L * 4L, image.projectedAllocatedCpuBytes(16, 8));
  }
}
