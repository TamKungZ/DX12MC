package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.mesh.Batch;
import org.lwjgl.bgfx.BGFXMemory;
import org.lwjgl.bgfx.BGFXVertexLayout;

public final class Mesh implements AutoCloseable {
  public enum Kind {
    SORTABLE,
    POOLED,
    SHARED_QUADS,
    ARENA
  }

  private final Kind kind;
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
  private int startVertex;
  private VertexArena arena;
  private VertexArena.Allocation allocation;
  private VertexArena indexArena;
  private VertexArena.Allocation indexAllocation;

  private Mesh(
      Kind kind,
      short vertices,
      short indices,
      int vertexCount,
      int indexCount,
      int vertexBytes,
      int indexBytes,
      boolean index32) {
    this.kind = kind;
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
    return create(batch, layout, Kind.SORTABLE);
  }

  public static Mesh create(Batch batch, BGFXVertexLayout layout, Kind kind) {
    if (net.dx12mc.bridge.Profile.ENABLED)
      net.dx12mc.bridge.Profile.count(net.dx12mc.bridge.Profile.Count.MESH_UPLOADS, 2);
    boolean index32 = Packing.index32(batch);
    int vertexBytes = Packing.vertexBytes(batch);
    int indexBytes = Math.multiplyExact(batch.indexCount(), index32 ? Integer.BYTES : Short.BYTES);
    short vertexHandle = BGFX_INVALID_HANDLE;
    short indexHandle = BGFX_INVALID_HANDLE;
    try {
      BGFXMemory vertexMemory =
          StagingMemory.allocate(vertexBytes, data -> Packing.vertices(data, batch));
      vertexHandle =
          kind == Kind.POOLED
              ? bgfx_create_dynamic_vertex_buffer_mem(vertexMemory, layout, BGFX_BUFFER_NONE)
              : bgfx_create_vertex_buffer(vertexMemory, layout, BGFX_BUFFER_NONE);
      if (!BGFX_HANDLE_IS_VALID(vertexHandle)) {
        throw new IllegalStateException("bgfx rejected mirrored Minecraft vertex buffer");
      }
      BGFXMemory indexMemory =
          StagingMemory.allocate(
              indexBytes,
              data -> Packing.indices(data, batch.indices(), batch.indexCount(), index32));
      int widthFlag = index32 ? BGFX_BUFFER_INDEX32 : BGFX_BUFFER_NONE;
      indexHandle =
          switch (kind) {
            case SORTABLE ->
                bgfx_create_dynamic_index_buffer_mem(
                    indexMemory, BGFX_BUFFER_ALLOW_RESIZE | widthFlag);
            case POOLED -> bgfx_create_dynamic_index_buffer_mem(indexMemory, widthFlag);
            case SHARED_QUADS, ARENA ->
                throw new IllegalArgumentException("shared quad meshes use createSharedQuads");
          };
      if (!BGFX_HANDLE_IS_VALID(indexHandle)) {
        throw new IllegalStateException("bgfx rejected mirrored Minecraft index buffer");
      }
      return new Mesh(
          kind,
          vertexHandle,
          indexHandle,
          batch.vertexCount(),
          batch.indexCount(),
          vertexBytes,
          indexBytes,
          index32);
    } catch (Throwable throwable) {
      destroy(kind, vertexHandle, indexHandle);
      throw throwable;
    }
  }

  public static boolean canShareQuadIndices(Batch batch) {
    return QuadIndices.supports(batch.vertexCount(), Packing.index32(batch))
        && batch.indexCount() == batch.vertexCount() / 4 * 6;
  }

  public static Mesh createSharedQuads(Batch batch, BGFXVertexLayout layout) {
    if (net.dx12mc.bridge.Profile.ENABLED)
      net.dx12mc.bridge.Profile.count(net.dx12mc.bridge.Profile.Count.MESH_UPLOADS, 1);
    if (!canShareQuadIndices(batch)) {
      throw new IllegalArgumentException("batch cannot use the shared quad index buffer");
    }
    boolean index32 = Packing.index32(batch);
    int vertexBytes = Packing.vertexBytes(batch);
    short vertexHandle = BGFX_INVALID_HANDLE;
    try {
      BGFXMemory vertexMemory =
          StagingMemory.allocate(vertexBytes, data -> Packing.vertices(data, batch));
      vertexHandle = bgfx_create_dynamic_vertex_buffer_mem(vertexMemory, layout, BGFX_BUFFER_NONE);
      if (!BGFX_HANDLE_IS_VALID(vertexHandle)) {
        throw new IllegalStateException("bgfx rejected mirrored Minecraft vertex buffer");
      }
      return new Mesh(
          Kind.SHARED_QUADS,
          vertexHandle,
          QuadIndices.handle(index32),
          batch.vertexCount(),
          batch.indexCount(),
          vertexBytes,
          0,
          index32);
    } catch (Throwable throwable) {
      if (BGFX_HANDLE_IS_VALID(vertexHandle)) bgfx_destroy_dynamic_vertex_buffer(vertexHandle);
      throw throwable;
    }
  }

  public static Mesh createArena(Batch batch, VertexArena arena) {
    if (!canShareQuadIndices(batch)) {
      throw new IllegalArgumentException("batch cannot use the shared quad index buffer");
    }
    boolean index32 = Packing.index32(batch);
    VertexArena.Allocation allocation =
        arena.allocate(batch.vertexCount(), data -> Packing.vertices(data, batch));
    if (allocation == null) return null;
    Mesh mesh =
        new Mesh(
            Kind.ARENA,
            allocation.handle(),
            QuadIndices.handle(index32),
            batch.vertexCount(),
            batch.indexCount(),
            Packing.vertexBytes(batch),
            0,
            index32);
    mesh.startVertex = allocation.startVertex();
    mesh.arena = arena;
    mesh.allocation = allocation;
    return mesh;
  }

