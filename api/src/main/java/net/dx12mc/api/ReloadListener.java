package net.dx12mc.api;

import java.util.List;

/** Notified on the render thread after shader packs were loaded or reloaded. */
@FunctionalInterface
public interface ReloadListener {
  void onReload(List<PackStatus> packs);
}
