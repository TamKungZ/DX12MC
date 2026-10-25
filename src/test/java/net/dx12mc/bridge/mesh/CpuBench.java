package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.dx12mc.graphics.bgfx.Packing;
import org.lwjgl.system.MemoryUtil;

public final class CpuBench {
  private static final int VERTICES = 2000;
  private static final int ROUNDS = 4000;

  private CpuBench() {}

  public static void main(String[] args) {
    ByteBuffer target = MemoryUtil.memAlloc(VERTICES * 4 * 32 + 64);
    MeshData mesh = meshOnce();
    Batch batch = Encoder.encode(mesh);
    for (int pass = 0; pass < 3; pass++) {
      System.out.println("pass " + pass);
      bench("Encoder.encode (BLOCK, " + VERTICES + " verts)", mesh, null, target, 0);
      bench("Packing.vertices current", null, batch, target, 1);
      bench("Packing.vertices legacy", null, batch, target, 2);
      bench("Packing.indices 16-bit", null, batch, target, 3);
      bench("Assembler 4 members", null, batch, target, 4);
    }
    mesh.close();
    MemoryUtil.memFree(target);
  }

  private static void bench(
      String name, MeshData source, Batch batch, ByteBuffer target, int kind) {
    long best = Long.MAX_VALUE;
    for (int repeat = 0; repeat < 5; repeat++) {
      long start = System.nanoTime();
      long sink = 0;
      for (int i = 0; i < ROUNDS; i++) {
        switch (kind) {
          case 0 -> {
            sink += Encoder.encode(source).vertexCount();
          }
          case 1 -> {
            Packing.vertices(target, batch);
            sink += target.get(5);
          }
          case 2 -> {
            legacyVertices(target, batch);
            sink += target.get(5);
          }
          case 3 -> {
            Packing.indices(target, batch.indices(), batch.indexCount(), false);
            sink += target.get(3);
          }
          case 4 -> {
            sink += assemble(batch).vertexCount();
          }
          default -> throw new IllegalStateException();
        }
      }
      long elapsed = System.nanoTime() - start;
      best = Math.min(best, elapsed);
      if (sink == 42) System.out.print("");
    }
    System.out.printf("  %-45s %8.1f us/op%n", name, best / 1000.0 / ROUNDS);
  }

  private static final SectionRegionBatcher.Assembler ASSEMBLER =
      new SectionRegionBatcher.Assembler();

  private static Batch assemble(Batch batch) {
    List<SectionRegionBatcher.Member> members = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      members.add(
          new SectionRegionBatcher.Member(new net.dx12mc.bridge.Slot(), batch, i * 16, 0, 0));
    }
    return ASSEMBLER.assemble(members, 0, members.size(), 0, 0, 0);
  }

  private static MeshData meshOnce() {
    ByteBufferBuilder buffer = new ByteBufferBuilder(VERTICES * 40);
    BufferBuilder builder =
        new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
    for (int q = 0; q < VERTICES / 4; q++) {
      float x = q % 16;
      float z = q / 16 % 16;
      float y = q / 256;
      for (int c = 0; c < 4; c++) {
        builder
            .addVertex(x + (c == 1 || c == 2 ? 1f : 0f), y, z + (c >= 2 ? 1f : 0f))
            .setColor(255, 255, 255, 255)
            .setUv(0.25f * c, 0.5f)
            .setLight(0xF000F0)
            .setNormal(0f, 1f, 0f);
      }
    }
    return builder.buildOrThrow();
  }

  private static void legacyVertices(ByteBuffer target, Batch batch) {
    target.order(ByteOrder.nativeOrder());
    float[] positions = batch.positions();
    int[] colors = batch.colors();
    float[] uv0 = batch.uv0();
    short[] uv2 = batch.uv2();
    for (int vertex = 0; vertex < batch.vertexCount(); vertex++) {
      int p = vertex * 3;
      int uv = vertex * 2;
      int dst = vertex * Packing.VERTEX_STRIDE;
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
}
