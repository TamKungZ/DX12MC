package net.dx12mc.api;

import java.util.List;
import java.util.Optional;

/**
 * Entry point to DX12MC for other mods, passed to {@link Dx12mcEntrypoint#onDx12mc}.
 *
 * <p>Every method must be called on the render thread (the client main thread). A call from any
 * other thread throws {@link WrongThreadException}. Callbacks registered here are also invoked on
 * the render thread. The API is experimental until version 1.0 and may change in minor releases.
 */
public interface Dx12mc {
  /** The DX12MC mod version, for example {@code 0.12.1+mc1.21.1}. */
  String modVersion();

  /** The version of this API implemented by the running DX12MC. */
  ApiVersion apiVersion();

  /**
   * Whether the Direct3D 12 renderer is running. False before the device exists and after a fatal
   * error.
   */
  boolean active();

  /** The adapter the renderer opened, or empty before the device exists. */
  Optional<AdapterInfo> adapter();

  /** Whether this DX12MC version and the running device support a feature. */
  boolean supports(Capability capability);

  /**
   * Supplies the values of a shader pack uniform declared with {@code "provider": name}. The
   * provider is called once per frame, only while an active pack uses that uniform.
   *
   * @param name a namespaced id such as {@code veylith:settings}
   * @throws IllegalArgumentException if the name is not namespaced or is already registered
   */
  void registerUniformProvider(String name, UniformProvider provider);

  /** Called after every shader pack reload, including the first load after the device starts. */
  void addReloadListener(ReloadListener listener);

  /** The shader packs found by the last reload, highest priority first. */
  List<PackStatus> packs();
}
