package net.dx12mc.platform.win32;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DxgiAdaptersTest {

  @Test
  void readsDescriptionAndPciIdsFromDxgiAdapterDesc1Layout() {
    ByteBuffer desc = ByteBuffer.allocate(DxgiAdapters.DESC1_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    byte[] name = "NVIDIA GeForce GT 1030".getBytes(StandardCharsets.UTF_16LE);
    desc.put(0, name);
    desc.putInt(256, 0x10DE);
    desc.putInt(260, 0x1D01);
    desc.putInt(264, 0xDEADBEEF);

    assertEquals("NVIDIA GeForce GT 1030", DxgiAdapters.name(desc));
    assertEquals(0x10DE, DxgiAdapters.vendorId(desc));
    assertEquals(0x1D01, DxgiAdapters.deviceId(desc));
  }

  @Test
  void descriptionWithoutTerminatorUsesAll128Characters() {
    ByteBuffer desc = ByteBuffer.allocate(DxgiAdapters.DESC1_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    byte[] name = "A".repeat(128).getBytes(StandardCharsets.UTF_16LE);
    desc.put(0, name);
    desc.putInt(256, 0x10DE);
    assertEquals(128, DxgiAdapters.name(desc).length());
  }
}
