package net.dx12mc.api;

/** Thrown when the API is used from a thread other than the render thread. */
public final class WrongThreadException extends IllegalStateException {
  public WrongThreadException(String message) {
    super(message);
  }
}
