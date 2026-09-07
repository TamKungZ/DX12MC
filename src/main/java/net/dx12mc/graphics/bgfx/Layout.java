package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import org.lwjgl.bgfx.BGFXVertexLayout;

public final class Layout implements AutoCloseable {
  private BGFXVertexLayout value;

  public Layout() {
    value = BGFXVertexLayout.calloc();
    bgfx_vertex_layout_begin(value, BGFX_RENDERER_TYPE_DIRECT3D12);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_POSITION, 3, BGFX_ATTRIB_TYPE_FLOAT, false, false);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_TEXCOORD1, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_TEXCOORD2, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
    bgfx_vertex_layout_add(value, BGFX_ATTRIB_NORMAL, 3, BGFX_ATTRIB_TYPE_FLOAT, false, false);
    bgfx_vertex_layout_end(value);
  }

  public BGFXVertexLayout value() {
    if (value == null) throw new IllegalStateException("layout is closed");
    return value;
  }

  @Override
  public void close() {
    if (value != null) {
      value.free();
      value = null;
    }
  }
}
