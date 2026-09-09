package net.dx12mc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.dx12mc.bridge.Slot;
import net.dx12mc.config.Config;
import net.dx12mc.render.Controller;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class Runtime implements AutoCloseable {
  private final Controller controller;

  public Runtime(Config config) {
    this.controller = new Controller(config);
  }

  public void initialize() {
    controller.initialize();
  }

  public void capture(Slot slot, MeshData mesh, VertexBuffer.Usage usage) {
    controller.capture(slot, mesh, usage);
  }

  public void updateIndices(Slot slot, ByteBufferBuilder.Result result) {
    controller.updateIndices(slot, result);
  }

  public void beginLegacyVertexBufferUpload() {
    controller.beginLegacyVertexBufferUpload();
  }

  public void endLegacyVertexBufferUpload() {
    controller.endLegacyVertexBufferUpload();
  }

  public boolean suppressLegacyVertexBufferStorage(int target, long bytes) {
    return controller.suppressLegacyVertexBufferStorage(target, bytes);
  }

  public boolean draw(Slot slot, Matrix4f modelView, Matrix4f projection) {
    return controller.draw(slot, modelView, projection);
  }

  public void beginSky() {
    controller.beginSky();
  }

  public void endSky() {
    controller.endSky();
  }

  public void beginSectionLayer(Matrix4f modelView, Matrix4f projection) {
    controller.beginSectionLayer(modelView, projection);
  }

  public void sectionChunkOffset(float x, float y, float z) {
    controller.sectionChunkOffset(x, y, z);
  }

  public boolean drawSection(Slot slot) {
    return controller.drawSection(slot);
  }

  public boolean suppressLegacySectionDriverCalls() {
    return controller.suppressLegacySectionDriverCalls();
  }

  public void endSectionLayer() {
    controller.endSectionLayer();
  }

  public boolean drawImmediate(MeshData mesh) {
    return controller.drawImmediate(mesh);
  }

  public void bindTexture(int id) {
    controller.bindTexture(id);
  }

  public void depthTest(boolean enabled) {
    controller.depthTest(enabled);
  }

  public void depthFunc(int func) {
    controller.depthFunc(func);
  }

  public void depthMask(boolean write) {
    controller.depthMask(write);
  }

  public void blend(boolean enabled) {
    controller.blend(enabled);
  }

  public void blendFunc(int src, int dst) {
    controller.blendFunc(src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    controller.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
  }

  public void blendEquation(int equation) {
    controller.blendEquation(equation);
  }

  public void cull(boolean enabled) {
    controller.cull(enabled);
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    controller.colorMask(red, green, blue, alpha);
  }

  public void scissor(boolean enabled) {
    controller.scissor(enabled);
  }

  public void scissorBox(int x, int y, int width, int height) {
    controller.scissorBox(x, y, width, height);
  }

  public void polygonOffset(boolean enabled) {
    controller.polygonOffset(enabled);
  }

  public void polygonOffset(float factor, float units) {
    controller.polygonOffset(factor, units);
  }

  public void polygonMode(int face, int mode) {
    controller.polygonMode(face, mode);
  }

  public void colorLogic(boolean enabled) {
    controller.colorLogic(enabled);
  }

  public void logicOp(int op) {
    controller.logicOp(op);
  }

  public void allocateTexture(int id, int maxLevel, int width, int height) {
    controller.allocateTexture(id, maxLevel, width, height);
  }

  public void mirrorBoundOpenGlTextureAllocation(int level, int width, int height) {
    controller.mirrorBoundOpenGlTextureAllocation(level, width, height);
  }

  public void uploadTexture(
      NativeImage image,
      int level,
      int offsetX,
      int offsetY,
      int skipPixels,
      int skipRows,
      int width,
      int height,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    controller.uploadTexture(
        image, level, offsetX, offsetY, skipPixels, skipRows, width, height, blur, clamp, mipmap);
  }

  public void deleteTexture(int id) {
    controller.deleteTexture(id);
  }

  public void release(Slot slot) {
    controller.release(slot);
  }

  public void onRenderHead(Minecraft minecraft) {
    controller.onRenderHead(minecraft);
  }

  public void presentAtSwap(Minecraft minecraft) {
    controller.presentAtSwap(minecraft);
  }

  public boolean openGlPresentationAllowed() {
    return controller.openGlPresentationAllowed();
  }

  public boolean suppressVanillaFramebufferBlit() {
    return controller.suppressVanillaFramebufferBlit();
  }

  public boolean suppressLegacyBlurPostProcess() {
    return controller.suppressLegacyBlurPostProcess();
  }

  public boolean suppressAutomaticWorldIconCapture() {
    return controller.suppressAutomaticWorldIconCapture();
  }

  public void assertOpenGlDrawAllowed(String operation) {
    controller.assertOpenGlDrawAllowed(operation);
  }

  public void assertOpenGlReadbackAllowed(String operation) {
    controller.assertOpenGlReadbackAllowed(operation);
  }

  public boolean shouldCancelGameRenderer() {
    return controller.shouldCancelGameRenderer();
  }

  @Override
  public void close() {
    controller.close();
  }
}
