package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Objects;

public record Batch(
    float[] positions,
    int[] colors,
    float[] uv0,
    boolean hasUv0,
    short[] uv1,
    boolean hasUv1,
    short[] uv2,
    boolean hasUv2,
    byte[] normals,
    boolean hasNormal,
    int[] indices,
    int indexCount,
    int vertexCount,
    VertexFormat.Mode sourceMode) {
  public Batch {
    Objects.requireNonNull(positions, "positions");
    Objects.requireNonNull(colors, "colors");
    Objects.requireNonNull(uv0, "uv0");
    Objects.requireNonNull(uv1, "uv1");
    Objects.requireNonNull(uv2, "uv2");
    Objects.requireNonNull(normals, "normals");
    Objects.requireNonNull(indices, "indices");
    Objects.requireNonNull(sourceMode, "sourceMode");
    if (vertexCount < 0) throw new IllegalArgumentException("vertexCount < 0");
    if (positions.length < vertexCount * 3)
      throw new IllegalArgumentException("position count does not match vertexCount");
    if (colors.length < vertexCount)
      throw new IllegalArgumentException("color count does not match vertexCount");
    if (uv0.length < (hasUv0 ? vertexCount * 2 : 0))
      throw new IllegalArgumentException("uv0 count does not match vertexCount/hasUv0");
    if (uv1.length < (hasUv1 ? vertexCount * 2 : 0))
      throw new IllegalArgumentException("uv1 count does not match vertexCount/hasUv1");
    if (uv2.length < (hasUv2 ? vertexCount * 2 : 0))
      throw new IllegalArgumentException("uv2 count does not match vertexCount/hasUv2");
    if (normals.length < (hasNormal ? vertexCount * 3 : 0))
      throw new IllegalArgumentException("normal count does not match vertexCount/hasNormal");
    if (indexCount <= 0 || indexCount > indices.length || indexCount % 3 != 0)
      throw new IllegalArgumentException("native pipeline requires triangle-list indices");
  }

  public Batch(
      float[] positions,
      int[] colors,
      float[] uv0,
      boolean hasUv0,
      short[] uv1,
      boolean hasUv1,
      short[] uv2,
      boolean hasUv2,
      byte[] normals,
      boolean hasNormal,
      int[] indices,
      int vertexCount,
      VertexFormat.Mode sourceMode) {
    this(
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
        indices == null ? 0 : indices.length,
        vertexCount,
        sourceMode);
  }

  public Batch withIndices(int[] replacement) {
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
        replacement,
        replacement.length,
        vertexCount,
        sourceMode);
  }

  public long cpuBytes() {
    return (long) vertexCount * 3 * Float.BYTES
        + (long) vertexCount * Integer.BYTES
        + (hasUv0 ? (long) vertexCount * 2 * Float.BYTES : 0L)
        + (hasUv1 ? (long) vertexCount * 2 * Short.BYTES : 0L)
        + (hasUv2 ? (long) vertexCount * 2 * Short.BYTES : 0L)
        + (hasNormal ? (long) vertexCount * 3 : 0L)
        + (long) indexCount * Integer.BYTES;
  }
}
