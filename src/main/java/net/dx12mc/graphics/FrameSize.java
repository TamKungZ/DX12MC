package net.dx12mc.graphics;

public record FrameSize(int width, int height) {
  public FrameSize {
    width = Math.max(1, width);
    height = Math.max(1, height);
  }
}
