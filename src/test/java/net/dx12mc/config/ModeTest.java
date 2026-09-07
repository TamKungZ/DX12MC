package net.dx12mc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModeTest {

  @Test
  void defaultsToNative() {
    assertEquals(Mode.NATIVE, Mode.parse(null));
    assertEquals(Mode.NATIVE, Mode.parse(""));
    assertEquals(Mode.NATIVE, Mode.parse("something-unknown"));
  }

  @Test
  void acceptsDebugAliases() {
    assertEquals(Mode.DEBUG, Mode.parse("debug"));
    assertEquals(Mode.DEBUG, Mode.parse(" TAKEOVER "));
    assertEquals(Mode.DEBUG, Mode.parse("diagnostic"));
  }

  @Test
  void acceptsProbeAliases() {
    assertEquals(Mode.PROBE, Mode.parse("probe"));
    assertEquals(Mode.PROBE, Mode.parse("CHECK"));
  }
}
