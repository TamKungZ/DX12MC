package net.dx12mc.harness;

import net.minecraft.client.Minecraft;

public final class FlightHarnessHook {
  private FlightHarnessHook() {}

  public static void frame(Minecraft minecraft) {
    if (FlightHarness.ENABLED) FlightHarness.instance().onFrame(minecraft);
  }
}
