package net.dx12mc.bridge.state;

record ScissorRect(int x, int y, int width, int height, boolean empty) {
  static ScissorRect clip(
      boolean enabled,
      int x,
      int y,
      int width,
      int height,
      int framebufferWidth,
      int framebufferHeight) {
    if (!enabled) return new ScissorRect(0, 0, 0, 0, false);
    int fbWidth = Math.max(0, framebufferWidth);
    int fbHeight = Math.max(0, framebufferHeight);
    long left = x;
    long right = (long) x + Math.max(0, width);
    long bottom = y;
    long top = (long) y + Math.max(0, height);
    long clippedLeft = clamp(left, 0, fbWidth);
    long clippedRight = clamp(right, 0, fbWidth);
    long clippedBottom = clamp(bottom, 0, fbHeight);
    long clippedTop = clamp(top, 0, fbHeight);
    if (clippedRight <= clippedLeft || clippedTop <= clippedBottom) {
      return new ScissorRect(0, 0, 0, 0, true);
    }
    return new ScissorRect(
        clamp16(clippedLeft),
        clamp16((long) fbHeight - clippedTop),
        clamp16(clippedRight - clippedLeft),
        clamp16(clippedTop - clippedBottom),
        false);
  }

  private static long clamp(long value, long min, long max) {
    return Math.max(min, Math.min(max, value));
  }

  private static int clamp16(long value) {
    return (int) Math.max(0, Math.min(0xFFFFL, value));
  }
}
