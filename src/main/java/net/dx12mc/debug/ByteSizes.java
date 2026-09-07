package net.dx12mc.debug;

import java.util.Locale;

final class ByteSizes {
  private ByteSizes() {}

  static String human(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kib = bytes / 1024.0;
    if (kib < 1024) return String.format(Locale.ROOT, "%.1f KiB", kib);
    double mib = kib / 1024.0;
    if (mib < 1024) return String.format(Locale.ROOT, "%.1f MiB", mib);
    return String.format(Locale.ROOT, "%.2f GiB", mib / 1024.0);
  }
}
