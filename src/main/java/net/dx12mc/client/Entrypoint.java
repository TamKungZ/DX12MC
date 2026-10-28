package net.dx12mc.client;

import net.dx12mc.DX12MC;
import net.dx12mc.api.ApiVersion;
import net.dx12mc.api.Dx12mcEntrypoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;

public final class Entrypoint implements ClientModInitializer {
  static final String API_KEY = "dx12mc:api";

  @Override
  public void onInitializeClient() {
    DX12MC.runtime().initialize();
    Api api = DX12MC.runtime().createApi(apiVersion());
    for (EntrypointContainer<Dx12mcEntrypoint> container :
        FabricLoader.getInstance().getEntrypointContainers("dx12mc", Dx12mcEntrypoint.class)) {
      String mod = container.getProvider().getMetadata().getId();
      try {
        container.getEntrypoint().onDx12mc(api);
      } catch (Throwable throwable) {
        DX12MC.LOGGER.error(
            "The DX12MC entrypoint of mod {} failed; DX12MC continues without it", mod, throwable);
      }
    }
  }

  static ApiVersion apiVersion() {
    CustomValue value =
        FabricLoader.getInstance()
            .getModContainer(DX12MC.ID)
            .map(container -> container.getMetadata().getCustomValue(API_KEY))
            .orElse(null);
    if (value == null || value.getType() != CustomValue.CvType.STRING) {
      throw new IllegalStateException("fabric.mod.json has no " + API_KEY + " string");
    }
    return ApiVersion.parse(value.getAsString());
  }
}
