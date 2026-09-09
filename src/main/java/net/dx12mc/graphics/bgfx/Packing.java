package net.dx12mc.graphics.bgfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.dx12mc.bridge.mesh.Batch;

public final class Packing {
  public static final int VERTEX_STRIDE = 32;

  private Packing() {}

  public static int vertexBytes(Batch batch) {
    return Math.multiplyExact(batch.vertexCount(), VERTEX_STRIDE);
  }

  public static int indexBytes(Batch batch) {
    return Math.multiplyExact(batch.indexCount(), index32(batch) ? Integer.BYTES : Short.BYTES);
  }

  public static long gpuBytes(Batch batch) {
    return (long) vertexBytes(batch) + indexBytes(batch);
  }

  public static boolean index32(int[] indices) {
    for (int index : indices) if (index > 0xFFFF) return true;
    return false;
  }

  public static boolean index32(Batch batch) {
    return batch.vertexCount() > 0xFFFF;
  }

  public static void vertices(ByteBuffer target, Batch batch) {
    target.order(ByteOrder.nativeOrder());
    float[] positions = batch.positions();
    int[] colors = batch.colors();
    float[] uv0 = batch.uv0();
    short[] uv2 = batch.uv2();
    for (int vertex = 0; vertex < batch.vertexCount(); vertex++) {
      int p = vertex * 3;
      int uv = vertex * 2;
      int dst = vertex * VERTEX_STRIDE;
      int rgba = colors[vertex];
      target.putFloat(dst, positions[p]);
      target.putFloat(dst + 4, positions[p + 1]);
      target.putFloat(dst + 8, positions[p + 2]);
      target.put(dst + 12, (byte) (rgba >>> 24));
      target.put(dst + 13, (byte) (rgba >>> 16));
      target.put(dst + 14, (byte) (rgba >>> 8));
      target.put(dst + 15, (byte) rgba);
      target.putFloat(dst + 16, batch.hasUv0() ? uv0[uv] : 0.0f);
      target.putFloat(dst + 20, batch.hasUv0() ? uv0[uv + 1] : 0.0f);
      target.putFloat(dst + 24, batch.hasUv2() ? Short.toUnsignedInt(uv2[uv]) : 0.0f);
      target.putFloat(dst + 28, batch.hasUv2() ? Short.toUnsignedInt(uv2[uv + 1]) : 0.0f);
    }
  }

  public static void indices(ByteBuffer target, int[] source, boolean index32) {
    indices(target, source, source.length, index32);
  }

  public static void indices(ByteBuffer target, int[] source, int count, boolean index32) {
    target.order(ByteOrder.nativeOrder());
    if (index32) for (int i = 0; i < count; i++) target.putInt(i * Integer.BYTES, source[i]);
    else for (int i = 0; i < count; i++) target.putShort(i * Short.BYTES, (short) source[i]);
  }
}
