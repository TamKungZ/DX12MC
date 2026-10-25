package net.dx12mc.bridge.texture;

public final class TextureNotReadyException extends RuntimeException {
  private final int textureId;

  TextureNotReadyException(int textureId) {
    super(
        "texture " + textureId + " was allocated but has not received pixels yet",
        null,
        false,
        false);
    this.textureId = textureId;
  }

  public int textureId() {
    return textureId;
  }
}
