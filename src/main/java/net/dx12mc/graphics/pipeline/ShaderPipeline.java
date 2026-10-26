package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.DrawContext;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Packing;
import net.dx12mc.graphics.bgfx.Program;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.bgfx.ViewSequence;
import org.lwjgl.bgfx.BGFXTransientIndexBuffer;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.system.MemoryStack;

final class ShaderPipeline implements AutoCloseable {
  private static final int SECTION_GEOMETRY_DISCARD =
      BGFX_DISCARD_INDEX_BUFFER | BGFX_DISCARD_VERTEX_STREAMS;
  private static final String SHADERS = "/assets/dx12mc/shaders/d3d12/";
  private final DrawContext.Kind kind;
  private final Layout layout;
  private final Uniforms uniforms;
  private final Program program;
  private final Program alphaTestProgram;

  ShaderPipeline(
      DrawContext.Kind kind,
      Layout layout,
      Uniforms uniforms,
      String vertex,
      String fragment,
      String alphaTestFragment) {
    this.kind = kind;
    this.layout = layout;
    this.uniforms = uniforms;
    program = Program.load(SHADERS + vertex + ".bin", SHADERS + fragment + ".bin");
    Program alpha;
    try {
      alpha = Program.load(SHADERS + vertex + ".bin", SHADERS + alphaTestFragment + ".bin");
    } catch (Throwable throwable) {
      program.close();
      throw throwable;
    }
    alphaTestProgram = alpha;
  }

  Layout layout() {
    return layout;
  }

  boolean submit(Mesh mesh, DrawContext context, boolean reuseState, boolean preserveState) {
    RenderState.Snapshot state = context.state();
    if (state.scissorEmpty()) return true;
    if (mesh == null || !mesh.valid() || !texturesValid(context)) return false;
    mesh.bind();
    return submitState(context, reuseState, preserveState);
  }

  boolean submit(Batch batch, DrawContext context, boolean reuseState, boolean preserveState) {
    RenderState.Snapshot state = context.state();
    if (state.scissorEmpty()) return true;
    if (!texturesValid(context)) return false;
    boolean index32 = Packing.index32(batch);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      BGFXTransientVertexBuffer vertices = BGFXTransientVertexBuffer.calloc(stack);
      BGFXTransientIndexBuffer indices = BGFXTransientIndexBuffer.calloc(stack);
      if (!bgfx_alloc_transient_buffers(
          vertices, layout.value(), batch.vertexCount(), indices, batch.indexCount(), index32))
        return false;
      if (kind == DrawContext.Kind.ENTITY) Packing.entityVertices(vertices.data(), batch);
      else if (kind == DrawContext.Kind.LINE) Packing.lineVertices(vertices.data(), batch);
      else Packing.vertices(vertices.data(), batch);
      Packing.indices(indices.data(), batch.indices(), batch.indexCount(), index32);
      bgfx_set_transient_vertex_buffer(0, vertices, 0, batch.vertexCount());
      bgfx_set_transient_index_buffer(indices, 0, batch.indexCount());
      return submitState(context, reuseState, preserveState);
    }
  }

  @Override
  public void close() {
    alphaTestProgram.close();
    program.close();
  }

  private boolean texturesValid(DrawContext context) {
    if (kind.textured() && !valid(context.texture())) return false;
    return kind == DrawContext.Kind.ENTITY || !kind.lightmapped() || valid(context.lightmap());
  }

  private boolean submitState(DrawContext context, boolean reuseState, boolean preserveState) {
    RenderState.Snapshot state = context.state();
    uniforms.perDraw(context);
    if (!reuseState) {
      uniforms.shared(context);
      if (kind.textured()) {
        Texture texture = context.texture();
        bgfx_set_texture(0, uniforms.texture, texture.handle(), texture.samplerFlags());
      }
      if (kind.lightmapped()) {
        Texture lightmap = valid(context.lightmap()) ? context.lightmap() : context.texture();
        bgfx_set_texture(2, uniforms.lightmap, lightmap.handle(), lightmap.samplerFlags());
      }
      if (kind == DrawContext.Kind.ENTITY) {
        Texture overlay = valid(context.overlay()) ? context.overlay() : context.texture();
        bgfx_set_texture(1, uniforms.overlay, overlay.handle(), overlay.samplerFlags());
      }
      if (state.scissorEnabled()) {
        bgfx_set_scissor(
            state.scissorX(), state.scissorY(), state.scissorWidth(), state.scissorHeight());
      }
      bgfx_set_state(state.bgfxState() | context.primitive(), 0);
    }
    bgfx_submit(
        ViewSequence.current(),
        context.alphaTest() ? alphaTestProgram.handle() : program.handle(),
        0,
        preserveState ? SECTION_GEOMETRY_DISCARD : BGFX_DISCARD_ALL);
    ViewSequence.used();
    return true;
  }

  private static boolean valid(Texture texture) {
    return texture != null && texture.valid();
  }
}
