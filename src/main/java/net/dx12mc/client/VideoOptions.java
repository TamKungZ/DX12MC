package net.dx12mc.client;

import java.util.List;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

public final class VideoOptions {
  public static final String MENU_BLUR_UNAVAILABLE =
      "Not available with DX12MC: menu blur needs post-processing, which the Direct3D 12"
          + " renderer does not support yet.";

  private VideoOptions() {}

  public static <T> List<T> graphicsModes(List<T> values, boolean postProcessingAvailable) {
    if (postProcessingAvailable || !values.contains(GraphicsStatus.FABULOUS)) return values;
    return values.stream().filter(value -> value != GraphicsStatus.FABULOUS).toList();
  }

  public static String unavailableReason(
      Options options, OptionInstance<?> option, boolean postProcessingAvailable) {
    return unavailableReason(option, options.menuBackgroundBlurriness(), postProcessingAvailable);
  }

  static String unavailableReason(
      OptionInstance<?> option, OptionInstance<?> menuBlur, boolean postProcessingAvailable) {
    if (postProcessingAvailable) return null;
    return option == menuBlur ? MENU_BLUR_UNAVAILABLE : null;
  }
}
