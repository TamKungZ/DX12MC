package net.dx12mc.graphics;

import java.util.Locale;

public record Adapter(int vendorId, int deviceId, String description) {
  private static final String API = "Direct3D 12";

  public String vendorName() {
    return switch (vendorId) {
      case 0x10DE -> "NVIDIA";
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

  public RendererInfo rendererInfo(String modVersion) {
    String apiVersion = API + " (DX12MC " + modVersion + ")";
    String name = name();
    String vendor = vendorName();
    return new RendererInfo(vendor, name, apiVersion, name + " " + apiVersion + ", " + vendor);
  }
}
