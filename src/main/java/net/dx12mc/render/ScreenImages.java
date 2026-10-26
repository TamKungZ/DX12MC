package net.dx12mc.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import net.dx12mc.graphics.bgfx.ScreenCapture;

public final class ScreenImages {

  private ScreenImages() {}

  public static NativeImage toNativeImage(ScreenCapture.Frame frame) {
    NativeImage image = new NativeImage(frame.width(), frame.height(), false);
    try {
      for (int y = 0; y < frame.height(); y++) {
        int row = sourceRow(y, frame.height(), frame.yflip()) * frame.pitch();
        for (int x = 0; x < frame.width(); x++) {
          image.setPixelRGBA(x, y, opaqueAbgr(frame.bgra(), row + x * 4));
        }
      }
      return image;
    } catch (RuntimeException exception) {
      image.close();
      throw exception;
    }
  }

  static int sourceRow(int y, int height, boolean yflip) {
    return yflip ? height - 1 - y : y;
  }

  static int opaqueAbgr(ByteBuffer bgra, int offset) {
    int blue = Byte.toUnsignedInt(bgra.get(offset));
    int green = Byte.toUnsignedInt(bgra.get(offset + 1));
    int red = Byte.toUnsignedInt(bgra.get(offset + 2));
    return 0xFF000000 | blue << 16 | green << 8 | red;
  }
}
