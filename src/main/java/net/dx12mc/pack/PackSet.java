package net.dx12mc.pack;

import static org.lwjgl.bgfx.BGFX.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.dx12mc.DX12MC;
import net.dx12mc.api.PackStatus;
import net.dx12mc.graphics.bgfx.StagingBackpressureException;
import net.dx12mc.graphics.bgfx.StagingMemory;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.pipeline.PostPipeline;

public final class PackSet implements AutoCloseable {
  public static final long VRAM_CAP =
      Math.max(16L, Long.getLong("dx12mc.pack.maxVramMiB", 256L)) << 20;
  static final String PASS_VERTEX = "/assets/dx12mc/shaders/d3d12/vs_post.bin";
  private static final long WRITE = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A;
  private static final int CLAMP = BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
  private static final int POINT =
      BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT;

  public interface Providers {
    boolean update(String provider, FloatBuffer values);
  }

  private final FrameUniforms frame = new FrameUniforms();
  private final List<Active> packs = new ArrayList<>();
  private final Map<String, PackProgram> overrides = new HashMap<>();
  private final Map<String, List<Pass>> stages = new HashMap<>();
  private final Map<String, SharedUniform> shared = new HashMap<>();
  private final Map<String, float[]> customValues = new HashMap<>();
  private final List<ProviderBinding> providerBindings = new ArrayList<>();
  private final List<PackStatus> statuses = new ArrayList<>();
  private short passVertex = BGFX_INVALID_HANDLE;
  private static ShaderBinary passVertexBinary;
  private Target scene;
  private int[] sceneFormats = new int[0];
  private String sceneOwner;
  private boolean needsScene;
  private int width;
  private int height;
  private MaterialAtlas.Layout materialLayout;
  private Map<String, RgbaImage> normalImages = Map.of();
  private Map<String, RgbaImage> specularImages = Map.of();
  private boolean materialsDirty;
  private Texture normalAtlas;
  private Texture specularAtlas;
  private short flatNormal = BGFX_INVALID_HANDLE;
  private short flatSpecular = BGFX_INVALID_HANDLE;
  private int[] normalDefault;
  private int[] specularDefault;
  private IntFunction<Texture> textureLookup = id -> null;
  private int revision;

  private record SharedUniform(short handle, int type, int count, int[] references) {}

  private record ProviderBinding(
      String pack, String uniform, String provider, FloatBuffer values) {}

  private record Pass(
      Active pack, PackProgram program, String[] sources, String output, long state, int filter) {}

  private static final class Active {
    final PackPlan plan;
    final Map<String, PackProgram> programs = new LinkedHashMap<>();
    final List<Short> shaders = new ArrayList<>();
    final List<Short> programHandles = new ArrayList<>();
    final Map<String, Short> textures = new HashMap<>();
    final Map<String, Target> targets = new HashMap<>();
    final List<String> uniforms = new ArrayList<>();

    Active(PackPlan plan) {
      this.plan = plan;
    }
  }

  public boolean active() {
    return !packs.isEmpty();
  }

  public int revision() {
    return revision;
  }

  public List<PackStatus> statuses() {
    return List.copyOf(statuses);
  }

  public FrameUniforms frame() {
    return frame;
  }

  public void textureLookup(IntFunction<Texture> lookup) {
    this.textureLookup = lookup;
  }

  public static synchronized ShaderBinary passVertexBinary() {
    if (passVertexBinary == null) passVertexBinary = ShaderBinary.parse(resource(PASS_VERTEX));
    return passVertexBinary;
  }

