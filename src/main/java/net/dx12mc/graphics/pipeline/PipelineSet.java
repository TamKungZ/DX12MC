package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.DrawContext;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.QuadIndices;
import org.lwjgl.bgfx.BGFXVertexLayout;

public final class PipelineSet implements AutoCloseable {
  private Layout layout;
  private Layout entityLayout;
  private QuadIndices quadIndices;
  private Uniforms uniforms;
  private final ShaderPipeline[] pipelines = new ShaderPipeline[DrawContext.Kind.values().length];
  private int preparedSectionPipeline = -1;

  public void start() {
    if (layout != null) return;
    try {
      layout = new Layout();
      entityLayout = Layout.entity();
      quadIndices = new QuadIndices();
      uniforms = new Uniforms();
      create(
          DrawContext.Kind.COLOR,
          layout,
          "vs_position_color",
          "fs_position_color",
          "fs_position_color_alpha");
      create(
          DrawContext.Kind.COLOR_LIGHT,
          layout,
          "vs_position_color_light",
          "fs_position_color",
          "fs_position_color_alpha");
      create(
          DrawContext.Kind.TEXTURE,
          layout,
          "vs_position_color_tex",
          "fs_position_color_tex",
          "fs_position_color_tex_cutout");
      create(
          DrawContext.Kind.TEXTURE_LIGHT,
          layout,
          "vs_position_color_tex_light",
          "fs_position_color_tex",
          "fs_position_color_tex_cutout");
      create(DrawContext.Kind.ENTITY, entityLayout, "vs_entity", "fs_entity", "fs_entity_cutout");
    } catch (Throwable throwable) {
      close();
      throw throwable;
    }
  }

  public boolean started() {
    for (ShaderPipeline pipeline : pipelines) if (pipeline == null) return false;
    return layout != null;
  }

  public BGFXVertexLayout layout() {
    if (!started()) throw new IllegalStateException("native pipelines are not initialized");
    return layout.value();
  }

  public BGFXVertexLayout layout(DrawContext context) {
    if (!started()) throw new IllegalStateException("native pipelines are not initialized");
    return pipeline(context, false).layout().value();
  }

  public boolean submit(Batch batch, DrawContext context) {
    return pipeline(context, false).submit(batch, context, false, false);
  }

  public boolean submitSection(Batch batch, DrawContext context) {
    ShaderPipeline pipeline = pipeline(context, false);
    int selected = selected(context, pipeline);
    boolean submitted = pipeline.submit(batch, context, preparedSectionPipeline == selected, true);
    if (submitted) preparedSectionPipeline = selected;
    return submitted;
  }

  public boolean submit(Mesh mesh, DrawContext context) {
    return pipeline(context, true).submit(mesh, context, false, false);
  }

  public boolean submitSection(Mesh mesh, DrawContext context) {
    ShaderPipeline pipeline = pipeline(context, true);
    int selected = selected(context, pipeline);
    boolean submitted = pipeline.submit(mesh, context, preparedSectionPipeline == selected, true);
    if (submitted) preparedSectionPipeline = selected;
    return submitted;
  }

  public void endSectionLayer() {
    if (preparedSectionPipeline < 0) return;
    bgfx_discard(BGFX_DISCARD_ALL);
    preparedSectionPipeline = -1;
  }

  @Override
  public void close() {
    endSectionLayer();
    for (int i = 0; i < pipelines.length; i++) {
      if (pipelines[i] != null) {
        pipelines[i].close();
        pipelines[i] = null;
      }
    }
    if (uniforms != null) {
      uniforms.close();
      uniforms = null;
    }
    if (quadIndices != null) {
      quadIndices.close();
      quadIndices = null;
    }
    if (entityLayout != null) {
      entityLayout.close();
      entityLayout = null;
    }
    if (layout != null) {
      layout.close();
      layout = null;
    }
  }

  private void create(
      DrawContext.Kind kind, Layout layout, String vertex, String fragment, String alphaTest) {
    pipelines[kind.ordinal()] =
        new ShaderPipeline(kind, layout, uniforms, vertex, fragment, alphaTest);
  }

  private ShaderPipeline pipeline(DrawContext context, boolean persistent) {
    DrawContext.Kind kind = context.kind();
    if (persistent && kind == DrawContext.Kind.ENTITY) kind = DrawContext.Kind.TEXTURE_LIGHT;
    return pipelines[kind.ordinal()];
  }

  private int selected(DrawContext context, ShaderPipeline pipeline) {
    int index = 0;
    while (pipelines[index] != pipeline) index++;
    return index * 2 + (context.alphaTest() ? 1 : 0);
  }
}
