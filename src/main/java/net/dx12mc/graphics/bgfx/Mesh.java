package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.mesh.Batch;
import org.lwjgl.bgfx.BGFXMemory;
import org.lwjgl.bgfx.BGFXVertexLayout;

public final class Mesh implements AutoCloseable {
  private short vertices;
  private short indices;
  private final int vertexCount;
  private int indexCount;
  private final int vertexBytes;
  private int indexBytes;
  private final boolean index32;
  private long bytes;
  private int drawFirstIndex;
  private int drawIndexCount = -1;

  private Mesh(
      short vertices,
      short indices,
      int vertexCount,
      int indexCount,
      int vertexBytes,
      int indexBytes,
      boolean index32) {
    this.vertices = vertices;
    this.indices = indices;
    this.vertexCount = vertexCount;
    this.indexCount = indexCount;
    this.vertexBytes = vertexBytes;
    this.indexBytes = indexBytes;
    this.index32 = index32;
    this.bytes = (long) vertexBytes + indexBytes;
  }

  public static Mesh create(Batch batch, BGFXVertexLayout layout) {
    boolean index32 = Packing.index32(batch);
    int vertexBytes = Packing.vertexBytes(batch);
    int indexBytes = Math.multiplyExact(batch.indexCount(), index32 ? Integer.BYTES : Short.BYTES);
    short vertexHandle = BGFX_INVALID_HANDLE;
    short indexHandle = BGFX_INVALID_HANDLE;
    try {
      BGFXMemory vertexMemory =
          StagingMemory.allocate(vertexBytes, data -> Packing.vertices(data, batch));
      vertexHandle = bgfx_create_vertex_buffer(vertexMemory, layout, BGFX_BUFFER_NONE);
      if (!BGFX_HANDLE_IS_VALID(vertexHandle)) {
        throw new IllegalStateException("bgfx rejected mirrored Minecraft vertex buffer");
      }
      BGFXMemory indexMemory =
          StagingMemory.allocate(
              indexBytes,
              data -> Packing.indices(data, batch.indices(), batch.indexCount(), index32));
      int flags = BGFX_BUFFER_ALLOW_RESIZE | (index32 ? BGFX_BUFFER_INDEX32 : BGFX_BUFFER_NONE);
      indexHandle = bgfx_create_dynamic_index_buffer_mem(indexMemory, flags);
      if (!BGFX_HANDLE_IS_VALID(indexHandle)) {
        throw new IllegalStateException("bgfx rejected mirrored Minecraft dynamic index buffer");
      }
      return new Mesh(
          vertexHandle,
          indexHandle,
          batch.vertexCount(),
          batch.indexCount(),
          vertexBytes,
          indexBytes,
          index32);
    } catch (Throwable throwable) {
      if (BGFX_HANDLE_IS_VALID(vertexHandle)) bgfx_destroy_vertex_buffer(vertexHandle);
      if (BGFX_HANDLE_IS_VALID(indexHandle)) bgfx_destroy_dynamic_index_buffer(indexHandle);
      throw throwable;
    }
  }

  public static long estimatedBytes(Batch batch) {
    boolean index32 = Packing.index32(batch);
    return (long) Packing.vertexBytes(batch)
        + (long) batch.indexCount() * (index32 ? Integer.BYTES : Short.BYTES);
  }

  public void updateIndices(Batch batch) {
    if (!valid()) throw new IllegalStateException("mesh is closed");
    if (batch.vertexCount() != vertexCount) {
      throw new IllegalStateException(
          "index update vertex generation no longer matches resident mesh");
    }
    if (!index32 && Packing.index32(batch)) {
      throw new IllegalStateException("index update requires wider index storage");
    }
    int newIndexBytes =
        Math.multiplyExact(batch.indexCount(), index32 ? Integer.BYTES : Short.BYTES);
    BGFXMemory indexMemory =
        StagingMemory.allocate(
            newIndexBytes,
            data -> Packing.indices(data, batch.indices(), batch.indexCount(), index32));
    bgfx_update_dynamic_index_buffer(indices, 0, indexMemory);
    indexCount = batch.indexCount();
    indexBytes = newIndexBytes;
    bytes = (long) vertexBytes + indexBytes;
  }

  public long bytes() {
    return bytes;
  }

  public void bind() {
    if (!valid()) throw new IllegalStateException("mesh is closed");
    bgfx_set_vertex_buffer(0, vertices, 0, vertexCount);
    int first = drawIndexCount < 0 ? 0 : drawFirstIndex;
    int count = drawIndexCount < 0 ? indexCount : drawIndexCount;
    bgfx_set_dynamic_index_buffer(indices, first, count);
  }

  public void drawRange(int firstIndex, int count) {
    if (firstIndex < 0 || count <= 0 || (long) firstIndex + count > indexCount) {
      throw new IllegalArgumentException(
          "index range " + firstIndex + "+" + count + " is outside " + indexCount);
    }
    drawFirstIndex = firstIndex;
    drawIndexCount = count;
  }

  public void drawAll() {
    drawFirstIndex = 0;
    drawIndexCount = -1;
  }

  public boolean valid() {
    return BGFX_HANDLE_IS_VALID(vertices) && BGFX_HANDLE_IS_VALID(indices);
  }

  @Override
  public void close() {
    if (BGFX_HANDLE_IS_VALID(vertices)) {
      bgfx_destroy_vertex_buffer(vertices);
      vertices = BGFX_INVALID_HANDLE;
    }
    if (BGFX_HANDLE_IS_VALID(indices)) {
      bgfx_destroy_dynamic_index_buffer(indices);
      indices = BGFX_INVALID_HANDLE;
    }
  }
}
