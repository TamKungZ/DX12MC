package net.dx12mc.bridge.texture;

import java.util.List;

final class UploadQueue {
  private final List<Image.Upload> uploads;
  private final long revision;
  private final int regionCount;
  private int uploadIndex;
  private int row;
  private long uploadedBytes;

  UploadQueue(List<Image.Upload> uploads, long revision) {
    this.uploads = List.copyOf(uploads);
    this.revision = revision;
    this.regionCount = uploads.size();
  }

  boolean complete() {
    return uploadIndex >= uploads.size();
  }

  long revision() {
    return revision;
  }

  int regionCount() {
    return regionCount;
  }

  long uploadedBytes() {
    return uploadedBytes;
  }

  Band next(int maxChunkBytes) {
    if (complete()) throw new IllegalStateException("texture upload queue is complete");
    if (maxChunkBytes <= 0) throw new IllegalArgumentException("maxChunkBytes must be positive");
    Image.Upload upload = uploads.get(uploadIndex);
    int rowBytes = Math.multiplyExact(upload.width(), 4);
    int rows = Math.min(upload.height() - row, Math.max(1, maxChunkBytes / rowBytes));
    int bytes = Math.multiplyExact(rowBytes, rows);
    int sourceOffset = Math.multiplyExact(row, rowBytes);
    return new Band(uploadIndex, row, upload, rows, bytes, sourceOffset);
  }

  void submitted(Band band) {
    if (band == null || complete() || band.uploadIndex() != uploadIndex || band.startRow() != row) {
      throw new IllegalStateException("texture upload band does not match the current cursor");
    }
    uploadedBytes += band.bytes();
    row += band.rows();
    if (row == band.upload().height()) {
      uploadIndex++;
      row = 0;
    }
  }

  record Band(
      int uploadIndex, int startRow, Image.Upload upload, int rows, int bytes, int sourceOffset) {}
}
