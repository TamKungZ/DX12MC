package net.dx12mc.harness;

import java.util.Arrays;
import java.util.Locale;

public final class Timing {
  public enum Part {
    FRAME,
    CLIENT_TICK,
    GAME_RENDER,
    RENDER_LEVEL,
    SETUP_RENDER,
    COMPILE_SECTIONS,
    SECTION_LAYERS,
    ENTITIES,
    PENDING_UPLOADS,
    FLIP_FRAME
  }

  private static final long[] TOTAL = new long[Part.values().length];
  private static final long[] STARTED = new long[Part.values().length];

  private Timing() {}

  public static void begin(Part part) {
    if (FlightHarness.ENABLED) STARTED[part.ordinal()] = System.nanoTime();
  }

  public static void end(Part part) {
    if (FlightHarness.ENABLED && STARTED[part.ordinal()] != 0L) {
      TOTAL[part.ordinal()] += System.nanoTime() - STARTED[part.ordinal()];
      STARTED[part.ordinal()] = 0L;
    }
  }

  static String drain(long frames) {
    StringBuilder text = new StringBuilder("ms/frame");
    for (Part part : Part.values()) {
      text.append(
          String.format(
              Locale.ROOT,
              " %s %.3f",
              part.name().toLowerCase(Locale.ROOT).replace('_', '-'),
              TOTAL[part.ordinal()] / 1e6 / Math.max(1L, frames)));
    }
    Arrays.fill(TOTAL, 0L);
    return text.toString();
  }

  static void reset() {
    Arrays.fill(TOTAL, 0L);
  }
}
