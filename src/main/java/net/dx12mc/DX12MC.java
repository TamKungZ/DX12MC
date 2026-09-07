package net.dx12mc;

import net.dx12mc.client.Runtime;
import net.dx12mc.config.Config;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DX12MC {
  public static final String ID = "dx12mc";
  public static final String VERSION =
      FabricLoader.getInstance()
          .getModContainer(ID)
          .map(container -> container.getMetadata().getVersion().getFriendlyString())
          .orElse("dev");
  public static final Logger LOGGER = LoggerFactory.getLogger("DX12MC");
  private static final Runtime RUNTIME = new Runtime(Config.load());

  private DX12MC() {}

  public static Runtime runtime() {
    return RUNTIME;
  }
}
