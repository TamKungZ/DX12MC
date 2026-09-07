package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Encoder {

  private Encoder() {}

  public static Batch encode(MeshData mesh) {
    if (mesh == null) return null;
    MeshData.DrawState state = mesh.drawState();
    VertexFormat format = state.format();
    int vertexCount = state.vertexCount();
    if (vertexCount <= 0 || !format.contains(VertexFormatElement.POSITION)) return null;
    int stride = format.getVertexSize();
    int positionOffset = format.getOffset(VertexFormatElement.POSITION);
    if (stride <= 0 || positionOffset < 0 || positionOffset + Float.BYTES * 3 > stride) return null;
    ByteBuffer source = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
    long required = (long) stride * vertexCount;
    if (required > source.remaining()) return null;
    float[] positions = new float[vertexCount * 3];
    int[] colors = new int[vertexCount];
    boolean hasColor = valid(format, VertexFormatElement.COLOR, 4, stride);
    int colorOffset = hasColor ? format.getOffset(VertexFormatElement.COLOR) : -1;
    boolean hasUv0 = valid(format, VertexFormatElement.UV0, Float.BYTES * 2, stride);
    int uv0Offset = hasUv0 ? format.getOffset(VertexFormatElement.UV0) : -1;
    boolean hasUv1 = valid(format, VertexFormatElement.UV1, Short.BYTES * 2, stride);
    int uv1Offset = hasUv1 ? format.getOffset(VertexFormatElement.UV1) : -1;
    boolean hasUv2 = valid(format, VertexFormatElement.UV2, Short.BYTES * 2, stride);
    int uv2Offset = hasUv2 ? format.getOffset(VertexFormatElement.UV2) : -1;
    boolean hasNormal = valid(format, VertexFormatElement.NORMAL, 3, stride);
    int normalOffset = hasNormal ? format.getOffset(VertexFormatElement.NORMAL) : -1;
    float[] uv0 = hasUv0 ? new float[vertexCount * 2] : new float[0];
    short[] uv1 = hasUv1 ? new short[vertexCount * 2] : new short[0];
    short[] uv2 = hasUv2 ? new short[vertexCount * 2] : new short[0];
    byte[] normals = hasNormal ? new byte[vertexCount * 3] : new byte[0];
    int base = source.position();
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      int cursor = base + vertex * stride;
      int p = cursor + positionOffset;
      int target = vertex * 3;
      positions[target] = source.getFloat(p);
      positions[target + 1] = source.getFloat(p + Float.BYTES);
      positions[target + 2] = source.getFloat(p + Float.BYTES * 2);
      if (hasColor) {
        int c = cursor + colorOffset;
        int r = Byte.toUnsignedInt(source.get(c));
        int g = Byte.toUnsignedInt(source.get(c + 1));
        int b = Byte.toUnsignedInt(source.get(c + 2));
        int a = Byte.toUnsignedInt(source.get(c + 3));
        colors[vertex] = (r << 24) | (g << 16) | (b << 8) | a;
      } else {
        colors[vertex] = 0xFFFFFFFF;
      }
      if (hasUv0) {
        int uv = cursor + uv0Offset;
        int u = vertex * 2;
        uv0[u] = source.getFloat(uv);
        uv0[u + 1] = source.getFloat(uv + Float.BYTES);
      }
      if (hasUv1) {
        int uv = cursor + uv1Offset;
        int u = vertex * 2;
        uv1[u] = source.getShort(uv);
        uv1[u + 1] = source.getShort(uv + Short.BYTES);
      }
      if (hasUv2) {
        int uv = cursor + uv2Offset;
        int u = vertex * 2;
        uv2[u] = source.getShort(uv);
        uv2[u + 1] = source.getShort(uv + Short.BYTES);
      }
      if (hasNormal) {
        int n = cursor + normalOffset;
        normals[target] = source.get(n);
        normals[target + 1] = source.get(n + 1);
        normals[target + 2] = source.get(n + 2);
      }
    }
    int[] indices;
    ByteBuffer explicit = mesh.indexBuffer();
    if (explicit != null)
      indices = decodeIndices(explicit, state.indexType(), state.indexCount(), vertexCount);
    else indices = Indices.triangles(state.mode(), vertexCount);
    if (!valid(indices, vertexCount)) return null;
    return new Batch(
        positions,
        colors,
        uv0,
        hasUv0,
        uv1,
        hasUv1,
        uv2,
        hasUv2,
        normals,
        hasNormal,
        indices,
        vertexCount,
        state.mode());
  }

  public static int[] decodeIndices(
      ByteBuffer source, VertexFormat.IndexType type, int indexCount, int vertexCount) {
    return decodeIndices(source, type, indexCount, vertexCount, ByteOrder.nativeOrder());
  }

  public static int[] decodeIndexUpdate(
      ByteBuffer source, VertexFormat.IndexType expectedType, int indexCount, int vertexCount) {
    if (source == null || indexCount <= 0 || vertexCount <= 0) return null;
    ByteBuffer data = source.duplicate();
    int remaining = data.remaining();
    long shortBytes = (long) indexCount * Short.BYTES;
    long intBytes = (long) indexCount * Integer.BYTES;
    if (remaining == shortBytes) {
      int[] decoded =
          decodeWithCommonOrders(data, VertexFormat.IndexType.SHORT, indexCount, vertexCount);
      if (decoded != null) return decoded;
    }
    if (remaining == intBytes) {
      int[] decoded =
          decodeWithCommonOrders(data, VertexFormat.IndexType.INT, indexCount, vertexCount);
      if (decoded != null) return decoded;
    }
    if (expectedType != null) {
      int bytesPerIndex =
          expectedType == VertexFormat.IndexType.SHORT ? Short.BYTES : Integer.BYTES;
      if ((long) bytesPerIndex * indexCount <= remaining) {
        return decodeWithCommonOrders(data, expectedType, indexCount, vertexCount);
      }
    }
    return null;
  }

  private static int[] decodeWithCommonOrders(
      ByteBuffer source, VertexFormat.IndexType type, int indexCount, int vertexCount) {
    int[] nativeDecoded =
        decodeIndices(source, type, indexCount, vertexCount, ByteOrder.nativeOrder());
    if (nativeDecoded != null) return nativeDecoded;
    ByteOrder alternate =
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
            ? ByteOrder.BIG_ENDIAN
            : ByteOrder.LITTLE_ENDIAN;
    return decodeIndices(source, type, indexCount, vertexCount, alternate);
  }

  private static int[] decodeIndices(
      ByteBuffer source,
      VertexFormat.IndexType type,
      int indexCount,
      int vertexCount,
      ByteOrder order) {
    if (source == null || type == null || indexCount <= 0 || vertexCount <= 0) return null;
    ByteBuffer data = source.duplicate().order(order);
    int bytesPerIndex =
        switch (type) {
          case SHORT -> Short.BYTES;
          case INT -> Integer.BYTES;
        };
    long required = (long) bytesPerIndex * indexCount;
    if (required > data.remaining()) return null;
    int[] indices = new int[indexCount];
    int base = data.position();
    for (int i = 0; i < indexCount; i++) {
      indices[i] =
          switch (type) {
            case SHORT -> Short.toUnsignedInt(data.getShort(base + i * Short.BYTES));
            case INT -> data.getInt(base + i * Integer.BYTES);
          };
    }
    return valid(indices, vertexCount) ? indices : null;
  }

  private static boolean valid(
      VertexFormat format, VertexFormatElement element, int bytes, int stride) {
    if (!format.contains(element)) return false;
    int offset = format.getOffset(element);
    return offset >= 0 && offset + bytes <= stride;
  }

  private static boolean valid(int[] indices, int vertexCount) {
    if (indices == null || indices.length == 0 || indices.length % 3 != 0) return false;
    for (int index : indices) if (index < 0 || index >= vertexCount) return false;
    return true;
  }
}
