package net.dx12mc.bridge.shader;

import java.util.Locale;
import java.util.Map;

public record ShaderSemantics(
    AlphaTest alphaTest,
    boolean alphaTestsTexture,
    FogMode fogMode,
    FogSpace fogSpace,
    Lightmap lightmap,
    boolean diffuseLighting,
    boolean overlay,
    boolean intensity,
    boolean glintAlpha,
    boolean screenSpaceLines) {

  public enum AlphaTest {
    NONE(-1.0f),
    ZERO(0.5f / 255.0f),
    TENTH(0.1f),
    HALF(0.5f),
    VERTEX_ALPHA(0.0f);

    private final float threshold;

    AlphaTest(float threshold) {
      this.threshold = threshold;
    }

    public float threshold() {
      return threshold;
    }
  }

  public enum FogMode {
    NONE,
    LINEAR,
    FADE,
    FADE_RGB
  }

  public enum FogSpace {
    POSITION,
    VIEW,
    VIEW_SPHERE,
    VIEW_DEPTH
  }

  public enum Lightmap {
    NONE,
    FILTERED,
    FETCH
  }

  public boolean discards() {
    return alphaTest != AlphaTest.NONE;
  }

  private static final ShaderSemantics GUI_COLOR =
      new ShaderSemantics(
          AlphaTest.ZERO,
          false,
          FogMode.NONE,
          FogSpace.POSITION,
          Lightmap.NONE,
          false,
          false,
          false,
          false,
          false);
  private static final ShaderSemantics TERRAIN =
      new ShaderSemantics(
          AlphaTest.NONE,
          false,
          FogMode.LINEAR,
          FogSpace.POSITION,
          Lightmap.FILTERED,
          false,
          false,
          false,
          false,
          false);
  private static final ShaderSemantics TERRAIN_CUTOUT = TERRAIN.withAlpha(AlphaTest.TENTH, false);
  private static final ShaderSemantics ENTITY =
      new ShaderSemantics(
          AlphaTest.NONE,
          false,
          FogMode.LINEAR,
          FogSpace.POSITION,
          Lightmap.FETCH,
          true,
          true,
          false,
          false,
          false);
  private static final ShaderSemantics ENTITY_CUTOUT = ENTITY.withAlpha(AlphaTest.TENTH, true);
  private static final ShaderSemantics ENTITY_LIT_VERTEX_ALPHA =
      ENTITY.withAlpha(AlphaTest.TENTH, false).withOverlay(false);
  private static final ShaderSemantics GLINT =
      new ShaderSemantics(
          AlphaTest.TENTH,
          false,
          FogMode.FADE_RGB,
          FogSpace.POSITION,
          Lightmap.NONE,
          false,
          false,
          false,
          true,
          false);
  private static final ShaderSemantics TEXT =
      new ShaderSemantics(
          AlphaTest.TENTH,
          false,
          FogMode.LINEAR,
          FogSpace.POSITION,
          Lightmap.FETCH,
          false,
          false,
          false,
          false,
          false);
  private static final ShaderSemantics SEE_THROUGH =
      new ShaderSemantics(
          AlphaTest.TENTH,
          false,
          FogMode.NONE,
          FogSpace.POSITION,
          Lightmap.NONE,
          false,
          false,
          false,
          false,
          false);
  private static final ShaderSemantics PLAIN =
      new ShaderSemantics(
          AlphaTest.NONE,
          false,
          FogMode.NONE,
          FogSpace.POSITION,
          Lightmap.NONE,
          false,
          false,
          false,
          false,
          false);
  private static final ShaderSemantics FADE =
      new ShaderSemantics(
          AlphaTest.NONE,
          false,
          FogMode.FADE,
          FogSpace.POSITION,
          Lightmap.NONE,
          false,
          false,
          false,
          false,
          false);

  static final Map<String, ShaderSemantics> VANILLA =
      Map.ofEntries(
          Map.entry("blit_screen", PLAIN),
          Map.entry("particle", TEXT),
          Map.entry("position", PLAIN.withFog(FogMode.LINEAR)),
          Map.entry("position_color", GUI_COLOR),
          Map.entry("position_color_lightmap", PLAIN),
          Map.entry("position_color_tex_lightmap", SEE_THROUGH),
          Map.entry("position_tex", GUI_COLOR.withAlpha(AlphaTest.ZERO, true)),
          Map.entry("position_tex_color", SEE_THROUGH),
          Map.entry("rendertype_armor_cutout_no_cull", ENTITY_LIT_VERTEX_ALPHA),
          Map.entry("rendertype_armor_entity_glint", GLINT),
          Map.entry("rendertype_beacon_beam", PLAIN.withFog(FogMode.LINEAR, FogSpace.VIEW_DEPTH)),
          Map.entry("rendertype_breeze_wind", TEXT.withFog(FogMode.FADE)),
          Map.entry("rendertype_clouds", SEE_THROUGH.withFog(FogMode.LINEAR, FogSpace.VIEW)),
          Map.entry("rendertype_crumbling", SEE_THROUGH),
          Map.entry("rendertype_cutout", TERRAIN_CUTOUT),
          Map.entry("rendertype_cutout_mipped", TERRAIN.withAlpha(AlphaTest.HALF, false)),
          Map.entry("rendertype_end_portal", PLAIN),
          Map.entry("rendertype_energy_swirl", SEE_THROUGH.withFog(FogMode.FADE)),
          Map.entry("rendertype_entity_alpha", PLAIN.withAlpha(AlphaTest.VERTEX_ALPHA, true)),
          Map.entry("rendertype_entity_cutout", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_cutout_no_cull", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_cutout_no_cull_z_offset", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_decal", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_glint", GLINT),
          Map.entry("rendertype_entity_glint_direct", GLINT),
          Map.entry("rendertype_entity_no_outline", ENTITY.withOverlay(false)),
          Map.entry("rendertype_entity_shadow", PLAIN.withFog(FogMode.LINEAR)),
          Map.entry("rendertype_entity_smooth_cutout", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_solid", ENTITY),
          Map.entry("rendertype_entity_translucent", ENTITY_CUTOUT),
          Map.entry("rendertype_entity_translucent_cull", ENTITY_LIT_VERTEX_ALPHA),
          Map.entry(
              "rendertype_entity_translucent_emissive",
              ENTITY_CUTOUT
                  .withFog(FogMode.FADE, FogSpace.VIEW_SPHERE)
                  .withLightmap(Lightmap.NONE)),
          Map.entry("rendertype_eyes", FADE),
          Map.entry("rendertype_glint", GLINT),
          Map.entry("rendertype_glint_translucent", GLINT),
          Map.entry("rendertype_gui", GUI_COLOR),
          Map.entry("rendertype_gui_ghost_recipe_overlay", GUI_COLOR),
          Map.entry("rendertype_gui_overlay", GUI_COLOR),
          Map.entry("rendertype_gui_text_highlight", GUI_COLOR),
          Map.entry("rendertype_item_entity_translucent_cull", ENTITY_LIT_VERTEX_ALPHA),
          Map.entry("rendertype_leash", PLAIN.withFog(FogMode.LINEAR).withLightmap(Lightmap.FETCH)),
          Map.entry("rendertype_lightning", FADE),
          Map.entry("rendertype_lines", PLAIN.withFog(FogMode.LINEAR).withScreenSpaceLines()),
          Map.entry("rendertype_outline", GUI_COLOR.withAlpha(AlphaTest.ZERO, true)),
          Map.entry("rendertype_solid", TERRAIN),
          Map.entry("rendertype_text", TEXT),
          Map.entry("rendertype_text_background", TEXT),
          Map.entry("rendertype_text_background_see_through", SEE_THROUGH),
          Map.entry("rendertype_text_intensity", TEXT.withIntensity()),
          Map.entry("rendertype_text_intensity_see_through", SEE_THROUGH.withIntensity()),
          Map.entry("rendertype_text_see_through", SEE_THROUGH),
          Map.entry("rendertype_translucent", TERRAIN),
          Map.entry("rendertype_translucent_moving_block", PLAIN.withLightmap(Lightmap.FETCH)),
          Map.entry("rendertype_tripwire", TERRAIN_CUTOUT),
          Map.entry("rendertype_water_mask", PLAIN));

  public static ShaderSemantics of(String shaderName) {
    if (shaderName == null) return PLAIN;
    ShaderSemantics vanilla = VANILLA.get(shaderName);
    if (vanilla != null) return vanilla;
    String name = shaderName.toLowerCase(Locale.ROOT);
    int namespace = name.indexOf(':');
    if (namespace >= 0) {
      vanilla = VANILLA.get(name.substring(namespace + 1));
      if (vanilla != null) return vanilla;
    }
    return name.contains("cutout") ? PLAIN.withAlpha(AlphaTest.TENTH, false) : PLAIN;
  }

  public static boolean isVanilla(String shaderName) {
    return shaderName != null && VANILLA.containsKey(shaderName);
  }

  private ShaderSemantics withAlpha(AlphaTest test, boolean testsTexture) {
    return new ShaderSemantics(
        test,
        testsTexture,
        fogMode,
        fogSpace,
        lightmap,
        diffuseLighting,
        overlay,
        intensity,
        glintAlpha,
        screenSpaceLines);
  }

  private ShaderSemantics withFog(FogMode mode) {
    return withFog(mode, fogSpace);
  }

  private ShaderSemantics withFog(FogMode mode, FogSpace space) {
    return new ShaderSemantics(
        alphaTest,
        alphaTestsTexture,
        mode,
        space,
        lightmap,
        diffuseLighting,
        overlay,
        intensity,
        glintAlpha,
        screenSpaceLines);
  }

  private ShaderSemantics withLightmap(Lightmap mode) {
    return new ShaderSemantics(
        alphaTest,
        alphaTestsTexture,
        fogMode,
        fogSpace,
        mode,
        diffuseLighting,
        overlay,
        intensity,
        glintAlpha,
        screenSpaceLines);
  }

  private ShaderSemantics withOverlay(boolean enabled) {
    return new ShaderSemantics(
        alphaTest,
        alphaTestsTexture,
        fogMode,
        fogSpace,
        lightmap,
        diffuseLighting,
        enabled,
        intensity,
        glintAlpha,
        screenSpaceLines);
  }

  private ShaderSemantics withIntensity() {
    return new ShaderSemantics(
        alphaTest,
        alphaTestsTexture,
        fogMode,
        fogSpace,
        lightmap,
        diffuseLighting,
        overlay,
        true,
        glintAlpha,
        screenSpaceLines);
  }

  private ShaderSemantics withScreenSpaceLines() {
    return new ShaderSemantics(
        alphaTest,
        alphaTestsTexture,
        fogMode,
        fogSpace,
        lightmap,
        diffuseLighting,
        overlay,
        intensity,
        glintAlpha,
        true);
  }
}
