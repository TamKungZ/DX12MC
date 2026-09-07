package net.dx12mc.debug;

import net.dx12mc.bridge.Stats;

final class NativeOverlayRenderer {
  void render(
      OverlayWriter writer,
      Stats.Snapshot stats,
      GarbageCollectionMonitor.Sample garbageCollection) {
    writer.line(0x0E, "Mode: STRICT NATIVE GEOMETRY + TEXTURE + STATE + LIGHTMAP BRIDGE");
    renderCapture(writer, stats);
    renderFrame(writer, stats, garbageCollection);
    renderTextures(writer, stats);
    renderMeshes(writer, stats);
    renderCompatibility(writer, stats);
    renderNotes(writer);
  }

  private static void renderCapture(OverlayWriter writer, Stats.Snapshot stats) {
    writer.line(
        0x0F,
        "VertexBuffer captured / rejected / failed: "
            + stats.capturedTotal()
            + " / "
            + stats.rejectedCaptureTotal()
            + " / "
            + stats.captureFailureTotal());
    writer.line(
        0x0F,
        "Immediate MeshData captured / rejected: "
            + stats.immediateCapturedTotal()
            + " / "
            + stats.rejectedImmediateTotal());
    writer.line(
        0x0F,
        "Index updates accepted / stale-preserved / rejected: "
            + stats.indexUpdateTotal()
            + " / "
            + stats.staleSortedIndexUpdateTotal()
            + " / "
            + stats.rejectedIndexUpdateTotal());
    writer.line(
        0x0A,
        "Resident index updates in-place / pressure-deferred / rebuilt: "
            + stats.inPlaceGpuIndexUpdateTotal()
            + " / "
            + stats.deferredGpuIndexUpdateTotal()
            + " / "
            + stats.deferredGpuIndexRebuildTotal());
  }

  private static void renderFrame(
      OverlayWriter writer, Stats.Snapshot stats, GarbageCollectionMonitor.Sample gc) {
    writer.line(
        0x0A,
        "D3D12 submits this frame: "
            + stats.submittedFrame()
            + " (textured "
            + stats.texturedSubmitFrame()
            + ", lightmapped "
            + stats.lightmappedSubmitFrame()
            + ")");
    writer.line(
        0x0F,
        "Vertices / indices this frame: " + stats.verticesFrame() + " / " + stats.indicesFrame());
    writer.line(
        0x0F,
        "D3D12 submits total: "
            + stats.submittedTotal()
            + " (textured "
            + stats.texturedSubmitTotal()
            + ", lightmapped "
            + stats.lightmappedSubmitTotal()
            + ")");
    writer.line(
        0x0F,
        "Static / transient submits total: "
            + stats.staticSubmitTotal()
            + " / "
            + stats.transientSubmitTotal());
    writer.line(
        gc.millisDelta() >= 25L ? 0x0C : 0x0F,
        "GC total / since overlay refresh: "
            + gc.collections()
            + " collections, "
            + gc.millis()
            + " ms / +"
            + gc.collectionDelta()
            + ", +"
            + gc.millisDelta()
            + " ms");
  }

  private static void renderTextures(OverlayWriter writer, Stats.Snapshot stats) {
    writer.line(
        0x0F,
        "Texture CPU allocations / uploads / rejected: "
            + stats.textureAllocationTotal()
            + " / "
            + stats.textureUploadTotal()
            + " / "
            + stats.rejectedTextureUploadTotal());
    writer.line(
        0x0F,
        "D3D12 texture creates / update regions: "
            + stats.textureGpuCreateTotal()
            + " / "
            + stats.textureGpuUpdateRegionTotal());
    writer.line(0x0F, "Texture CPU mirror resident: " + ByteSizes.human(stats.textureCpuBytes()));
    writer.line(
        0x0F,
        "D3D12 texture staging submitted: " + ByteSizes.human(stats.textureGpuUploadBytesTotal()));
    writer.line(
        0x0F,
        "Native staging outstanding / peak: "
            + ByteSizes.human(stats.nativeStagingBytes())
            + " / "
            + ByteSizes.human(stats.nativeStagingBytesPeak()));
    writer.line(
        0x0E,
        "Texture GPU updates deferred by staging pressure: " + stats.textureGpuDeferredTotal());
  }

