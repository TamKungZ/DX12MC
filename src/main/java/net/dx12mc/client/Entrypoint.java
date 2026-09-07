package net.dx12mc.client;

import net.dx12mc.DX12MC;
import net.fabricmc.api.ClientModInitializer;

public final class Entrypoint implements ClientModInitializer {

  @Override
  public void onInitializeClient() {
    DX12MC.runtime().initialize();
  }
}
