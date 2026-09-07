package net.dx12mc.config;

import java.util.Locale;

public enum Mode {
  NATIVE,
  DEBUG,
  PROBE;

  public static Mode parse(String value) {
    if (value == null || value.isBlank()) {
      return NATIVE;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "debug", "takeover", "diagnostic" -> DEBUG;
      case "probe", "check" -> PROBE;
      default -> NATIVE;
    };
  }
}
