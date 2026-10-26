package net.dx12mc.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.bridge.texture.Registry;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.mixin.RenderSystemAccessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class DrawContextFactory {
  private final Registry textures;
  private final RenderState renderState = new RenderState();
  private final Matrix4f mvpScratch = new Matrix4f();
  private final Matrix4f clipScratch = new Matrix4f();
  private final Matrix4f fogScratch = new Matrix4f();
  private final DrawContext context = new DrawContext();
  private String classifiedShaderName;
  private ShaderSemantics semantics = ShaderSemantics.of(null);
  private boolean skyIntent;
  private boolean sectionContextPrepared;
  private float sectionDepthBias;

  DrawContextFactory(Registry textures) {
    this.textures = textures;
  }

  DrawContext create(
      Batch batch,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight,
      boolean immediate) {
    mvpScratch.set(projection).mul(modelView);
    clipScratch.identity().m22(0.5f).m32(0.5f).mul(mvpScratch).get(context.matrix);
    String shader = shaderName();
    classifyShader(shader);
    prepareSharedUniforms();
    fogSpaceFromClip(clipScratch, ShaderInputs.viewSpaceFog(semantics) ? modelView : null);
    RenderState.Snapshot state = snapshot(framebufferWidth, framebufferHeight);
    DrawContext prepared = prepareResources(batch, shader, immediate, state);
    applyDepthBias(state.depthBias());
    return prepared;
  }

  DrawContext createSection(
      Batch batch,
      Matrix4f clipTransform,
      int framebufferWidth,
      int framebufferHeight,
      float offsetX,
      float offsetY,
      float offsetZ) {
    clipTransform.get(context.matrix);
    if (sectionContextPrepared) {
      applyDepthBias(sectionDepthBias);
      return context;
    }
    String shader = shaderName();
    classifyShader(shader);
    prepareSharedUniforms();
    fogSpaceFromClip(clipTransform, null);
    fogScratch.mulLocal(clipScratch.translation(offsetX, offsetY, offsetZ)).get(context.fogModel);
    RenderState.Snapshot state = snapshot(framebufferWidth, framebufferHeight);
    prepareResources(batch, shader, false, state);
    sectionDepthBias = state.depthBias();
    applyDepthBias(sectionDepthBias);
    sectionContextPrepared = true;
    return context;
  }

  void sectionMatrix(Matrix4f clipTransform) {
    clipTransform.get(context.matrix);
    applyDepthBias(sectionDepthBias);
  }

  private void fogSpaceFromClip(Matrix4f clip, Matrix4f viewSpace) {
    if (semantics.fogMode() == ShaderSemantics.FogMode.NONE || clip.determinant() == 0.0f) {
      fogScratch.identity().get(context.fogModel);
      return;
    }
    fogScratch.set(clip).invert();
    if (viewSpace != null) fogScratch.mulLocal(viewSpace);
    fogScratch.get(context.fogModel);
  }

  void semanticIntent(String intent) {
    skyIntent = intent != null && intent.startsWith("level.sky");
    sectionContextPrepared = false;
    sectionDepthBias = 0.0f;
  }

  RenderState state() {
    return renderState;
  }

  DrawContext context() {
    return context;
  }

  private void prepareSharedUniforms() {
    RenderSystem.getTextureMatrix().get(context.textureMatrix);
    System.arraycopy(RenderSystem.getShaderColor(), 0, context.tint, 0, context.tint.length);
    if (semantics.glintAlpha()) applyGlintAlpha(RenderSystem.getShaderGlintAlpha());
    ShaderInputs.fog(
        semantics,
        RenderSystem.getShaderFogStart(),
        RenderSystem.getShaderFogEnd(),
        RenderSystem.getShaderFogShape().getIndex(),
        context.fogParams);
    System.arraycopy(RenderSystem.getShaderFogColor(), 0, context.fogColor, 0, 4);
    ShaderInputs.material(semantics, context.material);
  }

  private DrawContext prepareResources(
      Batch batch, String shader, boolean immediate, RenderState.Snapshot state) {
    Texture sampled = resolveBaseTexture(batch, shader);
    Texture lightmap =
        semantics.lightmap() != ShaderSemantics.Lightmap.NONE && batch.hasUv2()
            ? resolveOptional(RenderSystem.getShaderTexture(2))
            : null;
    DrawContext.Kind kind = kind(batch, sampled != null, lightmap != null, immediate);
    Texture overlay =
        kind == DrawContext.Kind.ENTITY && semantics.overlay() && batch.hasUv1()
            ? resolveOptional(RenderSystem.getShaderTexture(1))
            : null;
    ShaderInputs.light(
        semantics,
        lightmap != null,
        overlay != null,
        kind == DrawContext.Kind.ENTITY,
        context.lightParams);
    context.textureLod[0] = sampled == null ? 0.0f : sampled.maxLod();
    if (kind == DrawContext.Kind.ENTITY) prepareLightDirections();
    return context.set(kind, sampled, lightmap, overlay, semantics.discards(), state);
  }

  private DrawContext.Kind kind(
      Batch batch, boolean textured, boolean lightmapped, boolean immediate) {
    if (!textured) return lightmapped ? DrawContext.Kind.COLOR_LIGHT : DrawContext.Kind.COLOR;
    if (immediate && batch.hasNormal() && (semantics.diffuseLighting() || semantics.overlay()))
      return DrawContext.Kind.ENTITY;
    return lightmapped ? DrawContext.Kind.TEXTURE_LIGHT : DrawContext.Kind.TEXTURE;
  }

  private void prepareLightDirections() {
    Vector3f[] directions = RenderSystemAccessor.dx12mc$lightDirections();
    direction(directions[0], context.lightDirection0);
    direction(directions[1], context.lightDirection1);
  }

  private static void direction(Vector3f source, float[] target) {
    target[0] = source == null ? 0.0f : source.x();
    target[1] = source == null ? 0.0f : source.y();
    target[2] = source == null ? 0.0f : source.z();
    target[3] = 0.0f;
  }

  void applyGlintAlpha(float glintAlpha) {
    context.tint[0] *= glintAlpha;
    context.tint[1] *= glintAlpha;
    context.tint[2] *= glintAlpha;
  }

  private void applyDepthBias(float bias) {
    if (bias == 0.0f) return;
    float[] matrix = context.matrix;
    matrix[2] += bias * matrix[3];
    matrix[6] += bias * matrix[7];
    matrix[10] += bias * matrix[11];
    matrix[14] += bias * matrix[15];
  }

  private RenderState.Snapshot snapshot(int width, int height) {
    if (skyIntent) return renderState.skyCompatibilitySnapshot(width, height);
    return renderState.snapshot(width, height);
  }

  private void classifyShader(String shader) {
    if (Objects.equals(classifiedShaderName, shader)) return;
    classifiedShaderName = shader;
    semantics = ShaderSemantics.of(shader);
  }

  private Texture resolveBaseTexture(Batch batch, String shader) {
    if (!batch.hasUv0()) return null;
    int textureId = RenderSystem.getShaderTexture(0);
    Texture sampled = textures.resolve(textureId);
    if (sampled != null) return sampled;
    throw new UnsupportedRenderPathException(
        "Textured D3D12 draw has no usable texture mirror: textureId="
            + textureId
            + ", shader="
            + shader
            + ", "
            + textures.describeMissing(textureId)
            + ". OpenGL draw fallback is disabled.");
  }

  private Texture resolveOptional(int textureId) {
    return textureId <= 0 ? null : textures.resolve(textureId);
  }

  static String shaderName() {
    try {
      var shader = RenderSystem.getShader();
      return shader == null ? "<none>" : shader.getName();
    } catch (Throwable ignored) {
      return "<unavailable>";
    }
  }
}
