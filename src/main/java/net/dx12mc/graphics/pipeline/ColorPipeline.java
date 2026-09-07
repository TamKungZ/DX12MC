package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Packing;
import net.dx12mc.graphics.bgfx.Program;
import org.lwjgl.bgfx.BGFXTransientIndexBuffer;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.system.MemoryStack;

public final class ColorPipeline implements AutoCloseable {
  private static final String VS = "/assets/dx12mc/shaders/d3d12/vs_position_color.bin";
  private static final String FS = "/assets/dx12mc/shaders/d3d12/fs_position_color.bin";
  private final Layout layout;
  private final Program program;
  private short mvpUniform = BGFX_INVALID_HANDLE;
  private short colorUniform = BGFX_INVALID_HANDLE;

  public ColorPipeline(Layout layout) {
    this.layout = layout;
    program = Program.load(VS, FS);
    mvpUniform = bgfx_create_uniform("u_mvp", BGFX_UNIFORM_TYPE_MAT4, 1);
    colorUniform = bgfx_create_uniform("u_color", BGFX_UNIFORM_TYPE_VEC4, 1);
    if (!BGFX_HANDLE_IS_VALID(mvpUniform) || !BGFX_HANDLE_IS_VALID(colorUniform)) {
      close();
      throw new IllegalStateException("Unable to create color pipeline uniforms");
    }
  }

  public Mesh upload(Batch batch) {
    return Mesh.create(batch, layout.value());
  }

  public boolean submit(Mesh mesh, float[] mvp, float[] color, RenderState.Snapshot state) {
    if (state.scissorEmpty()) return true;
    if (mesh == null || !mesh.valid()) return false;
    mesh.bind();
    return submitState(mvp, color, state);
  }

  public boolean submit(Batch batch, float[] mvp, float[] color, RenderState.Snapshot state) {
    if (state.scissorEmpty()) return true;
    boolean index32 = Packing.index32(batch.indices());
    try (MemoryStack stack = MemoryStack.stackPush()) {
      BGFXTransientVertexBuffer vertices = BGFXTransientVertexBuffer.calloc(stack);
      BGFXTransientIndexBuffer indices = BGFXTransientIndexBuffer.calloc(stack);
      if (!bgfx_alloc_transient_buffers(
          vertices, layout.value(), batch.vertexCount(), indices, batch.indices().length, index32))
        return false;
      Packing.vertices(vertices.data(), batch);
      Packing.indices(indices.data(), batch.indices(), index32);
      bgfx_set_transient_vertex_buffer(0, vertices, 0, batch.vertexCount());
      bgfx_set_transient_index_buffer(indices, 0, batch.indices().length);
      return submitState(mvp, color, state);
    }
  }

  @Override
  public void close() {
    if (BGFX_HANDLE_IS_VALID(mvpUniform)) {
      bgfx_destroy_uniform(mvpUniform);
      mvpUniform = BGFX_INVALID_HANDLE;
    }
    if (BGFX_HANDLE_IS_VALID(colorUniform)) {
      bgfx_destroy_uniform(colorUniform);
      colorUniform = BGFX_INVALID_HANDLE;
    }
    program.close();
  }

  private boolean submitState(float[] mvp, float[] color, RenderState.Snapshot state) {
    if (state.scissorEmpty()) return true;
    bgfx_set_uniform(mvpUniform, mvp, 1);
    bgfx_set_uniform(colorUniform, color, 1);
    if (state.scissorEnabled()) {
      bgfx_set_scissor(
          state.scissorX(), state.scissorY(), state.scissorWidth(), state.scissorHeight());
    }
    bgfx_set_state(state.bgfxState(), 0);
    bgfx_submit(Backend.MAIN_VIEW, program.handle(), 0, BGFX_DISCARD_ALL);
    return true;
  }
}