  private static void renderMeshes(OverlayWriter writer, Stats.Snapshot stats) {
    writer.line(
        0x0A,
        "Legacy GL lifecycle retained; duplicate VertexBuffer VBO storage suppressed after"
            + " takeover");
    writer.line(
        0x0A,
        "Legacy GL vertex VBO storage skipped: "
            + stats.suppressedLegacyVertexStorageTotal()
            + " calls / "
            + ByteSizes.human(stats.suppressedLegacyVertexStorageBytes())
            + " would-be uploads");
    writer.line(0x0A, "D3D12 texture staging: sparse dirty regions + bounded native ownership");
    writer.line(0x0A, "Vanilla draw calls replaced by D3D12: " + stats.cancelledVanillaDrawTotal());
    writer.line(0x0A, "Compiled section-layer draws replaced: " + stats.sectionLayerDrawTotal());
    writer.line(
        0x0A,
        "Resident section meshes current / peak: "
            + stats.residentMeshCurrent()
            + " / "
            + stats.residentMeshPeak());
    writer.line(
        0x0A,
        "Resident section GPU bytes current / peak: "
            + ByteSizes.human(stats.residentMeshBytes())
            + " / "
            + ByteSizes.human(stats.residentMeshBytesPeak()));
    writer.line(
        0x0A,
        "DX12MC CPU mesh mirror current / peak: "
            + ByteSizes.human(stats.meshCpuBytes())
            + " / "
            + ByteSizes.human(stats.meshCpuBytesPeak()));
    writer.line(
        0x0A,
        "Section LRU evictions / static allocation failures: "
            + stats.residentMeshEvictionTotal()
            + " / "
            + stats.residentMeshRetryTotal());
    writer.line(
        0x0A,
        "Native D3D12 streaming spills / one-frame section defers: "
            + stats.nativeStreamingSpillTotal()
            + " / "
            + stats.deferredSectionDrawTotal());
  }

  private static void renderCompatibility(OverlayWriter writer, Stats.Snapshot stats) {
    writer.line(
        0x0A, "Fully clipped draws skipped before GPU submit: " + stats.scissorSkippedDrawTotal());
    writer.line(0x0A, "Raw OpenGL draw escapes detected: " + stats.rawOpenGlEscapeTotal());
    writer.line(
        0x0A,
        "Legacy framebuffer screen blits suppressed: "
            + stats.suppressedOpenGlFramebufferBlitTotal());
    writer.line(0x0A, "OpenGL window swaps suppressed: " + stats.suppressedOpenGlSwapTotal());
    writer.line(
        0x0A, "Legacy GL menu blur passes suppressed: " + stats.suppressedLegacyBlurTotal());
    writer.line(
        0x0A,
        "Automatic world-icon GL readbacks suppressed: "
            + stats.suppressedAutomaticWorldIconCaptureTotal());
    writer.line(
        0x0E,
        "Native thick-line overlays omitted (no GL fallback): "
            + stats.omittedNativeLineOverlayTotal());
    if (stats.failedSubmitTotal() > 0) {
      writer.line(0x0C, "D3D12 submit failures: " + stats.failedSubmitTotal());
    }
    writer.gap();
  }

  private static void renderNotes(OverlayWriter writer) {
    writer.line(0x08, "Note: read these counters with the same world, view and settings");
    writer.line(0x08, "FPS also depends on render distance, resource packs, driver and JVM state");
    writer.line(0x08, "Frame CPU/GPU times are more useful than one moment of FPS");
    writer.line(0x08, "High GC delta points outside the renderer path shown here");
    writer.line(0x08, "Deferred texture or section work usually means staging pressure this frame");
    writer.line(0x08, "Streaming spills mean the resident mesh cache did not cover that draw");
    writer.line(0x08, "Skipped clipped draws are expected when scissor removes all visible pixels");
    writer.line(
        0x08, "Submit failures and raw GL escapes are the main counters to treat as errors");
    writer.line(0x08, "Memory current/peak values help compare scenes, not predict every stall");
    writer.line(0x08, "Line overlays and menu blur are limited paths in this build");
    writer.line(0x08, "Direct3D 12 backend is active for the native bridge counters above");
  }
}
