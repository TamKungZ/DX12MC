package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryUtil;

public final class VertexArena implements AutoCloseable {
  static final int CHUNK_VERTICES =
      Math.max(1 << 16, Integer.getInteger("dx12mc.arena.chunkVertices", 1 << 20));
  static final int MAX_CHUNKS = Math.max(1, Integer.getInteger("dx12mc.arena.chunks", 12));
  static final int BLOCK_VERTICES = Math.max(1 << 12, CHUNK_VERTICES / 8);

  interface Backing {
    short create(int vertices);

    void update(short handle, int startVertex, ByteBuffer data, int bytes);

    void destroy(short handle);
  }

  public record Allocation(short handle, int chunk, int startVertex, int vertexCount) {}

  private final int stride;
  private final Backing backing;
  private final List<Chunk> chunks = new ArrayList<>();
  private final List<Allocation> quarantine = new ArrayList<>();
  private ByteBuffer pending;
  private int pendingChunk = -1;
  private int pendingStart;
  private int pendingEnd;
  private int blockEnd;
  private int uploadsThisFrame;
  private long uploadedBytesThisFrame;

  private static final class Chunk {
    final short handle;
    final FreeList free = new FreeList();

    Chunk(short handle) {
      this.handle = handle;
      free.release(0, CHUNK_VERTICES);
    }
  }

  public VertexArena(BGFXVertexLayout layout, int stride) {
    this(
        stride,
        new Backing() {
          @Override
          public short create(int vertices) {
            return bgfx_create_dynamic_vertex_buffer(vertices, layout, BGFX_BUFFER_NONE);
          }

          @Override
          public void update(short handle, int startVertex, ByteBuffer data, int bytes) {
            bgfx_update_dynamic_vertex_buffer(
                handle,
                startVertex,
                StagingMemory.allocate(bytes, target -> target.put(0, data, 0, bytes)));
          }

          @Override
          public void destroy(short handle) {
            bgfx_destroy_dynamic_vertex_buffer(handle);
          }
        });
  }

  public static VertexArena indices32() {
    return new VertexArena(
        Integer.BYTES,
        new Backing() {
          @Override
          public short create(int indices) {
            return bgfx_create_dynamic_index_buffer(indices, BGFX_BUFFER_INDEX32);
          }

          @Override
          public void update(short handle, int startIndex, ByteBuffer data, int bytes) {
            bgfx_update_dynamic_index_buffer(
                handle,
                startIndex,
                StagingMemory.allocate(bytes, target -> target.put(0, data, 0, bytes)));
          }

          @Override
          public void destroy(short handle) {
            bgfx_destroy_dynamic_index_buffer(handle);
          }
        });
  }

  VertexArena(int stride, Backing backing) {
    this.stride = stride;
    this.backing = backing;
  }

  public Allocation allocate(int vertexCount, Consumer<ByteBuffer> writer) {
    if (vertexCount <= 0 || vertexCount > CHUNK_VERTICES) return null;
    if (pendingChunk < 0 || pendingEnd + vertexCount > blockEnd) {
      flush();
      if (!reserve(vertexCount)) return null;
    }
    int start = pendingEnd;
    int offset = (start - pendingStart) * stride;
    int bytes = vertexCount * stride;
    ensurePending(offset + bytes);
    writer.accept(pending.slice(offset, bytes));
    pendingEnd += vertexCount;
    return new Allocation(chunks.get(pendingChunk).handle, pendingChunk, start, vertexCount);
  }

  public void free(Allocation allocation) {
    if (allocation != null) quarantine.add(allocation);
  }

  public void flush() {
    if (pendingChunk < 0) return;
    Chunk chunk = chunks.get(pendingChunk);
    int written = pendingEnd - pendingStart;
    if (written > 0) {
      backing.update(chunk.handle, pendingStart, pending, written * stride);
      uploadsThisFrame++;
      if (net.dx12mc.bridge.Profile.ENABLED)
        net.dx12mc.bridge.Profile.count(net.dx12mc.bridge.Profile.Count.ARENA_UPLOADS, 1);
      uploadedBytesThisFrame += (long) written * stride;
    }
    if (blockEnd > pendingEnd) chunk.free.release(pendingEnd, blockEnd - pendingEnd);
    pendingChunk = -1;
  }

  public void onFrameBoundary() {
    for (Allocation allocation : quarantine) {
      chunks
          .get(allocation.chunk())
          .free
          .release(allocation.startVertex(), allocation.vertexCount());
    }
    quarantine.clear();
    uploadsThisFrame = 0;
    uploadedBytesThisFrame = 0L;
  }

