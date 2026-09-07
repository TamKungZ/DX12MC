package net.dx12mc.config;

public record Config(Mode mode, boolean vsync, int clearColorRgba, boolean overlay) {
  private static final int DEFAULT_CLEAR = 0x18202AFF;

  public static Config load() {
    return new Config(
        Mode.parse(System.getProperty("dx12mc.mode", "native")),
        Boolean.parseBoolean(System.getProperty("dx12mc.vsync", "false")),
        parseRgba(System.getProperty("dx12mc.clear", "18202AFF")),
        Boolean.parseBoolean(System.getProperty("dx12mc.overlay", "false")));
  }

  public boolean nativeMode() {
    return mode == Mode.NATIVE;
  }

  public boolean debugMode() {
    return mode == Mode.DEBUG;
  }

  public boolean probeMode() {
    return mode == Mode.PROBE;
  }

  public boolean openGlPresentationAllowed() {
    return probeMode();
  }

  private static int parseRgba(String value) {
    try {
      String normalized = value == null ? "" : value.trim().replace("#", "");
      if (normalized.length() != 8) return DEFAULT_CLEAR;
      return (int) Long.parseUnsignedLong(normalized, 16);
    } catch (RuntimeException ignored) {
      return DEFAULT_CLEAR;
    }
  }
}