  public void apply(
      List<PackPlan> plans, List<PackStatus> rejected, Providers providers, int width, int height) {
    release();
    statuses.clear();
    this.width = Math.max(1, width);
    this.height = Math.max(1, height);
    long budget = VRAM_CAP;
    for (PackPlan plan : plans) {
      String failure = admit(plan, budget);
      if (failure == null) {
        try {
          Active pack = create(plan);
          packs.add(pack);
          budget -= plan.vramBytes(this.width, this.height);
          statuses.add(new PackStatus(plan.id(), plan.source(), PackStatus.State.ACTIVE, ""));
          DX12MC.LOGGER.info("DX12MC shader pack {} from {} is active", plan.id(), plan.source());
          continue;
        } catch (RuntimeException exception) {
          failure = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        }
      }
      statuses.add(new PackStatus(plan.id(), plan.source(), PackStatus.State.FAILED, failure));
      DX12MC.LOGGER.warn(
          "DX12MC shader pack {} from {} is disabled and the built-in renderer is used: {}",
          plan.id(),
          plan.source(),
          failure);
    }
    statuses.addAll(rejected);
    for (int i = packs.size() - 1; i >= 0; i--) {
      Active pack = packs.get(i);
      for (Map.Entry<String, String> entry : pack.plan.manifest().overrides().entrySet()) {
        PackProgram previous = overrides.put(entry.getKey(), pack.programs.get(entry.getValue()));
        if (previous != null) {
          DX12MC.LOGGER.warn(
              "Shader packs {} and {} both override {}; {} is used because it is higher in the"
                  + " resource pack list",
              pack.plan.id(),
              previous.pack,
              entry.getKey(),
              pack.plan.id());
        }
      }
    }
    for (Active pack : packs) {
      for (PackManifest.Pass pass : pack.plan.manifest().passes()) {
        stages.computeIfAbsent(pass.stage(), ignored -> new ArrayList<>()).add(pass(pack, pass));
      }
    }
    if (normalDefault != null) {
      flatNormal = flat(normalDefault);
      flatSpecular = flat(specularDefault);
      materialsDirty = true;
    }
    connectProviders(providers);
    revision++;
  }

  private String admit(PackPlan plan, long budget) {
    long bytes = plan.vramBytes(width, height);
    if (bytes > budget) {
      return "needs "
          + (bytes >> 20)
          + " MiB of render targets and textures at "
          + width
          + "x"
          + height
          + ", over the "
          + (VRAM_CAP >> 20)
          + " MiB limit (-Ddx12mc.pack.maxVramMiB)";
    }
    for (Map.Entry<String, PackManifest.Uniform> entry : plan.manifest().uniforms().entrySet()) {
      SharedUniform existing = shared.get(entry.getKey());
      if (existing != null
          && (existing.type != uniformType(entry.getValue().type())
              || existing.count != entry.getValue().count())) {
        return "uniform " + entry.getKey() + " is declared differently by another active pack";
      }
    }
    PackManifest.Scene declared = plan.manifest().scene();
    if (declared != null && !declared.gbuffer().isEmpty() && sceneOwner != null) {
      return "the scene G-buffer is already declared by pack " + sceneOwner;
    }
    return null;
  }

  private Active create(PackPlan plan) {
    Active pack = new Active(plan);
    try {
      PackManifest manifest = plan.manifest();
      for (Map.Entry<String, PackManifest.Uniform> entry : manifest.uniforms().entrySet()) {
        uniform(pack, entry.getKey(), entry.getValue().type(), entry.getValue().count());
        float[] values =
            customValues.computeIfAbsent(
                entry.getKey(), ignored -> new float[entry.getValue().floats()]);
        System.arraycopy(
            entry.getValue().value(),
            0,
            values,
            0,
            Math.min(values.length, entry.getValue().value().length));
      }
      for (Map.Entry<String, RgbaImage> entry : plan.textures().entrySet()) {
        RgbaImage image = entry.getValue();
        short texture =
            bgfx_create_texture_2d(
                image.width(),
                image.height(),
                false,
                1,
                BGFX_TEXTURE_FORMAT_RGBA8,
                BGFX_SAMPLER_NONE,
                StagingMemory.copy(image.rgba()));
        if (!BGFX_HANDLE_IS_VALID(texture))
          throw new IllegalStateException("bgfx rejected texture " + entry.getKey());
        pack.textures.put(entry.getKey(), texture);
      }
      for (Map.Entry<String, PackManifest.Target> entry : manifest.targets().entrySet()) {
        PackManifest.Target target = entry.getValue();
        pack.targets.put(
            entry.getKey(),
            Target.createColor(
                PackPlan.scaled(width, target.scale()),
                PackPlan.scaled(height, target.scale()),
                format(target.format())));
      }
      for (PackPlan.Program program : plan.programs().values()) {
        pack.programs.put(program.name(), program(pack, program));
      }
      if (plan.needsScene()) {
        needsScene = true;
        PackManifest.Scene declared = manifest.scene();
        if (declared != null && !declared.gbuffer().isEmpty()) {
          sceneOwner = plan.id();
          sceneFormats =
              declared.gbuffer().stream()
                  .mapToInt(attachment -> format(attachment.format()))
                  .toArray();
        }
      }
      if (manifest.materials() != null) {
        if (normalDefault == null) {
          normalDefault = manifest.materials().normalDefault();
          specularDefault = manifest.materials().specularDefault();
        }
      }
      return pack;
    } catch (RuntimeException exception) {
      close(pack);
      throw exception;
    }
  }

