package net.dx12mc.bridge.shader;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShaderInputsTest {

  @Test
  void terrainFogUsesVanillaStartEndShapeAndLinearMode() {
    float[] fog = new float[4];
    ShaderInputs.fog(ShaderSemantics.of("rendertype_solid"), 160.0f, 192.0f, 1, fog);
    assertArrayEquals(new float[] {160.0f, 192.0f, 1.0f, 1.0f}, fog);
  }

  @Test
  void emissiveAndBeaconFogIgnoreFogShapeLikeTheirVanillaShaders() {
    float[] fog = new float[4];
    ShaderInputs.fog(ShaderSemantics.of("rendertype_entity_translucent_emissive"), 0, 10, 1, fog);
    assertEquals(ShaderInputs.FOG_DISTANCE_SPHERE, fog[2]);
    assertEquals(ShaderSemantics.FogMode.FADE.ordinal(), fog[3]);
    ShaderInputs.fog(ShaderSemantics.of("rendertype_beacon_beam"), 0, 10, 0, fog);
    assertEquals(ShaderInputs.FOG_DISTANCE_EYE_DEPTH, fog[2]);
  }

  @Test
  void guiProgramsHaveNoFogAndDiscardOnlyTransparentTexels() {
    float[] fog = new float[4];
    float[] material = new float[4];
    ShaderSemantics gui = ShaderSemantics.of("position_tex");
    ShaderInputs.fog(gui, 0, 10, 0, fog);
    ShaderInputs.material(gui, material);
    assertEquals(0.0f, fog[3]);
    assertTrue(material[0] > 0.0f && material[0] < 1.0f / 255.0f);
    assertEquals(1.0f, material[1], "position_tex tests the texture alpha before ColorModulator");
  }

  @Test
  void intensityTextUsesTheRedChannelAsAlpha() {
    float[] material = new float[4];
    ShaderInputs.material(ShaderSemantics.of("rendertype_text_intensity"), material);
    assertEquals(1.0f, material[3]);
    ShaderInputs.material(ShaderSemantics.of("rendertype_text"), material);
    assertEquals(0.0f, material[3]);
  }

  @Test
  void terrainSamplesTheFilteredLightmapAndEntitiesFetchTexelsWithDiffuseAndOverlay() {
    float[] light = new float[4];
    ShaderInputs.light(ShaderSemantics.of("rendertype_solid"), true, false, false, light);
    assertEquals(ShaderInputs.LIGHTMAP_FILTERED, light[0]);
    ShaderInputs.light(ShaderSemantics.of("rendertype_entity_cutout"), true, true, true, light);
    assertArrayEquals(new float[] {ShaderInputs.LIGHTMAP_FETCH, 1.0f, 1.0f, 0.0f}, light);
    ShaderInputs.light(ShaderSemantics.of("rendertype_entity_cutout"), false, false, false, light);
    assertArrayEquals(new float[] {ShaderInputs.LIGHTMAP_NONE, 0.0f, 0.0f, 0.0f}, light);
  }
}
