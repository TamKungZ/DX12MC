package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.DX12MC;
import net.dx12mc.graphics.Adapter;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.platform.win32.DxgiAdapters;
import org.lwjgl.bgfx.BGFXCaps;
import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.bgfx.BGFXStats;

public final class Backend implements AutoCloseable {
  public static final int MAIN_VIEW = 0;
  private static final int TRANSIENT_VERTEX_BYTES =
      (int)
          Math.min(
              Integer.MAX_VALUE,
              Math.max(6L, Long.getLong("dx12mc.transient.vertexMiB", 24L)) << 20);
  private static final int TRANSIENT_INDEX_BYTES =
      (int)
          Math.min(
              Integer.MAX_VALUE, Math.max(2L, Long.getLong("dx12mc.transient.indexMiB", 8L)) << 20);
  private final DebugText debugText = new DebugText();
  private final SwapChain swapChain = new SwapChain();
  private boolean initialized;
  private String rendererName = "uninitialized";
  private Adapter adapter;
  private ScreenCapture screenCapture;
  private long frameNumber;
  private int clearColorRgba;
  private int viewClearFlags;
  private int viewClearRgba;
  private float viewClearDepth;
  private final boolean[] viewHasFrameBuffer = new boolean[ViewSequence.MAX_VIEWS + 1];
  private Target mainTarget;
  private Target currentTarget;
  private boolean presentView;
  private boolean mainMrt;

  public void initialize(
      long nativeWindow, FrameSize initialSize, int clearColorRgba, boolean debugText) {
    if (initialized) {
      return;
    }
    swapChain.size(initialSize);
    swapChain.takeResetPending();
    FrameSize size = swapChain.size();
    screenCapture = new ScreenCapture();
    try (BGFXInit init = BGFXInit.calloc()) {
      bgfx_init_ctor(init);
      init.callback(screenCapture.callbackInterface());
      init.type(BGFX_RENDERER_TYPE_DIRECT3D12);
      init.platformData().nwh(nativeWindow);
      init.resolution().width(size.width()).height(size.height()).reset(swapChain.resetFlags());
      init.limits().transientVbSize(TRANSIENT_VERTEX_BYTES).transientIbSize(TRANSIENT_INDEX_BYTES);
      if (!bgfx_init(init)) {
        screenCapture.close();
        screenCapture = null;
        throw new IllegalStateException("bgfx_init returned false for Direct3D 12");
      }
    }
    initialized = true;
    int actualType = bgfx_get_renderer_type();
    rendererName = rendererName(actualType);
    if (actualType != BGFX_RENDERER_TYPE_DIRECT3D12) {
      close();
      throw new IllegalStateException(
          "Unexpected bgfx renderer: " + rendererName + " (" + actualType + ")");
    }
    bgfx_set_view_name(MAIN_VIEW, "DX12MC Main");
    bgfx_set_view_mode(MAIN_VIEW, BGFX_VIEW_MODE_SEQUENTIAL);
    this.clearColorRgba = clearColorRgba;
    bgfx_set_debug(debugText ? BGFX_DEBUG_TEXT : BGFX_DEBUG_NONE);
    adapter = queryAdapter();
    DX12MC.LOGGER.info(
        "bgfx initialized using {} on {} ({}), vsync={}",
        rendererName,
        adapter.name(),
        adapter.logDetails(),
        swapChain.vsync());
  }

  public void vsync(boolean enabled) {
    swapChain.vsync(enabled);
  }

  public boolean vsync() {
    return swapChain.vsync();
  }

  public void beginFrame(FrameSize newSize) {
    beginFrame(newSize, null);
  }

  public void beginFrame(FrameSize newSize, Target mainRedirect) {
    ensureInitialized();
    swapChain.size(newSize);
    FrameSize size = swapChain.size();
    if (swapChain.takeResetPending()) {
      bgfx_reset(size.width(), size.height(), swapChain.resetFlags(), BGFX_TEXTURE_FORMAT_COUNT);
    }
    ViewSequence.reset();
    mainTarget = mainRedirect;
    currentTarget = null;
    presentView = false;
    mainMrt = false;
    viewClearFlags = BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH;
    viewClearRgba = clearColorRgba;
    viewClearDepth = 1.0f;
    prepareView(MAIN_VIEW);
  }

