package net.dx12mc.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdapterTest {

  @Test
  void rendererInfoDescribesTheDirect3D12AdapterForEveryGlUtilConsumer() {
    RendererInfo info = new Adapter(0x10DE, 0x1D01, "NVIDIA GeForce GT 1030").rendererInfo("0.9.2");
    assertEquals("NVIDIA", info.vendor());
    assertEquals("NVIDIA GeForce GT 1030", info.renderer());
    assertEquals("Direct3D 12 (DX12MC 0.9.2)", info.apiVersion());
    assertEquals(
        "NVIDIA GeForce GT 1030 Direct3D 12 (DX12MC 0.9.2), NVIDIA", info.apiDescription());
  }

  @Test
  void vendorNamesComeFromThePciVendorId() {
    assertEquals("AMD", new Adapter(0x1002, 1, "x").vendorName());
    assertEquals("Intel", new Adapter(0x8086, 1, "x").vendorName());
    assertEquals("Microsoft", new Adapter(0x1414, 0x8C, "x").vendorName());
    assertEquals("Vendor 0x1234", new Adapter(0x1234, 1, "x").vendorName());
  }

  @Test
  void missingDxgiDescriptionFallsBackToPciIdsInsteadOfAGlString() {
    assertEquals("NVIDIA device 0x1D01", new Adapter(0x10DE, 0x1D01, null).name());
    assertEquals("Intel device 0x3E92", new Adapter(0x8086, 0x3E92, "  ").name());
    assertEquals("Radeon RX 6600", new Adapter(0x1002, 0x73FF, " Radeon RX 6600 ").name());
  }
}
