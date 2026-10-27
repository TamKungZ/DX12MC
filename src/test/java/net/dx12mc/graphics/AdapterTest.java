package net.dx12mc.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static long version(long a, long b, long c, long d) {
    return a << 48 | b << 32 | c << 16 | d;
  }

  @Test
  void nvidiaDriverVersionIsDecodedFromTheDxgiFourPartNumber() {
    Adapter adapter =
        new Adapter(0x10DE, 0x1D01, "NVIDIA GeForce GT 1030", version(32, 0, 15, 8278), 0xC100);
    assertEquals("582.78", adapter.driver());
    assertEquals("32.0.15.8278", adapter.rawDriverVersion());
    assertEquals("12_1", adapter.featureLevelName());
    assertEquals("FL 12_1, driver 582.78", adapter.details());
    assertEquals("FL 12_1, driver 582.78 (raw 32.0.15.8278)", adapter.logDetails());
    assertEquals(
        "Direct3D 12 (DX12MC 0.11.1), FL 12_1, driver 582.78",
        adapter.rendererInfo("0.11.1").apiVersion());
    assertEquals("552.22", new Adapter(0x10DE, 1, "x", version(31, 0, 15, 5222), 0).driver());
    assertEquals("610.05", new Adapter(0x10DE, 1, "x", version(31, 0, 16, 1005), 0).driver());
  }

  @Test
  void otherVendorsAndUnrecognisedNvidiaNumbersShowTheRawDottedVersion() {
    assertEquals(
        "31.0.101.5186", new Adapter(0x8086, 1, "x", version(31, 0, 101, 5186), 0).driver());
    assertEquals(
        "32.0.21013.1000", new Adapter(0x1002, 1, "x", version(32, 0, 21013, 1000), 0).driver());
    assertEquals(
        "27.21.14.12345", new Adapter(0x10DE, 1, "x", version(27, 21, 14, 12345), 0).driver());
    assertEquals("30.0.9.1", new Adapter(0x10DE, 1, "x", version(30, 0, 9, 1), 0).driver());
  }

  @Test
  void missingDriverOrFeatureLevelIsOmittedInsteadOfGuessed() {
    Adapter none = new Adapter(0x10DE, 0x1D01, "GPU");
    assertNull(none.driver());
    assertNull(none.featureLevelName());
    assertEquals("", none.details());
    assertEquals("Direct3D 12 (DX12MC 1.0.0)", none.rendererInfo("1.0.0").apiVersion());
    assertEquals("FL 12_0", new Adapter(0x10DE, 1, "x", 0L, 0xC000).details());
    assertEquals("driver 1.2.3.4", new Adapter(0x1414, 1, "x", version(1, 2, 3, 4), 0).details());
  }

  @Test
  void f3CrashReportAndLogReadTheSameAdapterDetails() throws IOException {
    Path java = Path.of("src", "main", "java", "net", "dx12mc");
    String mixin = Files.readString(java.resolve("mixin/GlUtilMixin.java"));
    assertTrue(mixin.contains("cir.setReturnValue(renderer.apiVersion())"));
    String lifecycle = Files.readString(java.resolve("render/RendererLifecycle.java"));
    assertTrue(lifecycle.contains("backend.adapter().rendererInfo(DX12MC.VERSION)"));
    assertTrue(lifecycle.contains("setApiDescription(rendererInfo.apiDescription())"));
    String adapter = Files.readString(java.resolve("graphics/Adapter.java"));
    assertTrue(adapter.contains("String details = details();"));
    String backend = Files.readString(java.resolve("graphics/bgfx/Backend.java"));
    assertTrue(backend.contains("adapter.logDetails()"));
    assertTrue(backend.contains("DxgiAdapters.query(vendorId, deviceId)"));
    for (String frame : new String[] {"render/Controller.java", "render/RendererLifecycle.java"}) {
      assertTrue(!Files.readString(java.resolve(frame)).contains("DxgiAdapters"), frame);
    }
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
