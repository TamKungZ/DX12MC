package net.dx12mc.pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dx12mc.DX12MC;
import net.dx12mc.api.PackStatus;

public record PackPlan(
    String id,
    String source,
    PackManifest manifest,
    Map<String, Program> programs,
    Map<String, RgbaImage> textures,
    boolean needsScene) {

  public static final String SCENE_COLOR = "scene:color";
  public static final String SCENE_DEPTH = "scene:depth";

  public record Program(
      String name,
      ShaderContract.Family family,
      ShaderBinary vertex,
      ShaderBinary fragment,
      Map<String, Integer> samplers,
      Set<String> uniforms) {}

  public static PackPlan load(
      PackInput input, ShaderBinary defaultPassVertex, String line, List<PackStatus> rejected) {
    try {
      return validate(input, defaultPassVertex, line);
    } catch (InvalidPackException exception) {
      reject(input.id(), input.source(), exception.getMessage(), rejected);
      return null;
    }
  }

  public static void reject(String id, String source, String message, List<PackStatus> rejected) {
    rejected.add(new PackStatus(id, source, PackStatus.State.FAILED, message));
    DX12MC.LOGGER.warn(
        "DX12MC shader pack {} from {} is disabled and the built-in renderer is used: {}",
        id,
        source,
        message);
  }

  public static PackPlan validate(PackInput input, ShaderBinary defaultPassVertex, String line) {
    PackManifest manifest = input.manifest();
    for (String name : manifest.uniforms().keySet()) {
      if (ShaderContract.uniform(name) != null
          || ShaderContract.RESERVED.contains(name)
          || ShaderContract.BGFX_PREDEFINED.contains(name)) {
        throw new InvalidPackException("uniform " + name + " is reserved by DX12MC");
      }
    }
    for (String name : manifest.textures().keySet()) {
      if (ShaderContract.sampler(name) != null || ShaderContract.RESERVED.contains(name)) {
        throw new InvalidPackException("texture " + name + " is reserved by DX12MC");
      }
    }
    Map<String, ShaderContract.Family> families = new HashMap<>();
    for (Map.Entry<String, String> override : manifest.overrides().entrySet()) {
      ShaderContract.Family family = ShaderContract.PROGRAMS.get(override.getKey());
      if (family == null) {
        throw new InvalidPackException(
            override.getKey()
                + " cannot be overridden in shader contract "
                + ShaderContract.VERSION);
      }
      join(families, program(manifest, override.getValue()), family);
    }
    boolean needsScene = manifest.scene() != null;
    Map<String, List<PackManifest.Pass>> passesByProgram = new HashMap<>();
    for (PackManifest.Pass pass : manifest.passes()) {
      join(families, program(manifest, pass.program()), ShaderContract.Family.PASS);
      passesByProgram.computeIfAbsent(pass.program(), ignored -> new ArrayList<>()).add(pass);
      for (String source : pass.inputs().values()) {
        needsScene |= source.startsWith("scene:");
        checkSource(manifest, source);
      }
      if (pass.inputs().containsValue(pass.output())) {
        throw new InvalidPackException("pass " + pass.program() + " reads the target it writes");
      }
      if (pass.output().equals(SCENE_COLOR)) needsScene = true;
      else if (!pass.output().startsWith("target:")
          || !manifest.targets().containsKey(pass.output().substring(7))) {
        throw new InvalidPackException(
            "pass output " + pass.output() + " is not scene:color or a declared target");
      }
    }
    Map<String, Program> programs = new LinkedHashMap<>();
    for (Map.Entry<String, ShaderContract.Family> entry : families.entrySet()) {
      String name = entry.getKey();
      PackManifest.Program files = manifest.programs().get(name).forLine(line);
      programs.put(
          name,
          program(
              name,
              entry.getValue(),
              files,
              input,
              defaultPassVertex,
              passesByProgram.getOrDefault(name, List.of())));
    }
    Map<String, RgbaImage> textures = new LinkedHashMap<>();
    for (Map.Entry<String, PackManifest.Texture> texture : manifest.textures().entrySet()) {
      RgbaImage image = input.images().get(texture.getValue().file());
      if (image == null)
        throw new InvalidPackException("missing texture file " + texture.getValue().file());
      if (image.width() > 2048 || image.height() > 2048) {
        throw new InvalidPackException("texture " + texture.getKey() + " is larger than 2048x2048");
      }
      textures.put(texture.getKey(), image);
    }
    if (manifest.materials() != null
        && !manifest.materials().normal()
        && !manifest.materials().specular()) {
      throw new InvalidPackException("materials declares neither normal nor specular");
    }
    return new PackPlan(
        input.id(),
        input.source(),
        manifest,
        Map.copyOf(programs),
        Map.copyOf(textures),
        needsScene);
  }

  public long vramBytes(int width, int height) {
    long bytes = 0;
    for (PackManifest.Target target : manifest.targets().values()) {
      bytes +=
          (long) scaled(width, target.scale())
              * scaled(height, target.scale())
              * bytesPerPixel(target.format());
    }
    if (needsScene) {
      bytes += (long) width * height * 8;
      if (manifest.scene() != null) {
        for (PackManifest.Attachment attachment : manifest.scene().gbuffer()) {
          bytes += (long) width * height * bytesPerPixel(attachment.format());
        }
      }
    }
    for (RgbaImage image : textures.values()) bytes += (long) image.width() * image.height() * 4;
    return bytes;
  }

  public static int scaled(int size, float scale) {
    return Math.max(1, Math.round(size * scale));
  }

  public static int bytesPerPixel(String format) {
    return switch (format) {
      case "rgba16f" -> 8;
      case "r16f", "rg8" -> 2;
      case "r8" -> 1;
      default -> 4;
    };
  }

  private static void checkSource(PackManifest manifest, String source) {
    if (source.equals(SCENE_COLOR)) return;
    if (source.equals(SCENE_DEPTH)) {
      if (manifest.scene() == null || !manifest.scene().depth()) {
        throw new InvalidPackException("scene:depth is read but scene.depth is not true");
      }
      return;
    }
    if (source.startsWith("scene:")) {
      String name = source.substring(6);
      if (manifest.scene() != null) {
        for (PackManifest.Attachment attachment : manifest.scene().gbuffer()) {
          if (attachment.name().equals(name)) return;
        }
      }
      throw new InvalidPackException(source + " is not a declared scene.gbuffer attachment");
    }
    if (source.startsWith("target:") && manifest.targets().containsKey(source.substring(7))) return;
    if (source.startsWith("texture:") && manifest.textures().containsKey(source.substring(8)))
      return;
    throw new InvalidPackException(
        "pass input " + source + " is not a scene, target or texture source");
  }

  private static String program(PackManifest manifest, String name) {
    if (!manifest.programs().containsKey(name))
      throw new InvalidPackException("unknown program " + name);
    return name;
  }

  private static void join(
      Map<String, ShaderContract.Family> families, String name, ShaderContract.Family family) {
    ShaderContract.Family previous = families.putIfAbsent(name, family);
    if (previous != null && previous != family) {
      throw new InvalidPackException(
          "program "
              + name
              + " is used as both "
              + previous.name().toLowerCase()
              + " and "
              + family.name().toLowerCase());
    }
  }

  private static Program program(
      String name,
      ShaderContract.Family family,
      PackManifest.Program files,
      PackInput input,
      ShaderBinary defaultPassVertex,
      List<PackManifest.Pass> passes) {
    String where = "program " + name + ": ";
    try {
      ShaderBinary vertex;
      if (files.vertex() == null) {
        if (family != ShaderContract.Family.PASS)
          throw new InvalidPackException("needs a vertex shader");
        vertex = defaultPassVertex;
      } else {
        vertex = ShaderContainer.read(file(input, files.vertex()));
      }
      ShaderBinary fragment = ShaderContainer.read(file(input, files.fragment()));
      if (vertex.stage() != 'V')
        throw new InvalidPackException(files.vertex() + " is not a vertex shader");
      if (fragment.stage() != 'F')
        throw new InvalidPackException(files.fragment() + " is not a fragment shader");
      for (int attribute : vertex.attributes()) {
        if (!family.attributes().contains(attribute)) {
          throw new InvalidPackException(
              "vertex shader reads attribute 0x"
                  + Integer.toHexString(attribute)
                  + " that "
                  + family.name().toLowerCase()
                  + " vertices do not have");
        }
      }
      if (vertex.hashOut() != fragment.hashIn()) {
        throw new InvalidPackException("fragment inputs do not match the vertex outputs");
      }
      Map<String, Integer> samplers = new LinkedHashMap<>();
      Set<String> uniforms = new HashSet<>();
      for (ShaderBinary shader : List.of(vertex, fragment)) {
        for (ShaderBinary.Uniform uniform : shader.uniforms()) {
          if (uniform.sampler()) sampler(input.manifest(), family, passes, uniform, samplers);
          else uniforms.add(uniform(input.manifest(), family, uniform));
        }
      }
      return new Program(
          name, family, vertex, fragment, Map.copyOf(samplers), Set.copyOf(uniforms));
    } catch (InvalidPackException exception) {
      throw new InvalidPackException(where + exception.getMessage());
    }
  }

  private static byte[] file(PackInput input, String path) {
    byte[] bytes = input.shaders().get(path);
    if (bytes == null) throw new InvalidPackException("missing shader file " + path);
    return bytes;
  }

  private static void sampler(
      PackManifest manifest,
      ShaderContract.Family family,
      List<PackManifest.Pass> passes,
      ShaderBinary.Uniform uniform,
      Map<String, Integer> samplers) {
    String name = uniform.name();
    int slot = uniform.register();
    ShaderContract.SamplerSpec spec = ShaderContract.sampler(name);
    if (family != ShaderContract.Family.PASS && spec != null) {
      if (!ShaderContract.available(spec.scope(), family)) {
        throw new InvalidPackException(
            "sampler "
                + name
                + " is not available to "
                + family.name().toLowerCase()
                + " programs");
      }
      if (slot != spec.slot())
        throw new InvalidPackException("sampler " + name + " must use slot " + spec.slot());
      if (name.equals("s_normals")
          && (manifest.materials() == null || !manifest.materials().normal())) {
        throw new InvalidPackException("s_normals is sampled but materials.normal is not true");
      }
      if (name.equals("s_specular")
          && (manifest.materials() == null || !manifest.materials().specular())) {
        throw new InvalidPackException("s_specular is sampled but materials.specular is not true");
      }
    } else if (family == ShaderContract.Family.PASS) {
      for (PackManifest.Pass pass : passes) {
        if (!pass.inputs().containsKey(name)) {
          throw new InvalidPackException(
              "sampler " + name + " has no input in a pass that uses this program");
        }
      }
    } else {
      if (!manifest.textures().containsKey(name)) {
        throw new InvalidPackException(
            "sampler " + name + " is neither a contract sampler nor a declared texture");
      }
      if (slot < ShaderContract.FIRST_PACK_SAMPLER || slot > ShaderContract.MAX_SAMPLER) {
        throw new InvalidPackException(
            "pack texture "
                + name
                + " must use a slot from "
                + ShaderContract.FIRST_PACK_SAMPLER
                + " to "
                + ShaderContract.MAX_SAMPLER);
      }
    }
    Integer previous = samplers.putIfAbsent(name, slot);
    if (previous != null && previous != slot) {
      throw new InvalidPackException(
          "sampler " + name + " uses different slots in the two shaders");
    }
    for (Map.Entry<String, Integer> other : samplers.entrySet()) {
      if (!other.getKey().equals(name) && other.getValue() == slot) {
        throw new InvalidPackException(
            "samplers " + name + " and " + other.getKey() + " share slot " + slot);
      }
    }
  }

  private static String uniform(
      PackManifest manifest, ShaderContract.Family family, ShaderBinary.Uniform uniform) {
    String name = uniform.name();
    if (ShaderContract.BGFX_PREDEFINED.contains(name)) {
      throw new InvalidPackException(
          "uniform " + name + " is a bgfx predefined uniform that DX12MC does not set");
    }
    ShaderContract.UniformSpec spec = ShaderContract.uniform(name);
    if (spec != null) {
      if (!ShaderContract.available(spec.scope(), family)) {
        throw new InvalidPackException(
            "uniform "
                + name
                + " is not available to "
                + family.name().toLowerCase()
                + " programs");
      }
      if (uniform.baseType() != ShaderContract.baseType(spec.type())) {
        throw new InvalidPackException("uniform " + name + " must be a " + spec.type());
      }
      return name;
    }
    PackManifest.Uniform declared = manifest.uniforms().get(name);
    if (declared == null) {
      throw new InvalidPackException(
          "uniform " + name + " is neither in the contract nor declared in pack.json");
    }
    if (uniform.baseType() != ShaderContract.baseType(declared.type())) {
      throw new InvalidPackException("uniform " + name + " is declared as " + declared.type());
    }
    if (Math.max(1, uniform.count()) > declared.count()) {
      throw new InvalidPackException("uniform " + name + " has more elements than declared");
    }
    return name;
  }
}