  public void clear(int glMask, int rgba, float depth) {
    ensureInitialized();
    int flags = ViewSequence.clearFlags(glMask);
    if (flags == BGFX_CLEAR_NONE) return;
    if (ViewSequence.advanceIfUsed()) viewClearFlags = BGFX_CLEAR_NONE;
    viewClearFlags |= flags;
    if ((flags & BGFX_CLEAR_COLOR) != 0) viewClearRgba = rgba;
    if ((flags & BGFX_CLEAR_DEPTH) != 0) viewClearDepth = depth;
    prepareView(ViewSequence.current());
  }

  public boolean mainRedirected() {
    return mainTarget != null;
  }

  public void bindTarget(Target target) {
    ensureInitialized();
    if (presentView || currentTarget == target) return;
    currentTarget = target;
    startView(target != null ? target : mainTarget);
  }

  public void mainMrt(boolean enabled) {
    ensureInitialized();
    if (mainTarget == null || !mainTarget.hasMrt() || mainMrt == enabled) return;
    mainMrt = enabled;
    if (!presentView && currentTarget == null) startView(mainTarget);
  }

  public void clearMainAttachments() {
    ensureInitialized();
    if (presentView || mainTarget == null || !mainTarget.hasMrt()) return;
    if (ViewSequence.isUsed() || viewClearFlags != BGFX_CLEAR_NONE) ViewSequence.advance();
    int view = ViewSequence.current();
    bgfx_set_view_mode(view, BGFX_VIEW_MODE_SEQUENTIAL);
    bgfx_set_view_rect(view, 0, 0, mainTarget.width(), mainTarget.height());
    bgfx_set_view_frame_buffer(view, mainTarget.extrasFrameBuffer());
    viewHasFrameBuffer[view] = true;
    bgfx_set_view_clear(view, BGFX_CLEAR_COLOR, 0, 1.0f, 0);
    bgfx_touch(view);
    ViewSequence.used();
    viewClearFlags = BGFX_CLEAR_NONE;
    startView(currentTarget != null ? currentTarget : mainTarget);
  }

  public void beginPresentView() {
    ensureInitialized();
    presentView = true;
    currentTarget = null;
    startView(null);
  }

  private void startView(Target destination) {
    if (ViewSequence.isUsed() || viewClearFlags != BGFX_CLEAR_NONE) ViewSequence.advance();
    viewClearFlags = BGFX_CLEAR_NONE;
    if (destination != null && destination.takeNeedsClear()) {
      viewClearFlags = BGFX_CLEAR_COLOR | (destination.hasDepth() ? BGFX_CLEAR_DEPTH : 0);
      viewClearRgba = 0;
      viewClearDepth = 1.0f;
    }
    prepareView(ViewSequence.current());
  }

  private void prepareView(int view) {
    Target destination = presentView ? null : currentTarget != null ? currentTarget : mainTarget;
    FrameSize size = swapChain.size();
    int width = destination != null ? destination.width() : size.width();
    int height = destination != null ? destination.height() : size.height();
    bgfx_set_view_mode(view, BGFX_VIEW_MODE_SEQUENTIAL);
    bgfx_set_view_rect(view, 0, 0, Math.min(width, 0xFFFF), Math.min(height, 0xFFFF));
    if (destination != null) {
      bgfx_set_view_frame_buffer(
          view,
          mainMrt && destination == mainTarget
              ? destination.mrtFrameBuffer()
              : destination.frameBuffer());
      viewHasFrameBuffer[view] = true;
    } else if (viewHasFrameBuffer[view]) {
      bgfx_set_view_frame_buffer(view, BGFX_INVALID_HANDLE);
      viewHasFrameBuffer[view] = false;
    }
    bgfx_set_view_clear(view, viewClearFlags, viewClearRgba, viewClearDepth, 0);
    bgfx_touch(view);
  }

