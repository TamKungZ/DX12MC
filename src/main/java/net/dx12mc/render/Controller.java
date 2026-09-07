package net.dx12mc.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Bridge;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import net.dx12mc.config.Config;
import net.dx12mc.debug.Scene;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Probe;
import net.dx12mc.graphics.bgfx.ProbeResult;
import net.dx12mc.platform.win32.WindowHandle;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class Controller implements AutoCloseable {
  private final Config config;
  private final Backend backend = new Backend();
  private final Stats stats = new Stats();
  private final Scene debug = new Scene();
  private final Bridge bridge;
  private State state = State.NEW;
  private boolean frameOpen;
  private FatalRenderException failure;
  private boolean sectionLayerActive;
  private Matrix4f sectionModelView;
  private Matrix4f sectionProjection;
  private float sectionOffsetX, sectionOffsetY, sectionOffsetZ;
  private String semanticIntent = "frame";
  private int legacyVertexBufferUploadDepth;
  private static final int GL_ARRAY_BUFFER = 0x8892;

  public Controller(Config config) {
    this.config = config;
    this.bridge = config.nativeMode() ? new Bridge(stats) : null;
  }

  public void initialize() {
    DX12MC.LOGGER.info(
        "DX12MC {} loaded. mode={}, vsync={}, overlay={}, strictD3D12={}",
        DX12MC.VERSION,
        config.mode(),
        config.vsync(),
        config.overlay(),
        !config.probeMode());
    if (config.probeMode()) {
      DX12MC.LOGGER.warn(
          "PROBE MODE: renderer takeover is intentionally disabled; vanilla OpenGL may present only"
              + " in this explicit diagnostic mode.");
    } else {
      DX12MC.LOGGER.warn(
          "STRICT D3D12 POLICY: no OpenGL draw fallback and no OpenGL SwapBuffers fallback. Any"
              + " untranslated/failed native path is fatal.");
    }
    DX12MC.LOGGER.info("Native path: Minecraft geometry -> DX12MC -> bgfx -> Direct3D 12 / DXGI.");
  }

  public void capture(Slot slot, MeshData mesh, VertexBuffer.Usage usage) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state == State.CLOSED) return;
    try {
      bridge.capture(slot, mesh, usage);
    } catch (Throwable throwable) {
      stats.captureFailed();
      if (slot != null) slot.clearCpu();
      throw fatal("Unable to translate a Minecraft VertexBuffer upload to D3D12.", throwable);
    }
  }

  public void updateIndices(Slot slot, ByteBufferBuilder.Result result) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state == State.CLOSED || result == null) return;
    try {
      bridge.updateIndices(slot, result.byteBuffer());
    } catch (Throwable throwable) {
      throw fatal("Unable to translate a Minecraft index-buffer update to D3D12.", throwable);
    }
  }

  public void beginLegacyVertexBufferUpload() {
    if (config.nativeMode()) legacyVertexBufferUploadDepth++;
  }

  public void endLegacyVertexBufferUpload() {
    if (!config.nativeMode()) return;
    if (legacyVertexBufferUploadDepth > 0) legacyVertexBufferUploadDepth--;
  }

  public boolean suppressLegacyVertexBufferStorage(int target, long bytes) {
    if (!config.nativeMode() || state != State.ACTIVE || legacyVertexBufferUploadDepth <= 0)
      return false;
    if (target != GL_ARRAY_BUFFER) return false;
    stats.suppressedLegacyVertexStorage(bytes);
    return true;
  }

  public void bindTexture(int id) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state != State.CLOSED) bridge.bindTexture(id);
  }

  public void depthTest(boolean enabled) {
    if (bridge != null) bridge.depthTest(enabled);
  }

  public void depthFunc(int func) {
    if (bridge != null) bridge.depthFunc(func);
  }

  public void depthMask(boolean write) {
    if (bridge != null) bridge.depthMask(write);
  }

  public void blend(boolean enabled) {
    if (bridge != null) bridge.blend(enabled);
  }

  public void blendFunc(int src, int dst) {
    if (bridge != null) bridge.blendFunc(src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    if (bridge != null) bridge.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
  }

  public void blendEquation(int equation) {
    if (bridge != null) bridge.blendEquation(equation);
  }

  public void cull(boolean enabled) {
    if (bridge != null) bridge.cull(enabled);
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    if (bridge != null) bridge.colorMask(red, green, blue, alpha);
  }

  public void scissor(boolean enabled) {
    if (bridge != null) bridge.scissor(enabled);
  }

  public void scissorBox(int x, int y, int width, int height) {
    if (bridge != null) bridge.scissorBox(x, y, width, height);
  }

  public void polygonOffset(boolean enabled) {
    if (bridge != null) bridge.polygonOffset(enabled);
  }

  public void polygonOffset(float factor, float units) {
    if (bridge != null) bridge.polygonOffset(factor, units);
  }

  public void polygonMode(int face, int mode) {
    if (bridge != null) bridge.polygonMode(face, mode);
  }

  public void colorLogic(boolean enabled) {
    if (bridge != null) bridge.colorLogic(enabled);
  }

  public void logicOp(int op) {
    if (bridge != null) bridge.logicOp(op);
  }

  public void allocateTexture(int id, int maxLevel, int width, int height) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state == State.CLOSED) return;
    try {
      bridge.allocateTexture(id, maxLevel, width, height);
      stats.textureCpuBytes(bridge.textureCpuBytes());
    } catch (Throwable throwable) {
      throw fatal("Unable to mirror a Minecraft texture allocation for D3D12.", throwable);
    }
  }

  public void mirrorBoundOpenGlTextureAllocation(int level, int width, int height) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state == State.CLOSED) return;
    try {
      bridge.allocateBoundTextureLevel(level, width, height);
      stats.textureCpuBytes(bridge.textureCpuBytes());
    } catch (Throwable throwable) {
      throw fatal(
          "Unable to observe a Minecraft OpenGL texture allocation for the D3D12 mirror: level="
              + level
              + ", size="
              + width
              + "x"
              + height
              + ".",
          throwable);
    }
  }

  public void uploadTexture(
      NativeImage image,
      int level,
      int offsetX,
      int offsetY,
      int skipPixels,
      int skipRows,
      int width,
      int height,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (state == State.CLOSED) return;
    try {
      bridge.uploadTexture(
          image, level, offsetX, offsetY, skipPixels, skipRows, width, height, blur, clamp, mipmap);
      stats.textureCpuBytes(bridge.textureCpuBytes());
    } catch (Throwable throwable) {
      throw fatal("Unable to mirror a Minecraft texture upload for D3D12.", throwable);
    }
  }

  public void deleteTexture(int id) {
    if (bridge != null) {
      bridge.deleteTexture(id);
      stats.textureCpuBytes(bridge.textureCpuBytes());
    }
  }

  public void onRenderHead(Minecraft minecraft) {
    ensureInitialized(minecraft);
  }

  public boolean draw(Slot slot, Matrix4f modelView, Matrix4f projection) {
    if (!config.nativeMode()) return false;
    throwIfFailed();
    ensureInitialized(Minecraft.getInstance());
    requireActive("VertexBuffer draw");
    try {
      ensureFrame(Minecraft.getInstance());
      Window window = Minecraft.getInstance().getWindow();
      if (!bridge.draw(slot, modelView, projection, window.getWidth(), window.getHeight())) {
        throw new IllegalStateException("D3D12 bridge returned no submission");
      }
      stats.cancelledVanillaDraw();
      return true;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Native D3D12 VertexBuffer submission failed.", throwable);
    }
  }

  public void beginSky() {
    if (!config.nativeMode()) return;
    this.semanticIntent = "level.sky";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
  }

  public void endSky() {
    if (!config.nativeMode()) return;
    this.semanticIntent = "frame";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
  }

  public void beginSectionLayer(Matrix4f modelView, Matrix4f projection) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (modelView == null || projection == null) {
      throw fatal("Minecraft section-layer draw supplied null matrices.", null);
    }
    this.sectionModelView = new Matrix4f(modelView);
    this.sectionProjection = new Matrix4f(projection);
    this.sectionOffsetX = this.sectionOffsetY = this.sectionOffsetZ = 0.0f;
    this.sectionLayerActive = true;
    this.semanticIntent = "level.section-layer";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
  }

  public void sectionChunkOffset(float x, float y, float z) {
    if (!sectionLayerActive) return;
    this.sectionOffsetX = x;
    this.sectionOffsetY = y;
    this.sectionOffsetZ = z;
  }

  public boolean drawSection(Slot slot) {
    if (!config.nativeMode() || !sectionLayerActive) return false;
    throwIfFailed();
    if (sectionModelView == null || sectionProjection == null) {
      throw fatal("D3D12 section-layer context is incomplete.", null);
    }
    Matrix4f modelView =
        new Matrix4f(sectionModelView).translate(sectionOffsetX, sectionOffsetY, sectionOffsetZ);
    boolean submitted = draw(slot, modelView, sectionProjection);
    if (submitted) stats.sectionLayerDraw();
    return submitted;
  }

  public void endSectionLayer() {
    this.sectionLayerActive = false;
    this.sectionModelView = null;
    this.sectionProjection = null;
    this.sectionOffsetX = this.sectionOffsetY = this.sectionOffsetZ = 0.0f;
    this.semanticIntent = "frame";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
  }

  public boolean drawImmediate(MeshData mesh) {
    if (!config.nativeMode()) return false;
    throwIfFailed();
    ensureInitialized(Minecraft.getInstance());
    requireActive("immediate MeshData draw");
    try {
      ensureFrame(Minecraft.getInstance());
      Window window = Minecraft.getInstance().getWindow();
      if (!bridge.drawImmediate(
          mesh,
          RenderSystem.getModelViewMatrix(),
          RenderSystem.getProjectionMatrix(),
          window.getWidth(),
          window.getHeight())) {
        throw new IllegalStateException("D3D12 immediate bridge returned no submission");
      }
      stats.cancelledVanillaDraw();
      return true;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Native D3D12 immediate submission failed.", throwable);
    }
  }

  public void release(Slot slot) {
    if (slot == null) return;
    if (bridge != null) bridge.release(slot);
    else slot.clearCpu();
  }

  public void presentAtSwap(Minecraft minecraft) {
    if (config.probeMode()) {
      throw new IllegalStateException("presentAtSwap must not be used in probe mode");
    }
    throwIfFailed();
    ensureInitialized(minecraft);
    requireActive("D3D12 present");
    try {
      ensureFrame(minecraft);
      if (!frameOpen) throw new IllegalStateException("D3D12 frame was not opened before present");
      FrameSize size = frameSize(minecraft.getWindow());
      debug.render(backend, size, config.mode(), stats.snapshot(), config.overlay());
      backend.endFrame();
      frameOpen = false;
      if (bridge != null) bridge.onFrameBoundary();
      stats.suppressedOpenGlSwap();
      stats.nextFrame();
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("D3D12 frame submission/present failed.", throwable);
    }
  }

  public boolean openGlPresentationAllowed() {
    return config.openGlPresentationAllowed();
  }

  public boolean suppressVanillaFramebufferBlit() {
    if (config.probeMode()) return false;
    throwIfFailed();
    if (state != State.ACTIVE) return false;
    stats.suppressedOpenGlFramebufferBlit();
    return true;
  }

  public boolean suppressLegacyBlurPostProcess() {
    if (config.probeMode()) return false;
    throwIfFailed();
    if (state != State.ACTIVE) return false;
    stats.suppressedLegacyBlur();
    return true;
  }

  public boolean suppressAutomaticWorldIconCapture() {
    if (config.probeMode()) return false;
    throwIfFailed();
    if (state != State.ACTIVE) return false;
    stats.suppressedAutomaticWorldIconCapture();
    return true;
  }

  public void assertOpenGlDrawAllowed(String operation) {
    if (config.probeMode() || state == State.NEW || state == State.PROBED || state == State.CLOSED)
      return;
    throwIfFailed();
    if (state == State.ACTIVE) {
      stats.rawOpenGlEscape();
      throw fatal(
          "Raw OpenGL draw escaped the D3D12 bridge after takeover: "
              + operation
              + ". semanticIntent="
              + semanticIntent
              + ".",
          null);
    }
  }

  public void assertOpenGlReadbackAllowed(String operation) {
    if (config.probeMode() || state == State.NEW || state == State.PROBED || state == State.CLOSED)
      return;
    throwIfFailed();
    if (state == State.ACTIVE) {
      throw fatal(
          "OpenGL readback requested after D3D12 takeover: "
              + operation
              + ". semanticIntent="
              + semanticIntent
              + ". A D3D12 readback path is not implemented yet.",
          null);
    }
  }

  public boolean shouldCancelGameRenderer() {
    return config.debugMode() && state == State.ACTIVE;
  }

  @Override
  public void close() {
    if (state == State.CLOSED) return;
    state = State.CLOSED;
    frameOpen = false;
    legacyVertexBufferUploadDepth = 0;
    closeBridge();
    backend.close();
  }

  private void ensureInitialized(Minecraft minecraft) {
    throwIfFailed();
    if (state != State.NEW) return;
    if (minecraft == null) {
      throw fatal(
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
      throw fatal("DX12MC strict D3D12 rendering currently supports Windows only.", null);
    }
    try {
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
          hwnd,
          frameSize(window),
          config.vsync(),
          config.clearColorRgba(),
          config.debugMode() || config.overlay());
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
      state = State.ACTIVE;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Unable to initialize the Direct3D 12 renderer.", throwable);
    }
  }

  private void ensureFrame(Minecraft minecraft) {
    throwIfFailed();
    if (frameOpen) return;
    if (state != State.ACTIVE)
      throw new IllegalStateException("D3D12 frame requested while renderer state=" + state);
    if (minecraft == null) throw new IllegalStateException("Minecraft instance is null");
    backend.beginFrame(frameSize(minecraft.getWindow()));
    frameOpen = true;
  }

  private void requireActive(String operation) {
    throwIfFailed();
    if (state != State.ACTIVE) {
      throw fatal(operation + " requested while renderer state=" + state + ".", null);
    }
  }

  private void throwIfFailed() {
    if (failure != null) throw failure;
  }

  private FatalRenderException fatal(String message, Throwable cause) {
    if (failure != null) return failure;
    state = State.FAILED;
    frameOpen = false;
    legacyVertexBufferUploadDepth = 0;
    String full =
        "DX12MC STRICT D3D12 ERROR: "
            + message
            + " OpenGL rendering/presentation fallback is disabled by design.";
    failure = new FatalRenderException(full, cause);
    DX12MC.LOGGER.error(full, cause);
    closeBridge();
    try {
      backend.close();
    } catch (Throwable closeFailure) {
      failure.addSuppressed(closeFailure);
    }
    return failure;
  }

  private void closeBridge() {
    if (bridge == null) return;
    try {
      bridge.close();
    } catch (Throwable throwable) {
      DX12MC.LOGGER.debug("Error while releasing native bridge resources", throwable);
    }
  }

  private static FrameSize frameSize(Window window) {
    return new FrameSize(window.getWidth(), window.getHeight());
  }

  private enum State {
    NEW,
    PROBED,
    ACTIVE,
    FAILED,
    CLOSED
  }
}
