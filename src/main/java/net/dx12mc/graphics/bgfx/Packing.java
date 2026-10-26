package net.dx12mc.graphics.bgfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.dx12mc.bridge.mesh.Batch;

public final class Packing {
  public static final int VERTEX_STRIDE = 32;
  public static final int ENTITY_VERTEX_STRIDE = 44;
  private static final int NORMAL_UP =
      ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
          ? Integer.reverseBytes(0x80FF80FF)
          : 0x80FF80FF;

  private Packing() {}

  public static int vertexBytes(Batch batch) {
    return Math.multiplyExact(batch.vertexCount(), VERTEX_STRIDE);
  }

  public static int indexBytes(Batch batch) {
    return Math.multiplyExact(batch.indexCount(), index32(batch) ? Integer.BYTES : Short.BYTES);
  }

  public static boolean index32(int[] indices) {
    for (int index : indices) if (index > 0xFFFF) return true;
    return false;
  }

  public static boolean index32(Batch batch) {
    return batch.vertexCount() > 0xFFFF;
  }

  public static void vertices(ByteBuffer target, Batch batch) {
    ByteOrder order = ByteOrder.nativeOrder();
    target.order(order);
    boolean reverseColor = order == ByteOrder.LITTLE_ENDIAN;
    float[] positions = batch.positions();
    int[] colors = batch.colors();
    float[] uv0 = batch.uv0();
    short[] uv2 = batch.uv2();
    boolean hasUv0 = batch.hasUv0();
    boolean hasUv2 = batch.hasUv2();
    int count = batch.vertexCount();
    for (int vertex = 0; vertex < count; vertex++) {
      int p = vertex * 3;
      int uv = vertex * 2;
      int dst = vertex * VERTEX_STRIDE;
      int rgba = colors[vertex];
      target.putFloat(dst, positions[p]);
      target.putFloat(dst + 4, positions[p + 1]);
      target.putFloat(dst + 8, positions[p + 2]);
      target.putInt(dst + 12, reverseColor ? Integer.reverseBytes(rgba) : rgba);
      target.putFloat(dst + 16, hasUv0 ? uv0[uv] : 0.0f);
      target.putFloat(dst + 20, hasUv0 ? uv0[uv + 1] : 0.0f);
      target.putFloat(dst + 24, hasUv2 ? Short.toUnsignedInt(uv2[uv]) : 0.0f);
      target.putFloat(dst + 28, hasUv2 ? Short.toUnsignedInt(uv2[uv + 1]) : 0.0f);
    }
  }

  public static void lineVertices(ByteBuffer target, Batch batch) {
    ByteOrder order = ByteOrder.nativeOrder();
    target.order(order);
    boolean reverseColor = order == ByteOrder.LITTLE_ENDIAN;
    float[] positions = batch.positions();
    int[] colors = batch.colors();
    byte[] normals = batch.normals();
    int count = batch.vertexCount();
    for (int vertex = 0; vertex < count; vertex++) {
      int p = vertex * 3;
      int dst = vertex * ENTITY_VERTEX_STRIDE;
      int rgba = colors[vertex];
      target.putFloat(dst, positions[p]);
      target.putFloat(dst + 4, positions[p + 1]);
      target.putFloat(dst + 8, positions[p + 2]);
      target.putInt(dst + 12, reverseColor ? Integer.reverseBytes(rgba) : rgba);
      target.putFloat(dst + 16, lineSide(vertex));
      target.putFloat(dst + 20, 0.0f);
      target.putFloat(dst + 24, 0.0f);
      target.putFloat(dst + 28, 0.0f);
      target.putFloat(dst + 32, 0.0f);
      target.putFloat(dst + 36, 0.0f);
      target.putInt(dst + 40, packNormal(normals, p));
    }
  }

  static float lineSide(int vertex) {
    return (vertex & 1) == 0 ? 1.0f : -1.0f;
  }

  public static void entityVertices(ByteBuffer target, Batch batch) {
    ByteOrder order = ByteOrder.nativeOrder();
    target.order(order);
    boolean reverseColor = order == ByteOrder.LITTLE_ENDIAN;
    float[] positions = batch.positions();
    int[] colors = batch.colors();
    float[] uv0 = batch.uv0();
    short[] uv1 = batch.uv1();
    short[] uv2 = batch.uv2();
    byte[] normals = batch.normals();
    boolean hasUv0 = batch.hasUv0();
    boolean hasUv1 = batch.hasUv1();
    boolean hasUv2 = batch.hasUv2();
    boolean hasNormal = batch.hasNormal();
    int count = batch.vertexCount();
    for (int vertex = 0; vertex < count; vertex++) {
      int p = vertex * 3;
      int uv = vertex * 2;
      int dst = vertex * ENTITY_VERTEX_STRIDE;
      int rgba = colors[vertex];
      target.putFloat(dst, positions[p]);
      target.putFloat(dst + 4, positions[p + 1]);
      target.putFloat(dst + 8, positions[p + 2]);
      target.putInt(dst + 12, reverseColor ? Integer.reverseBytes(rgba) : rgba);
      target.putFloat(dst + 16, hasUv0 ? uv0[uv] : 0.0f);
      target.putFloat(dst + 20, hasUv0 ? uv0[uv + 1] : 0.0f);
      target.putFloat(dst + 24, hasUv1 ? uv1[uv] : 0.0f);
      target.putFloat(dst + 28, hasUv1 ? uv1[uv + 1] : 0.0f);
      target.putFloat(dst + 32, hasUv2 ? Short.toUnsignedInt(uv2[uv]) : 0.0f);
      target.putFloat(dst + 36, hasUv2 ? Short.toUnsignedInt(uv2[uv + 1]) : 0.0f);
      target.putInt(dst + 40, hasNormal ? packNormal(normals, p) : NORMAL_UP);
    }
  }

  static int packNormal(byte[] normals, int offset) {
    int x = normals[offset] + 128;
    int y = normals[offset + 1] + 128;
    int z = normals[offset + 2] + 128;
    int packed = (x << 24) | (y << 16) | (z << 8) | 0xFF;
    return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        ? Integer.reverseBytes(packed)
        : packed;
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
