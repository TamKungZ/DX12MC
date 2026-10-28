package net.dx12mc.graphics.bgfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VertexArenaTest {
  private static final int STRIDE = 28;

  private static final class FakeBacking implements VertexArena.Backing {
    final List<int[]> updates = new ArrayList<>();
    final List<byte[]> payloads = new ArrayList<>();
    int created;
    int destroyed;

    @Override
    public short create(int vertices) {
      return (short) ++created;
    }

    @Override
    public void update(short handle, int startVertex, ByteBuffer data, int bytes) {
      updates.add(new int[] {handle, startVertex, bytes});
      byte[] copy = new byte[bytes];
      data.get(0, copy);
      payloads.add(copy);
    }

    @Override
    public void destroy(short handle) {
      destroyed++;
    }
  }

  private static VertexArena.Allocation allocate(VertexArena arena, int vertices, byte fill) {
    return arena.allocate(
        vertices,
        data -> {
          for (int i = 0; i < vertices * STRIDE; i++) data.put(i, fill);
        });
  }

  @Test
  void pagesBuiltInOneFrameReachBgfxAsOneUpload() {
    FakeBacking backing = new FakeBacking();
    VertexArena arena = new VertexArena(STRIDE, backing);
    VertexArena.Allocation a = allocate(arena, 100, (byte) 1);
    VertexArena.Allocation b = allocate(arena, 50, (byte) 2);
    VertexArena.Allocation c = allocate(arena, 10, (byte) 3);
    assertEquals(a.startVertex() + 100, b.startVertex());
    assertEquals(b.startVertex() + 50, c.startVertex());
    arena.flush();
    assertEquals(1, backing.updates.size());
    assertEquals(1, backing.created);
    assertEquals(160 * STRIDE, backing.updates.get(0)[2]);
    byte[] payload = backing.payloads.get(0);
    assertEquals(1, payload[0]);
    assertEquals(2, payload[100 * STRIDE]);
    assertEquals(3, payload[150 * STRIDE]);
    assertEquals(1, arena.uploadsThisFrame());
    arena.flush();
    assertEquals(1, backing.updates.size());
  }

  @Test
  void freedRangesAreReusedOnlyAfterTheFrameBoundary() {
    FakeBacking backing = new FakeBacking();
    VertexArena arena = new VertexArena(STRIDE, backing);
    VertexArena.Allocation first = allocate(arena, VertexArena.CHUNK_VERTICES, (byte) 1);
    arena.flush();
    arena.onFrameBoundary();
    arena.free(first);
    VertexArena.Allocation second = allocate(arena, 1000, (byte) 2);
    assertNotEquals(
        first.chunk(), second.chunk(), "a range freed this frame must not be reused yet");
    arena.flush();
    arena.onFrameBoundary();
    VertexArena.Allocation third = allocate(arena, 1000, (byte) 3);
    assertEquals(first.chunk(), third.chunk());
    assertEquals(0, third.startVertex());
    arena.flush();
    assertEquals(2, backing.created);
  }

  @Test
  void releasedRangesMergeAndDoubleReleaseIsRejected() {
    VertexArena.FreeList free = new VertexArena.FreeList();
    free.release(0, 10);
    free.release(20, 10);
    free.release(10, 10);
    assertEquals(1, free.ranges());
    assertEquals(30L, free.total());
    assertThrows(IllegalStateException.class, () -> free.release(5, 2));
    int[] taken = free.take(4, 8);
    assertEquals(0, taken[0]);
    assertEquals(8, taken[1]);
    assertEquals(22L, free.total());
    assertNull(free.take(100, 100));
  }

  @Test
  void aSmallFragmentDoesNotSplitOneFrameIntoSeveralUploads() {
    VertexArena.FreeList free = new VertexArena.FreeList();
    free.release(0, 64);
    free.release(1000, 50_000);
    int[] block = free.take(32, 40_000);
    assertEquals(1000, block[0]);
    assertEquals(40_000, block[1]);
    assertEquals(64 + 10_000L, free.total());
    int[] fallback = free.take(20_000, 40_000);
    assertNull(fallback);
    int[] small = free.take(32, 40_000);
    assertEquals(0, small[0]);
    assertEquals(64, small[1]);
  }

  @Test
  void unusedBlockTailsReturnToTheFreeListAndCloseDestroysChunks() {
    FakeBacking backing = new FakeBacking();
    VertexArena arena = new VertexArena(STRIDE, backing);
    allocate(arena, 10, (byte) 1);
    arena.flush();
    assertEquals(VertexArena.CHUNK_VERTICES - 10L, arena.freeVertices());
    assertNull(allocate(arena, VertexArena.CHUNK_VERTICES + 1, (byte) 1));
    arena.close();
    assertEquals(backing.created, backing.destroyed);
    assertTrue(backing.created >= 1);
  }
}
