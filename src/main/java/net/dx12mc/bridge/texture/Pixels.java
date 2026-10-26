package net.dx12mc.bridge.texture;

import com.mojang.blaze3d.platform.NativeImage;

final class Pixels {

  private Pixels() {}

  static void writeAbgrAsRgba(byte[] target, int offset, int abgr) {
    if (target == null || offset < 0 || offset + 4 > target.length) {
      throw new IndexOutOfBoundsException("RGBA destination is too small");
    }
    target[offset] = (byte) abgr;
    target[offset + 1] = (byte) (abgr >>> 8);
    target[offset + 2] = (byte) (abgr >>> 16);
    target[offset + 3] = (byte) (abgr >>> 24);
  }

  static void writeNativeImageAsRgba(NativeImage image, int x, int y, byte[] target, int offset) {
    if (image == null) throw new IllegalArgumentException("image is null");
    if (target == null || offset < 0 || offset + 4 > target.length) {
      throw new IndexOutOfBoundsException("RGBA destination is too small");
    }
    NativeImage.Format format = image.format();
    if (format == NativeImage.Format.RGBA) {
      writeAbgrAsRgba(target, offset, image.getPixelRGBA(x, y));
      return;
    }
    if (format == NativeImage.Format.LUMINANCE) {
      writeRedTexel(target, offset, image.getRedOrLuminance(x, y));
      return;
    }
    int r = Byte.toUnsignedInt(image.getRedOrLuminance(x, y));
    int g = format.hasGreen() ? Byte.toUnsignedInt(image.getGreenOrLuminance(x, y)) : r;
    int b = format.hasBlue() ? Byte.toUnsignedInt(image.getBlueOrLuminance(x, y)) : r;
    int a = format.hasAlpha() ? Byte.toUnsignedInt(image.getLuminanceOrAlpha(x, y)) : 0xFF;
    target[offset] = (byte) r;
    target[offset + 1] = (byte) g;
    target[offset + 2] = (byte) b;
    target[offset + 3] = (byte) a;
  }

  static void writeRedTexel(byte[] target, int offset, byte red) {
    target[offset] = red;
    target[offset + 1] = 0;
    target[offset + 2] = 0;
    target[offset + 3] = (byte) 0xFF;
  }
}
