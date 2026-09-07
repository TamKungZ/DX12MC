package net.dx12mc.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void onlyExplicitProbeModeAllowsOpenGlPresentation() {
    assertFalse(new Config(Mode.NATIVE, true, 0x18202AFF, true).openGlPresentationAllowed());
    assertFalse(new Config(Mode.DEBUG, true, 0x18202AFF, true).openGlPresentationAllowed());
    assertTrue(new Config(Mode.PROBE, true, 0x18202AFF, true).openGlPresentationAllowed());
  }
}
