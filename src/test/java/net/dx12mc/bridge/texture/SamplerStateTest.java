package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.Stats;
import org.junit.jupiter.api.Test;

class SamplerStateTest {

  @Test
  void lightmapLinearFilterFromTurnOnLightLayerIsNotPointSampled() {
    SamplerState sampler = new SamplerState();
    sampler.parameter(0x2801, 0x2600);
    sampler.parameter(0x2800, 0x2600);
    sampler.parameter(0x2801, 0x2601);
    sampler.parameter(0x2800, 0x2601);
    int flags = sampler.bgfxFlags();
    assertEquals(0, flags & (BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT));
  }

  @Test
  void atlasNearestMipmapLinearKeepsLinearMipBlendingAndClampsLodToMaxLevel() {
    SamplerState sampler = new SamplerState();
    sampler.parameter(0x813D, 4);
    sampler.parameter(0x813B, 4);
    sampler.parameter(0x2801, 0x2702);
    sampler.parameter(0x2800, 0x2600);
    int flags = sampler.bgfxFlags();
    assertEquals(BGFX_SAMPLER_MIN_POINT, flags & BGFX_SAMPLER_MIN_MASK);
    assertEquals(BGFX_SAMPLER_MAG_POINT, flags & BGFX_SAMPLER_MAG_MASK);
    assertEquals(0, flags & BGFX_SAMPLER_MIP_MASK, "GL_NEAREST_MIPMAP_LINEAR blends mip levels");
    assertEquals(4.0f, sampler.maxLod(10));
    assertEquals(2.0f, sampler.maxLod(2));
  }

  @Test
  void nonMipmappedMinFilterSamplesOnlyTheBaseLevel() {
    SamplerState sampler = new SamplerState();
    sampler.parameter(0x2801, 0x2600);
    assertEquals(0.0f, sampler.maxLod(4));
  }

  @Test
  void wrapModesMapToBgfx() {
    SamplerState sampler = new SamplerState();
    sampler.parameter(0x2802, 0x812F);
    sampler.parameter(0x2803, 0x8370);
    int flags = sampler.bgfxFlags();
    assertEquals(BGFX_SAMPLER_U_CLAMP, flags & BGFX_SAMPLER_U_MASK);
    assertEquals(BGFX_SAMPLER_V_MIRROR, flags & BGFX_SAMPLER_V_MASK);
  }

  @Test
  void parametersFollowTheBoundTextureAndSurviveReallocation() {
    Registry registry = new Registry(new Stats());
    registry.start();
    registry.allocate(5, 0, 16, 16);
    registry.bind(5);
    registry.texParameter(0x0DE1, 0x2801, 0x2601);
    long revision = registry.sampler(5).revision();
    registry.allocate(5, 0, 32, 32);
    assertEquals(revision, registry.sampler(5).revision());
    assertEquals(0, registry.sampler(5).bgfxFlags() & BGFX_SAMPLER_MIN_MASK);
    registry.texParameter(0x8513, 0x2801, 0x2600);
    assertEquals(revision, registry.sampler(5).revision(), "non-2D targets are ignored");
  }
}
