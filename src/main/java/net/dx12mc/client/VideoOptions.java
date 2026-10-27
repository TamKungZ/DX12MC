package net.dx12mc.client;

import java.util.List;
import net.minecraft.client.GraphicsStatus;

public final class VideoOptions {
  private VideoOptions() {}

  public static <T> List<T> graphicsModes(List<T> values, boolean postProcessingAvailable) {
    if (postProcessingAvailable || !values.contains(GraphicsStatus.FABULOUS)) return values;
    return values.stream().filter(value -> value != GraphicsStatus.FABULOUS).toList();
  }
}
