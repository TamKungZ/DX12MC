package net.dx12mc.graphics;

import java.util.Locale;

public record Adapter(
    int vendorId, int deviceId, String description, long driverVersion, int featureLevel) {
  private static final String API = "Direct3D 12";
  private static final int NVIDIA = 0x10DE;

  public Adapter(int vendorId, int deviceId, String description) {
    this(vendorId, deviceId, description, 0L, 0);
  }

  public String vendorName() {
    return switch (vendorId) {
      case NVIDIA -> "NVIDIA";
      case 0x1002, 0x1022 -> "AMD";
      case 0x8086 -> "Intel";
      case 0x1414 -> "Microsoft";
      case 0x5143 -> "Qualcomm";
      case 0x13B5 -> "ARM";
      default -> String.format(Locale.ROOT, "Vendor 0x%04X", vendorId);
    };
  }

  public String name() {
    if (description != null && !description.isBlank()) return description.trim();
    return String.format(Locale.ROOT, "%s device 0x%04X", vendorName(), deviceId);
  }

  public String rawDriverVersion() {
    if (driverVersion == 0L) return null;
    return String.format(
        Locale.ROOT,
        "%d.%d.%d.%d",
        driverVersion >>> 48 & 0xFFFF,
        driverVersion >>> 32 & 0xFFFF,
        driverVersion >>> 16 & 0xFFFF,
        driverVersion & 0xFFFF);
  }

  public String driver() {
    if (driverVersion == 0L) return null;
    if (vendorId == NVIDIA) {
      long third = driverVersion >>> 16 & 0xFFFF;
      long fourth = driverVersion & 0xFFFF;
      if (third >= 10 && third <= 19 && fourth <= 9999) {
        long number = third % 10 * 10000 + fourth;
        return String.format(Locale.ROOT, "%d.%02d", number / 100, number % 100);
      }
    }
    return rawDriverVersion();
  }

  public String featureLevelName() {
    if (featureLevel == 0) return null;
    return String.format(Locale.ROOT, "%d_%d", featureLevel >> 12 & 0xF, featureLevel >> 8 & 0xF);
  }

  public String details() {
    StringBuilder text = new StringBuilder();
    if (featureLevel != 0) text.append("FL ").append(featureLevelName());
    String driver = driver();
    if (driver != null) {
      if (text.length() > 0) text.append(", ");
      text.append("driver ").append(driver);
    }
    return text.toString();
  }

  public String logDetails() {
    String details = details();
    String raw = rawDriverVersion();
    if (raw != null && !raw.equals(driver())) details += " (raw " + raw + ")";
    return details;
  }

  public RendererInfo rendererInfo(String modVersion) {
    String details = details();
    String apiVersion =
        API + " (DX12MC " + modVersion + ")" + (details.isEmpty() ? "" : ", " + details);
    String name = name();
    String vendor = vendorName();
    return new RendererInfo(vendor, name, apiVersion, name + " " + apiVersion + ", " + vendor);
  }
}
