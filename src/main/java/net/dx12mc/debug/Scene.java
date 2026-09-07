package net.dx12mc.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Stats;
import net.dx12mc.config.Mode;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.DebugText;

public final class Scene {
  private static final long OVERLAY_REFRESH_NANOS =
      Math.max(50L, Long.getLong("dx12mc.overlay.refreshMillis", 250L)) * 1_000_000L;
  private final List<GarbageCollectorMXBean> garbageCollectors =
      ManagementFactory.getGarbageCollectorMXBeans();
  private long nextOverlayRefreshNanos;
  private long previousGcCollections;
  private long previousGcMillis;
  private boolean gcInitialized;

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
    int y = 1;
    text.print(2, y++, 0x0F, "DX12MC " + DX12MC.VERSION);
    text.print(2, y++, 0x0A, "Renderer: " + backend.rendererName());
    text.print(2, y++, 0x0F, "Backbuffer: " + size.width() + " x " + size.height());
    text.print(2, y++, 0x0F, "Frame: " + backend.frameNumber());
    Backend.FrameTiming timing = backend.frameTiming();
    text.print(
        2,
        y++,
        0x0F,
        String.format(
            java.util.Locale.ROOT,
            "Previous frame CPU/GPU: %.2f / %.2f ms (draws %d)",
            timing.cpuMillis(),
            timing.gpuMillis(),
            timing.draws()));
    text.print(
        2,
        y++,
        0x0F,
        String.format(
            java.util.Locale.ROOT,
            "bgfx wait submit/render: %.2f / %.2f ms",
            timing.waitSubmitMillis(),
            timing.waitRenderMillis()));
    text.print(2, y++, 0x0B, "Path: Minecraft -> DX12MC -> bgfx -> Direct3D 12 / DXGI");
    text.print(2, y++, 0x0A, "Present owner: D3D12 ONLY (OpenGL SwapBuffers disabled)");
    text.print(2, y++, 0x0C, "Fallback policy: NONE - unsupported/failed native paths are fatal");
    y++;
    if (mode == Mode.DEBUG) {
      text.print(2, y++, 0x0E, "Minecraft GameRenderer: BYPASSED (debug mode)");
      text.print(
          2,
          y++,
          0x08,
          "GLFW/OpenGL bootstrap context may still exist for Minecraft lifecycle only.");
      y++;
      text.print(2, y++, 0x0F, "bgfx VGA palette / D3D12 draw test:");
      text.colorBar(5, y++, 3);
      y++;
      text.print(2, y, 0x0B, "Visible frame and presentation are bgfx Direct3D 12.");
      return;
    }
    text.print(2, y++, 0x0E, "Mode: STRICT NATIVE GEOMETRY + TEXTURE + STATE + LIGHTMAP BRIDGE");
    text.print(
        2,
        y++,
        0x0F,
        "VertexBuffer captured / rejected / failed: "
            + stats.capturedTotal()
            + " / "
            + stats.rejectedCaptureTotal()
            + " / "
            + stats.captureFailureTotal());
    text.print(
        2,
        y++,
        0x0F,
        "Immediate MeshData captured / rejected: "
            + stats.immediateCapturedTotal()
            + " / "
            + stats.rejectedImmediateTotal());
    text.print(
        2,
        y++,
        0x0F,
        "Index updates accepted / stale-preserved / rejected: "
            + stats.indexUpdateTotal()
            + " / "
            + stats.staleSortedIndexUpdateTotal()
            + " / "
            + stats.rejectedIndexUpdateTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Resident index updates in-place / pressure-deferred / rebuilt: "
            + stats.inPlaceGpuIndexUpdateTotal()
            + " / "
            + stats.deferredGpuIndexUpdateTotal()
            + " / "
            + stats.deferredGpuIndexRebuildTotal());
    text.print(
        2,
        y++,
        0x0A,
        "D3D12 submits this frame: "
            + stats.submittedFrame()
            + " (textured "
            + stats.texturedSubmitFrame()
            + ", lightmapped "
            + stats.lightmappedSubmitFrame()
            + ")");
    text.print(
        2,
        y++,
        0x0F,
        "Vertices / indices this frame: " + stats.verticesFrame() + " / " + stats.indicesFrame());
    text.print(
        2,
        y++,
        0x0F,
        "D3D12 submits total: "
            + stats.submittedTotal()
            + " (textured "
            + stats.texturedSubmitTotal()
            + ", lightmapped "
            + stats.lightmappedSubmitTotal()
            + ")");
    text.print(
        2,
        y++,
        0x0F,
        "Static / transient submits total: "
            + stats.staticSubmitTotal()
            + " / "
            + stats.transientSubmitTotal());
    long gcCollections = gcCollections();
    long gcMillis = gcMillis();
    long gcCollectionDelta = gcInitialized ? gcCollections - previousGcCollections : 0L;
    long gcMillisDelta = gcInitialized ? gcMillis - previousGcMillis : 0L;
    previousGcCollections = gcCollections;
    previousGcMillis = gcMillis;
    gcInitialized = true;
    text.print(
        2,
        y++,
        gcMillisDelta >= 25L ? 0x0C : 0x0F,
        "GC total / since overlay refresh: "
            + gcCollections
            + " collections, "
            + gcMillis
            + " ms / +"
            + gcCollectionDelta
            + ", +"
            + gcMillisDelta
            + " ms");
    text.print(
        2,
        y++,
        0x0F,
        "Texture CPU allocations / uploads / rejected: "
            + stats.textureAllocationTotal()
            + " / "
            + stats.textureUploadTotal()
            + " / "
            + stats.rejectedTextureUploadTotal());
    text.print(
        2,
        y++,
        0x0F,
        "D3D12 texture creates / update regions: "
            + stats.textureGpuCreateTotal()
            + " / "
            + stats.textureGpuUpdateRegionTotal());
    text.print(2, y++, 0x0F, "Texture CPU mirror resident: " + humanBytes(stats.textureCpuBytes()));
    text.print(
        2,
        y++,
        0x0F,
        "D3D12 texture staging submitted: " + humanBytes(stats.textureGpuUploadBytesTotal()));
    text.print(
        2,
        y++,
        0x0F,
        "Native staging outstanding / peak: "
            + humanBytes(stats.nativeStagingBytes())
            + " / "
            + humanBytes(stats.nativeStagingBytesPeak()));
    text.print(
        2,
        y++,
        0x0E,
        "Texture GPU updates deferred by staging pressure: " + stats.textureGpuDeferredTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Legacy GL lifecycle retained; duplicate VertexBuffer VBO storage suppressed after"
            + " takeover");
    text.print(
        2,
        y++,
        0x0A,
        "Legacy GL vertex VBO storage skipped: "
            + stats.suppressedLegacyVertexStorageTotal()
            + " calls / "
            + humanBytes(stats.suppressedLegacyVertexStorageBytes())
            + " would-be uploads");
    text.print(
        2, y++, 0x0A, "D3D12 texture staging: sparse dirty regions + bounded native ownership");
    text.print(
        2, y++, 0x0A, "Vanilla draw calls replaced by D3D12: " + stats.cancelledVanillaDrawTotal());
    text.print(
        2, y++, 0x0A, "Compiled section-layer draws replaced: " + stats.sectionLayerDrawTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Resident section meshes current / peak: "
            + stats.residentMeshCurrent()
            + " / "
            + stats.residentMeshPeak());
    text.print(
        2,
        y++,
        0x0A,
        "Resident section GPU bytes current / peak: "
            + humanBytes(stats.residentMeshBytes())
            + " / "
            + humanBytes(stats.residentMeshBytesPeak()));
    text.print(
        2,
        y++,
        0x0A,
        "DX12MC CPU mesh mirror current / peak: "
            + humanBytes(stats.meshCpuBytes())
            + " / "
            + humanBytes(stats.meshCpuBytesPeak()));
    text.print(
        2,
        y++,
        0x0A,
        "Section LRU evictions / static allocation failures: "
            + stats.residentMeshEvictionTotal()
            + " / "
            + stats.residentMeshRetryTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Native D3D12 streaming spills / one-frame section defers: "
            + stats.nativeStreamingSpillTotal()
            + " / "
            + stats.deferredSectionDrawTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Fully clipped draws skipped before GPU submit: " + stats.scissorSkippedDrawTotal());
    text.print(2, y++, 0x0A, "Raw OpenGL draw escapes detected: " + stats.rawOpenGlEscapeTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Legacy framebuffer screen blits suppressed: "
            + stats.suppressedOpenGlFramebufferBlitTotal());
    text.print(
        2, y++, 0x0A, "OpenGL window swaps suppressed: " + stats.suppressedOpenGlSwapTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Legacy GL menu blur passes suppressed: " + stats.suppressedLegacyBlurTotal());
    text.print(
        2,
        y++,
        0x0A,
        "Automatic world-icon GL readbacks suppressed: "
            + stats.suppressedAutomaticWorldIconCaptureTotal());
    text.print(
        2,
        y++,
        0x0E,
        "Native thick-line overlays omitted (no GL fallback): "
            + stats.omittedNativeLineOverlayTotal());
    if (stats.failedSubmitTotal() > 0)
      text.print(2, y++, 0x0C, "D3D12 submit failures: " + stats.failedSubmitTotal());
    y++;
    text.print(
        2, y++, 0x08, "Coverage 0.8.10: frame pacing diagnostics + throttled native overlay");
    text.print(
        2, y++, 0x08, "Texture pixels mirror NativeImage CPU uploads; no OpenGL texture readback");
    text.print(
        2,
        y++,
        0x08,
        "Texture uploads are coalesced and staged lazily on D3D12 use (mips included)");
    text.print(
        2,
        y++,
        0x08,
        "Compiled chunks use static D3D12 vertices + dynamic sorted index buffers + bounded"
            + " residency");
    text.print(
        2,
        y++,
        0x08,
        "Static bgfx allocation pressure spills to native transient D3D12 for the current frame");
    text.print(
        2,
        y++,
        0x08,
        "Immediate blend bridge preserves special world-overlay blend modes (vignette no longer"
            + " opaque)");
    text.print(
        2,
        y++,
        0x08,
        "Cutout terrain uses native alpha discard; rendertype_lines remains semantically omitted");
    text.print(
        2,
        y++,
        0x08,
        "Automatic world-icon capture is skipped at GameRenderer; raw GL readback remains"
            + " forbidden");
    text.print(
        2,
        y++,
        0x08,
        "Legacy GL menu blur/post chain is suppressed until native render-target translation"
            + " lands");
    text.print(
        2,
        y++,
        0x08,
        "OpenGL bootstrap/context: retained only for Minecraft/GLFW lifecycle compatibility");
    text.print(2, y, 0x08, "Direct3D 12 backend active");
  }

  private static String humanBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kib = bytes / 1024.0;
    if (kib < 1024) return String.format(java.util.Locale.ROOT, "%.1f KiB", kib);
    double mib = kib / 1024.0;
    if (mib < 1024) return String.format(java.util.Locale.ROOT, "%.1f MiB", mib);
    return String.format(java.util.Locale.ROOT, "%.2f GiB", mib / 1024.0);
  }

  private long gcCollections() {
    long total = 0L;
    for (GarbageCollectorMXBean collector : garbageCollectors) {
      total += Math.max(0L, collector.getCollectionCount());
    }
    return total;
  }

  private long gcMillis() {
    long total = 0L;
    for (GarbageCollectorMXBean collector : garbageCollectors) {
      total += Math.max(0L, collector.getCollectionTime());
    }
    return total;
  }
}
