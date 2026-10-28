package net.dx12mc.api;

/**
 * Fabric entrypoint under the key {@code dx12mc}. DX12MC calls it once during client
 * initialization, so a mod that lists it never loads API classes when DX12MC is absent.
 */
@FunctionalInterface
public interface Dx12mcEntrypoint {
  void onDx12mc(Dx12mc dx12mc);
}
