package net.dx12mc.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProfileTest {
  @Test
  void profilingIsOffUnlessRequested() {
    assertFalse(Profile.ENABLED);
  }

  @Test
  void drainReportsPerFrameValuesAndResets() {
    long started = Profile.start() - 4_000_000L;
    Profile.add(Profile.Section.REGION_REBUILD, started);
    Profile.count(Profile.Count.ARENA_UPLOADS, 3);
    String first = Profile.drain(2);
    assertTrue(first.contains(" region-rebuild 2."), first);
    assertTrue(first.contains(" arena-uploads 1.5"), first);
    String second = Profile.drain(2);
    assertTrue(second.contains(" region-rebuild 0.000"), second);
    assertTrue(second.contains(" arena-uploads 0.0"), second);
  }
}
