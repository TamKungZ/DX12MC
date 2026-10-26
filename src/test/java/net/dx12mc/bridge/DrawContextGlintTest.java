package net.dx12mc.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.texture.Registry;
import org.junit.jupiter.api.Test;

class DrawContextGlintTest {

  @Test
  void everyVanillaShaderDeclaringGlintAlphaIsRecognised() {
    for (String shader :
        new String[] {
          "rendertype_glint",
          "rendertype_glint_translucent",
          "rendertype_entity_glint",
          "rendertype_entity_glint_direct",
          "rendertype_armor_entity_glint"
        }) {
      assertTrue(ShaderSemantics.of(shader).glintAlpha(), shader);
    }
    assertFalse(ShaderSemantics.of("rendertype_entity_cutout").glintAlpha());
    assertFalse(ShaderSemantics.of(null).glintAlpha());
  }

  @Test
  void glintAlphaScalesColorButNotAlphaLikeTheVanillaGlintShaders() {
    DrawContextFactory factory = new DrawContextFactory(new Registry(new Stats()));
    float[] tint = factory.context().tint();
    tint[0] = 1.0f;
    tint[1] = 0.5f;
    tint[2] = 0.25f;
    tint[3] = 1.0f;
    factory.applyGlintAlpha(0.75f);
    assertArrayEquals(new float[] {0.75f, 0.375f, 0.1875f, 1.0f}, tint);
  }
}