  public int uploadsThisFrame() {
    return uploadsThisFrame;
  }

  public long uploadedBytesThisFrame() {
    return uploadedBytesThisFrame;
  }

  public int chunkCount() {
    return chunks.size();
  }

  public long freeVertices() {
    long total = 0;
    for (Chunk chunk : chunks) total += chunk.free.total();
    return total;
  }

  private boolean reserve(int vertexCount) {
    int want = Math.max(vertexCount, BLOCK_VERTICES);
    for (int i = 0; i < chunks.size(); i++) {
      int[] range = chunks.get(i).free.take(vertexCount, want);
      if (range != null) return begin(i, range);
    }
    if (chunks.size() >= MAX_CHUNKS) return false;
    short handle = backing.create(CHUNK_VERTICES);
    if (!BGFX_HANDLE_IS_VALID(handle)) return false;
    chunks.add(new Chunk(handle));
    int[] range = chunks.get(chunks.size() - 1).free.take(vertexCount, want);
    return range != null && begin(chunks.size() - 1, range);
  }

  private boolean begin(int chunk, int[] range) {
    pendingChunk = chunk;
    pendingStart = range[0];
    pendingEnd = range[0];
    blockEnd = range[0] + range[1];
    return true;
  }

  private void ensurePending(int bytes) {
    if (pending != null && pending.capacity() >= bytes) return;
    int capacity =
        Math.max(bytes, pending == null ? BLOCK_VERTICES * stride : pending.capacity() * 2);
    ByteBuffer grown = MemoryUtil.memAlloc(capacity);
    if (pending != null) {
      grown.put(0, pending, 0, pending.capacity());
      MemoryUtil.memFree(pending);
    }
    pending = grown;
  }

  @Override
  public void close() {
    pendingChunk = -1;
    quarantine.clear();
    for (Chunk chunk : chunks) backing.destroy(chunk.handle);
    chunks.clear();
    if (pending != null) {
      MemoryUtil.memFree(pending);
      pending = null;
    }
  }

  static final class FreeList {
    private int[] starts = new int[16];
    private int[] sizes = new int[16];
    private int count;

    int[] take(int minimum, int wanted) {
      int chosen = -1;
      for (int i = 0; i < count; i++) {
        if (sizes[i] >= wanted) {
          chosen = i;
          break;
        }
        if (chosen < 0 && sizes[i] >= minimum) chosen = i;
      }
      if (chosen < 0) return null;
      int size = Math.min(sizes[chosen], wanted);
      int start = starts[chosen];
      starts[chosen] += size;
      sizes[chosen] -= size;
      if (sizes[chosen] == 0) remove(chosen);
      return new int[] {start, size};
    }

    void release(int start, int size) {
      if (size <= 0) return;
      int at = 0;
      while (at < count && starts[at] < start) at++;
      if (at > 0 && starts[at - 1] + sizes[at - 1] > start) {
        throw new IllegalStateException("vertex range released twice");
      }
      if (at < count && start + size > starts[at]) {
        throw new IllegalStateException("vertex range released twice");
      }
      boolean mergeLeft = at > 0 && starts[at - 1] + sizes[at - 1] == start;
      boolean mergeRight = at < count && start + size == starts[at];
      if (mergeLeft && mergeRight) {
        sizes[at - 1] += size + sizes[at];
        remove(at);
      } else if (mergeLeft) {
        sizes[at - 1] += size;
      } else if (mergeRight) {
        starts[at] = start;
        sizes[at] += size;
      } else {
        insert(at, start, size);
      }
    }

    long total() {
      long total = 0;
      for (int i = 0; i < count; i++) total += sizes[i];
      return total;
    }

    int ranges() {
      return count;
    }

    private void insert(int at, int start, int size) {
      if (count == starts.length) {
        starts = java.util.Arrays.copyOf(starts, count * 2);
        sizes = java.util.Arrays.copyOf(sizes, count * 2);
      }
      System.arraycopy(starts, at, starts, at + 1, count - at);
      System.arraycopy(sizes, at, sizes, at + 1, count - at);
      starts[at] = start;
      sizes[at] = size;
      count++;
    }

    private void remove(int at) {
      System.arraycopy(starts, at + 1, starts, at, count - at - 1);
      System.arraycopy(sizes, at + 1, sizes, at, count - at - 1);
      count--;
    }
  }
}
