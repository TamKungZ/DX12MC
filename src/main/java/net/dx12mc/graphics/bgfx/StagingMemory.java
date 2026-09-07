package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.bgfx_make_ref_release;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.lwjgl.bgfx.BGFXMemory;
import org.lwjgl.bgfx.BGFXReleaseFunctionCallbackI;
import org.lwjgl.system.MemoryUtil;

public final class StagingMemory {
  private static final long MAX_OUTSTANDING_BYTES =
      Math.max(4L << 20, Long.getLong("dx12mc.staging.maxOutstandingBytes", 16L << 20));
  private static final AtomicLong OUTSTANDING = new AtomicLong();
  private static final AtomicLong PEAK = new AtomicLong();
  private static final BGFXReleaseFunctionCallbackI RELEASE =
      (ptr, userData) -> {
        try {
          if (ptr != 0L) MemoryUtil.nmemFree(ptr);
        } finally {
          long bytes = Math.max(0L, userData);
          OUTSTANDING.getAndUpdate(current -> Math.max(0L, current - bytes));
        }
      };

  private StagingMemory() {}

  public static BGFXMemory copy(byte[] source) {
    if (source == null) throw new NullPointerException("source");
    return allocate(source.length, target -> target.put(source));
  }

  public static BGFXMemory allocate(int bytes, Consumer<ByteBuffer> writer) {
    if (bytes <= 0) throw new IllegalArgumentException("staging bytes must be > 0: " + bytes);
    if (writer == null) throw new NullPointerException("writer");
    reserve(bytes);
    ByteBuffer buffer = null;
    boolean handedOff = false;
    try {
      buffer = MemoryUtil.memAlloc(bytes);
      writer.accept(buffer);
      buffer.position(0);
      buffer.limit(bytes);
      BGFXMemory memory = bgfx_make_ref_release(buffer, RELEASE, bytes);
      if (memory == null) {
        throw new StagingBackpressureException(
            "bgfx_make_ref_release rejected " + bytes + " staging bytes");
      }
      handedOff = true;
      return memory;
    } catch (OutOfMemoryError oom) {
      throw new StagingBackpressureException(
          "native staging allocation failed for "
              + bytes
              + " bytes; outstanding="
              + OUTSTANDING.get()
              + "/"
              + MAX_OUTSTANDING_BYTES,
          oom);
    } finally {
      if (!handedOff) {
        if (buffer != null) MemoryUtil.memFree(buffer);
        releaseReservation(bytes);
      }
    }
  }

  public static long outstandingBytes() {
    return OUTSTANDING.get();
  }

  public static long peakBytes() {
    return PEAK.get();
  }

  public static long maxOutstandingBytes() {
    return MAX_OUTSTANDING_BYTES;
  }

  private static void reserve(int bytes) {
    while (true) {
      long current = OUTSTANDING.get();
      long next;
      try {
        next = Math.addExact(current, bytes);
      } catch (ArithmeticException overflow) {
        throw new StagingBackpressureException("native staging byte counter overflow");
      }
      if (next > MAX_OUTSTANDING_BYTES) {
        throw new StagingBackpressureException(
            "native staging budget exhausted: requested="
                + bytes
                + ", outstanding="
                + current
                + ", limit="
                + MAX_OUTSTANDING_BYTES);
      }
      if (OUTSTANDING.compareAndSet(current, next)) {
        PEAK.accumulateAndGet(next, Math::max);
        return;
      }
    }
  }

  private static void releaseReservation(long bytes) {
    OUTSTANDING.getAndUpdate(current -> Math.max(0L, current - Math.max(0L, bytes)));
  }
}
