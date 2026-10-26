package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Encoder {
  private static final float[] EMPTY_FLOATS = new float[0];
  private static final short[] EMPTY_SHORTS = new short[0];
  private static final byte[] EMPTY_BYTES = new byte[0];

  private Encoder() {}

  public static Batch encode(MeshData mesh) {
    return encode(mesh, null);
  }

  public static Batch encodeReusable(MeshData mesh, Scratch scratch) {
    if (scratch == null) throw new IllegalArgumentException("scratch is null");
    return encode(mesh, scratch);
  }

  private static Batch encode(MeshData mesh, Scratch scratch) {
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
    int positionCount = Math.multiplyExact(vertexCount, 3);
    int uvCount = Math.multiplyExact(vertexCount, 2);
    float[] positions =
        scratch == null ? new float[positionCount] : scratch.positions(positionCount);
    int[] colors = scratch == null ? new int[vertexCount] : scratch.colors(vertexCount);
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
    float[] uv0 =
        hasUv0 ? (scratch == null ? new float[uvCount] : scratch.uv0(uvCount)) : EMPTY_FLOATS;
    short[] uv1 =
        hasUv1 ? (scratch == null ? new short[uvCount] : scratch.uv1(uvCount)) : EMPTY_SHORTS;
    short[] uv2 =
        hasUv2 ? (scratch == null ? new short[uvCount] : scratch.uv2(uvCount)) : EMPTY_SHORTS;
    byte[] normals =
        hasNormal
            ? (scratch == null ? new byte[positionCount] : scratch.normals(positionCount))
            : EMPTY_BYTES;
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
    ByteBuffer explicit = mesh.indexBuffer();
    int indexCount =
        explicit != null
            ? state.indexCount()
            : Indices.triangleIndexCount(state.mode(), vertexCount);
    if (!Indices.completePrimitives(state.mode(), indexCount)) return null;
    int[] indices = scratch == null ? new int[indexCount] : scratch.indices(indexCount);
    if (explicit != null) {
      if (!decodeIndicesInto(
          explicit, state.indexType(), indexCount, vertexCount, ByteOrder.nativeOrder(), indices))
        return null;
    } else {
      Indices.writeTriangles(state.mode(), vertexCount, indices);
    }
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
        indexCount,
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
    int[] indices = new int[indexCount];
    return decodeIndicesInto(source, type, indexCount, vertexCount, order, indices)
        ? indices
        : null;
  }

  private static boolean decodeIndicesInto(
      ByteBuffer source,
      VertexFormat.IndexType type,
      int indexCount,
      int vertexCount,
      ByteOrder order,
      int[] indices) {
    if (source == null
        || type == null
        || indexCount <= 0
        || vertexCount <= 0
        || indices == null
        || indices.length < indexCount) return false;
    ByteBuffer data = source.duplicate().order(order);
    int bytesPerIndex =
        switch (type) {
          case SHORT -> Short.BYTES;
          case INT -> Integer.BYTES;
        };
    long required = (long) bytesPerIndex * indexCount;
    if (required > data.remaining()) return false;
    int base = data.position();
    for (int i = 0; i < indexCount; i++) {
      indices[i] =
          switch (type) {
            case SHORT -> Short.toUnsignedInt(data.getShort(base + i * Short.BYTES));
            case INT -> data.getInt(base + i * Integer.BYTES);
          };
    }
    return valid(indices, indexCount, vertexCount);
  }

  private static boolean valid(
      VertexFormat format, VertexFormatElement element, int bytes, int stride) {
    if (!format.contains(element)) return false;
    int offset = format.getOffset(element);
    return offset >= 0 && offset + bytes <= stride;
  }

  private static boolean valid(int[] indices, int indexCount, int vertexCount) {
    if (indices == null || indexCount <= 0 || indexCount > indices.length || indexCount % 3 != 0)
      return false;
    for (int i = 0; i < indexCount; i++) {
      int index = indices[i];
      if (index < 0 || index >= vertexCount) return false;
    }
    return true;
  }

  public static final class Scratch {
    private static final int MAX_RETAINED_ELEMENTS =
        Math.max(65_536, Integer.getInteger("dx12mc.immediate.scratch.elements", 1_048_576));

    private float[] positions = EMPTY_FLOATS;
    private int[] colors = new int[0];
    private float[] uv0 = EMPTY_FLOATS;
    private short[] uv1 = EMPTY_SHORTS;
    private short[] uv2 = EMPTY_SHORTS;
    private byte[] normals = EMPTY_BYTES;
    private int[] indices = new int[0];

    private float[] positions(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new float[required];
      if (positions.length < required) positions = new float[grownCapacity(required)];
      return positions;
    }

    private int[] colors(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new int[required];
      if (colors.length < required) colors = new int[grownCapacity(required)];
      return colors;
    }

    private float[] uv0(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new float[required];
      if (uv0.length < required) uv0 = new float[grownCapacity(required)];
      return uv0;
    }

    private short[] uv1(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new short[required];
      if (uv1.length < required) uv1 = new short[grownCapacity(required)];
      return uv1;
    }

    private short[] uv2(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new short[required];
      if (uv2.length < required) uv2 = new short[grownCapacity(required)];
      return uv2;
    }

    private byte[] normals(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new byte[required];
      if (normals.length < required) normals = new byte[grownCapacity(required)];
      return normals;
    }

    private int[] indices(int required) {
      if (required > MAX_RETAINED_ELEMENTS) return new int[required];
      if (indices.length < required) indices = new int[grownCapacity(required)];
      return indices;
    }

    private static int grownCapacity(int required) {
      if (required <= 1) return required;
      int highest = Integer.highestOneBit(required - 1);
      return highest > Integer.MAX_VALUE / 2 ? required : highest << 1;
    }
  }
}
