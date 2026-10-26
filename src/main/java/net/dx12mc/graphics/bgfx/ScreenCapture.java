package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.BGFX_FATAL_DEBUG_CHECK;
import static org.lwjgl.bgfx.BGFX.BGFX_INVALID_HANDLE;
import static org.lwjgl.bgfx.BGFX.bgfx_request_screen_shot;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import net.dx12mc.DX12MC;
import org.lwjgl.bgfx.BGFXCacheReadCallback;
import org.lwjgl.bgfx.BGFXCacheReadSizeCallback;
import org.lwjgl.bgfx.BGFXCacheWriteCallback;
import org.lwjgl.bgfx.BGFXCallbackInterface;
import org.lwjgl.bgfx.BGFXCallbackVtbl;
import org.lwjgl.bgfx.BGFXCaptureBeginCallback;
import org.lwjgl.bgfx.BGFXCaptureEndCallback;
import org.lwjgl.bgfx.BGFXCaptureFrameCallback;
import org.lwjgl.bgfx.BGFXFatalCallback;
import org.lwjgl.bgfx.BGFXProfilerBegin;
import org.lwjgl.bgfx.BGFXProfilerBeginLiteral;
import org.lwjgl.bgfx.BGFXProfilerEnd;
import org.lwjgl.bgfx.BGFXScreenShotCallback;
import org.lwjgl.bgfx.BGFXTraceVarArgsCallback;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryUtil;

public final class ScreenCapture implements AutoCloseable {
  private final Callback[] callbacks;
  private final BGFXCallbackVtbl vtbl;
  private final BGFXCallbackInterface callbackInterface;
  private final AtomicReference<Frame> captured = new AtomicReference<>();

  public record Frame(ByteBuffer bgra, int width, int height, int pitch, boolean yflip) {}

  public ScreenCapture() {
    BGFXFatalCallback fatal = BGFXFatalCallback.create(ScreenCapture::fatal);
    BGFXTraceVarArgsCallback trace =
        BGFXTraceVarArgsCallback.create((self, file, line, format, args) -> {});
    BGFXProfilerBegin profilerBegin =
        BGFXProfilerBegin.create((self, name, abgr, file, line) -> {});
    BGFXProfilerBeginLiteral profilerBeginLiteral =
        BGFXProfilerBeginLiteral.create((self, name, abgr, file, line) -> {});
    BGFXProfilerEnd profilerEnd = BGFXProfilerEnd.create(self -> {});
    BGFXCacheReadSizeCallback cacheReadSize = BGFXCacheReadSizeCallback.create((self, id) -> 0);
    BGFXCacheReadCallback cacheRead = BGFXCacheReadCallback.create((self, id, data, size) -> false);
    BGFXCacheWriteCallback cacheWrite = BGFXCacheWriteCallback.create((self, id, data, size) -> {});
    BGFXScreenShotCallback screenShot = BGFXScreenShotCallback.create(this::screenShot);
    BGFXCaptureBeginCallback captureBegin =
        BGFXCaptureBeginCallback.create((self, width, height, pitch, format, yflip) -> {});
    BGFXCaptureEndCallback captureEnd = BGFXCaptureEndCallback.create(self -> {});
    BGFXCaptureFrameCallback captureFrame =
        BGFXCaptureFrameCallback.create((self, data, size) -> {});
    callbacks =
        new Callback[] {
          fatal,
          trace,
          profilerBegin,
          profilerBeginLiteral,
          profilerEnd,
          cacheReadSize,
          cacheRead,
          cacheWrite,
          screenShot,
          captureBegin,
          captureEnd,
          captureFrame
        };
    vtbl =
        BGFXCallbackVtbl.calloc()
            .set(
                fatal,
                trace,
                profilerBegin,
                profilerBeginLiteral,
                profilerEnd,
                cacheReadSize,
                cacheRead,
                cacheWrite,
                screenShot,
                captureBegin,
                captureEnd,
                captureFrame);
    callbackInterface = BGFXCallbackInterface.calloc().vtbl(vtbl);
  }

  BGFXCallbackInterface callbackInterface() {
    return callbackInterface;
  }

  void request() {
    bgfx_request_screen_shot(BGFX_INVALID_HANDLE, "dx12mc");
  }

  public Frame take() {
    return captured.getAndSet(null);
  }

  public static void free(Frame frame) {
    if (frame != null) MemoryUtil.memFree(frame.bgra());
  }

  @Override
  public void close() {
    free(take());
    callbackInterface.free();
    vtbl.free();
    for (Callback callback : callbacks) callback.free();
  }

  private void screenShot(
      long self,
      long filePath,
      int width,
      int height,
      int pitch,
      long data,
      int size,
      boolean yflip) {
    ByteBuffer copy = MemoryUtil.memAlloc(size);
    MemoryUtil.memCopy(data, MemoryUtil.memAddress(copy), size);
    free(captured.getAndSet(new Frame(copy, width, height, pitch, yflip)));
  }

  private static void fatal(long self, long filePath, short line, int code, long message) {
    String text = message == 0L ? "" : MemoryUtil.memUTF8Safe(message);
    String file = filePath == 0L ? "?" : MemoryUtil.memUTF8Safe(filePath);
    if (code == BGFX_FATAL_DEBUG_CHECK) {
      DX12MC.LOGGER.warn("bgfx debug check at {}:{}: {}", file, line, text);
      return;
    }
    DX12MC.LOGGER.error(
        "bgfx fatal error 0x{} at {}:{}: {}", Integer.toHexString(code), file, line, text);
    java.lang.Runtime.getRuntime().halt(1);
  }
}
