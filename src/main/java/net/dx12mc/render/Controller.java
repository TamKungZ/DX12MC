package net.dx12mc.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Bridge;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import net.dx12mc.config.Config;
import net.dx12mc.graphics.RendererInfo;
import net.dx12mc.graphics.bgfx.ScreenCapture;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PostPipeline;
import net.dx12mc.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.bgfx.BGFX;

public final class Controller implements AutoCloseable {
  private final Config config;
  private final Stats stats = new Stats();
  private final Bridge bridge;
  private final RendererLifecycle lifecycle;
  private final SectionRenderContext section = new SectionRenderContext();
  private String semanticIntent = "frame";
  private int legacyVertexBufferUploadDepth;
  private int clearRgba;
  private float clearDepth = 1.0f;
  private static final int GL_LINEAR = 9729;
  private final PostProcessor post;
  private final Map<RenderTarget, PendingClear> pendingClears = new IdentityHashMap<>();
  private final float[] blitValues = new float[PostPipeline.UNIFORM_FLOATS];
  private RenderTarget boundTarget;
  private RenderTarget appliedTarget;
  private boolean appliedStale;
  private static final int SCREEN_CAPTURE_TIMEOUT_FRAMES = 60;
  private final List<CaptureRequest> captureRequests = new ArrayList<>();
  private boolean captureInFlight;
  private int captureWaitFrames;
  private NativeImage capturedScreenshot;
  private static final int GL_ARRAY_BUFFER = 0x8892;

  public Controller(Config config) {
    this.config = config;
    this.bridge = config.nativeMode() ? new Bridge(stats) : null;
    this.lifecycle = new RendererLifecycle(config, bridge, stats);
    this.clearRgba = config.clearColorRgba();
    this.post = bridge == null ? null : new PostProcessor(bridge, this);
  }

  public void initialize() {
    lifecycle.initialize();
  }