  private PackProgram program(Active pack, PackPlan.Program plan) {
    short vertex;
    boolean ownVertex =
        plan.family() != ShaderContract.Family.PASS || plan.vertex() != passVertexBinary();
    if (ownVertex) {
      vertex = shader(pack, plan.vertex());
    } else {
      if (!BGFX_HANDLE_IS_VALID(passVertex)) {
        passVertex = bgfx_create_shader(StagingMemory.copy(passVertexBinary().bytes()));
        if (!BGFX_HANDLE_IS_VALID(passVertex))
          throw new IllegalStateException("bgfx rejected vs_post");
      }
      vertex = passVertex;
    }
    short fragment = shader(pack, plan.fragment());
    short handle = bgfx_create_program(vertex, fragment, false);
    if (!BGFX_HANDLE_IS_VALID(handle))
      throw new IllegalStateException("bgfx rejected program " + plan.name());
    pack.programHandles.add(handle);
    List<String> uniformNames = new ArrayList<>();
    for (String name : plan.uniforms()) {
      ShaderContract.UniformSpec spec = ShaderContract.uniform(name);
      if (spec != null
          && (spec.scope() == ShaderContract.Scope.DRAW
              || spec.scope() == ShaderContract.Scope.ENTITY_DRAW)) continue;
      uniformNames.add(name);
    }
    short[] uniformHandles = new short[uniformNames.size()];
    float[][] values = new float[uniformNames.size()][];
    int[] counts = new int[uniformNames.size()];
    float[] targetSize = null;
    for (int i = 0; i < uniformNames.size(); i++) {
      String name = uniformNames.get(i);
      ShaderContract.UniformSpec spec = ShaderContract.uniform(name);
      if (spec != null) {
        uniformHandles[i] = uniform(pack, name, spec.type(), 1);
        if (spec.scope() == ShaderContract.Scope.PASS) {
          targetSize = new float[4];
          values[i] = targetSize;
        } else {
          values[i] = frame.value(name);
        }
        counts[i] = 1;
      } else {
        PackManifest.Uniform declared = pack.plan.manifest().uniforms().get(name);
        uniformHandles[i] = shared.get(name).handle;
        values[i] = customValues.get(name);
        counts[i] = declared.count();
      }
    }
    List<String> samplerNames = new ArrayList<>();
    for (String name : plan.samplers().keySet()) {
      if (name.equals("s_texColor") || name.equals("s_overlay") || name.equals("s_lightmap")) {
        if (plan.family() != ShaderContract.Family.PASS) continue;
      }
      samplerNames.add(name);
    }
    short[] samplerHandles = new short[samplerNames.size()];
    int[] slots = new int[samplerNames.size()];
    for (int i = 0; i < samplerNames.size(); i++) {
      samplerHandles[i] = uniform(pack, samplerNames.get(i), "sampler", 1);
      slots[i] = plan.samplers().get(samplerNames.get(i));
    }
    PackProgram program =
        new PackProgram(
            pack.plan.id(),
            plan,
            handle,
            uniformHandles,
            values,
            counts,
            samplerNames.toArray(String[]::new),
            samplerHandles,
            slots);
    program.targetSize = targetSize;
    program.packs = this;
    for (int i = 0; i < samplerNames.size(); i++) {
      Short texture = pack.textures.get(samplerNames.get(i));
      if (texture != null) {
        program.samplerTextures[i] = texture;
        PackManifest.Texture declared = pack.plan.manifest().textures().get(samplerNames.get(i));
        program.samplerFlags[i] = (declared.linear() ? 0 : POINT) | (declared.repeat() ? 0 : CLAMP);
      } else {
        program.samplerTextures[i] = BGFX_INVALID_HANDLE;
      }
    }
    return program;
  }

