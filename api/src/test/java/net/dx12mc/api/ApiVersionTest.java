package net.dx12mc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ApiVersionTest {
  @Test
  void experimentalVersionsOnlySatisfyTheSameMinor() {
    ApiVersion running = ApiVersion.parse("0.1");
    assertTrue(running.satisfies(new ApiVersion(0, 1)));
    assertFalse(running.satisfies(new ApiVersion(0, 2)));
    assertFalse(new ApiVersion(0, 2).satisfies(running));
  }

  @Test
  void stableVersionsSatisfyOlderMinors() {
    assertTrue(new ApiVersion(1, 3).satisfies(new ApiVersion(1, 1)));
    assertFalse(new ApiVersion(1, 1).satisfies(new ApiVersion(1, 3)));
    assertFalse(new ApiVersion(2, 0).satisfies(new ApiVersion(1, 0)));
  }

  @Test
  void parseRejectsMalformedText() {
    assertEquals("0.1", ApiVersion.parse("0.1").toString());
    assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1"));
    assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1."));
    assertThrows(IllegalArgumentException.class, () -> new ApiVersion(-1, 0));
  }
}
