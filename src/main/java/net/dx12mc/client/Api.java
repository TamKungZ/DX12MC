package net.dx12mc.client;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.dx12mc.DX12MC;
import net.dx12mc.api.AdapterInfo;
import net.dx12mc.api.ApiVersion;
import net.dx12mc.api.Capability;
import net.dx12mc.api.Dx12mc;
import net.dx12mc.api.PackStatus;
import net.dx12mc.api.ReloadListener;
import net.dx12mc.api.UniformProvider;
import net.dx12mc.api.WrongThreadException;
import net.dx12mc.graphics.Adapter;
import net.dx12mc.render.Controller;

final class Api implements Dx12mc {
  private static final Pattern PROVIDER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
  private final Controller controller;
  private final boolean nativeMode;
  private final ApiVersion version;
  private final Thread renderThread;
  private final Map<String, UniformProvider> providers = new HashMap<>();
  private final List<String> reportedMissing = new ArrayList<>();
  private final List<ReloadListener> listeners = new ArrayList<>();

  Api(Controller controller, boolean nativeMode, ApiVersion version, Thread renderThread) {
    this.controller = controller;
    this.nativeMode = nativeMode;
    this.version = version;
    this.renderThread = renderThread;
    controller.connectApi(this::update, this::notifyListeners);
  }

  @Override
  public String modVersion() {
    requireRenderThread();
    return DX12MC.VERSION;
  }

  @Override
  public ApiVersion apiVersion() {
    requireRenderThread();
    return version;
  }

  @Override
  public boolean active() {
    requireRenderThread();
    return controller.active();
  }

  @Override
  public Optional<AdapterInfo> adapter() {
    requireRenderThread();
    Adapter adapter = controller.adapter();
    if (adapter == null) return Optional.empty();
    return Optional.of(
        new AdapterInfo(
            adapter.name(),
            adapter.vendorId(),
            adapter.deviceId(),
            adapter.driver() == null ? "" : adapter.driver(),
            adapter.featureLevelName() == null ? "" : adapter.featureLevelName()));
  }

  @Override
  public boolean supports(Capability capability) {
    requireRenderThread();
    return nativeMode;
  }

  @Override
  public void registerUniformProvider(String name, UniformProvider provider) {
    requireRenderThread();
    if (name == null || !PROVIDER.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "uniform provider name must be namespaced, like mymod:values");
    }
    if (provider == null) throw new IllegalArgumentException("provider is null");
    if (providers.putIfAbsent(name, provider) != null) {
      throw new IllegalArgumentException("uniform provider " + name + " is already registered");
    }
  }

  @Override
  public void addReloadListener(ReloadListener listener) {
    requireRenderThread();
    if (listener == null) throw new IllegalArgumentException("listener is null");
    listeners.add(listener);
  }

  @Override
  public List<PackStatus> packs() {
    requireRenderThread();
    return controller.packStatuses();
  }

  boolean update(String name, FloatBuffer values) {
    UniformProvider provider = providers.get(name);
    if (provider == null) {
      if (!reportedMissing.contains(name)) {
        reportedMissing.add(name);
        DX12MC.LOGGER.warn(
            "Shader pack uniform provider {} is not registered; its values stay zero", name);
      }
      return false;
    }
    try {
      provider.update(values);
      return true;
    } catch (Throwable throwable) {
      providers.remove(name);
      reportedMissing.add(name);
      DX12MC.LOGGER.error(
          "Uniform provider {} failed and is disabled; its values stay as they were",
          name,
          throwable);
      return false;
    }
  }

  private void notifyListeners(List<PackStatus> statuses) {
    for (ReloadListener listener : List.copyOf(listeners)) {
      try {
        listener.onReload(statuses);
      } catch (Throwable throwable) {
        listeners.remove(listener);
        DX12MC.LOGGER.error(
            "A DX12MC reload listener failed and was removed: {}", listener, throwable);
      }
    }
  }

  void requireRenderThread() {
    if (Thread.currentThread() != renderThread) {
      throw new WrongThreadException(
          "DX12MC API used from thread "
              + Thread.currentThread().getName()
              + "; call it on the render thread "
              + renderThread.getName());
    }
  }
}
