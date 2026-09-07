package net.dx12mc.bridge.texture;

import java.util.List;
import net.dx12mc.bridge.Stats;
import net.dx12mc.graphics.bgfx.StagingBackpressureException;
import net.dx12mc.graphics.bgfx.StagingMemory;
import net.dx12mc.graphics.bgfx.Texture;

final class TextureEntry implements AutoCloseable {
  private static final int MAX_UPLOAD_CHUNK_BYTES =
      (int)
          Math.min(
              StagingMemory.maxOutstandingBytes(),
              Math.max(64L << 10, Long.getLong("dx12mc.texture.maxUploadChunkBytes", 4L << 20)));

  final Image image;
  boolean authoritativeDimensions;
  private final Stats stats;
  private Texture gpu;
  private boolean gpuReady;
  private long gpuRevision = -1;
  private UploadQueue initialUploads;
  private UploadQueue dirtyUploads;
  private long pressureBoundary = -1;
  private StagingBackpressureException deferredPressure;

  TextureEntry(Image image, boolean authoritativeDimensions, Stats stats) {
    this.image = image;
    this.authoritativeDimensions = authoritativeDimensions;
    this.stats = stats;
  }

  Texture resolve(int textureId, long frameBoundary, long registryCpuBytes) {
    if (pressureBoundary == frameBoundary && deferredPressure != null) {
      if (usableGpu()) return gpu;
      throw deferredPressure;
    }
    try {
      createGpuIfNeeded();
      uploadInitialPixels();
      uploadDirtyPixels();
      pressureBoundary = -1;
      deferredPressure = null;
      stats.nativeStaging(StagingMemory.outstandingBytes(), StagingMemory.peakBytes());
      return gpu;
    } catch (StagingBackpressureException pressure) {
      stats.textureGpuDeferred();
      stats.nativeStaging(StagingMemory.outstandingBytes(), StagingMemory.peakBytes());
      pressureBoundary = frameBoundary;
      deferredPressure = pressure;
      if (usableGpu()) return gpu;
      throw pressure;
    } catch (Throwable throwable) {
      close();
      throw resolveFailure(textureId, registryCpuBytes, throwable);
    }
  }

  Texture currentIfReady() {
    return gpuReady
            && gpuRevision == image.revision()
            && initialUploads == null
            && dirtyUploads == null
            && gpu != null
            && gpu.valid()
        ? gpu
        : null;
  }

  private void createGpuIfNeeded() {
    if (gpu != null && gpu.valid()) return;
    gpu =
        Texture.create(
            image.width(),
            image.height(),
            image.maxLevel(),
            image.blur(),
            image.clamp(),
            image.mipmap());
    gpuReady = false;
    initialUploads = new UploadQueue(image.fullUploads(), image.revision());
    image.clearDirty();
  }

  private void uploadInitialPixels() {
    if (gpuReady) return;
    drain(initialUploads);
    gpuReady = true;
    gpuRevision = initialUploads.revision();
    stats.textureGpuCreated(initialUploads.uploadedBytes());
    initialUploads = null;
  }

  private void uploadDirtyPixels() {
    if (gpuRevision == image.revision()) return;
    if (dirtyUploads == null) {
      long revision = image.revision();
      List<Image.Upload> snapshot = image.dirtyUploads();
      image.clearDirty();
      if (snapshot.isEmpty()) {
        gpuRevision = revision;
        return;
      }
      dirtyUploads = new UploadQueue(snapshot, revision);
    }
    drain(dirtyUploads);
    gpuRevision = dirtyUploads.revision();
    stats.textureGpuUpdated(dirtyUploads.regionCount(), dirtyUploads.uploadedBytes());
    dirtyUploads = null;
  }

  private void drain(UploadQueue queue) {
    while (!queue.complete()) {
      UploadQueue.Band band = queue.next(MAX_UPLOAD_CHUNK_BYTES);
      Image.Upload upload = band.upload();
      gpu.update(
          upload.level(),
          upload.x(),
          upload.y() + band.startRow(),
          upload.width(),
          band.rows(),
          upload.rgba(),
          band.sourceOffset(),
          image.blur(),
          image.clamp(),
          image.mipmap());
      queue.submitted(band);
    }
  }

  private boolean usableGpu() {
    return gpuReady && gpu != null && gpu.valid();
  }

  private IllegalStateException resolveFailure(
      int textureId, long registryCpuBytes, Throwable cause) {
    return new IllegalStateException(
        "D3D12 texture resolve failed: id="
            + textureId
            + ", size="
            + image.width()
            + "x"
            + image.height()
            + ", maxLevel="
            + image.maxLevel()
            + ", revision="
            + image.revision()
            + ", cpuBytes="
            + image.allocatedCpuBytes()
            + ", registryCpuBytes="
            + registryCpuBytes,
        cause);
  }

  @Override
  public void close() {
    if (gpu != null) {
      gpu.close();
      gpu = null;
    }
    gpuReady = false;
    gpuRevision = -1;
    initialUploads = null;
    dirtyUploads = null;
    pressureBoundary = -1;
    deferredPressure = null;
  }
}
