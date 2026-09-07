package net.dx12mc.bridge.shader;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShaderFamilyTest {

  @Test
  void classifiesVanillaFamiliesWithoutTreatingGuiAsWorldLighting() {
    assertEquals(ShaderFamily.GUI, ShaderFamily.classify("position_tex"));
    assertEquals(ShaderFamily.GUI, ShaderFamily.classify("rendertype_gui"));
    assertEquals(ShaderFamily.TEXT, ShaderFamily.classify("rendertype_text"));
    assertEquals(ShaderFamily.TERRAIN, ShaderFamily.classify("rendertype_solid"));
    assertEquals(ShaderFamily.ENTITY, ShaderFamily.classify("rendertype_entity_translucent"));
    assertEquals(ShaderFamily.PARTICLE, ShaderFamily.classify("particle"));
    assertFalse(ShaderFamily.GUI.lightmapCandidate());
    assertTrue(ShaderFamily.TEXT.lightmapCandidate());
    assertTrue(ShaderFamily.ENTITY.lightmapCandidate());
  }
}
