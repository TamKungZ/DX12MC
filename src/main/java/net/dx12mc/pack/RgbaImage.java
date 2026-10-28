package net.dx12mc.pack;

public record RgbaImage(int width, int height, byte[] rgba) {
  public RgbaImage {
    if (width <= 0 || height <= 0 || (long) width * height * 4 != rgba.length) {
      throw new IllegalArgumentException("RGBA image size does not match its pixels");
    }
  }
}
