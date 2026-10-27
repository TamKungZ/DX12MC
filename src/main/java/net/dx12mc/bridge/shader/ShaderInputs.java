package net.dx12mc.bridge.shader;

public final class ShaderInputs {
  public static final float LIGHTMAP_NONE = 0.0f;
  public static final float LIGHTMAP_FILTERED = 1.0f;
  public static final float LIGHTMAP_FETCH = 2.0f;
  public static final float FOG_DISTANCE_SPHERE = 0.0f;
  public static final float FOG_DISTANCE_CYLINDER = 1.0f;
  public static final float FOG_DISTANCE_EYE_DEPTH = 2.0f;

  private ShaderInputs() {}

  public static void material(ShaderSemantics semantics, float[] target) {
    target[0] = semantics.alphaTest().threshold();
    target[1] = semantics.alphaTestsTexture() ? 1.0f : 0.0f;
    target[2] = semantics.alphaTest() == ShaderSemantics.AlphaTest.VERTEX_ALPHA ? 1.0f : 0.0f;
    target[3] = semantics.intensity() ? 1.0f : 0.0f;
  }

  public static void portal(ShaderSemantics semantics, float gameTime, float[] target) {
    target[0] = gameTime;
    target[1] = semantics.endPortalLayers();
    target[2] = 0.0f;
    target[3] = 0.0f;
  }

  public static void fog(
      ShaderSemantics semantics, float start, float end, int shapeIndex, float[] target) {
    target[0] = start;
    target[1] = end;
    target[2] =
        switch (semantics.fogSpace()) {
          case VIEW_DEPTH -> FOG_DISTANCE_EYE_DEPTH;
          case VIEW_SPHERE -> FOG_DISTANCE_SPHERE;
          default -> shapeIndex == 0 ? FOG_DISTANCE_SPHERE : FOG_DISTANCE_CYLINDER;
        };
    target[3] = semantics.fogMode().ordinal();
  }

  public static void light(
      ShaderSemantics semantics,
      boolean lightmapBound,
      boolean overlayBound,
      boolean diffuse,
      float[] target) {
    target[0] =
        !lightmapBound
            ? LIGHTMAP_NONE
            : semantics.lightmap() == ShaderSemantics.Lightmap.FILTERED
                ? LIGHTMAP_FILTERED
                : LIGHTMAP_FETCH;
    target[1] = overlayBound && semantics.overlay() ? 1.0f : 0.0f;
    target[2] = diffuse && semantics.diffuseLighting() ? 1.0f : 0.0f;
    target[3] = 0.0f;
  }

  public static boolean viewSpaceFog(ShaderSemantics semantics) {
    return semantics.fogSpace() != ShaderSemantics.FogSpace.POSITION;
  }
}
