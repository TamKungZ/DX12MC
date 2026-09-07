package net.dx12mc.debug;

import net.dx12mc.bridge.Stats;

/** Formats strict-native bridge diagnostics without owning refresh or JVM sampling state. */
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
    renderCoverage(writer);
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

  private static void renderCoverage(OverlayWriter writer) {
    writer.line(0x08, "Coverage 0.8.10: frame pacing diagnostics + throttled native overlay");
    writer.line(0x08, "Texture pixels mirror NativeImage CPU uploads; no OpenGL texture readback");
    writer.line(
        0x08, "Texture uploads are coalesced and staged lazily on D3D12 use (mips included)");
    writer.line(
        0x08,
        "Compiled chunks use static D3D12 vertices + dynamic sorted index buffers + bounded"
            + " residency");
    writer.line(
        0x08,
        "Static bgfx allocation pressure spills to native transient D3D12 for the current frame");
    writer.line(
        0x08,
        "Immediate blend bridge preserves special world-overlay blend modes (vignette no longer"
            + " opaque)");
    writer.line(
        0x08,
        "Cutout terrain uses native alpha discard; rendertype_lines remains semantically omitted");
    writer.line(
        0x08,
        "Automatic world-icon capture is skipped at GameRenderer; raw GL readback remains"
            + " forbidden");
    writer.line(
        0x08,
        "Legacy GL menu blur/post chain is suppressed until native render-target translation"
            + " lands");
    writer.line(
        0x08, "OpenGL bootstrap/context: retained only for Minecraft/GLFW lifecycle compatibility");
    writer.line(0x08, "Direct3D 12 backend active");
  }
}
