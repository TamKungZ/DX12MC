package net.dx12mc.bridge.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;

public final class AtlasAnimation {
  private final Image image;
  private final NativeImage[] tiles;
  private final int sprites;

  public AtlasAnimation(int atlasSize, int mipLevels, int sprites) {
    this.image = new Image(atlasSize, atlasSize, mipLevels);
    this.sprites = sprites;
    this.tiles = new NativeImage[mipLevels + 1];
    for (int level = 0; level <= mipLevels; level++) {
      int size = Math.max(1, 16 >> level);
      tiles[level] = new NativeImage(NativeImage.Format.RGBA, size, size, true);
    }
    for (int level = 0; level <= mipLevels; level++) {
      upload(level, 0, 0);
    }
    image.clearDirty();
  }

  public List<Image.Upload> frameUploads() {
    int columns = 32;
    for (int sprite = 0; sprite < sprites; sprite++) {
      int x = sprite % columns * 32 + (sprite * 7) % 16;
      int y = sprite / columns * 96 + (sprite * 13) % 48;
      for (int level = 0; level < tiles.length; level++) {
        upload(level, x >> level, y >> level);
      }
    }
    List<Image.Upload> uploads = image.dirtyUploads();
    image.clearDirty();
    return uploads;
  }

  private void upload(int level, int x, int y) {
    int size = tiles[level].getWidth();
    image.upload(tiles[level], level, x, y, 0, 0, size, size);
  }
}
