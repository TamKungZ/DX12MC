package net.dx12mc.render;

import java.util.Locale;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Stats;
import net.dx12mc.graphics.bgfx.Backend;

final class PerformanceTelemetry {
  private static final boolean ENABLED =
      Boolean.parseBoolean(System.getProperty("dx12mc.perfLog", "true"));
  private static final long INTERVAL_NANOS =
      Math.max(2L, Long.getLong("dx12mc.perfLog.seconds", 5L)) * 1_000_000_000L;

  private final Stats stats;
  private long startedNanos;
  private long frames;
  private long submits;
  private long vertices;
  private long indices;
  private long regionSections;
  private long regionPages;
  private long previousRebuilds;
  private long previousRebuildDeferrals;

  PerformanceTelemetry(Stats stats) {
    this.stats = stats;
  }

  void record(Backend backend) {
    if (!ENABLED) return;
    long now = System.nanoTime();
    if (startedNanos == 0L) startedNanos = now;
    frames++;
    submits += stats.submittedThisFrame();
    vertices += stats.verticesThisFrame();
    indices += stats.indicesThisFrame();
    regionSections += stats.regionSectionsThisFrame();
    regionPages += stats.regionPagesThisFrame();
    long elapsed = now - startedNanos;
    if (elapsed < INTERVAL_NANOS) return;
    Backend.FrameTiming timing = backend.frameTiming();
    long rebuilds = stats.regionRebuilds();
    long rebuildDeferrals = stats.regionRebuildDeferrals();
    double seconds = elapsed / 1_000_000_000.0;
    DX12MC.LOGGER.info(
        String.format(
            Locale.ROOT,
            "DX12MC PERF %.1fs: %.1f fps, submits/frame %.1f, bgfx draws %d,"
                + " vertices/frame %.0f, indices/frame %.0f, opaque sections/pages %.1f/%.1f,"
                + " region rebuilds/deferred +%d/+%d, bgfx CPU/GPU/wait-submit/wait-render"
                + " %.3f/%.3f/%.3f/%.3f ms",
            seconds,
            frames / seconds,
            average(submits),
            timing.draws(),
            average(vertices),
            average(indices),
            average(regionSections),
            average(regionPages),
            rebuilds - previousRebuilds,
            rebuildDeferrals - previousRebuildDeferrals,
            timing.cpuMillis(),
            timing.gpuMillis(),
            timing.waitSubmitMillis(),
            timing.waitRenderMillis()));
    previousRebuilds = rebuilds;
    previousRebuildDeferrals = rebuildDeferrals;
    startedNanos = now;
    frames = submits = vertices = indices = regionSections = regionPages = 0L;
  }

  private double average(long value) {
    return frames == 0L ? 0.0 : (double) value / frames;
  }
}