  public long endFrame() {
    ensureInitialized();
    long started = net.dx12mc.bridge.Profile.ENABLED ? System.nanoTime() : 0L;
    bgfx_frame(false);
    if (net.dx12mc.bridge.Profile.ENABLED) {
      net.dx12mc.bridge.Profile.add(net.dx12mc.bridge.Profile.Section.BGFX_FRAME, started);
      long stats = nbgfx_get_stats();
      if (stats != 0L) {
        net.dx12mc.bridge.Profile.renderThread(
            nanoseconds(
                BGFXStats.ncpuTimeEnd(stats) - BGFXStats.ncpuTimeBegin(stats),
                BGFXStats.ncpuTimerFreq(stats)));
      }
    }
    swapChain.presented();
    return ++frameNumber;
  }

  public DebugText debugText() {
    ensureInitialized();
    return debugText;
  }

  public String rendererName() {
    return rendererName;
  }

  public boolean requestScreenCapture() {
    ensureInitialized();
    if (!swapChain.holdsPresentedFrame()) return false;
    screenCapture.request();
    return true;
  }

  public ScreenCapture.Frame takeScreenCapture() {
    return screenCapture == null ? null : screenCapture.take();
  }

  public Adapter adapter() {
    return adapter;
  }

  public long gpuFrameNanos() {
    ensureInitialized();
    long stats = nbgfx_get_stats();
    if (stats == 0L) return 0L;
    return nanoseconds(
        BGFXStats.ngpuTimeEnd(stats) - BGFXStats.ngpuTimeBegin(stats),
        BGFXStats.ngpuTimerFreq(stats));
  }

  public long frameNumber() {
    return frameNumber;
  }

  public FrameTiming frameTiming() {
    ensureInitialized();
    BGFXStats stats = bgfx_get_stats();
    if (stats == null) return new FrameTiming(0.0, 0.0, 0.0, 0.0, 0);
    return new FrameTiming(
        milliseconds(stats.cpuTimeFrame(), stats.cpuTimerFreq()),
        milliseconds(stats.gpuTimeEnd() - stats.gpuTimeBegin(), stats.gpuTimerFreq()),
        milliseconds(stats.waitSubmit(), stats.cpuTimerFreq()),
        milliseconds(stats.waitRender(), stats.cpuTimerFreq()),
        stats.numDraw());
  }

  public boolean initialized() {
    return initialized;
  }

  @Override
  public void close() {
    if (!initialized) {
      return;
    }
    initialized = false;
    bgfx_shutdown();
    screenCapture.close();
    screenCapture = null;
    rendererName = "uninitialized";
    adapter = null;
    DX12MC.LOGGER.info("bgfx shutdown complete");
  }

  private static Adapter queryAdapter() {
    long caps = nbgfx_get_caps();
    int vendorId = Short.toUnsignedInt(BGFXCaps.nvendorId(caps));
    int deviceId = Short.toUnsignedInt(BGFXCaps.ndeviceId(caps));
    DxgiAdapters.Details details = null;
    try {
      details = DxgiAdapters.query(vendorId, deviceId);
      if (details == null) {
        DX12MC.LOGGER.warn(
            "DXGI did not list the adapter bgfx opened ({}:{}); using its PCI ids as its name",
            Integer.toHexString(vendorId),
            Integer.toHexString(deviceId));
      } else {
        for (String problem : details.problems()) DX12MC.LOGGER.warn("{}", problem);
      }
    } catch (Throwable throwable) {
      DX12MC.LOGGER.warn("Unable to read the Direct3D 12 adapter details from DXGI", throwable);
    }
    if (details == null) return new Adapter(vendorId, deviceId, null);
    return new Adapter(
        vendorId, deviceId, details.description(), details.driverVersion(), details.featureLevel());
  }

  private static String rendererName(int type) {
    String name = bgfx_get_renderer_name(type);
    return name == null ? Integer.toString(type) : name;
  }

  private static double milliseconds(long ticks, long frequency) {
    if (ticks <= 0L || frequency <= 0L) return 0.0;
    return ticks * 1000.0 / frequency;
  }

  static long nanoseconds(long ticks, long frequency) {
    if (ticks <= 0L || frequency <= 0L) return 0L;
    return (long) (ticks * 1_000_000_000.0 / frequency);
  }

  private void ensureInitialized() {
    if (!initialized) {
      throw new IllegalStateException("bgfx backend is not initialized");
    }
  }

  public record FrameTiming(
      double cpuMillis,
      double gpuMillis,
      double waitSubmitMillis,
      double waitRenderMillis,
      int draws) {}
}
