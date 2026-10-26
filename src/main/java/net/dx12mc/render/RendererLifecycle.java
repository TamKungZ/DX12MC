package net.dx12mc.render;

import com.mojang.blaze3d.platform.Window;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Bridge;
import net.dx12mc.bridge.Stats;
import net.dx12mc.config.Config;
import net.dx12mc.debug.Scene;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.RendererInfo;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Probe;
import net.dx12mc.graphics.bgfx.ProbeResult;
import net.dx12mc.mixin.RenderSystemAccessor;
import net.dx12mc.platform.win32.WindowHandle;
import net.minecraft.client.Minecraft;

final class RendererLifecycle implements AutoCloseable {
  private final Config config;
  private final Bridge bridge;
  private final Stats stats;
  private final Backend backend = new Backend();
  private final Scene debug = new Scene();
  private final PerformanceTelemetry performance;
  private State state = State.NEW;
  private boolean frameOpen;
  private FatalRenderException failure;
  private FrameSize currentFrameSize = new FrameSize(1, 1);
  private RendererInfo rendererInfo;

  RendererLifecycle(Config config, Bridge bridge, Stats stats) {
    this.config = config;
    this.bridge = bridge;
    this.stats = stats;
    this.performance = new PerformanceTelemetry(stats);
  }

  void initialize() {
    DX12MC.LOGGER.info(
        "DX12MC {} loaded. mode={}, overlay={}, strictD3D12={}",
        DX12MC.VERSION,
        config.mode(),
        config.overlay(),
        !config.probeMode());
    if (System.getProperty("dx12mc.vsync") != null) {
      DX12MC.LOGGER.warn("-Ddx12mc.vsync is no longer read. VSync follows Video Settings > VSync.");
    }
    if (config.probeMode()) {
      DX12MC.LOGGER.warn(
          "PROBE MODE: renderer takeover is intentionally disabled; vanilla OpenGL may present"
              + " only in this explicit diagnostic mode.");
    } else {
      DX12MC.LOGGER.warn(
          "STRICT D3D12 POLICY: no OpenGL draw fallback and no OpenGL SwapBuffers fallback. Any"
              + " untranslated/failed native path is fatal.");
    }
    DX12MC.LOGGER.info("Native path: Minecraft geometry -> DX12MC -> bgfx -> Direct3D 12 / DXGI.");
  }

  void ensureInitialized(Minecraft minecraft) {
    throwIfFailed();
    if (state != State.NEW) return;
    if (minecraft == null) {
      throw fail(
          "Minecraft instance was unavailable while DX12MC needed to initialize D3D12.", null);
    }
    if (!WindowHandle.supported()) {
      if (config.probeMode()) {
        state = State.PROBED;
        DX12MC.LOGGER.warn(
            "DX12MC D3D12 probe/takeover is Windows-only; probe mode leaves vanilla rendering"
                + " untouched.");
        return;
      }
      throw fail("DX12MC strict D3D12 rendering currently supports Windows only.", null);
    }
    try {
      initializeNative(minecraft);
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fail("Unable to initialize the Direct3D 12 renderer.", throwable);
    }
  }

  void ensureFrame(Minecraft minecraft) {
    throwIfFailed();
    if (frameOpen) return;
    if (state != State.ACTIVE) {
      throw new IllegalStateException("D3D12 frame requested while renderer state=" + state);
    }
    if (minecraft == null) throw new IllegalStateException("Minecraft instance is null");
    backend.beginFrame(frameSize(minecraft.getWindow()));
    frameOpen = true;
  }

  void present(Minecraft minecraft) {
    if (config.probeMode())
      throw new IllegalStateException("presentAtSwap must not be used in probe mode");
    throwIfFailed();
    ensureInitialized(minecraft);
    requireActive("D3D12 present");
    try {
      ensureFrame(minecraft);
      if (!frameOpen) throw new IllegalStateException("D3D12 frame was not opened before present");
      FrameSize size = frameSize(minecraft.getWindow());
      if (debug.shouldRender(config.mode(), config.overlay())) {
        debug.render(backend, size, config.mode(), stats.snapshot(), config.overlay());
      }
      backend.endFrame();
      frameOpen = false;
      if (bridge != null) bridge.onFrameBoundary();
      performance.record(backend);
      stats.suppressedOpenGlSwap();
      stats.nextFrame();
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fail("D3D12 frame submission/present failed.", throwable);
    }
  }

