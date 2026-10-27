package net.dx12mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class DrawContextFogTest {
  private static final Matrix4f MODEL_VIEW =
      new Matrix4f()
          .scale(12.0f, 1.0f, 12.0f)
          .rotateX(0.4f)
          .rotateY(1.1f)
          .translate(3.0f, -2.0f, 5.0f);
  private static final Matrix3f INVERSE_VIEW_ROTATION = new Matrix3f().rotateY(0.9f).rotateX(-0.3f);

  @Test
  void eachShaderFamilyGetsTheFogPositionItsVanillaVertexShaderComputes() {
    Matrix4f scratch = new Matrix4f();
    for (String terrain :
        new String[] {
          "rendertype_solid", "rendertype_cutout", "rendertype_translucent", "particle"
        }) {
      ShaderSemantics semantics = ShaderSemantics.of(terrain);
      assertNull(
          DrawContextFactory.fogModelTransform(
              semantics, MODEL_VIEW, INVERSE_VIEW_ROTATION, scratch),
          terrain);
      assertTrue(DrawContextFactory.fogAppliesModelView(semantics), terrain);
    }
    for (String entity :
        new String[] {
          "rendertype_entity_solid", "rendertype_text", "rendertype_glint", "rendertype_leash"
        }) {
      ShaderSemantics semantics = ShaderSemantics.of(entity);
      Matrix4f rotation =
          DrawContextFactory.fogModelTransform(
              semantics, MODEL_VIEW, INVERSE_VIEW_ROTATION, scratch);
      assertSame(scratch, rotation, entity);
      assertTrue(DrawContextFactory.fogAppliesModelView(semantics), entity);
    }
    ShaderSemantics emissive = ShaderSemantics.of("rendertype_entity_translucent_emissive");
    assertSame(
        MODEL_VIEW,
        DrawContextFactory.fogModelTransform(emissive, MODEL_VIEW, INVERSE_VIEW_ROTATION, scratch));
    assertFalse(DrawContextFactory.fogAppliesModelView(emissive));
    assertFalse(DrawContextFactory.fogAppliesModelView(ShaderSemantics.of("position_color")));
  }

  @Test
  void shaderDistanceEqualsTheVanillaFogDistanceForEveryFogShapeAndFamily() {
    Vector3f[] positions = {
      new Vector3f(10.0f, 4.0f, -7.0f),
      new Vector3f(-33.5f, 12.25f, 90.0f),
      new Vector3f(0.5f, -64.0f, 0.5f)
    };
    Matrix4f scratch = new Matrix4f();
    for (String program :
        new String[] {
          "rendertype_solid",
          "particle",
          "position_tex_color_normal",
          "rendertype_lines",
          "rendertype_entity_solid",
          "rendertype_eyes",
          "rendertype_lightning"
        }) {
      ShaderSemantics semantics = ShaderSemantics.of(program);
      boolean rotated =
          semantics.fogSpace() == ShaderSemantics.FogSpace.MODEL_VIEW_INVERSE_ROTATION;
      for (int shape = 0; shape <= 1; shape++) {
        for (Vector3f position : positions) {
          Vector3f fogInput = new Vector3f(position);
          Matrix4f pre =
              DrawContextFactory.fogModelTransform(
                  semantics, MODEL_VIEW, INVERSE_VIEW_ROTATION, scratch);
          if (pre != null) pre.transformPosition(fogInput);
          Matrix4f view =
              DrawContextFactory.fogAppliesModelView(semantics) ? MODEL_VIEW : new Matrix4f();
          float actual = shaderFogDistance(fogInput, view, shape);
          Vector3f vanillaInput = new Vector3f(position);
          if (rotated) INVERSE_VIEW_ROTATION.transform(vanillaInput);
          float expected = vanillaFogDistance(MODEL_VIEW, vanillaInput, shape);
          assertEquals(expected, actual, 1.0e-3f, program + " shape " + shape);
        }
      }
    }
  }

  @Test
  void fogShapeIndexIsTheShaderDistanceModeForEveryShapeIndependentProgram() {
    float[] fog = new float[4];
    ShaderInputs.fog(ShaderSemantics.of("rendertype_entity_solid"), 0, 100, 0, fog);
    assertEquals(ShaderInputs.FOG_DISTANCE_SPHERE, fog[2]);
    ShaderInputs.fog(ShaderSemantics.of("rendertype_entity_solid"), 0, 100, 1, fog);
    assertEquals(ShaderInputs.FOG_DISTANCE_CYLINDER, fog[2]);
  }

  private static float vanillaFogDistance(Matrix4f modelView, Vector3f pos, int shape) {
    if (shape == 0) return transform(modelView, pos.x, pos.y, pos.z).length();
    float distXZ = transform(modelView, pos.x, 0.0f, pos.z).length();
    float distY = transform(modelView, 0.0f, pos.y, 0.0f).length();
    return Math.max(distXZ, distY);
  }

  private static float shaderFogDistance(Vector3f pos, Matrix4f view, int shape) {
    if (shape == 0) return transform(view, pos.x, pos.y, pos.z).length();
    float distXZ = transform(view, pos.x, 0.0f, pos.z).length();
    float distY = transform(view, 0.0f, pos.y, 0.0f).length();
    return Math.max(distXZ, distY);
  }

  private static Vector3f transform(Matrix4f matrix, float x, float y, float z) {
    Vector4f result = matrix.transform(new Vector4f(x, y, z, 1.0f));
    return new Vector3f(result.x, result.y, result.z);
  }
}
