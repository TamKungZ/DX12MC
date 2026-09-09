package net.dx12mc.graphics.bgfx;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.dx12mc.bridge.mesh.Batch;
import org.junit.jupiter.api.Test;

class PackingTest {

  @Test
  void packsOnlyShaderConsumedAttributesIntoThirtyTwoBytes() {
    Batch batch =
        new Batch(
            new float[] {1.0f, 2.0f, 3.0f},
            new int[] {0x11223344},
            new float[] {0.25f, 0.75f},
            true,
            new short[] {7, 9},
            true,
            new short[] {240, 128},
            true,
            new byte[] {0, 127, -127},
            true,
            new int[] {0, 0, 0},
            1,
            VertexFormat.Mode.TRIANGLES);
    ByteBuffer out = ByteBuffer.allocate(Packing.VERTEX_STRIDE).order(ByteOrder.nativeOrder());
    Packing.vertices(out, batch);
    assertEquals(32, Packing.VERTEX_STRIDE);
    assertEquals(1.0f, out.getFloat(0));
    assertEquals(2.0f, out.getFloat(4));
    assertEquals(3.0f, out.getFloat(8));
    assertEquals(0x11, Byte.toUnsignedInt(out.get(12)));
    assertEquals(0x22, Byte.toUnsignedInt(out.get(13)));
    assertEquals(0x33, Byte.toUnsignedInt(out.get(14)));
    assertEquals(0x44, Byte.toUnsignedInt(out.get(15)));
    assertEquals(0.25f, out.getFloat(16));
    assertEquals(0.75f, out.getFloat(20));
    assertEquals(240.0f, out.getFloat(24));
    assertEquals(128.0f, out.getFloat(28));
  }

  @Test
  void absentExtendedAttributesNeedNoRetainedCpuArrays() {
    Batch batch =
        new Batch(
            new float[] {1.0f, 2.0f, 3.0f},
            new int[] {0xFFFFFFFF},
            new float[0],
            false,
            new short[0],
            false,
            new short[0],
            false,
            new byte[0],
            false,
            new int[] {0, 0, 0},
            1,
            VertexFormat.Mode.TRIANGLES);
    ByteBuffer out = ByteBuffer.allocate(Packing.VERTEX_STRIDE).order(ByteOrder.nativeOrder());
    Packing.vertices(out, batch);
    assertEquals(28L, batch.cpuBytes());
    for (int offset = 16; offset < 32; offset += 4) assertEquals(0.0f, out.getFloat(offset));
  }

  @Test
  void selectsThirtyTwoBitIndicesOnlyWhenNeeded() {
    assertFalse(Packing.index32(new int[] {0, 1, 65535}));
    assertTrue(Packing.index32(new int[] {0, 65536}));
  }

  @Test
  void packsSixteenBitIndicesUnsigned() {
    ByteBuffer out = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder());
    Packing.indices(out, new int[] {0, 1, 65535}, false);
    assertEquals(0, Short.toUnsignedInt(out.getShort(0)));
    assertEquals(1, Short.toUnsignedInt(out.getShort(2)));
    assertEquals(65535, Short.toUnsignedInt(out.getShort(4)));
  }

  @Test
  void ignoresUnusedCapacityInReusableBatches() {
    Batch batch =
        new Batch(
            new float[12],
            new int[4],
            new float[0],
            false,
            new short[0],
            false,
            new short[0],
            false,
            new byte[0],
            false,
            new int[] {0, 0, 0, 65536, 65536, 65536},
            3,
            1,
            VertexFormat.Mode.TRIANGLES);
    ByteBuffer out = ByteBuffer.allocate(Packing.indexBytes(batch)).order(ByteOrder.nativeOrder());
    Packing.indices(out, batch.indices(), batch.indexCount(), Packing.index32(batch));
    assertEquals(6, Packing.indexBytes(batch));
    assertEquals(28L, batch.cpuBytes());
    assertEquals(0, Short.toUnsignedInt(out.getShort(4)));
  }
}
