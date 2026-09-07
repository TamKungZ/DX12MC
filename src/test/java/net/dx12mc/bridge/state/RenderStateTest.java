package net.dx12mc.bridge.state;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RenderStateTest {

  @Test
  void snapshotsAreReusedUntilStateOrFramebufferChanges() {
    RenderState state = new RenderState();

    RenderState.Snapshot standard = state.snapshot(1920, 1080);
    assertSame(standard, state.snapshot(1920, 1080));
    assertNotSame(standard, state.snapshot(1280, 720));

    RenderState.Snapshot immediate = state.immediateCompatibilitySnapshot(1280, 720);
    assertSame(immediate, state.immediateCompatibilitySnapshot(1280, 720));

    RenderState.Snapshot sky = state.skyCompatibilitySnapshot(1280, 720);
    assertSame(sky, state.skyCompatibilitySnapshot(1280, 720));

    state.blend(true);
    assertNotSame(standard, state.snapshot(1920, 1080));
    assertNotSame(immediate, state.immediateCompatibilitySnapshot(1280, 720));
    assertNotSame(sky, state.skyCompatibilitySnapshot(1280, 720));
  }

  @Test
  void disabledDepthTestDoesNotWriteDepthEvenWhenDepthMaskIsTrue() {
    RenderState state = new RenderState();
    state.depthTest(false);
    state.depthMask(true);
    RenderState.Snapshot snapshot = state.snapshot(854, 480);
    assertEquals(0xFL, snapshot.bgfxState() & 0xFL, "RGBA writes should be enabled");
    assertEquals(
        0L,
        snapshot.bgfxState() & 0x0000004000000000L,
        "disabled depth test must also suppress bgfx depth writes");
    assertEquals(
        0L, snapshot.bgfxState() & 0xF0L, "disabled depth test must emit no bgfx depth function");
    assertEquals(0L, snapshot.bgfxState() & 0x000000000FFFF000L, "blend should remain disabled");
    assertEquals(0L, snapshot.bgfxState() & 0x0000003000000000L, "cull should remain disabled");
  }

  @Test
  void depthBlendCullAndColorMaskProduceBgfxState() {
    RenderState state = new RenderState();
    state.depthTest(true);
    state.depthFunc(0x0203);
    state.depthMask(true);
    state.blend(true);
    state.blendFunc(0x0302, 0x0303);
    state.cull(true);
    state.colorMask(true, false, true, false);
    long bits = state.snapshot(854, 480).bgfxState();
    assertEquals(0x20L, bits & 0xF0L);
    assertNotEquals(0L, bits & 0x0000004000000000L);
    assertEquals(0x06565000L, bits & 0x000000000FFFF000L);
    assertEquals(0x0000001000000000L, bits & 0x0000003000000000L);
    assertEquals(0x5L, bits & 0xFL);
  }

  @Test
  void oneBlendEquationIsAppliedToRgbAndAlphaLikeBgfxMacro() {
    RenderState state = new RenderState();
    state.blend(true);
    state.blendEquation(0x800A);
    long bits = state.snapshot(854, 480).bgfxState();
    assertEquals(0x0000000090000000L, bits & 0x00000003F0000000L);
  }

  @Test
  void scissorConvertsAndClipsGlBottomLeftToBgfxTopLeft() {
    RenderState state = new RenderState();
    state.scissor(true);
    state.scissorBox(10, 20, 100, 40);
    RenderState.Snapshot snapshot = state.snapshot(854, 480);
    assertTrue(snapshot.scissorEnabled());
    assertFalse(snapshot.scissorEmpty());
    assertEquals(10, snapshot.scissorX());
    assertEquals(420, snapshot.scissorY());
    assertEquals(100, snapshot.scissorWidth());
    assertEquals(40, snapshot.scissorHeight());
    state.scissorBox(-10, 470, 30, 30);
    snapshot = state.snapshot(854, 480);
    assertEquals(0, snapshot.scissorX());
    assertEquals(0, snapshot.scissorY());
    assertEquals(20, snapshot.scissorWidth());
    assertEquals(10, snapshot.scissorHeight());
  }

  @Test
  void unsupportedBlendFactorFailsAtDrawSnapshotNotAtStateMutation() {
    RenderState state = new RenderState();
    state.blend(true);
    state.blendFunc(0x8001, 0x8002);
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> state.snapshot(854, 480));
    assertTrue(failure.getMessage().contains("blend factor"));
  }

  @Test
  void unsupportedRasterStatesFailExplicitlyInsteadOfRenderingDifferently() {
    RenderState state = new RenderState();
    state.polygonOffset(true);
    state.polygonOffset(1.0f, 1.0f);
    assertTrue(
        assertThrows(IllegalStateException.class, () -> state.snapshot(854, 480))
            .getMessage()
            .contains("polygon offset"));
    state.polygonOffset(false);
    state.polygonMode(0x0408, 0x1B01);
    assertTrue(
        assertThrows(IllegalStateException.class, () -> state.snapshot(854, 480))
            .getMessage()
            .contains("polygon mode"));
    state.polygonMode(0x0408, 0x1B02);
    state.colorLogic(true);
    state.logicOp(0x1506);
    assertTrue(
        assertThrows(IllegalStateException.class, () -> state.snapshot(854, 480))
            .getMessage()
            .contains("color logic"));
  }

  @Test
  void initialMirrorMatchesMinecraftPostBootstrapDepthState() {
    RenderState state = new RenderState();
    RenderState.Snapshot snapshot = state.snapshot(1920, 1080);
    assertTrue(snapshot.depthTest());
    assertTrue(snapshot.depthWrite());
    assertEquals(
        0x20L,
        snapshot.bgfxState() & 0xF0L,
        "initial depth function must be LEQUAL, not raw OpenGL LESS");
  }

  @Test
  void immediateCompatibilityStateMatchesKnownGood0512Pipeline() {
    RenderState state = new RenderState();
    state.depthTest(false);
    state.depthMask(false);
    state.blend(false);
    state.cull(true);
    state.colorMask(false, false, false, false);
    long bits = state.immediateCompatibilitySnapshot(1920, 1080).bgfxState();
    assertEquals(0xFL, bits & 0xFL);
    assertEquals(0x20L, bits & 0xF0L);
    assertNotEquals(0L, bits & 0x0000004000000000L);
    assertEquals(0x06565000L, bits & 0x000000000FFFF000L);
    assertEquals(0L, bits & 0x0000003000000000L);
  }

  @Test
  void immediateCompatibilityStillHonorsScissor() {
    RenderState state = new RenderState();
    state.scissor(true);
    state.scissorBox(10, 20, 100, 40);
    RenderState.Snapshot snapshot = state.immediateCompatibilitySnapshot(854, 480);
    assertTrue(snapshot.scissorEnabled());
    assertFalse(snapshot.scissorEmpty());
    assertEquals(10, snapshot.scissorX());
    assertEquals(420, snapshot.scissorY());
    assertEquals(100, snapshot.scissorWidth());
    assertEquals(40, snapshot.scissorHeight());
  }

  @Test
  void immediateCompatibilityHonorsExplicitSpecialBlendModesForWorldOverlays() {
    RenderState state = new RenderState();
    state.blend(true);
    state.blendFunc(0x0000, 0x0301);
    long liveBlend = state.snapshot(1920, 1080).bgfxState() & 0x00000003FFFFF000L;
    long immediateBlend =
        state.immediateCompatibilitySnapshot(1920, 1080).bgfxState() & 0x00000003FFFFF000L;
    assertEquals(
        liveBlend,
        immediateBlend,
        "immediate compatibility must preserve an explicitly enabled special blend mode");
    assertNotEquals(
        0x0000000006565000L,
        immediateBlend,
        "vignette-style multiply must not be forced back to ordinary alpha blend");
  }

  @Test
  void persistentWorldCullDoesNotForceOpenGlFrontCcwOnD3D12Viewport() {
    RenderState state = new RenderState();
    state.cull(true);
    long bits = state.snapshot(1920, 1080).bgfxState();
    assertEquals(0x0000001000000000L, bits & 0x0000003000000000L);
    assertEquals(
        0L,
        bits & 0x0000008000000000L,
        "forcing FRONT_CCW in D3D12 inverts Minecraft section faces after viewport Y transform");
  }
}
