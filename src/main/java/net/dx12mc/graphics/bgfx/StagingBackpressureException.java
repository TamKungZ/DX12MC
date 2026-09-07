package net.dx12mc.graphics.bgfx;

public final class StagingBackpressureException extends IllegalStateException {

  public StagingBackpressureException(String message) {
    super(message);
  }

  public StagingBackpressureException(String message, Throwable cause) {
    super(message, cause);
  }
}
