package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import net.dx12mc.bridge.Stats;
import org.junit.jupiter.api.Test;

class RegistryMipLevelTest {
  private static final int ATLAS = 7;

  @Test
  void loweringMipmapLevelsDropsTheStaleMipChainOfAnUnchangedAtlas() {
    Registry registry = new Registry(new Stats());
    registry.start();
    registry.allocate(ATLAS, 2, 16, 16);
    registry.bind(ATLAS);
    uploadChain(registry, 2);
    assertEquals((256 + 64 + 16) * 4L, registry.allocatedCpuBytes());

    registry.allocate(ATLAS, 0, 16, 16);

    assertEquals(256 * 4L, registry.allocatedCpuBytes());
    try (NativeImage level1 = new NativeImage(NativeImage.Format.RGBA, 8, 8, true)) {
      assertFalse(registry.upload(level1, 1, 0, 0, 0, 0, 8, 8, false, false, true));
    }
  }

  @Test
  void raisingMipmapLevelsAgainAcceptsTheNewMipChain() {
    Registry registry = new Registry(new Stats());
    registry.start();
    registry.allocate(ATLAS, 0, 16, 16);
    registry.bind(ATLAS);
    uploadChain(registry, 0);

    registry.allocate(ATLAS, 2, 16, 16);

    try (NativeImage level2 = new NativeImage(NativeImage.Format.RGBA, 4, 4, true)) {
      assertTrue(registry.upload(level2, 2, 0, 0, 0, 0, 4, 4, false, false, true));
    }
  }

  @Test
  void imageMaxLevelFollowsTheAuthoritativeRequestInBothDirections() {
    Image image = new Image(16, 16, 4);
    assertTrue(image.setMaxLevel(1));
    assertEquals(1, image.maxLevel());
    assertFalse(image.setMaxLevel(1));
    assertTrue(image.setMaxLevel(3));
    assertEquals(3, image.maxLevel());
    assertTrue(image.setMaxLevel(99));
    assertEquals(4, image.maxLevel());
  }

  private static void uploadChain(Registry registry, int maxLevel) {
    for (int level = 0; level <= maxLevel; level++) {
      int size = 16 >> level;
      try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, true)) {
        assertTrue(registry.upload(image, level, 0, 0, 0, 0, size, size, false, false, true));
      }
    }
  }
}