  public static Mesh createSortableArena(Batch batch, VertexArena vertices, VertexArena indices) {
    VertexArena.Allocation vertexAllocation =
        vertices.allocate(batch.vertexCount(), data -> Packing.vertices(data, batch));
    if (vertexAllocation == null) return null;
    VertexArena.Allocation indexAllocation = allocateIndices(indices, batch);
    if (indexAllocation == null) {
      vertices.free(vertexAllocation);
      return null;
    }
    Mesh mesh =
        new Mesh(
            Kind.ARENA,
            vertexAllocation.handle(),
            indexAllocation.handle(),
            batch.vertexCount(),
            batch.indexCount(),
            Packing.vertexBytes(batch),
            batch.indexCount() * Integer.BYTES,
            true);
    mesh.startVertex = vertexAllocation.startVertex();
    mesh.arena = vertices;
    mesh.allocation = vertexAllocation;
    mesh.indexArena = indices;
    mesh.indexAllocation = indexAllocation;
    return mesh;
  }

  private static VertexArena.Allocation allocateIndices(VertexArena indices, Batch batch) {
    return indices.allocate(
        batch.indexCount(),
        data -> Packing.indices(data, batch.indices(), batch.indexCount(), true));
  }

  public boolean sortable() {
    return kind == Kind.SORTABLE || indexArena != null;
  }

  public static long estimatedBytes(Batch batch) {
    boolean index32 = Packing.index32(batch);
    return (long) Packing.vertexBytes(batch)
        + (long) batch.indexCount() * (index32 ? Integer.BYTES : Short.BYTES);
  }

  public void updateIndices(Batch batch) {
    if (!valid()) throw new IllegalStateException("mesh is closed");
    if (!sortable()) {
      throw new IllegalStateException("index update requires a sortable mesh, kind=" + kind);
    }
    if (batch.vertexCount() != vertexCount) {
      throw new IllegalStateException(
          "index update vertex generation no longer matches resident mesh");
    }
    if (!index32 && Packing.index32(batch)) {
      throw new IllegalStateException("index update requires wider index storage");
    }
    if (indexArena != null) {
      VertexArena.Allocation replacement = allocateIndices(indexArena, batch);
      if (replacement == null) throw new StagingBackpressureException("index arena is full");
      indexArena.free(indexAllocation);
      indexAllocation = replacement;
      indices = replacement.handle();
      indexCount = batch.indexCount();
      indexBytes = indexCount * Integer.BYTES;
      bytes = (long) vertexBytes + indexBytes;
      return;
    }
    int newIndexBytes =
        Math.multiplyExact(batch.indexCount(), index32 ? Integer.BYTES : Short.BYTES);
    BGFXMemory indexMemory =
        StagingMemory.allocate(
            newIndexBytes,
            data -> Packing.indices(data, batch.indices(), batch.indexCount(), index32));
    bgfx_update_dynamic_index_buffer(indices, 0, indexMemory);
    if (net.dx12mc.bridge.Profile.ENABLED)
      net.dx12mc.bridge.Profile.count(net.dx12mc.bridge.Profile.Count.INDEX_UPLOADS, 1);
    indexCount = batch.indexCount();
    indexBytes = newIndexBytes;
    bytes = (long) vertexBytes + indexBytes;
  }

  public long bytes() {
    return bytes;
  }

  public void bind() {
    if (!valid()) throw new IllegalStateException("mesh is closed");
    int first = drawIndexCount < 0 ? 0 : drawFirstIndex;
    int count = drawIndexCount < 0 ? indexCount : drawIndexCount;
    if (kind == Kind.POOLED || kind == Kind.SHARED_QUADS || kind == Kind.ARENA) {
      bgfx_set_dynamic_vertex_buffer(0, vertices, startVertex, vertexCount);
    } else {
      bgfx_set_vertex_buffer(0, vertices, 0, vertexCount);
    }
    if (indexArena != null) {
      bgfx_set_dynamic_index_buffer(indices, indexAllocation.startVertex() + first, count);
    } else if (kind == Kind.SHARED_QUADS || kind == Kind.ARENA) {
      bgfx_set_index_buffer(indices, first, count);
    } else bgfx_set_dynamic_index_buffer(indices, first, count);
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
    if (kind == Kind.ARENA) {
      if (allocation != null) arena.free(allocation);
      if (indexAllocation != null) indexArena.free(indexAllocation);
      allocation = null;
      indexAllocation = null;
      vertices = BGFX_INVALID_HANDLE;
      indices = BGFX_INVALID_HANDLE;
      return;
    }
    destroy(kind, vertices, kind == Kind.SHARED_QUADS ? BGFX_INVALID_HANDLE : indices);
    vertices = BGFX_INVALID_HANDLE;
    indices = BGFX_INVALID_HANDLE;
  }

  private static void destroy(Kind kind, short vertices, short indices) {
    if (BGFX_HANDLE_IS_VALID(vertices)) {
      if (kind == Kind.POOLED || kind == Kind.SHARED_QUADS) {
        bgfx_destroy_dynamic_vertex_buffer(vertices);
      } else bgfx_destroy_vertex_buffer(vertices);
    }
    if (BGFX_HANDLE_IS_VALID(indices)) {
      bgfx_destroy_dynamic_index_buffer(indices);
    }
  }
}
