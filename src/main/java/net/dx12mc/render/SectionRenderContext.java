package net.dx12mc.render;

import org.joml.Matrix4f;

final class SectionRenderContext {
  private final Matrix4f layerClipTransform = new Matrix4f();
  private final Matrix4f drawClipTransform = new Matrix4f();
  private boolean active;
  private boolean matricesValid;
  private int framebufferWidth;
  private int framebufferHeight;
  private float offsetX;
  private float offsetY;
  private float offsetZ;

  void begin(Matrix4f modelView, Matrix4f projection, int framebufferWidth, int framebufferHeight) {
    layerClipTransform.identity().m22(0.5f).m32(0.5f).mul(projection).mul(modelView);
    this.framebufferWidth = framebufferWidth;
    this.framebufferHeight = framebufferHeight;
    offsetX = offsetY = offsetZ = 0.0f;
    matricesValid = true;
    active = true;
  }

  void offset(float x, float y, float z) {
    if (!active) return;
    offsetX = x;
    offsetY = y;
    offsetZ = z;
  }

  Matrix4f drawClipTransform() {
    return drawClipTransform.set(layerClipTransform).translate(offsetX, offsetY, offsetZ);
  }

  int framebufferWidth() {
    return framebufferWidth;
  }

  int framebufferHeight() {
    return framebufferHeight;
  }

  boolean active() {
    return active;
  }

  boolean ready() {
    return active && matricesValid;
  }

  void end() {
    active = false;
    matricesValid = false;
    framebufferWidth = framebufferHeight = 0;
    offsetX = offsetY = offsetZ = 0.0f;
  }
}