  private Pass pass(Active pack, PackManifest.Pass declared) {
    PackProgram program = pack.programs.get(declared.program());
    String[] sources = new String[program.samplerNames.length];
    for (int i = 0; i < sources.length; i++)
      sources[i] = declared.inputs().get(program.samplerNames[i]);
    long state =
        switch (declared.blend()) {
          case "alpha" ->
              WRITE
                  | BGFX_STATE_BLEND_FUNC(
                      BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA);
          case "add" -> WRITE | BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_ONE);
          default -> WRITE;
        };
    return new Pass(
        pack,
        program,
        sources,
        declared.output(),
        state,
        declared.linear() ? CLAMP : CLAMP | POINT);
  }

  private void connectProviders(Providers providers) {
    providerBindings.clear();
    for (Active pack : packs) {
      for (Map.Entry<String, PackManifest.Uniform> entry :
          pack.plan.manifest().uniforms().entrySet()) {
        String provider = entry.getValue().provider();
        if (provider == null) continue;
        FloatBuffer values = FloatBuffer.wrap(customValues.get(entry.getKey()));
        providerBindings.add(new ProviderBinding(pack.plan.id(), entry.getKey(), provider, values));
      }
    }
    this.providers = providers;
  }

  private Providers providers;

  public void beginFrame() {
    if (providerBindings.isEmpty() || providers == null) return;
    for (ProviderBinding binding : providerBindings) {
      binding.values.clear();
      providers.update(binding.provider, binding.values);
    }
  }

  public PackProgram override(String shaderName) {
    if (overrides.isEmpty() || shaderName == null) return null;
    PackProgram program = overrides.get(shaderName);
    if (program != null) return program;
    int namespace = shaderName.indexOf(':');
    return namespace < 0 ? null : overrides.get(shaderName.substring(namespace + 1));
  }

  public boolean needsScene() {
    return needsScene;
  }

  public boolean gbuffer() {
    return sceneOwner != null;
  }

  public Target scene(int width, int height) {
    if (!needsScene) return null;
    if (scene != null && scene.valid() && scene.width() == width && scene.height() == height)
      return scene;
    resize(width, height);
    if (scene == null || !scene.valid()) scene = Target.createScene(width, height, sceneFormats);
    return scene;
  }

  private void resize(int width, int height) {
    if (scene != null) {
      scene.close();
      scene = null;
    }
    if (this.width == width && this.height == height) return;
    this.width = width;
    this.height = height;
    for (Active pack : packs) {
      for (Map.Entry<String, PackManifest.Target> entry :
          pack.plan.manifest().targets().entrySet()) {
        pack.targets.remove(entry.getKey()).close();
        pack.targets.put(
            entry.getKey(),
            Target.createColor(
                PackPlan.scaled(width, entry.getValue().scale()),
                PackPlan.scaled(height, entry.getValue().scale()),
                format(entry.getValue().format())));
      }
    }
  }

  public boolean hasStage(String stage) {
    return stages.containsKey(stage);
  }

  public void runStage(String stage, Consumer<Target> bind, PostPipeline post) {
    List<Pass> passes = stages.get(stage);
    if (passes == null || scene == null && needsScene) return;
    for (Pass pass : passes) {
      Target output =
          pass.output.equals(PackPlan.SCENE_COLOR)
              ? scene.colorOnly()
              : pass.pack.targets.get(pass.output.substring(7));
      bind.accept(output);
      PackProgram program = pass.program;
      if (program.targetSize != null) {
        program.targetSize[0] = output.width();
        program.targetSize[1] = output.height();
        program.targetSize[2] = 1.0f / output.width();
        program.targetSize[3] = 1.0f / output.height();
      }
      program.setUniforms();
      for (int i = 0; i < program.samplerHandles.length; i++) {
        String source = pass.sources[i];
        short texture;
        int flags = pass.filter;
        if (source.startsWith("texture:")) {
          texture = program.samplerTextures[i];
          flags = program.samplerFlags[i];
        } else {
          texture = sourceTexture(pass.pack, source);
        }
        bgfx_set_texture(program.samplerSlots[i], program.samplerHandles[i], texture, flags);
      }
      if (!post.drawProgram(program.handle, pass.state)) {
        throw new IllegalStateException("bgfx/D3D12 rejected pack pass " + program.name());
      }
    }
  }

  private short sourceTexture(Active pack, String source) {
    if (source.equals(PackPlan.SCENE_COLOR)) return scene.colorTexture();
    if (source.equals(PackPlan.SCENE_DEPTH)) return scene.depthTexture();
    if (source.startsWith("scene:")) {
      String name = source.substring(6);
      PackManifest.Scene declared = owner().plan.manifest().scene();
      for (int i = 0; i < declared.gbuffer().size(); i++) {
        if (declared.gbuffer().get(i).name().equals(name)) return scene.attachment(i);
      }
    }
    return pack.targets.get(source.substring(7)).colorTexture();
  }

  private Active owner() {
    for (Active pack : packs) if (pack.plan.id().equals(sceneOwner)) return pack;
    throw new IllegalStateException("no scene owner");
  }

  public void materials(
      MaterialAtlas.Layout layout,
      Map<String, RgbaImage> normals,
      Map<String, RgbaImage> speculars) {
    if (layout != null) materialLayout = layout;
    if (normals != null) normalImages = normals;
    if (speculars != null) specularImages = speculars;
    materialsDirty = true;
  }

  public boolean wantsMaterials() {
    return normalDefault != null;
  }

  public void uploadMaterials() {
    if (!materialsDirty || normalDefault == null || materialLayout == null) return;
    long bytes = MaterialAtlas.bytes(materialLayout) * 2;
    long used = 0;
    for (Active pack : packs) used += pack.plan.vramBytes(width, height);
    if (used + bytes > VRAM_CAP) {
      materialsDirty = false;
      DX12MC.LOGGER.warn(
          "DX12MC material atlases need {} MiB and exceed the {} MiB pack limit; flat defaults are"
              + " used",
          bytes >> 20,
          VRAM_CAP >> 20);
      for (int i = 0; i < statuses.size(); i++) {
        PackStatus status = statuses.get(i);
        if (status.state() == PackStatus.State.ACTIVE) {
          statuses.set(
              i,
              new PackStatus(
                  status.id(),
                  status.source(),
                  PackStatus.State.ACTIVE,
                  "material atlases exceed the VRAM limit"));
        }
      }
      revision++;
      return;
    }
    try {
      Texture normals = upload(MaterialAtlas.build(materialLayout, normalImages, normalDefault));
      Texture speculars;
      try {
        speculars = upload(MaterialAtlas.build(materialLayout, specularImages, specularDefault));
      } catch (RuntimeException exception) {
        normals.close();
        throw exception;
      }
      closeAtlases();
      normalAtlas = normals;
      specularAtlas = speculars;
      materialsDirty = false;
    } catch (StagingBackpressureException pressure) {
      DX12MC.LOGGER.debug("Material atlas upload deferred: {}", pressure.getMessage());
    }
  }

  private Texture upload(byte[][] levels) {
    Texture texture =
        Texture.create(materialLayout.width(), materialLayout.height(), levels.length - 1);
    try {
      for (int level = 0; level < levels.length; level++) {
        texture.update(
            level,
            0,
            0,
            Math.max(1, materialLayout.width() >> level),
            Math.max(1, materialLayout.height() >> level),
            levels[level]);
      }
      return texture;
    } catch (RuntimeException exception) {
      texture.close();
      throw exception;
    }
  }

  private static short flat(int[] rgba) {
    byte[] pixel = {(byte) rgba[0], (byte) rgba[1], (byte) rgba[2], (byte) rgba[3]};
    short handle =
        bgfx_create_texture_2d(
            1,
            1,
            false,
            1,
            BGFX_TEXTURE_FORMAT_RGBA8,
            BGFX_SAMPLER_NONE,
            StagingMemory.copy(pixel));
    if (!BGFX_HANDLE_IS_VALID(handle))
      throw new IllegalStateException("bgfx rejected a material default texture");
    return handle;
  }

  short materialTexture(boolean normals, Texture base) {
    if (base != null && materialLayout != null && normalAtlas != null && base == atlas()) {
      return normals ? normalAtlas.handle() : specularAtlas.handle();
    }
    return normals ? flatNormal : flatSpecular;
  }

  private Texture atlas() {
    try {
      return textureLookup.apply(materialLayout.textureId());
    } catch (RuntimeException notReady) {
      return null;
    }
  }

  int materialFlags(Texture base) {
    return base == null ? POINT : base.samplerFlags();
  }

  private short shader(Active pack, ShaderBinary binary) {
    short handle = bgfx_create_shader(StagingMemory.copy(binary.bytes()));
    if (!BGFX_HANDLE_IS_VALID(handle)) throw new IllegalStateException("bgfx rejected a shader");
    pack.shaders.add(handle);
    return handle;
  }

  private short uniform(Active pack, String name, String type, int count) {
    int bgfxType = uniformType(type);
    SharedUniform existing = shared.get(name);
    if (existing == null) {
      short handle = bgfx_create_uniform(name, bgfxType, count);
      if (!BGFX_HANDLE_IS_VALID(handle))
        throw new IllegalStateException("bgfx rejected uniform " + name);
      existing = new SharedUniform(handle, bgfxType, count, new int[1]);
      shared.put(name, existing);
    }
    if (!pack.uniforms.contains(name)) {
      existing.references[0]++;
      pack.uniforms.add(name);
    }
    return existing.handle;
  }

  private static int uniformType(String type) {
    return switch (type) {
      case "mat4" -> BGFX_UNIFORM_TYPE_MAT4;
      case "sampler" -> BGFX_UNIFORM_TYPE_SAMPLER;
      default -> BGFX_UNIFORM_TYPE_VEC4;
    };
  }

  static int format(String format) {
    return switch (format) {
      case "rgba16f" -> BGFX_TEXTURE_FORMAT_RGBA16F;
      case "rg16f" -> BGFX_TEXTURE_FORMAT_RG16F;
      case "r16f" -> BGFX_TEXTURE_FORMAT_R16F;
      case "r32f" -> BGFX_TEXTURE_FORMAT_R32F;
      case "rgb10a2" -> BGFX_TEXTURE_FORMAT_RGB10A2;
      case "rg8" -> BGFX_TEXTURE_FORMAT_RG8;
      case "r8" -> BGFX_TEXTURE_FORMAT_R8;
      default -> BGFX_TEXTURE_FORMAT_RGBA8;
    };
  }

  private static byte[] resource(String path) {
    try (InputStream input = PackSet.class.getResourceAsStream(path)) {
      if (input == null) throw new IllegalStateException("missing " + path);
      return input.readAllBytes();
    } catch (IOException exception) {
      throw new IllegalStateException("unable to read " + path, exception);
    }
  }

  public int liveHandles() {
    int count = shared.size() + (BGFX_HANDLE_IS_VALID(passVertex) ? 1 : 0);
    for (Active pack : packs) {
      count +=
          pack.shaders.size()
              + pack.programHandles.size()
              + pack.textures.size()
              + pack.targets.size();
    }
    if (scene != null) count++;
    if (normalAtlas != null) count += 2;
    if (BGFX_HANDLE_IS_VALID(flatNormal)) count += 2;
    return count;
  }

  private void release() {
    for (Active pack : packs) close(pack);
    packs.clear();
    overrides.clear();
    stages.clear();
    providerBindings.clear();
    customValues.clear();
    for (SharedUniform uniform : shared.values()) bgfx_destroy_uniform(uniform.handle);
    shared.clear();
    if (scene != null) {
      scene.close();
      scene = null;
    }
    needsScene = false;
    sceneOwner = null;
    sceneFormats = new int[0];
    closeAtlases();
    if (BGFX_HANDLE_IS_VALID(flatNormal)) bgfx_destroy_texture(flatNormal);
    if (BGFX_HANDLE_IS_VALID(flatSpecular)) bgfx_destroy_texture(flatSpecular);
    flatNormal = flatSpecular = BGFX_INVALID_HANDLE;
    normalDefault = specularDefault = null;
    if (BGFX_HANDLE_IS_VALID(passVertex)) bgfx_destroy_shader(passVertex);
    passVertex = BGFX_INVALID_HANDLE;
    frame.reset();
  }

  private void closeAtlases() {
    if (normalAtlas != null) normalAtlas.close();
    if (specularAtlas != null) specularAtlas.close();
    normalAtlas = specularAtlas = null;
  }

  private void close(Active pack) {
    for (short handle : pack.programHandles) bgfx_destroy_program(handle);
    pack.programHandles.clear();
    pack.programs.clear();
    for (Short handle : pack.shaders) bgfx_destroy_shader(handle);
    pack.shaders.clear();
    for (short texture : pack.textures.values()) bgfx_destroy_texture(texture);
    pack.textures.clear();
    for (Target target : pack.targets.values()) target.close();
    pack.targets.clear();
    for (String name : pack.uniforms) {
      SharedUniform uniform = shared.get(name);
      if (uniform != null && --uniform.references[0] == 0) {
        bgfx_destroy_uniform(uniform.handle);
        shared.remove(name);
      }
    }
    pack.uniforms.clear();
  }

  @Override
  public void close() {
    release();
    statuses.clear();
    materialLayout = null;
  }
}
