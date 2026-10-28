package net.dx12mc.pack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dx12mc.bridge.DrawContext;

public final class ShaderContract {
  public static final int VERSION = 1;
  public static final String PROFILE = "d3d12_s_5_0";
  public static final int BGFX_SHADER_VERSION = 11;
  public static final int FIRST_PACK_SAMPLER = 5;
  public static final int MAX_SAMPLER = 15;
  public static final int MAX_SCENE_ATTACHMENTS = 3;

  public enum Family {
    BLOCK(
        DrawContext.Kind.TEXTURE_LIGHT,
        Set.of(
            ShaderBinary.ATTRIB_POSITION,
            ShaderBinary.ATTRIB_COLOR0,
            ShaderBinary.ATTRIB_TEXCOORD0,
            ShaderBinary.ATTRIB_TEXCOORD2)),
    ENTITY(
        DrawContext.Kind.ENTITY,
        Set.of(
            ShaderBinary.ATTRIB_POSITION,
            ShaderBinary.ATTRIB_COLOR0,
            ShaderBinary.ATTRIB_TEXCOORD0,
            ShaderBinary.ATTRIB_TEXCOORD1,
            ShaderBinary.ATTRIB_TEXCOORD2,
            ShaderBinary.ATTRIB_NORMAL)),
    PASS(null, Set.of(ShaderBinary.ATTRIB_POSITION));

    private final DrawContext.Kind kind;
    private final Set<Integer> attributes;

    Family(DrawContext.Kind kind, Set<Integer> attributes) {
      this.kind = kind;
      this.attributes = attributes;
    }

    public DrawContext.Kind kind() {
      return kind;
    }

    public Set<Integer> attributes() {
      return attributes;
    }
  }

  public enum Scope {
    DRAW,
    ENTITY_DRAW,
    FRAME,
    PASS
  }

  public record UniformSpec(String name, String type, Scope scope) {}

  public record SamplerSpec(String name, int slot, Scope scope) {}

  public static final Map<String, Family> PROGRAMS;

  static {
    Map<String, Family> programs = new LinkedHashMap<>();
    for (String name :
        List.of(
            "rendertype_solid",
            "rendertype_cutout_mipped",
            "rendertype_cutout",
            "rendertype_translucent",
            "rendertype_tripwire")) programs.put(name, Family.BLOCK);
    for (String name :
        List.of(
            "rendertype_entity_solid",
            "rendertype_entity_cutout",
            "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset",
            "rendertype_entity_smooth_cutout",
            "rendertype_entity_translucent",
            "rendertype_entity_translucent_cull",
            "rendertype_item_entity_translucent_cull",
            "rendertype_armor_cutout_no_cull")) programs.put(name, Family.ENTITY);
    PROGRAMS = Map.copyOf(programs);
  }

  public static final List<UniformSpec> UNIFORMS =
      List.of(
          new UniformSpec("u_mvp", "mat4", Scope.DRAW),
          new UniformSpec("u_color", "vec4", Scope.DRAW),
          new UniformSpec("u_texMatrix", "mat4", Scope.DRAW),
          new UniformSpec("u_fogModel", "mat4", Scope.DRAW),
          new UniformSpec("u_fogParams", "vec4", Scope.DRAW),
          new UniformSpec("u_fogColor", "vec4", Scope.DRAW),
          new UniformSpec("u_material", "vec4", Scope.DRAW),
          new UniformSpec("u_texLod", "vec4", Scope.DRAW),
          new UniformSpec("u_lightParams", "vec4", Scope.DRAW),
          new UniformSpec("u_lightDir0", "vec4", Scope.ENTITY_DRAW),
          new UniformSpec("u_lightDir1", "vec4", Scope.ENTITY_DRAW),
          new UniformSpec("u_cameraViewProj", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraInvViewProj", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraPrevViewProj", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraView", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraProj", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraInvProj", "mat4", Scope.FRAME),
          new UniformSpec("u_cameraPos", "vec4", Scope.FRAME),
          new UniformSpec("u_sunDir", "vec4", Scope.FRAME),
          new UniformSpec("u_moonDir", "vec4", Scope.FRAME),
          new UniformSpec("u_time", "vec4", Scope.FRAME),
          new UniformSpec("u_viewSize", "vec4", Scope.FRAME),
          new UniformSpec("u_frameFog", "vec4", Scope.FRAME),
          new UniformSpec("u_frameFogColor", "vec4", Scope.FRAME),
          new UniformSpec("u_targetSize", "vec4", Scope.PASS));

  public static final List<SamplerSpec> SAMPLERS =
      List.of(
          new SamplerSpec("s_texColor", 0, Scope.DRAW),
          new SamplerSpec("s_overlay", 1, Scope.ENTITY_DRAW),
          new SamplerSpec("s_lightmap", 2, Scope.DRAW),
          new SamplerSpec("s_normals", 3, Scope.DRAW),
          new SamplerSpec("s_specular", 4, Scope.DRAW));

  static final Set<String> RESERVED =
      Set.of(
          "u_postSize",
          "u_postA",
          "u_postB",
          "u_postC",
          "u_postD",
          "u_postE",
          "u_postF",
          "u_lineParams",
          "u_portal",
          "s_post0",
          "s_post1");

  static final Set<String> BGFX_PREDEFINED =
      Set.of(
          "u_viewRect",
          "u_viewTexel",
          "u_view",
          "u_invView",
          "u_proj",
          "u_invProj",
          "u_viewProj",
          "u_invViewProj",
          "u_model",
          "u_modelView",
          "u_modelViewProj",
          "u_alphaRef4");

  public static final List<String> STAGES =
      List.of("before_translucent", "after_world", "before_gui");

  private ShaderContract() {}

  public static UniformSpec uniform(String name) {
    for (UniformSpec spec : UNIFORMS) if (spec.name().equals(name)) return spec;
    return null;
  }

  public static SamplerSpec sampler(String name) {
    for (SamplerSpec spec : SAMPLERS) if (spec.name().equals(name)) return spec;
    return null;
  }

  static boolean available(Scope scope, Family family) {
    return switch (scope) {
      case DRAW -> family != Family.PASS;
      case ENTITY_DRAW -> family == Family.ENTITY;
      case FRAME -> true;
      case PASS -> family == Family.PASS;
    };
  }

  static int baseType(String type) {
    return type.equals("mat4") ? ShaderBinary.TYPE_MAT4 : ShaderBinary.TYPE_VEC4;
  }
}
