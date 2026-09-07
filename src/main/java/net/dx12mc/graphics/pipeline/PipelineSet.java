package net.dx12mc.graphics.pipeline;

import net.dx12mc.bridge.DrawContext;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;

public final class PipelineSet implements AutoCloseable {
  private Layout layout;
  private ColorPipeline color;
  private TexturePipeline texture;
  private LightmapTexturePipeline lightmapTexture;

  public void start() {
    if (layout != null) return;
    layout = new Layout();
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

  @Override
  public void close() {
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
    if (layout != null) {
      layout.close();
      layout = null;
    }
  }
}