  void requireActive(String operation) {
    throwIfFailed();
    if (state != State.ACTIVE) {
      throw fail(operation + " requested while renderer state=" + state + ".", null);
    }
  }

  void throwIfFailed() {
    if (failure != null) throw failure;
  }

  FatalRenderException fail(String message, Throwable cause) {
    if (failure != null) return failure;
    state = State.FAILED;
    frameOpen = false;
    String full =
        "DX12MC STRICT D3D12 ERROR: "
            + message
            + " OpenGL rendering/presentation fallback is disabled by design.";
    failure = new FatalRenderException(full, cause);
    DX12MC.LOGGER.error(full, cause);
    closeNativeResources();
    return failure;
  }

  boolean active() {
    return state == State.ACTIVE;
  }

  boolean closed() {
    return state == State.CLOSED;
  }

  void clear(Minecraft minecraft, int mask, int rgba, float depth) {
    ensureFrame(minecraft);
    backend.clear(mask, rgba, depth);
  }

  void vsync(boolean enabled) {
    backend.vsync(enabled);
  }

  RendererInfo rendererInfo() {
    return state == State.ACTIVE ? rendererInfo : null;
  }

  long gpuFrameNanos() {
    return state == State.ACTIVE ? backend.gpuFrameNanos() : 0L;
  }

  boolean openGlGuardAllowed() {
    return config.probeMode()
        || state == State.NEW
        || state == State.PROBED
        || state == State.CLOSED;
  }

  @Override
  public void close() {
    if (state == State.CLOSED) return;
    state = State.CLOSED;
    frameOpen = false;
    closeBridge();
    backend.close();
  }

  private void initializeNative(Minecraft minecraft) {
    Window window = minecraft.getWindow();
    long glfwWindow = window.getWindow();
    long hwnd = WindowHandle.fromGlfw(glfwWindow);
    ProbeResult probe = Probe.detect();
    DX12MC.LOGGER.info(
        "Minecraft GLFW window=0x{}, HWND=0x{}",
        Long.toHexString(glfwWindow),
        Long.toHexString(hwnd));
    DX12MC.LOGGER.info("bgfx renderer candidates: {}", probe.renderers());
    if (!probe.direct3D12Available()) {
      throw new IllegalStateException(
          "bgfx does not report a Direct3D 12 backend on this machine/build; candidates="
              + probe.renderers());
    }
    DX12MC.LOGGER.info("Direct3D 12 backend detected.");
    if (config.probeMode()) {
      state = State.PROBED;
      DX12MC.LOGGER.info(
          "Probe complete; no renderer takeover was performed. OpenGL remains active only because"
              + " probe mode was explicitly requested.");
      return;
    }
    backend.initialize(
        hwnd, frameSize(window), config.clearColorRgba(), config.debugMode() || config.overlay());
    if (config.nativeMode()) {
      bridge.start();
      DX12MC.LOGGER.warn(
          "NATIVE D3D12 BRIDGE ACTIVE: geometry + texture + per-draw render state are"
              + " native-only; untranslated paths are fatal.");
    } else {
      DX12MC.LOGGER.warn(
          "DEBUG D3D12 MODE ACTIVE: Minecraft GameRenderer is bypassed; bgfx/D3D12 owns the"
              + " visible frame.");
    }
    DX12MC.LOGGER.warn(
        "D3D12 PRESENT OWNER ACTIVE: GLFW window/event lifecycle remains, OpenGL context may"
            + " remain for bootstrap compatibility, but OpenGL SwapBuffers is disabled.");
    rendererInfo = backend.adapter().rendererInfo(DX12MC.VERSION);
    state = State.ACTIVE;
    RenderSystemAccessor.dx12mc$setApiDescription(rendererInfo.apiDescription());
  }

  private void closeNativeResources() {
    closeBridge();
    try {
      backend.close();
    } catch (Throwable closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  private void closeBridge() {
    if (bridge == null) return;
    try {
      bridge.close();
    } catch (Throwable throwable) {
      DX12MC.LOGGER.warn("Error while releasing native bridge resources", throwable);
    }
  }

  private FrameSize frameSize(Window window) {
    int width = Math.max(1, window.getWidth());
    int height = Math.max(1, window.getHeight());
    if (currentFrameSize.width() != width || currentFrameSize.height() != height) {
      currentFrameSize = new FrameSize(width, height);
    }
    return currentFrameSize;
  }

  private enum State {
    NEW,
    PROBED,
    ACTIVE,
    FAILED,
    CLOSED
  }
}
