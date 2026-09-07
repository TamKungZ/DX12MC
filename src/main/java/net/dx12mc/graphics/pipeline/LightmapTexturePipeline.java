package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Packing;
import net.dx12mc.graphics.bgfx.Program;
import net.dx12mc.graphics.bgfx.Texture;
import org.lwjgl.bgfx.BGFXTransientIndexBuffer;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.system.MemoryStack;

public final class LightmapTexturePipeline implements AutoCloseable {
  private static final String VS = "/assets/dx12mc/shaders/d3d12/vs_position_color_tex_light.bin";
  private static final String FS = "/assets/dx12mc/shaders/d3d12/fs_position_color_tex_light.bin";
  private static final String FS_CUTOUT =
      "/assets/dx12mc/shaders/d3d12/fs_position_color_tex_light_cutout.bin";
  private final Layout layout;
  private final Program program;
  private final Program cutoutProgram;
  private short mvpUniform = BGFX_INVALID_HANDLE;
  private short colorUniform = BGFX_INVALID_HANDLE;
  private short textureMatrixUniform = BGFX_INVALID_HANDLE;
  private short samplerUniform = BGFX_INVALID_HANDLE;
  private short lightmapUniform = BGFX_INVALID_HANDLE;

  public LightmapTexturePipeline(Layout layout) {
    this.layout = layout;
    program = Program.load(VS, FS);
    cutoutProgram = Program.load(VS, FS_CUTOUT);
    mvpUniform = bgfx_create_uniform("u_mvp", BGFX_UNIFORM_TYPE_MAT4, 1);
    colorUniform = bgfx_create_uniform("u_color", BGFX_UNIFORM_TYPE_VEC4, 1);
    textureMatrixUniform = bgfx_create_uniform("u_texMatrix", BGFX_UNIFORM_TYPE_MAT4, 1);
    samplerUniform = bgfx_create_uniform("s_texColor", BGFX_UNIFORM_TYPE_SAMPLER, 1);
    lightmapUniform = bgfx_create_uniform("s_lightmap", BGFX_UNIFORM_TYPE_SAMPLER, 1);
    if (!BGFX_HANDLE_IS_VALID(mvpUniform)
        || !BGFX_HANDLE_IS_VALID(colorUniform)
        || !BGFX_HANDLE_IS_VALID(textureMatrixUniform)
        || !BGFX_HANDLE_IS_VALID(samplerUniform)
        || !BGFX_HANDLE_IS_VALID(lightmapUniform)) {
      close();
      throw new IllegalStateException("Unable to create lightmap texture pipeline uniforms");
    }
  }

  public boolean submit(
      Mesh mesh,
      Texture texture,
      Texture lightmap,
      float[] mvp,
      float[] texMatrix,
      float[] color,
      RenderState.Snapshot state,
      boolean cutout) {
    if (state.scissorEmpty()) return true;
    if (mesh == null
        || !mesh.valid()
        || texture == null
        || !texture.valid()
        || lightmap == null
        || !lightmap.valid()) return false;
    mesh.bind();
    return submitState(texture, lightmap, mvp, texMatrix, color, state, cutout);
  }

  public boolean submit(
      Batch batch,
      Texture texture,
      Texture lightmap,
      float[] mvp,
      float[] texMatrix,
      float[] color,
      RenderState.Snapshot state,
      boolean cutout) {
    if (state.scissorEmpty()) return true;
    if (texture == null || !texture.valid() || lightmap == null || !lightmap.valid()) return false;
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
      return submitState(texture, lightmap, mvp, texMatrix, color, state, cutout);
    }
  }

  private boolean submitState(
      Texture texture,
      Texture lightmap,
      float[] mvp,
      float[] texMatrix,
      float[] color,
      RenderState.Snapshot state,
      boolean cutout) {
    bgfx_set_uniform(mvpUniform, mvp, 1);
    bgfx_set_uniform(colorUniform, color, 1);
    bgfx_set_uniform(textureMatrixUniform, texMatrix, 1);
    bgfx_set_texture(0, samplerUniform, texture.handle(), texture.samplerFlags());
    bgfx_set_texture(2, lightmapUniform, lightmap.handle(), lightmap.samplerFlags());
    if (state.scissorEnabled()) {
      bgfx_set_scissor(
          state.scissorX(), state.scissorY(), state.scissorWidth(), state.scissorHeight());
    }
    bgfx_set_state(state.bgfxState(), 0);
    bgfx_submit(
        Backend.MAIN_VIEW, cutout ? cutoutProgram.handle() : program.handle(), 0, BGFX_DISCARD_ALL);
    return true;
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
    if (BGFX_HANDLE_IS_VALID(textureMatrixUniform)) {
      bgfx_destroy_uniform(textureMatrixUniform);
      textureMatrixUniform = BGFX_INVALID_HANDLE;
    }
    if (BGFX_HANDLE_IS_VALID(samplerUniform)) {
      bgfx_destroy_uniform(samplerUniform);
      samplerUniform = BGFX_INVALID_HANDLE;
    }
    if (BGFX_HANDLE_IS_VALID(lightmapUniform)) {
      bgfx_destroy_uniform(lightmapUniform);
      lightmapUniform = BGFX_INVALID_HANDLE;
    }
    cutoutProgram.close();
    program.close();
  }
}
