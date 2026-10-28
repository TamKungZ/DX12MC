package net.dx12mc.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.dx12mc.DX12MC;
import net.dx12mc.api.PackStatus;
import net.dx12mc.bridge.Bridge;
import net.dx12mc.client.ShaderPackLoader;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.pack.MaterialAtlas;
import net.dx12mc.pack.PackSet;
import org.joml.Matrix4f;

final class ShaderPackHost {
  private final Bridge bridge;
  private final PackSet packs;
  private ShaderPackLoader.Result pending;
  private MaterialAtlas.Layout atlas;
  private boolean atlasChanged;
  private PackSet.Providers providers = (name, values) -> false;
  private Consumer<List<PackStatus>> listener = statuses -> {};
  private int notified = -1;
  private boolean worldMrt;
  private boolean translucentStarted;
  private String failure;
  private final long startNanos = System.nanoTime();

  ShaderPackHost(Bridge bridge) {
    this.bridge = bridge;
    this.packs = bridge.packs();
  }

  void connect(PackSet.Providers providers, Consumer<List<PackStatus>> listener) {
    this.providers = providers;
    this.listener = listener;
  }

  boolean active() {
    return packs.active() && failure == null;
  }

  void failed(String reason, Throwable cause) {
    if (failure != null) return;
    failure = reason;
    DX12MC.LOGGER.error(
        "DX12MC shader packs are disabled for this session of resources: {}", reason, cause);
  }

  private void disable(int width, int height) {
    List<PackStatus> statuses = new ArrayList<>();
    for (PackStatus status : packs.statuses()) {
      statuses.add(
          status.state() == PackStatus.State.ACTIVE
              ? new PackStatus(status.id(), status.source(), PackStatus.State.FAILED, failure)
              : status);
    }
    packs.apply(List.of(), statuses, providers, width, height);
    bridge.pipelines().releaseMrt();
    failure = null;
  }

  List<PackStatus> statuses() {
    return packs.statuses();
  }

  void loaded(ShaderPackLoader.Result result) {
    pending = result;
  }

  void atlas(MaterialAtlas.Layout layout) {
    atlas = layout;
    atlasChanged = true;
  }

  Target scene(int width, int height) {
    return packs.needsScene() ? packs.scene(width, height) : null;
  }

  void beginFrame() {
    if (packs.active()) packs.beginFrame();
  }

  void frameBoundary(int width, int height) {
    if (failure != null) disable(width, height);
    if (pending != null) {
      ShaderPackLoader.Result result = pending;
      pending = null;
      try {
        packs.apply(result.plans(), result.rejected(), providers, width, height);
        if (packs.wantsMaterials()) packs.materials(atlas, result.normals(), result.speculars());
      } catch (RuntimeException exception) {
        failed("applying shader packs failed: " + exception.getMessage(), exception);
        disable(width, height);
      }
      if (!packs.gbuffer()) bridge.pipelines().releaseMrt();
      atlasChanged = false;
    }
    if (atlasChanged && packs.wantsMaterials()) {
      packs.materials(atlas, null, null);
      atlasChanged = false;
    }
    if (packs.wantsMaterials()) {
      try {
        packs.uploadMaterials();
      } catch (RuntimeException exception) {
        failed("material atlas upload failed: " + exception.getMessage(), exception);
        disable(width, height);
      }
    }
    if (packs.revision() != notified) {
      notified = packs.revision();
      listener.accept(packs.statuses());
    }
    worldMrt = false;
    translucentStarted = false;
  }

  void beginWorld(
      Matrix4f cameraRotation,
      Matrix4f projection,
      double cameraX,
      double cameraY,
      double cameraZ,
      float sunAngle,
      float dayTime,
      float partialTick,
      int width,
      int height) {
    packs
        .frame()
        .camera(
            projection,
            cameraRotation,
            cameraX,
            cameraY,
            cameraZ,
            sunAngle,
            dayTime,
            ((System.nanoTime() - startNanos) / 1_000_000_000.0f) % 3600.0f,
            partialTick);
    packs.frame().viewSize(width, height);
  }

  boolean startOpaque(RendererLifecycle lifecycle) {
    if (worldMrt || !packs.gbuffer() || !lifecycle.mainRedirected()) return false;
    worldMrt = true;
    lifecycle.backend().clearMainAttachments();
    lifecycle.backend().mainMrt(true);
    bridge.pipelines().mrt(true);
    return true;
  }

  boolean startTranslucent() {
    if (translucentStarted) return false;
    translucentStarted = true;
    return packs.hasStage("before_translucent");
  }

  void endWorld(RendererLifecycle lifecycle) {
    if (!worldMrt) return;
    worldMrt = false;
    lifecycle.backend().mainMrt(false);
    bridge.pipelines().mrt(false);
  }

  boolean hasStage(String stage) {
    return packs.hasStage(stage);
  }

  void runStage(String stage, RendererLifecycle lifecycle) {
    packs
        .frame()
        .fog(
            RenderSystem.getShaderFogStart(),
            RenderSystem.getShaderFogEnd(),
            RenderSystem.getShaderFogShape().getIndex(),
            RenderSystem.getShaderFogColor());
    packs.runStage(stage, lifecycle::bindTarget, bridge.post());
  }
}
