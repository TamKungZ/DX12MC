package net.dx12mc.bridge;

import net.dx12mc.bridge.mesh.Batch;

public final class Stats {
  private long capturedTotal, rejectedCaptureTotal, captureFailureTotal;
  private long immediateCapturedTotal, rejectedImmediateTotal;
  private long indexUpdateTotal,
      rejectedIndexUpdateTotal,
      staleSortedIndexUpdateTotal,
      inPlaceGpuIndexUpdateTotal;
  private long deferredGpuIndexUpdateTotal, deferredGpuIndexRebuildTotal;
  private long submittedTotal, failedSubmitTotal, staticSubmitTotal, transientSubmitTotal;
  private long texturedSubmitTotal, lightmappedSubmitTotal;
  private long textureAllocationTotal, textureUploadTotal, rejectedTextureUploadTotal;
  private long textureGpuCreateTotal,
      textureGpuUpdateRegionTotal,
      textureGpuUploadBytesTotal,
      textureGpuDeferredTotal;
  private long cancelledVanillaDrawTotal,
      sectionLayerDrawTotal,
      scissorSkippedDrawTotal,
      rawOpenGlEscapeTotal;
  private long suppressedOpenGlFramebufferBlitTotal,
      suppressedOpenGlSwapTotal,
      suppressedLegacyBlurTotal;
  private long suppressedAutomaticWorldIconCaptureTotal;
  private long omittedNativeLineOverlayTotal;
  private long residentMeshCurrent,
      residentMeshPeak,
      residentMeshEvictionTotal,
      residentMeshRetryTotal;
  private long residentMeshBytes, residentMeshBytesPeak, meshCpuBytes, meshCpuBytesPeak;
  private long nativeStreamingSpillTotal, deferredSectionDrawTotal;
  private long suppressedLegacyVertexStorageTotal, suppressedLegacyVertexStorageBytes;
  private long textureCpuBytes;
  private long nativeStagingBytes, nativeStagingBytesPeak;
  private long submittedFrame,
      texturedSubmitFrame,
      lightmappedSubmitFrame,
      verticesFrame,
      indicesFrame;

  public void captured(Batch batch) {
    if (batch == null) rejectedCaptureTotal++;
    else capturedTotal++;
  }

  public void immediateCaptured(Batch batch) {
    if (batch == null) rejectedImmediateTotal++;
    else immediateCapturedTotal++;
  }

  public void captureFailed() {
    captureFailureTotal++;
  }

  public void indexUpdate(boolean accepted) {
    if (accepted) indexUpdateTotal++;
    else rejectedIndexUpdateTotal++;
  }

  public void staleSortedIndexUpdate() {
    staleSortedIndexUpdateTotal++;
  }

  public void inPlaceGpuIndexUpdate() {
    inPlaceGpuIndexUpdateTotal++;
  }

  public void deferredGpuIndexUpdate() {
    deferredGpuIndexUpdateTotal++;
  }

  public void deferredGpuIndexRebuild() {
    deferredGpuIndexRebuildTotal++;
  }

  public void textureAllocated() {
    textureAllocationTotal++;
  }

  public void textureUpload(boolean accepted) {
    if (accepted) textureUploadTotal++;
    else rejectedTextureUploadTotal++;
  }

  public void textureGpuCreated(long bytes) {
    textureGpuCreateTotal++;
    textureGpuUploadBytesTotal += Math.max(0, bytes);
  }

  public void textureGpuUpdated(int regions, long bytes) {
    textureGpuUpdateRegionTotal += Math.max(0, regions);
    textureGpuUploadBytesTotal += Math.max(0, bytes);
  }

  public void textureCpuBytes(long bytes) {
    textureCpuBytes = Math.max(0, bytes);
  }

  public void textureGpuDeferred() {
    textureGpuDeferredTotal++;
  }

  public void nativeStaging(long current, long peak) {
    nativeStagingBytes = Math.max(0, current);
    nativeStagingBytesPeak = Math.max(nativeStagingBytesPeak, Math.max(0, peak));
  }

  public void submitted(
      Batch batch,
      boolean success,
      boolean transientBuffer,
      boolean textured,
      boolean lightmapped) {
    if (!success) {
      failedSubmitTotal++;
      return;
    }
    submittedTotal++;
    submittedFrame++;
    verticesFrame += batch.vertexCount();
    indicesFrame += batch.indexCount();
    if (transientBuffer) transientSubmitTotal++;
    else staticSubmitTotal++;
    if (textured) {
      texturedSubmitTotal++;
      texturedSubmitFrame++;
    }
    if (lightmapped) {
      lightmappedSubmitTotal++;
      lightmappedSubmitFrame++;
    }
  }

  public void cancelledVanillaDraw() {
    cancelledVanillaDrawTotal++;
  }

  public void sectionLayerDraw() {
    sectionLayerDrawTotal++;
  }

  public void scissorSkippedDraw() {
    scissorSkippedDrawTotal++;
  }

  public void rawOpenGlEscape() {
    rawOpenGlEscapeTotal++;
  }

  public void suppressedOpenGlFramebufferBlit() {
    suppressedOpenGlFramebufferBlitTotal++;
  }

  public void suppressedOpenGlSwap() {
    suppressedOpenGlSwapTotal++;
  }

  public void suppressedLegacyBlur() {
    suppressedLegacyBlurTotal++;
  }

  public void suppressedAutomaticWorldIconCapture() {
    suppressedAutomaticWorldIconCaptureTotal++;
  }

