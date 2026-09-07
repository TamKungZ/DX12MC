package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegistryDimensionTest {

  @Test
  void destinationExtentAtLevelZeroIsNotSourceImageSize() {
    assertEquals(17, Registry.minimumBaseDimensionForLevelExtent(17, 0));
  }

  @Test
  void mipExtentCanRequireBaseGrowth() {
    assertEquals(16, Registry.minimumBaseDimensionForLevelExtent(4, 2));
    assertEquals(1, Registry.minimumBaseDimensionForLevelExtent(1, 3));
  }
}
