package net.dx12mc.bridge;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.graphics.bgfx.Mesh;

public final class Slot {
  private Batch batch;
  private Mesh mesh;
  private boolean dynamic;
  private VertexFormat.IndexType indexType;
  private int indexCount;
  private boolean indicesDirty;

  public void capture(
      Batch batch, boolean dynamic, VertexFormat.IndexType indexType, int indexCount) {
    releaseGpu();
    this.batch = batch;
    this.dynamic = dynamic;
    this.indexType = indexType;
    this.indexCount = indexCount;
    this.indicesDirty = false;
  }

  public boolean replaceIndices(int[] indices) {
    if (batch == null || indices == null) {
      return false;
    }
    batch = batch.withIndices(indices);
    indexCount = indices.length;
    indicesDirty = true;
    return true;
  }

  public Batch batch() {
    return batch;
  }

  public boolean dynamic() {
    return dynamic;
  }

  public VertexFormat.IndexType indexType() {
    return indexType;
  }

  public int indexCount() {
    return indexCount;
  }

  public Mesh mesh() {
    return mesh;
  }

  public void mesh(Mesh mesh) {
    releaseGpu();
    this.mesh = mesh;
    this.indicesDirty = false;
  }

  public boolean indicesDirty() {
    return indicesDirty;
  }

  public void indicesUploaded() {
    indicesDirty = false;
  }

  public void clearCpu() {
    releaseGpu();
    batch = null;
    dynamic = false;
    indexType = null;
    indexCount = 0;
    indicesDirty = false;
  }

  public void releaseGpu() {
    if (mesh != null) {
      mesh.close();
      mesh = null;
    }
  }

  public void release() {
    clearCpu();
  }
}
