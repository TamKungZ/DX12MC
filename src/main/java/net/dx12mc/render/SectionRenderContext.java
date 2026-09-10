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
  private double cameraX;
  private double cameraY;
  private double cameraZ;

  void begin(
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight,
      double cameraX,
      double cameraY,
      double cameraZ) {
    layerClipTransform.identity().m22(0.5f).m32(0.5f).mul(projection).mul(modelView);
    this.framebufferWidth = framebufferWidth;
    this.framebufferHeight = framebufferHeight;
    this.cameraX = cameraX;
    this.cameraY = cameraY;
    this.cameraZ = cameraZ;
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

  Matrix4f layerClipTransform() {
    return layerClipTransform;
  }

  int originX() {
    return sectionOrigin(cameraX, offsetX);
  }

  int originY() {
    return sectionOrigin(cameraY, offsetY);
  }

  int originZ() {
    return sectionOrigin(cameraZ, offsetZ);
  }

  double cameraX() {
    return cameraX;
  }

  double cameraY() {
    return cameraY;
  }

  double cameraZ() {
    return cameraZ;
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
    cameraX = cameraY = cameraZ = 0.0;
  }

  private static int sectionOrigin(double camera, float offset) {
    return Math.toIntExact(Math.multiplyExact(Math.round((camera + offset) / 16.0), 16L));
  }
}