  public void omittedNativeLineOverlay() {
    omittedNativeLineOverlayTotal++;
  }

  public void residentMeshes(long current, long bytes) {
    residentMeshCurrent = Math.max(0, current);
    residentMeshPeak = Math.max(residentMeshPeak, residentMeshCurrent);
    residentMeshBytes = Math.max(0, bytes);
    residentMeshBytesPeak = Math.max(residentMeshBytesPeak, residentMeshBytes);
  }

  public void residentMeshEvicted() {
    residentMeshEvictionTotal++;
  }

  public void residentMeshRetry() {
    residentMeshRetryTotal++;
  }

  public void nativeStreamingSpill() {
    nativeStreamingSpillTotal++;
  }

  public void deferredSectionDraw() {
    deferredSectionDrawTotal++;
  }

  public void meshCpuBytes(long bytes) {
    meshCpuBytes = Math.max(0, bytes);
    meshCpuBytesPeak = Math.max(meshCpuBytesPeak, meshCpuBytes);
  }

  public void suppressedLegacyVertexStorage(long bytes) {
    suppressedLegacyVertexStorageTotal++;
    suppressedLegacyVertexStorageBytes += Math.max(0, bytes);
  }

  public Snapshot snapshot() {
    return new Snapshot(
        capturedTotal,
        rejectedCaptureTotal,
        captureFailureTotal,
        immediateCapturedTotal,
        rejectedImmediateTotal,
        indexUpdateTotal,
        rejectedIndexUpdateTotal,
        staleSortedIndexUpdateTotal,
        inPlaceGpuIndexUpdateTotal,
        deferredGpuIndexUpdateTotal,
        deferredGpuIndexRebuildTotal,
        submittedTotal,
        failedSubmitTotal,
        staticSubmitTotal,
        transientSubmitTotal,
        texturedSubmitTotal,
        lightmappedSubmitTotal,
        textureAllocationTotal,
        textureUploadTotal,
        rejectedTextureUploadTotal,
        textureGpuCreateTotal,
        textureGpuUpdateRegionTotal,
        textureGpuUploadBytesTotal,
        textureGpuDeferredTotal,
        cancelledVanillaDrawTotal,
        sectionLayerDrawTotal,
        scissorSkippedDrawTotal,
        rawOpenGlEscapeTotal,
        suppressedOpenGlFramebufferBlitTotal,
        suppressedOpenGlSwapTotal,
        suppressedLegacyBlurTotal,
        suppressedAutomaticWorldIconCaptureTotal,
        omittedNativeLineOverlayTotal,
        residentMeshCurrent,
        residentMeshPeak,
        residentMeshEvictionTotal,
        residentMeshRetryTotal,
        residentMeshBytes,
        residentMeshBytesPeak,
        meshCpuBytes,
        meshCpuBytesPeak,
        nativeStreamingSpillTotal,
        deferredSectionDrawTotal,
        suppressedLegacyVertexStorageTotal,
        suppressedLegacyVertexStorageBytes,
        textureCpuBytes,
        nativeStagingBytes,
        nativeStagingBytesPeak,
        submittedFrame,
        texturedSubmitFrame,
        lightmappedSubmitFrame,
        verticesFrame,
        indicesFrame);
  }

  public void nextFrame() {
    submittedFrame = 0;
    texturedSubmitFrame = 0;
    lightmappedSubmitFrame = 0;
    verticesFrame = 0;
    indicesFrame = 0;
  }

  public record Snapshot(
      long capturedTotal,
      long rejectedCaptureTotal,
      long captureFailureTotal,
      long immediateCapturedTotal,
      long rejectedImmediateTotal,
      long indexUpdateTotal,
      long rejectedIndexUpdateTotal,
      long staleSortedIndexUpdateTotal,
      long inPlaceGpuIndexUpdateTotal,
      long deferredGpuIndexUpdateTotal,
      long deferredGpuIndexRebuildTotal,
      long submittedTotal,
      long failedSubmitTotal,
      long staticSubmitTotal,
      long transientSubmitTotal,
      long texturedSubmitTotal,
      long lightmappedSubmitTotal,
      long textureAllocationTotal,
      long textureUploadTotal,
      long rejectedTextureUploadTotal,
      long textureGpuCreateTotal,
      long textureGpuUpdateRegionTotal,
      long textureGpuUploadBytesTotal,
      long textureGpuDeferredTotal,
      long cancelledVanillaDrawTotal,
      long sectionLayerDrawTotal,
      long scissorSkippedDrawTotal,
      long rawOpenGlEscapeTotal,
      long suppressedOpenGlFramebufferBlitTotal,
      long suppressedOpenGlSwapTotal,
      long suppressedLegacyBlurTotal,
      long suppressedAutomaticWorldIconCaptureTotal,
      long omittedNativeLineOverlayTotal,
      long residentMeshCurrent,
      long residentMeshPeak,
      long residentMeshEvictionTotal,
      long residentMeshRetryTotal,
      long residentMeshBytes,
      long residentMeshBytesPeak,
      long meshCpuBytes,
      long meshCpuBytesPeak,
      long nativeStreamingSpillTotal,
      long deferredSectionDrawTotal,
      long suppressedLegacyVertexStorageTotal,
      long suppressedLegacyVertexStorageBytes,
      long textureCpuBytes,
      long nativeStagingBytes,
      long nativeStagingBytesPeak,
      long submittedFrame,
      long texturedSubmitFrame,
      long lightmappedSubmitFrame,
      long verticesFrame,
      long indicesFrame) {}
}
