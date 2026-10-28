package net.dx12mc.bridge;

import java.util.Locale;

public final class Profile {
  public static final boolean ENABLED = Boolean.getBoolean("dx12mc.profile.cpu");

  public enum Section {
    CAPTURE,
    INDEX_UPDATE,
    SECTION_DRAW,
    REGION_REBUILD,
    REGION_DRAW,
    REGION_STREAM,
    IMMEDIATE_DRAW,
    TEXTURE_MIRROR,
    TEXTURE_UPLOAD,
    BGFX_FRAME
  }

  public enum Count {
    CAPTURED_SECTIONS,
    CAPTURED_VERTICES,
    STREAMED_SECTIONS,
    STREAMED_BYTES,
    REBUILT_SECTIONS,
    REBUILT_BYTES,
    REPEAT_REBUILDS,
    WAITING_REGIONS,
    MESH_UPLOADS,
    INDEX_UPLOADS,
    TEXTURE_UPLOADS,
    ARENA_UPLOADS,
    SKIP_DRAW_BACKPRESSURE,
    SKIP_DRAW_TRANSIENT,
    SKIP_SECTION_BACKPRESSURE,
    SKIP_SECTION_TEXTURE,
    SKIP_SECTION_TRANSIENT,
    SKIP_STREAM_BUDGET,
    DEFER_BUDGET,
    DEFER_FAILED
  }

  private static final long[] NANOS = new long[Section.values().length];
  private static final long[] COUNTS = new long[Count.values().length];
  private static long renderThreadNanos;

  private Profile() {}

  public static long start() {
    return System.nanoTime();
  }

  public static void add(Section section, long started) {
    NANOS[section.ordinal()] += System.nanoTime() - started;
  }

  public static void count(Count count, long amount) {
    COUNTS[count.ordinal()] += amount;
  }

  public static void renderThread(long nanos) {
    renderThreadNanos += Math.max(0L, nanos);
  }

  public static String drain(long frames) {
    StringBuilder text = new StringBuilder(" | profile ms/frame");
    double divisor = Math.max(1L, frames) * 1_000_000.0;
    for (Section section : Section.values()) {
      text.append(
          String.format(
              Locale.ROOT, " %s %.3f", name(section), NANOS[section.ordinal()] / divisor));
    }
    text.append(String.format(Locale.ROOT, " render-thread %.3f", renderThreadNanos / divisor));
    text.append(" | per frame");
    for (Count count : Count.values()) {
      text.append(
          String.format(
              Locale.ROOT,
              " %s %.1f",
              name(count),
              COUNTS[count.ordinal()] / (double) Math.max(1L, frames)));
    }
    java.util.Arrays.fill(NANOS, 0L);
    java.util.Arrays.fill(COUNTS, 0L);
    renderThreadNanos = 0L;
    return text.toString();
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
