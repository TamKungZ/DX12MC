package net.dx12mc.render;

import org.joml.Matrix4f;

/** Holds the model-view state used while Minecraft submits one compiled section layer. */
final class SectionRenderContext {
  private final Matrix4f modelView = new Matrix4f();
  private final Matrix4f projection = new Matrix4f();
  private final Matrix4f drawModelView = new Matrix4f();
  private boolean active;
  private boolean matricesValid;
  private float offsetX;
  private float offsetY;
  private float offsetZ;

  void begin(Matrix4f modelView, Matrix4f projection) {
    this.modelView.set(modelView);
    this.projection.set(projection);
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

  Matrix4f drawModelView() {
    return drawModelView.set(modelView).translate(offsetX, offsetY, offsetZ);
  }

  Matrix4f projection() {
    return projection;
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
    offsetX = offsetY = offsetZ = 0.0f;
  }
}
