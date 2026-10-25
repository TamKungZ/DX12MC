package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.dx12mc.bridge.Stats;
import org.junit.jupiter.api.Test;

class RegistryNotReadyTest {
  @Test
  void allocatedTextureWithoutPixelsIsNotReady() {
    Registry registry = new Registry(new Stats());
    registry.start();
    registry.allocate(44, 0, 256, 256);
    TextureNotReadyException thrown =
        assertThrows(TextureNotReadyException.class, () -> registry.resolve(44));
    org.junit.jupiter.api.Assertions.assertEquals(44, thrown.textureId());
  }

  @Test
  void unknownTextureResolvesToNull() {
    Registry registry = new Registry(new Stats());
    registry.start();
    assertNull(registry.resolve(99));
  }

  @Test
  void missingTextureDiagnosticsReportRecentDeletion() {
    Registry registry = new Registry(new Stats());
    registry.start();
    registry.allocate(44, 0, 16, 16);
    registry.delete(44);
    registry.onFrameBoundary();
    registry.onFrameBoundary();
    String description = registry.describeMissing(44);
    assertTrue(description.contains("deleted 2 frames ago"), description);
  }
}