  public void capture(Slot slot, MeshData mesh, VertexBuffer.Usage usage) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (lifecycle.closed()) return;
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
    if (lifecycle.closed() || result == null) return;
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
    if (!config.nativeMode() || !lifecycle.active() || legacyVertexBufferUploadDepth <= 0)
      return false;
    if (target != GL_ARRAY_BUFFER) return false;
    stats.suppressedLegacyVertexStorage(bytes);
    return true;
  }

  public void texParameter(int target, int pname, float value) {
    if (bridge != null) bridge.texParameter(target, pname, value);
  }

  public void clearColor(float red, float green, float blue, float alpha) {
    clearRgba = ViewSequence.rgba(red, green, blue, alpha);
  }

  public void clearDepth(double depth) {
    clearDepth = (float) depth;
  }

  public boolean clear(int mask) {
    if (!config.nativeMode() || !lifecycle.active()) return false;
    throwIfFailed();
    if (boundTarget != null) {
      deferClear(boundTarget, mask);
      return true;
    }
    try {
      Minecraft minecraft = Minecraft.getInstance();
      ensureFrame(minecraft);
      syncTarget();
      lifecycle.clear(minecraft, mask, clearRgba, clearDepth);
      return true;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal(
          "Native D3D12 clear failed: mask=0x" + Integer.toHexString(mask) + ".", throwable);
    }
  }

  public void bindRenderTarget(RenderTarget target) {
    Minecraft minecraft = Minecraft.getInstance();
    boundTarget =
        target == null || minecraft == null || target == minecraft.getMainRenderTarget()
            ? null
            : target;
  }

  public void releaseRenderTarget(RenderTarget target) {
    pendingClears.remove(target);
    if (boundTarget == target) boundTarget = null;
    if (appliedTarget == target) appliedStale = true;
    if (bridge != null) bridge.releaseTarget(target);
  }

  public boolean processPostChain(PostChain chain, float partialTick) {
    if (!config.nativeMode() || !lifecycle.active()) return false;
    throwIfFailed();
    try {
      ensureFrame(Minecraft.getInstance());
      return post.process(chain, partialTick);
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Native D3D12 post-processing failed.", throwable);
    }
  }

  public boolean blitRenderTarget(RenderTarget target, boolean disableBlend) {
    if (!config.nativeMode() || !lifecycle.active()) return false;
    throwIfFailed();
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || target == minecraft.getMainRenderTarget()) {
      return suppressVanillaFramebufferBlit();
    }
    try {
      long state = bridge.blitState(disableBlend);
      PendingClear pending = pendingClears.get(target);
      if (pending != null && pending.active && (state & BGFX.BGFX_STATE_BLEND_MASK) != 0L) {
        return true;
      }
      ensureFrame(minecraft);
      materializeClear(target);
      syncTarget();
      Target source = bridge.target(target);
      java.util.Arrays.fill(blitValues, 0.0f);
      blitValues[0] = blitValues[2] = target.width;
      blitValues[1] = blitValues[3] = target.height;
      blitValues[4] = blitValues[5] = blitValues[6] = blitValues[7] = 1.0f;
      if (!bridge
          .post()
          .draw(
              PostPipeline.Effect.BLIT,
              source.colorTexture(),
              BGFX.BGFX_INVALID_HANDLE,
              target.filterMode == GL_LINEAR,
              blitValues,
              state)) {
        throw new IllegalStateException("bgfx/D3D12 rejected a render target blit");
      }
      return true;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Native D3D12 render target blit failed.", throwable);
    }
  }

  boolean mainRedirected() {
    return lifecycle.mainRedirected();
  }

  Target nativeTarget(RenderTarget target) {
    if (target == Minecraft.getInstance().getMainRenderTarget() && !lifecycle.mainRedirected()) {
      return null;
    }
    return bridge.target(target);
  }

  void applyTarget(RenderTarget target) {
    RenderTarget wanted = target == Minecraft.getInstance().getMainRenderTarget() ? null : target;
    if (wanted == appliedTarget && !appliedStale) return;
    lifecycle.bindTarget(wanted == null ? null : bridge.target(wanted));
    appliedTarget = wanted;
    appliedStale = false;
  }

  void clearApplied(int rgba, boolean depth) {
    lifecycle.clear(
        Minecraft.getInstance(),
        ViewSequence.GL_COLOR_BUFFER_BIT | (depth ? ViewSequence.GL_DEPTH_BUFFER_BIT : 0),
        rgba,
        1.0f);
  }

  void finishPostChain() {
    boundTarget = null;
  }

  void materializeClear(RenderTarget target) {
    PendingClear pending = pendingClears.get(target);
    if (pending == null || !pending.active) return;
    pending.active = false;
    Minecraft minecraft = Minecraft.getInstance();
    ensureFrame(minecraft);
    applyTarget(target);
    lifecycle.clear(minecraft, pending.mask, pending.rgba, pending.depth);
  }

  private void deferClear(RenderTarget target, int mask) {
    PendingClear pending = pendingClears.computeIfAbsent(target, ignored -> new PendingClear());
    pending.active = true;
    pending.mask = mask;
    pending.rgba = clearRgba;
    pending.depth = clearDepth;
  }

  private void syncTarget() {
    if (boundTarget == null && appliedTarget == null && !appliedStale) return;
    if (boundTarget != null) materializeClear(boundTarget);
    applyTarget(boundTarget == null ? Minecraft.getInstance().getMainRenderTarget() : boundTarget);
  }

  private void ensureFrame(Minecraft minecraft) {
    if (lifecycle.frameOpen()) return;
    Target main =
        needsReadableMain(minecraft) ? bridge.target(minecraft.getMainRenderTarget()) : null;
    lifecycle.ensureFrame(minecraft, main);
    appliedTarget = null;
    appliedStale = false;
  }

  private static boolean needsReadableMain(Minecraft minecraft) {
    GameRenderer renderer = minecraft.gameRenderer;
    if (renderer == null) return false;
    GameRendererAccessor access = (GameRendererAccessor) renderer;
    if (minecraft.screen != null
        && minecraft.options.getMenuBackgroundBlurriness() >= 1
        && access.dx12mc$blurEffect() != null) {
      return true;
    }
    return minecraft.level != null
        && renderer.currentEffect() != null
        && access.dx12mc$effectActive();
  }

  private static final class PendingClear {
    boolean active;
    int mask;
    int rgba;
    float depth;
  }

  public void bindTexture(int id) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (!lifecycle.closed()) bridge.bindTexture(id);
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
    if (lifecycle.closed()) return;
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
    if (lifecycle.closed()) return;
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
      int height) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (lifecycle.closed()) return;
    try {
      bridge.uploadTexture(image, level, offsetX, offsetY, skipPixels, skipRows, width, height);
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
    lifecycle.ensureInitialized(minecraft);
    if (captureInFlight) deliverScreenCapture();
  }

  public boolean ownsScreenCapture() {
    if (config.probeMode()) return false;
    throwIfFailed();
    return lifecycle.active();
  }

  public void requestScreenCapture(Runnable onCaptured, Consumer<String> onFailure) {
    captureRequests.add(new CaptureRequest(onCaptured, onFailure));
  }

  public boolean hasCapturedScreenshot() {
    return capturedScreenshot != null;
  }

  public NativeImage takeCapturedScreenshot() {
    NativeImage image = capturedScreenshot;
    capturedScreenshot = null;
    return image;
  }

  private void deliverScreenCapture() {
    ScreenCapture.Frame frame = lifecycle.takeScreenCapture();
    if (frame == null) {
      if (++captureWaitFrames > SCREEN_CAPTURE_TIMEOUT_FRAMES) {
        failScreenCaptures("bgfx did not return the requested screen capture");
      }
      return;
    }
    List<CaptureRequest> requests = new ArrayList<>(captureRequests);
    captureRequests.clear();
    captureInFlight = false;
    captureWaitFrames = 0;
    try {
      for (CaptureRequest request : requests) {
        capturedScreenshot = ScreenImages.toNativeImage(frame);
        try {
          request.onCaptured().run();
        } finally {
          NativeImage unused = takeCapturedScreenshot();
          if (unused != null) unused.close();
        }
      }
    } finally {
      ScreenCapture.free(frame);
    }
  }

  private void failScreenCaptures(String reason) {
    DX12MC.LOGGER.warn("D3D12 screen capture failed: {}", reason);
    List<CaptureRequest> requests = new ArrayList<>(captureRequests);
    captureRequests.clear();
    captureInFlight = false;
    captureWaitFrames = 0;
    for (CaptureRequest request : requests) request.onFailure().accept(reason);
  }

  private record CaptureRequest(Runnable onCaptured, Consumer<String> onFailure) {}

  public boolean draw(Slot slot, Matrix4f modelView, Matrix4f projection) {
    if (!config.nativeMode()) return false;
    throwIfFailed();
    lifecycle.ensureInitialized(Minecraft.getInstance());
    lifecycle.requireActive("VertexBuffer draw");
    try {
      ensureFrame(Minecraft.getInstance());
      syncTarget();
      Window window = Minecraft.getInstance().getWindow();
      if (!bridge.draw(
          slot, modelView, projection, framebufferWidth(window), framebufferHeight(window))) {
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

  public void beginSectionLayer(
      RenderType renderType,
      double cameraX,
      double cameraY,
      double cameraZ,
      Matrix4f modelView,
      Matrix4f projection) {
    if (!config.nativeMode()) return;
    throwIfFailed();
    if (modelView == null || projection == null) {
      throw fatal("Minecraft section-layer draw supplied null matrices.", null);
    }
    Minecraft minecraft = Minecraft.getInstance();
    lifecycle.ensureInitialized(minecraft);
    lifecycle.requireActive("section-layer draw");
    ensureFrame(minecraft);
    syncTarget();
    Window window = minecraft.getWindow();
    section.begin(
        modelView, projection, window.getWidth(), window.getHeight(), cameraX, cameraY, cameraZ);
    this.semanticIntent = "level.section-layer";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
    if (bridge != null) {
      boolean regionBatchable =
          renderType == RenderType.solid()
              || renderType == RenderType.cutout()
              || renderType == RenderType.cutoutMipped();
      bridge.beginSectionLayer(
          renderType,
          regionBatchable,
          section.layerClipTransform(),
          section.cameraX(),
          section.cameraY(),
          section.cameraZ());
    }
  }

  public void sectionChunkOffset(float x, float y, float z) {
    section.offset(x, y, z);
  }

  public boolean drawSection(Slot slot) {
    if (!config.nativeMode() || !section.active()) return false;
    throwIfFailed();
    if (!section.ready()) {
      throw fatal("D3D12 section-layer context is incomplete.", null);
    }
    try {
      if (!bridge.drawSection(
          slot,
          section.drawClipTransform(),
          section.framebufferWidth(),
          section.framebufferHeight(),
          section.originX(),
          section.originY(),
          section.originZ(),
          section.offsetX(),
          section.offsetY(),
          section.offsetZ())) {
        throw new IllegalStateException("D3D12 bridge returned no section submission");
      }
      stats.cancelledVanillaDraw();
      stats.sectionLayerDraw();
      return true;
    } catch (FatalRenderException fatal) {
      throw fatal;
    } catch (Throwable throwable) {
      throw fatal("Native D3D12 section submission failed.", throwable);
    }
  }

  public boolean suppressLegacySectionDriverCalls() {
    return config.nativeMode() && lifecycle.active() && section.active();
  }

  public void endSectionLayer() {
    if (bridge != null && section.active()) {
      try {
        bridge.endSectionLayer();
      } catch (FatalRenderException fatal) {
        throw fatal;
      } catch (Throwable throwable) {
        throw fatal("Native D3D12 section-layer flush failed.", throwable);
      }
    }
    section.end();
    this.semanticIntent = "frame";
    if (bridge != null) bridge.semanticIntent(this.semanticIntent);
  }

  public boolean drawImmediate(MeshData mesh) {
    if (!config.nativeMode()) return false;
    throwIfFailed();
    lifecycle.ensureInitialized(Minecraft.getInstance());
    lifecycle.requireActive("immediate MeshData draw");
    try {
      ensureFrame(Minecraft.getInstance());
      syncTarget();
      Window window = Minecraft.getInstance().getWindow();
      if (!bridge.drawImmediate(
          mesh,
          RenderSystem.getModelViewMatrix(),
          RenderSystem.getProjectionMatrix(),
          framebufferWidth(window),
          framebufferHeight(window))) {
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

  private int framebufferWidth(Window window) {
    return appliedTarget == null ? window.getWidth() : appliedTarget.width;
  }

  private int framebufferHeight(Window window) {
    return appliedTarget == null ? window.getHeight() : appliedTarget.height;
  }

  public void release(Slot slot) {
    if (slot == null) return;
    if (bridge != null) bridge.release(slot);
    else slot.clearCpu();
  }

  public void presentAtSwap(Minecraft minecraft) {
    if (!captureInFlight && !captureRequests.isEmpty() && lifecycle.active()) {
      captureInFlight = lifecycle.requestScreenCapture(minecraft);
    }
    lifecycle.present(minecraft);
  }

  public boolean openGlPresentationAllowed() {
    return config.openGlPresentationAllowed();
  }

  public void vsync(boolean enabled) {
    lifecycle.vsync(enabled);
  }

  public RendererInfo rendererInfo() {
    return lifecycle.rendererInfo();
  }

  public boolean ownsGpuTiming() {
    return lifecycle.active();
  }

  public long gpuFrameNanos() {
    return lifecycle.gpuFrameNanos();
  }

  public boolean legacyPostProcessingAvailable() {
    return config.legacyPostProcessingAvailable();
  }

  public boolean suppressVanillaFramebufferBlit() {
    if (config.probeMode()) return false;
    throwIfFailed();
    if (!lifecycle.active()) return false;
    stats.suppressedOpenGlFramebufferBlit();
    return true;
  }

  public boolean suppressLegacyOpenGlDriverWork() {
    return config.nativeMode() && lifecycle.active();
  }

  public void assertOpenGlDrawAllowed(String operation) {
    if (lifecycle.openGlGuardAllowed()) return;
    throwIfFailed();
    if (lifecycle.active()) {
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
    if (lifecycle.openGlGuardAllowed()) return;
    throwIfFailed();
    if (lifecycle.active()) {
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
    return config.debugMode() && lifecycle.active();
  }

  @Override
  public void close() {
    if (lifecycle.closed()) return;
    captureRequests.clear();
    captureInFlight = false;
    NativeImage pending = takeCapturedScreenshot();
    if (pending != null) pending.close();
    section.end();
    legacyVertexBufferUploadDepth = 0;
    lifecycle.close();
  }

  private void throwIfFailed() {
    lifecycle.throwIfFailed();
  }

  private FatalRenderException fatal(String message, Throwable cause) {
    section.end();
    legacyVertexBufferUploadDepth = 0;
    return lifecycle.fail(message, cause);
  }
}
