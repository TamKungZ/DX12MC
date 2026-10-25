package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.DrawContext;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.QuadIndices;

public final class PipelineSet implements AutoCloseable {
  private Layout layout;
  private QuadIndices quadIndices;
  private ColorPipeline color;
  private TexturePipeline texture;
  private LightmapTexturePipeline lightmapTexture;
  private int preparedSectionPipeline = -1;

  public void start() {
    if (layout != null) return;
    layout = new Layout();
    quadIndices = new QuadIndices();
    color = new ColorPipeline(layout);
    texture = new TexturePipeline(layout);
    lightmapTexture = new LightmapTexturePipeline(layout);
  }

  public boolean started() {
    return layout != null && color != null && texture != null && lightmapTexture != null;
  }

  public org.lwjgl.bgfx.BGFXVertexLayout layout() {
    if (!started()) throw new IllegalStateException("native pipelines are not initialized");
    return layout.value();
  }

  public boolean submit(
      Batch batch, DrawContext context, float[] matrix, float[] textureMatrix, float[] tint) {
    if (!context.textured()) return color.submit(batch, matrix, tint, context.state());
    if (context.lightmapped()) {
      return lightmapTexture.submit(
          batch,
          context.texture(),
          context.lightmap(),
          matrix,
          textureMatrix,
          tint,
          context.state(),
          context.cutout());
    }
    return texture.submit(
        batch, context.texture(), matrix, textureMatrix, tint, context.state(), context.cutout());
  }

  public boolean submitSection(
      Batch batch, DrawContext context, float[] matrix, float[] textureMatrix, float[] tint) {
    int selected = selectedPipeline(context);
    boolean reuseState = preparedSectionPipeline == selected;
    boolean submitted =
        !context.textured()
            ? color.submitSection(batch, matrix, tint, context.state(), reuseState)
            : context.lightmapped()
                ? lightmapTexture.submitSection(
                    batch,
                    context.texture(),
                    context.lightmap(),
                    matrix,
                    textureMatrix,
                    tint,
                    context.state(),
                    context.cutout(),
                    reuseState)
                : texture.submitSection(
                    batch,
                    context.texture(),
                    matrix,
                    textureMatrix,
                    tint,
                    context.state(),
                    context.cutout(),
                    reuseState);
    if (submitted) preparedSectionPipeline = selected;
    return submitted;
  }

  public boolean submit(
      Mesh mesh, DrawContext context, float[] matrix, float[] textureMatrix, float[] tint) {
    if (!context.textured()) return color.submit(mesh, matrix, tint, context.state());
    if (context.lightmapped()) {
      return lightmapTexture.submit(
          mesh,
          context.texture(),
          context.lightmap(),
          matrix,
          textureMatrix,
          tint,
          context.state(),
          context.cutout());
    }
    return texture.submit(
        mesh, context.texture(), matrix, textureMatrix, tint, context.state(), context.cutout());
  }

  public boolean submitSection(
      Mesh mesh, DrawContext context, float[] matrix, float[] textureMatrix, float[] tint) {
    int selected = selectedPipeline(context);
    boolean reuseState = preparedSectionPipeline == selected;
    boolean submitted =
        !context.textured()
            ? color.submitSection(mesh, matrix, tint, context.state(), reuseState)
            : context.lightmapped()
                ? lightmapTexture.submitSection(
                    mesh,
                    context.texture(),
                    context.lightmap(),
                    matrix,
                    textureMatrix,
                    tint,
                    context.state(),
                    context.cutout(),
                    reuseState)
                : texture.submitSection(
                    mesh,
                    context.texture(),
                    matrix,
                    textureMatrix,
                    tint,
                    context.state(),
                    context.cutout(),
                    reuseState);
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
    if (lightmapTexture != null) {
      lightmapTexture.close();
      lightmapTexture = null;
    }
    if (texture != null) {
      texture.close();
      texture = null;
    }
    if (color != null) {
      color.close();
      color = null;
    }
    if (quadIndices != null) {
      quadIndices.close();
      quadIndices = null;
    }
    if (layout != null) {
      layout.close();
      layout = null;
    }
  }

  private static int selectedPipeline(DrawContext context) {
    if (!context.textured()) return 0;
    if (!context.lightmapped()) return context.cutout() ? 2 : 1;
    return context.cutout() ? 4 : 3;
  }
}
