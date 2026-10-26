package net.dx12mc.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void onlyExplicitProbeModeAllowsOpenGlPresentation() {
    assertFalse(new Config(Mode.NATIVE, 0x18202AFF, true).openGlPresentationAllowed());
    assertFalse(new Config(Mode.DEBUG, 0x18202AFF, true).openGlPresentationAllowed());
    assertTrue(new Config(Mode.PROBE, 0x18202AFF, true).openGlPresentationAllowed());
  }

  @Test
  void legacyPostProcessingIsOnlyAvailableWhenOpenGlStillRenders() {
    assertFalse(new Config(Mode.NATIVE, 0x18202AFF, false).legacyPostProcessingAvailable());
    assertFalse(new Config(Mode.DEBUG, 0x18202AFF, false).legacyPostProcessingAvailable());
    assertTrue(new Config(Mode.PROBE, 0x18202AFF, false).legacyPostProcessingAvailable());
  }

  @Test
  void vsyncHasNoSecondSourceOfTruthInTheConfig() {
    for (var component : Config.class.getRecordComponents()) {
      assertNotEquals("vsync", component.getName());
    }
  }
}
