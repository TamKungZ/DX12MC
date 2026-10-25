package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.DX12MC;
import net.dx12mc.graphics.FrameSize;
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
  private boolean initialized;
  private FrameSize size = new FrameSize(1, 1);
  private int resetFlags;
  private String rendererName = "uninitialized";
  private long frameNumber;

  public void initialize(
      long nativeWindow,
      FrameSize initialSize,
      boolean vsync,
      int clearColorRgba,
      boolean debugText) {
    if (initialized) {
      return;
    }
    size = initialSize;
    resetFlags = vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
    try (BGFXInit init = BGFXInit.calloc()) {
      bgfx_init_ctor(init);
      init.type(BGFX_RENDERER_TYPE_DIRECT3D12);
      init.platformData().nwh(nativeWindow);
      init.resolution().width(size.width()).height(size.height()).reset(resetFlags);
      init.limits().transientVbSize(TRANSIENT_VERTEX_BYTES).transientIbSize(TRANSIENT_INDEX_BYTES);
      if (!bgfx_init(init)) {
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
    bgfx_set_view_clear(MAIN_VIEW, BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH, clearColorRgba, 1.0f, 0);
    bgfx_set_debug(debugText ? BGFX_DEBUG_TEXT : BGFX_DEBUG_NONE);
    DX12MC.LOGGER.info("bgfx initialized using {}", rendererName);
  }

  public void beginFrame(FrameSize newSize) {
    ensureInitialized();
    resizeIfNeeded(newSize);
    bgfx_set_view_rect(
        MAIN_VIEW, 0, 0, Math.min(size.width(), 0xFFFF), Math.min(size.height(), 0xFFFF));
    bgfx_touch(MAIN_VIEW);
  }

  public long endFrame() {
    ensureInitialized();
    bgfx_frame(false);
    return ++frameNumber;
  }

  public DebugText debugText() {
    ensureInitialized();
    return debugText;
  }

  public String rendererName() {
    return rendererName;
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
    rendererName = "uninitialized";
    DX12MC.LOGGER.info("bgfx shutdown complete");
  }

  private void resizeIfNeeded(FrameSize newSize) {
    if (newSize.equals(size)) {
      return;
    }
    size = newSize;
    bgfx_reset(size.width(), size.height(), resetFlags, BGFX_TEXTURE_FORMAT_COUNT);
  }

  private static String rendererName(int type) {
    String name = bgfx_get_renderer_name(type);
    return name == null ? Integer.toString(type) : name;
  }

  private static double milliseconds(long ticks, long frequency) {
    if (ticks <= 0L || frequency <= 0L) return 0.0;
    return ticks * 1000.0 / frequency;
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
