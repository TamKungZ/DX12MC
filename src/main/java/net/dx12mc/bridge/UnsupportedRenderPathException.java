package net.dx12mc.bridge;

public final class UnsupportedRenderPathException extends RuntimeException {

  public UnsupportedRenderPathException(String message) {
    super(message);
  }

  public UnsupportedRenderPathException(String message, Throwable cause) {
    super(message, cause);
  }
}
