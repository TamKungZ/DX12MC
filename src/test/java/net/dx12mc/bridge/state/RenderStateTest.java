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

    RenderState.Snapshot sky = state.skyCompatibilitySnapshot(1280, 720);
    assertSame(sky, state.skyCompatibilitySnapshot(1280, 720));

    state.blend(true);
    assertNotSame(standard, state.snapshot(1920, 1080));
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
  void polygonOffsetBecomesDepthBiasWhileUnsupportedRasterStatesStillFail() {
    RenderState state = new RenderState();
    state.polygonOffset(true);
    state.polygonOffset(1.0f, 1.0f);
    assertTrue(state.snapshot(854, 480).depthBias() > 0.0f);
    state.polygonOffset(false);
    assertEquals(0.0f, state.snapshot(854, 480).depthBias());
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
  void textHighlightOrReverseBecomesMaskThenInvertBlendPasses() {
    RenderState state = new RenderState();
    state.depthTest(false);
    state.blend(true);
    state.blendFuncSeparate(0x0302, 0x0303, 0x0001, 0x0303);
    RenderState.Snapshot plain = state.snapshot(1920, 1080);
    assertEquals(0L, plain.logicInvertState());
    state.colorLogic(true);
    state.logicOp(0x150B);
    RenderState.Snapshot logic = state.snapshot(1920, 1080);
    assertNotSame(plain, logic);
    assertEquals(
        0x06141000L,
        logic.bgfxState() & 0x00000003FFFFF000L,
        "dst * (1 - src) with ADD; the mirrored GL blend is ignored like GL does under a logic op");
    assertEquals(
        0x0181A000L, logic.logicInvertState() & 0x00000003FFFFF000L, "white * (1 - dst) with ADD");
    assertEquals(0xFL, logic.bgfxState() & 0xFL);
    assertEquals(0xFL, logic.logicInvertState() & 0xFL);
    assertEquals(0L, logic.bgfxState() & 0xF0L);
    assertEquals(0L, logic.logicInvertState() & 0xF0L);
    state.colorLogic(false);
    assertEquals(0L, state.snapshot(1920, 1080).logicInvertState());
  }

  @Test
  void depthWritingLogicDrawInvertsOnlyItsOwnFragments() {
    RenderState state = new RenderState();
    state.depthTest(true);
    state.depthFunc(0x0201);
    state.depthMask(true);
    state.colorLogic(true);
    state.logicOp(0x150B);
    RenderState.Snapshot logic = state.snapshot(1920, 1080);
    assertEquals(0x10L, logic.bgfxState() & 0xF0L);
    assertNotEquals(0L, logic.bgfxState() & 0x0000004000000000L);
    assertEquals(0x30L, logic.logicInvertState() & 0xF0L, "EQUAL against the depth just written");
    assertEquals(0L, logic.logicInvertState() & 0x0000004000000000L);
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
  void guiOverlayWithoutDepthTestNeitherTestsNorWritesDepth() {
    RenderState state = new RenderState();
    state.depthTest(false);
    state.depthMask(false);
    long bits = state.snapshot(1920, 1080).bgfxState();
    assertEquals(0L, bits & 0xF0L, "RenderType.guiOverlay uses NO_DEPTH_TEST");
    assertEquals(0L, bits & 0x0000004000000000L);
  }

  @Test
  void disabledBlendIsNotReplacedByAlphaBlending() {
    RenderState state = new RenderState();
    state.blend(false);
    state.blendFunc(0x0302, 0x0303);
    assertEquals(0L, state.snapshot(1920, 1080).bgfxState() & 0x00000003FFFFF000L);
  }

  @Test
  void crosshairInvertBlendReachesBgfxUnchanged() {
    RenderState state = new RenderState();
    state.blend(true);
    state.blendFuncSeparate(0x0307, 0x0301, 0x0001, 0x0000);
    long blend = state.snapshot(1920, 1080).bgfxState() & 0x000000000FFFF000L;
    assertEquals(0xA000L, blend & 0xF000L, "source rgb ONE_MINUS_DST_COLOR");
    assertEquals(0x40000L, blend & 0xF0000L, "destination rgb ONE_MINUS_SRC_COLOR");
    assertEquals(0x200000L, blend & 0xF00000L, "source alpha ONE");
    assertEquals(0x1000000L, blend & 0xF000000L, "destination alpha ZERO");
  }

  @Test
  void cullStateOfImmediateDrawsIsHonored() {
    RenderState state = new RenderState();
    state.cull(false);
    assertEquals(0L, state.snapshot(1920, 1080).bgfxState() & 0x0000003000000000L);
    state.cull(true);
    assertNotEquals(0L, state.snapshot(1920, 1080).bgfxState() & 0x0000003000000000L);
  }

  @Test
  void scissorIsHonored() {
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
