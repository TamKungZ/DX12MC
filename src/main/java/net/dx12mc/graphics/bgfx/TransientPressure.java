package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.bgfx_get_avail_transient_index_buffer;
import static org.lwjgl.bgfx.BGFX.bgfx_get_avail_transient_vertex_buffer;

import org.lwjgl.bgfx.BGFXVertexLayout;

public final class TransientPressure {
  private TransientPressure() {}

  public static boolean insufficient(
      BGFXVertexLayout layout, int vertexCount, int indexCount, boolean index32) {
    return Integer.compareUnsigned(
                bgfx_get_avail_transient_vertex_buffer(vertexCount, layout), vertexCount)
            < 0
        || Integer.compareUnsigned(
                bgfx_get_avail_transient_index_buffer(indexCount, index32), indexCount)
            < 0;
  }
}
