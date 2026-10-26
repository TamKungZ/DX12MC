package net.dx12mc.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import java.util.Objects;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderFamily;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.bridge.texture.Registry;
import net.dx12mc.graphics.bgfx.Texture;
import org.joml.Matrix4f;

final class DrawContextFactory {
  private final Registry textures;
  private final RenderState renderState = new RenderState();
  private final float[] matrix = new float[16];
  private final float[] textureMatrix = new float[16];
  private final float[] tint = new float[4];
  private final Matrix4f mvpScratch = new Matrix4f();
  private final Matrix4f clipScratch = new Matrix4f();
  private final DrawContext context = new DrawContext();
  private String classifiedShaderName;
  private ShaderFamily classifiedShaderFamily = ShaderFamily.BASIC;
  private boolean classifiedShaderCutout;
  private boolean classifiedShaderGlint;
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
    prepareUniforms(modelView, projection);
    String shader = shaderName();
    classifyShader(shader);
    if (classifiedShaderGlint) applyGlintAlpha(RenderSystem.getShaderGlintAlpha());
    Texture sampled = resolveBaseTexture(batch, shader);
    Texture lightmap = immediate ? null : resolveLightmap(batch, classifiedShaderFamily);
    RenderState.Snapshot state = snapshot(framebufferWidth, framebufferHeight, immediate);
    applyDepthBias(state.depthBias());
    return context.set(
        batch.hasUv0(), sampled, lightmap != null, lightmap, classifiedShaderCutout, state);
  }

  DrawContext createSection(
      Batch batch, Matrix4f clipTransform, int framebufferWidth, int framebufferHeight) {
    clipTransform.get(matrix);
    if (sectionContextPrepared) {
      applyDepthBias(sectionDepthBias);
      return context;
    }
    prepareSharedUniforms();
    String shader = shaderName();
    classifyShader(shader);
    Texture sampled = resolveBaseTexture(batch, shader);
    Texture lightmap = resolveLightmap(batch, classifiedShaderFamily);
    RenderState.Snapshot state = snapshot(framebufferWidth, framebufferHeight, false);
    sectionDepthBias = state.depthBias();
    applyDepthBias(sectionDepthBias);
    sectionContextPrepared = true;
    return context.set(
        batch.hasUv0(), sampled, lightmap != null, lightmap, classifiedShaderCutout, state);
  }

  void sectionMatrix(Matrix4f clipTransform) {
    clipTransform.get(matrix);
    applyDepthBias(sectionDepthBias);
  }

  void semanticIntent(String intent) {
    skyIntent = intent != null && intent.startsWith("level.sky");
    sectionContextPrepared = false;
    sectionDepthBias = 0.0f;
  }

  RenderState state() {
    return renderState;
  }

  float[] matrix() {
    return matrix;
  }

  float[] textureMatrix() {
    return textureMatrix;
  }

  float[] tint() {
    return tint;
  }

  private void prepareUniforms(Matrix4f modelView, Matrix4f projection) {
    mvpScratch.set(projection).mul(modelView);
    clipScratch.identity().m22(0.5f).m32(0.5f).mul(mvpScratch).get(matrix);
    prepareSharedUniforms();
  }

  private void prepareSharedUniforms() {
    RenderSystem.getTextureMatrix().get(textureMatrix);
    System.arraycopy(RenderSystem.getShaderColor(), 0, tint, 0, tint.length);
  }

  void applyGlintAlpha(float glintAlpha) {
    tint[0] *= glintAlpha;
    tint[1] *= glintAlpha;
    tint[2] *= glintAlpha;
  }

  private void applyDepthBias(float bias) {
    if (bias == 0.0f) return;
    matrix[2] += bias * matrix[3];
    matrix[6] += bias * matrix[7];
    matrix[10] += bias * matrix[11];
    matrix[14] += bias * matrix[15];
  }

  private RenderState.Snapshot snapshot(int width, int height, boolean immediate) {
    if (skyIntent) return renderState.skyCompatibilitySnapshot(width, height);
    return immediate
        ? renderState.immediateCompatibilitySnapshot(width, height)
        : renderState.snapshot(width, height);
  }

  private void classifyShader(String shader) {
    if (Objects.equals(classifiedShaderName, shader)) return;
    classifiedShaderName = shader;
    classifiedShaderFamily = ShaderFamily.classify(shader);
    classifiedShaderCutout = shader != null && shader.toLowerCase(Locale.ROOT).contains("cutout");
    classifiedShaderGlint = ShaderFamily.usesGlintAlpha(shader);
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

  private Texture resolveLightmap(Batch batch, ShaderFamily family) {
    if (!batch.hasUv2() || !family.lightmapCandidate()) return null;
    int lightmapId = RenderSystem.getShaderTexture(2);
    return lightmapId <= 0 ? null : textures.resolve(lightmapId);
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
