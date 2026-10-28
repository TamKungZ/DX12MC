package net.dx12mc.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CompiledPackTest {
  @Test
  void everyPackBuiltByTheToolPassesValidation() {
    for (String pack : List.of("tint", "gbuffer", "depth", "pass", "material", "sample-pack")) {
      PackPlan plan = PackDirectory.plan(pack);
      assertFalse(plan.programs().isEmpty(), pack);
    }
  }

  @Test
  void theSampleUsesMaterialSlotsAndOnlyTheUniformsItReads() {
    PackPlan.Program lit = PackDirectory.plan("sample-pack").programs().get("lit");
    assertEquals(ShaderContract.Family.BLOCK, lit.family());
    assertEquals(3, lit.samplers().get("s_normals"));
    assertEquals(4, lit.samplers().get("s_specular"));
    assertTrue(lit.uniforms().contains("u_cameraInvViewProj"));
    assertTrue(lit.uniforms().contains("u_light"));
    assertFalse(lit.uniforms().contains("u_cameraPrevViewProj"));
    assertNull(lit.fragment().uniform("u_tint"));
  }

  @Test
  void passProgramsLinkToTheBuiltInFullscreenVertexShader() {
    PackPlan plan = PackDirectory.plan("pass");
    PackPlan.Program gain = plan.programs().get("gain");
    assertEquals(ShaderContract.Family.PASS, gain.family());
    assertEquals(PackSet.passVertexBinary(), gain.vertex());
    assertTrue(gain.uniforms().contains("u_targetSize"));
    assertTrue(plan.needsScene());
  }
}
