package net.dx12mc.debug;

import java.util.Locale;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Stats;
import net.dx12mc.config.Mode;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.DebugText;

/** Coordinates refresh pacing and the two diagnostic overlay modes. */
public final class Scene {
  private static final long OVERLAY_REFRESH_NANOS =
      Math.max(50L, Long.getLong("dx12mc.overlay.refreshMillis", 250L)) * 1_000_000L;
  private final GarbageCollectionMonitor garbageCollection = new GarbageCollectionMonitor();
  private final NativeOverlayRenderer nativeOverlay = new NativeOverlayRenderer();
  private long nextOverlayRefreshNanos;

  public boolean shouldRender(Mode mode, boolean overlay) {
    if (mode == Mode.DEBUG) return true;
    if (!overlay) return false;
    long now = System.nanoTime();
    if (now < nextOverlayRefreshNanos) return false;
    nextOverlayRefreshNanos = now + OVERLAY_REFRESH_NANOS;
    return true;
  }

  public void render(
      Backend backend, FrameSize size, Mode mode, Stats.Snapshot stats, boolean overlay) {
    if (mode != Mode.DEBUG && !overlay) return;
    DebugText text = backend.debugText();
    text.clear();
    OverlayWriter writer = new OverlayWriter(text);
    renderHeader(writer, backend, size);
    if (mode == Mode.DEBUG) renderDebugScene(writer);
    else nativeOverlay.render(writer, stats, garbageCollection.sample());
  }

  private static void renderHeader(OverlayWriter writer, Backend backend, FrameSize size) {
    writer.line(0x0F, "DX12MC " + DX12MC.VERSION);
    writer.line(0x0A, "Renderer: " + backend.rendererName());
    writer.line(0x0F, "Backbuffer: " + size.width() + " x " + size.height());
    writer.line(0x0F, "Frame: " + backend.frameNumber());
    Backend.FrameTiming timing = backend.frameTiming();
    writer.line(
        0x0F,
        String.format(
            Locale.ROOT,
            "Previous frame CPU/GPU: %.2f / %.2f ms (draws %d)",
            timing.cpuMillis(),
            timing.gpuMillis(),
            timing.draws()));
    writer.line(
        0x0F,
        String.format(
            Locale.ROOT,
            "bgfx wait submit/render: %.2f / %.2f ms",
            timing.waitSubmitMillis(),
            timing.waitRenderMillis()));
    writer.line(0x0B, "Path: Minecraft -> DX12MC -> bgfx -> Direct3D 12 / DXGI");
    writer.line(0x0A, "Present owner: D3D12 ONLY (OpenGL SwapBuffers disabled)");
    writer.line(0x0C, "Fallback policy: NONE - unsupported/failed native paths are fatal");
    writer.gap();
  }

  private static void renderDebugScene(OverlayWriter writer) {
    writer.line(0x0E, "Minecraft GameRenderer: BYPASSED (debug mode)");
    writer.line(
        0x08, "GLFW/OpenGL bootstrap context may still exist for Minecraft lifecycle only.");
    writer.gap();
    writer.line(0x0F, "bgfx VGA palette / D3D12 draw test:");
    writer.colorBar(5, 3);
    writer.gap();
    writer.line(0x0B, "Visible frame and presentation are bgfx Direct3D 12.");
  }
}
