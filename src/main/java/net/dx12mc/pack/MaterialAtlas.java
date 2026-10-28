package net.dx12mc.pack;

import java.util.List;
import java.util.Map;

public final class MaterialAtlas {
  public record Sprite(String name, int x, int y, int width, int height) {}

  public record Layout(int textureId, int width, int height, int mipLevels, List<Sprite> sprites) {}

  private MaterialAtlas() {}

  public static long bytes(Layout layout) {
    long total = 0;
    for (int level = 0; level <= layout.mipLevels(); level++) {
      total +=
          (long) Math.max(1, layout.width() >> level) * Math.max(1, layout.height() >> level) * 4;
    }
    return total;
  }

  public static byte[][] build(Layout layout, Map<String, RgbaImage> companions, int[] fallback) {
    byte[] base = new byte[layout.width() * layout.height() * 4];
    for (int i = 0; i < base.length; i += 4) {
      base[i] = (byte) fallback[0];
      base[i + 1] = (byte) fallback[1];
      base[i + 2] = (byte) fallback[2];
      base[i + 3] = (byte) fallback[3];
    }
    for (Sprite sprite : layout.sprites()) {
      RgbaImage image = companions.get(sprite.name());
      if (image == null || image.width() != sprite.width() || image.height() < sprite.height())
        continue;
      if (sprite.x() < 0
          || sprite.y() < 0
          || sprite.x() + sprite.width() > layout.width()
          || sprite.y() + sprite.height() > layout.height()) continue;
      for (int row = 0; row < sprite.height(); row++) {
        System.arraycopy(
            image.rgba(),
            row * image.width() * 4,
            base,
            ((sprite.y() + row) * layout.width() + sprite.x()) * 4,
            sprite.width() * 4);
      }
    }
    byte[][] levels = new byte[layout.mipLevels() + 1][];
    levels[0] = base;
    for (int level = 1; level <= layout.mipLevels(); level++) {
      levels[level] =
          downsample(
              levels[level - 1],
              Math.max(1, layout.width() >> (level - 1)),
              Math.max(1, layout.height() >> (level - 1)));
    }
    return levels;
  }

  static byte[] downsample(byte[] source, int width, int height) {
    int targetWidth = Math.max(1, width >> 1);
    int targetHeight = Math.max(1, height >> 1);
    byte[] target = new byte[targetWidth * targetHeight * 4];
    for (int y = 0; y < targetHeight; y++) {
      int y0 = Math.min(height - 1, y * 2);
      int y1 = Math.min(height - 1, y * 2 + 1);
      for (int x = 0; x < targetWidth; x++) {
        int x0 = Math.min(width - 1, x * 2);
        int x1 = Math.min(width - 1, x * 2 + 1);
        for (int channel = 0; channel < 4; channel++) {
          int sum =
              Byte.toUnsignedInt(source[(y0 * width + x0) * 4 + channel])
                  + Byte.toUnsignedInt(source[(y0 * width + x1) * 4 + channel])
                  + Byte.toUnsignedInt(source[(y1 * width + x0) * 4 + channel])
                  + Byte.toUnsignedInt(source[(y1 * width + x1) * 4 + channel]);
          target[(y * targetWidth + x) * 4 + channel] = (byte) ((sum + 2) >> 2);
        }
      }
    }
    return target;
  }
}
