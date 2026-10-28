package net.dx12mc.pack;

import org.joml.Matrix4f;

public final class FrameUniforms {
  final float[] viewProj = new float[16];
  final float[] invViewProj = new float[16];
  final float[] prevViewProj = new float[16];
  final float[] view = new float[16];
  final float[] proj = new float[16];
  final float[] invProj = new float[16];
  final float[] cameraPos = new float[4];
  final float[] sunDir = new float[4];
  final float[] moonDir = new float[4];
  final float[] time = new float[4];
  final float[] viewSize = new float[4];
  final float[] frameFog = new float[4];
  final float[] frameFogColor = new float[4];
  private final Matrix4f remapped = new Matrix4f();
  private final Matrix4f scratch = new Matrix4f();
  private final Matrix4f previous = new Matrix4f();
  private final Matrix4f reprojection = new Matrix4f();
  private double previousX;
  private double previousY;
  private double previousZ;
  private boolean hasPrevious;
  private long frames;

  public void camera(
      Matrix4f projection,
      Matrix4f cameraRotation,
      double cameraX,
      double cameraY,
      double cameraZ,
      float sunAngle,
      float dayTime,
      float seconds,
      float partialTick) {
    remapped.identity().m22(0.5f).m32(0.5f).mul(projection);
    remapped.get(proj);
    scratch.set(remapped).invert().get(invProj);
    cameraRotation.get(view);
    scratch.set(remapped).mul(cameraRotation);
    scratch.get(viewProj);
    if (hasPrevious) {
      reprojection
          .set(previous)
          .translate(
              (float) (cameraX - previousX),
              (float) (cameraY - previousY),
              (float) (cameraZ - previousZ));
    } else {
      reprojection.set(scratch);
    }
    reprojection.get(prevViewProj);
    previous.set(scratch);
    scratch.invert().get(invViewProj);
    previousX = cameraX;
    previousY = cameraY;
    previousZ = cameraZ;
    hasPrevious = true;
    cameraPos[0] = (float) cameraX;
    cameraPos[1] = (float) cameraY;
    cameraPos[2] = (float) cameraZ;
    sunDirection(sunAngle, sunDir);
    moonDir[0] = -sunDir[0];
    moonDir[1] = -sunDir[1];
    moonDir[2] = -sunDir[2];
    moonDir[3] = sunDir[3];
    time[0] = dayTime;
    time[1] = seconds;
    time[2] = partialTick;
    time[3] = frames++ % 65536;
  }

  public void viewSize(int width, int height) {
    viewSize[0] = width;
    viewSize[1] = height;
    viewSize[2] = 1.0f / Math.max(1, width);
    viewSize[3] = 1.0f / Math.max(1, height);
  }

  public void fog(float start, float end, int shape, float[] color) {
    frameFog[0] = start;
    frameFog[1] = end;
    frameFog[2] = shape;
    frameFog[3] = 0.0f;
    System.arraycopy(color, 0, frameFogColor, 0, 4);
  }

  public void reset() {
    hasPrevious = false;
  }

  static void sunDirection(float sunAngle, float[] target) {
    target[0] = (float) -Math.sin(sunAngle);
    target[1] = (float) Math.cos(sunAngle);
    target[2] = 0.0f;
    target[3] = sunAngle;
  }

  float[] value(String name) {
    return switch (name) {
      case "u_cameraViewProj" -> viewProj;
      case "u_cameraInvViewProj" -> invViewProj;
      case "u_cameraPrevViewProj" -> prevViewProj;
      case "u_cameraView" -> view;
      case "u_cameraProj" -> proj;
      case "u_cameraInvProj" -> invProj;
      case "u_cameraPos" -> cameraPos;
      case "u_sunDir" -> sunDir;
      case "u_moonDir" -> moonDir;
      case "u_time" -> time;
      case "u_viewSize" -> viewSize;
      case "u_frameFog" -> frameFog;
      case "u_frameFogColor" -> frameFogColor;
      default -> null;
    };
  }
}
