package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import org.lwjgl.bgfx.BGFXMemory;

public final class QuadIndices implements AutoCloseable {
  private static final int MAX_QUADS_16 = 0x10000 / 4;
  private static final int MAX_QUADS_32 =
      Math.max(MAX_QUADS_16, Integer.getInteger("dx12mc.region.sharedQuads", 131_072));
  private static short handle16 = BGFX_INVALID_HANDLE;
  private static short handle32 = BGFX_INVALID_HANDLE;

  public QuadIndices() {
    try {
      handle16 = create(MAX_QUADS_16, false);
      handle32 = create(MAX_QUADS_32, true);
    } catch (Throwable throwable) {
      close();
      throw throwable;
    }
  }

  static boolean supports(int vertexCount, boolean index32) {
    if (vertexCount <= 0 || vertexCount % 4 != 0) return false;
    int quads = vertexCount / 4;
    return index32
        ? BGFX_HANDLE_IS_VALID(handle32) && quads <= MAX_QUADS_32
        : BGFX_HANDLE_IS_VALID(handle16) && quads <= MAX_QUADS_16;
  }

  static short handle(boolean index32) {
    return index32 ? handle32 : handle16;
  }

  @Override
  public void close() {
    if (BGFX_HANDLE_IS_VALID(handle16)) bgfx_destroy_index_buffer(handle16);
    if (BGFX_HANDLE_IS_VALID(handle32)) bgfx_destroy_index_buffer(handle32);
    handle16 = BGFX_INVALID_HANDLE;
    handle32 = BGFX_INVALID_HANDLE;
  }

  private static short create(int quads, boolean index32) {
    int indexCount = Math.multiplyExact(quads, 6);
    int bytes = Math.multiplyExact(indexCount, index32 ? Integer.BYTES : Short.BYTES);
    BGFXMemory memory =
        StagingMemory.allocate(
            bytes,
            data -> {
              data.order(java.nio.ByteOrder.nativeOrder());
              for (int quad = 0; quad < quads; quad++) {
                int vertex = quad * 4;
                int base = quad * 6;
                put(data, base, vertex, index32);
                put(data, base + 1, vertex + 1, index32);
                put(data, base + 2, vertex + 2, index32);
                put(data, base + 3, vertex + 2, index32);
                put(data, base + 4, vertex + 3, index32);
                put(data, base + 5, vertex, index32);
              }
            });
    short created =
        bgfx_create_index_buffer(memory, index32 ? BGFX_BUFFER_INDEX32 : BGFX_BUFFER_NONE);
    if (!BGFX_HANDLE_IS_VALID(created)) {
      throw new IllegalStateException("bgfx rejected the shared quad index buffer");
    }
    return created;
  }

  private static void put(java.nio.ByteBuffer data, int index, int value, boolean index32) {
    if (index32) data.putInt(index * Integer.BYTES, value);
    else data.putShort(index * Short.BYTES, (short) value);
  }
}
