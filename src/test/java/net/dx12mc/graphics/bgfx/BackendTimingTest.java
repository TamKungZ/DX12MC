package net.dx12mc.graphics.bgfx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BackendTimingTest {

  @Test
  void gpuTicksConvertToNanoseconds() {
    assertEquals(4_000_000L, Backend.nanoseconds(40_000L, 10_000_000L));
    assertEquals(2_500_000L, Backend.nanoseconds(2_500_000L, 1_000_000_000L));
  }

  @Test
  void missingGpuTimingYieldsZero() {
    assertEquals(0L, Backend.nanoseconds(0L, 10_000_000L));
    assertEquals(0L, Backend.nanoseconds(-5L, 10_000_000L));
    assertEquals(0L, Backend.nanoseconds(100L, 0L));
  }
}
