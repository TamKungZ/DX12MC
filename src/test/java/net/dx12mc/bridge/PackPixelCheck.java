package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.dx12mc.api.PackStatus;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PipelineSet;
import net.dx12mc.graphics.pipeline.PostPipeline;
import net.dx12mc.pack.MaterialAtlas;
import net.dx12mc.pack.PackDirectory;
import net.dx12mc.pack.PackInput;
import net.dx12mc.pack.PackPlan;
import net.dx12mc.pack.PackSet;
import net.dx12mc.pack.RgbaImage;
import org.joml.Matrix4f;
import org.lwjgl.bgfx.BGFXStats;
import org.lwjgl.system.MemoryUtil;

final class PackPixelCheck {
  private static final int SIZE = 64;
  private static final int READ_VIEW = 200;
  private static final int ATLAS_ID = 7;
  private static final int BOTH =
      ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT;
  private final Backend backend;
  private final PipelineSet pipelines;
  private final PostPipeline post = new PostPipeline();
  private final PackSet packs = new PackSet();
  private final RenderState state = new RenderState();
  private final DrawContext context = new DrawContext();
  private final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
  private final float[] gain = {0.5f, 0.5f, 0.5f, 1.0f};
  private Texture white;
  private Texture lightmap;
  private Texture atlas;
  private short readback = BGFX_INVALID_HANDLE;
  private int failures;

  private PackPixelCheck(Backend backend, PipelineSet pipelines) {
    this.backend = backend;
    this.pipelines = pipelines;
  }

  static int run(Backend backend, PipelineSet pipelines) {
    PackPixelCheck check = new PackPixelCheck(backend, pipelines);
    try {
      check.create();
      check.withoutAPackTheBuiltInProgramDraws();
      check.theTintOverrideChangesTheOutput();
      check.aPackBuiltByAnotherLineLoads();
      check.badBinariesFallBackToTheBuiltInProgram();
      check.theGbufferRoundTripReachesAPass();
      check.thePassReadsTheSceneDepth();
      check.aCustomPassRoundTripsThroughAHalfSizeTarget();
      check.materialAtlasesFollowTheSpriteLayout();
      check.reloadAndResizeReleaseEveryHandle();
      return check.failures;
    } finally {
      check.close();
    }
  }

  private void create() {
    post.start();
    white = Texture.create(1, 1, 0);
    white.sampler(BGFX_SAMPLER_POINT, 0.0f);
    white.update(0, 0, 0, 1, 1, new byte[] {-1, -1, -1, -1});
    lightmap = Texture.create(16, 16, 0);
    lightmap.sampler(BGFX_SAMPLER_UVW_CLAMP, 0.0f);
    byte[] lit = new byte[16 * 16 * 4];
    Arrays.fill(lit, (byte) -1);
    lightmap.update(0, 0, 0, 16, 16, lit);
    atlas = Texture.create(SIZE, SIZE, 0);
    atlas.sampler(BGFX_SAMPLER_POINT | BGFX_SAMPLER_UVW_CLAMP, 0.0f);
    byte[] texels = new byte[SIZE * SIZE * 4];
    Arrays.fill(texels, (byte) -1);
    atlas.update(0, 0, 0, SIZE, SIZE, texels);
    packs.textureLookup(id -> id == ATLAS_ID ? atlas : null);
    readback =
        bgfx_create_texture_2d(
            SIZE,
            SIZE,
            false,
            1,
            BGFX_TEXTURE_FORMAT_RGBA8,
            BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK,
            null);
    if (!BGFX_HANDLE_IS_VALID(readback)) throw new IllegalStateException("no pack readback");
  }

  private void apply(PackPlan... plans) {
    apply(List.of(), plans);
  }

  private void apply(List<PackStatus> rejected, PackPlan... plans) {
    packs.apply(
        List.of(plans),
        rejected,
        (name, values) -> {
          if (!name.equals("test:gain")) return false;
          values.put(gain);
          return true;
        },
        SIZE,
        SIZE);
    packs.frame().camera(new Matrix4f(), new Matrix4f(), 0, 64, 0, 0.0f, 6000.0f, 1.0f, 0.0f);
    packs.frame().viewSize(SIZE, SIZE);
  }

  private void withoutAPackTheBuiltInProgramDraws() {
    apply();
    Target target = Target.create(SIZE, SIZE, true);
    try {
      begin(target);
      drawSolid(-1, 1, 0.5f);
      read(target.colorTexture());
      expect("no pack: rendertype_solid uses the built-in program", 255, 255, 255, pixel(32, 32));
    } finally {
      target.close();
    }
  }

  private void theTintOverrideChangesTheOutput() {
    apply(PackDirectory.plan("tint"));
    Target target = Target.create(SIZE, SIZE, true);
    try {
      begin(target);
      drawSolid(-1, 1, 0.5f);
      read(target.colorTexture());
      expect("tint override multiplies by the pack uniform", 128, 255, 64, pixel(32, 32));
    } finally {
      target.close();
    }
  }

  private void aPackBuiltByAnotherLineLoads() {
    String root = System.getProperty("dx12mc.crossPacks");
    if (root == null) return;
    apply(
        PackPlan.validate(
            PackDirectory.read(new java.io.File(root), "tint"),
            PackSet.passVertexBinary(),
            "1.21.1"));
    Target target = Target.create(SIZE, SIZE, true);
    try {
      begin(target);
      drawSolid(-1, 1, 0.5f);
      read(target.colorTexture());
      expect(
          "tint pack compiled by another line's shaderc loads and draws",
          128,
          255,
          64,
          pixel(32, 32));
    } finally {
      target.close();
    }
  }

  private void badBinariesFallBackToTheBuiltInProgram() {
    PackInput tint = PackDirectory.read("tint");
    String fragment = "shaders/fs_tint.dxsh";
    for (String damage : List.of("checksum", "contract", "profile")) {
      byte[] bytes = tint.shaders().get(fragment).clone();
      switch (damage) {
        case "checksum" -> bytes[bytes.length - 60] ^= 0x10;
        case "contract" -> bytes[6] = 9;
        default -> bytes[12] = 'x';
      }
      Map<String, byte[]> shaders = new java.util.HashMap<>(tint.shaders());
      shaders.put(fragment, bytes);
      List<PackStatus> rejected = new ArrayList<>();
      PackPlan plan =
          PackPlan.load(
              new PackInput(tint.id(), tint.source(), tint.manifest(), shaders, tint.images()),
              PackSet.passVertexBinary(),
              "1.21.1",
              rejected);
      boolean failedCleanly =
          plan == null
              && rejected.size() == 1
              && rejected.get(0).state() == PackStatus.State.FAILED;
      report(
          "a "
              + damage
              + " mismatch is rejected: "
              + (rejected.isEmpty() ? "" : rejected.get(0).message()),
          failedCleanly);
      apply(rejected);
      Target target = Target.create(SIZE, SIZE, true);
      try {
        begin(target);
        drawSolid(-1, 1, 0.5f);
        read(target.colorTexture());
        expect(
            "after a " + damage + " mismatch the built-in program draws",
            255,
            255,
            255,
            pixel(32, 32));
      } finally {
        target.close();
      }
    }
  }

  private void theGbufferRoundTripReachesAPass() {
    apply(PackDirectory.plan("gbuffer"));
    Target scene = packs.scene(SIZE, SIZE);
    backend.beginFrame(new FrameSize(SIZE, SIZE), scene);
    backend.clear(BOTH, 0x102030FF, 1.0f);
    backend.clearMainAttachments();
    backend.mainMrt(true);
    pipelines.mrt(true);
    drawSolid(-1, 0, 0.5f);
    context.override = null;
    drawBuiltIn(0, 1, 0.5f);
    backend.mainMrt(false);
    pipelines.mrt(false);
    packs.runStage("after_world", backend::bindTarget, post);
    read(scene.colorTexture());
    expect("G-buffer: the override wrote attachment 1", 64, 128, 191, pixel(16, 32));
    expect("G-buffer: a built-in draw writes zero to attachment 1", 0, 0, 0, pixel(48, 32));
    Target single = Target.create(SIZE, SIZE, true);
    try {
      begin(single);
      pipelines.mrt(true);
      drawSolid(-1, 0, 0.5f);
      context.override = null;
      drawBuiltIn(0, 1, 0.5f);
      pipelines.mrt(false);
      read(single.colorTexture());
      expect(
          "G-buffer override into a one-colour target keeps output 0",
          255,
          255,
          255,
          pixel(16, 32));
      expect(
          "_mrt built-in program into a one-colour target keeps output 0",
          255,
          255,
          255,
          pixel(48, 32));
    } finally {
      single.close();
    }
    pipelines.releaseMrt();
  }

  private void thePassReadsTheSceneDepth() {
    apply(PackDirectory.plan("depth"));
    Target scene = packs.scene(SIZE, SIZE);
    backend.beginFrame(new FrameSize(SIZE, SIZE), scene);
    backend.clear(BOTH, 0x000000FF, 1.0f);
    drawBuiltIn(-1, 0, 0.25f);
    packs.runStage("before_gui", backend::bindTarget, post);
    read(scene.colorTexture());
    expect("depth pass: drawn quad depth 0.25", 64, 64, 64, pixel(16, 32));
    expect("depth pass: cleared depth 1.0", 255, 255, 255, pixel(48, 32));
  }

  private void aCustomPassRoundTripsThroughAHalfSizeTarget() {
    apply(PackDirectory.plan("pass"));
    Target scene = packs.scene(SIZE, SIZE);
    backend.beginFrame(new FrameSize(SIZE, SIZE), scene);
    backend.clear(BOTH, 0xC86432FF, 1.0f);
    packs.beginFrame();
    packs.runStage("before_gui", backend::bindTarget, post);
    read(scene.colorTexture());
    expect(
        "custom pass: provider gain through a half-size rgba16f target",
        100,
        50,
        25,
        pixel(32, 32));
  }

  private void materialAtlasesFollowTheSpriteLayout() {
    apply(PackDirectory.plan("material"));
    byte[] normal = new byte[16 * 16 * 4];
    byte[] specular = new byte[16 * 16 * 4];
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        int offset = (y * 16 + x) * 4;
        normal[offset] = (byte) (x * 16);
        normal[offset + 1] = (byte) (y * 16);
        normal[offset + 2] = (byte) 255;
        normal[offset + 3] = (byte) 255;
        specular[offset] = (byte) 200;
      }
    }
    packs.materials(
        new MaterialAtlas.Layout(
            ATLAS_ID,
            SIZE,
            SIZE,
            0,
            List.of(new MaterialAtlas.Sprite("test:block/a", 16, 32, 16, 16))),
        Map.of("test:block/a", new RgbaImage(16, 16, normal)),
        Map.of("test:block/a", new RgbaImage(16, 16, specular)));
    packs.uploadMaterials();
    Target target = Target.create(SIZE, SIZE, true);
    try {
      begin(target);
      drawTextured(atlas);
      read(target.colorTexture());
      int screenY = SIZE - 1 - (32 + 5);
      expect(
          "material: sprite texel (3,5) of the normal and specular companions",
          48,
          80,
          200,
          pixel(16 + 3, screenY));
      expect("material: outside sprites the defaults are used", 128, 128, 0, pixel(4, 4));
      begin(target);
      drawTextured(white);
      read(target.colorTexture());
      expect(
          "material: a draw from another texture gets the flat defaults",
          128,
          128,
          0,
          pixel(20, 20));
    } finally {
      target.close();
    }
  }

  private void reloadAndResizeReleaseEveryHandle() {
    apply();
    settle();
    int[] before = handles();
    for (String pack : List.of("tint", "gbuffer", "pass", "material", "depth")) {
      apply(PackDirectory.plan(pack));
      if (packs.needsScene()) {
        packs.scene(SIZE / 2, SIZE / 2);
        packs.scene(SIZE, SIZE);
      }
      settle();
    }
    apply(PackDirectory.plan("tint"), PackDirectory.plan("pass"));
    settle();
    apply();
    packs.close();
    packs.close();
    pipelines.releaseMrt();
    settle();
    int[] after = handles();
    report(
        "reload, resize and double close leave no bgfx handle behind: before "
            + Arrays.toString(before)
            + " after "
            + Arrays.toString(after),
        Arrays.equals(before, after));
  }

  private void settle() {
    for (int i = 0; i < 3; i++) {
      backend.beginFrame(new FrameSize(SIZE, SIZE), null);
      backend.endFrame();
    }
  }

  private int[] handles() {
    BGFXStats stats = bgfx_get_stats();
    return new int[] {
      stats.numPrograms(),
      stats.numShaders(),
      stats.numTextures(),
      stats.numFrameBuffers(),
      stats.numUniforms()
    };
  }

  private void begin(Target target) {
    backend.beginFrame(new FrameSize(SIZE, SIZE), null);
    backend.bindTarget(target);
    backend.clear(BOTH, 0x000000FF, 1.0f);
  }

  private void drawSolid(float x0, float x1, float z) {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_solid");
    DrawContext lit = litContext(white, semantics);
    lit.override = packs.override("rendertype_solid");
    submit(quad(x0, x1, z), lit);
  }

  private void drawBuiltIn(float x0, float x1, float z) {
    DrawContext lit = litContext(white, ShaderSemantics.of("rendertype_solid"));
    lit.override = null;
    submit(quad(x0, x1, z), lit);
  }

  private void drawTextured(Texture texture) {
    DrawContext lit = litContext(texture, ShaderSemantics.of("rendertype_solid"));
    lit.override = packs.override("rendertype_solid");
    submit(quad(-1, 1, 0.5f), lit);
  }

  private DrawContext litContext(Texture texture, ShaderSemantics semantics) {
    PixelCheck.reset(context, semantics);
    ShaderInputs.fog(semantics, Float.MAX_VALUE, 0, 0, context.fogParams());
    ShaderInputs.light(semantics, true, false, false, context.lightParams());
    state.depthTest(true);
    state.depthMask(true);
    return context.set(
        DrawContext.Kind.TEXTURE_LIGHT, texture, lightmap, null, false, state.snapshot(SIZE, SIZE));
  }

  private void submit(Batch batch, DrawContext context) {
    if (!pipelines.submit(batch, context))
      throw new IllegalStateException("pack check draw was rejected");
  }

  private static Batch quad(float x0, float x1, float z) {
    float[] positions = {x0, -1, z, x1, -1, z, x1, 1, z, x0, 1, z};
    float u0 = (x0 + 1) / 2;
    float u1 = (x1 + 1) / 2;
    float[] uv0 = {u0, 0, u1, 0, u1, 1, u0, 1};
    short light = (short) 0xF0;
    short[] lightUv = {light, light, light, light, light, light, light, light};
    return PixelCheck.batch(positions, 0xFFFFFFFF, uv0, null, lightUv, null);
  }

  private void read(short texture) {
    bgfx_set_view_rect(READ_VIEW, 0, 0, SIZE, SIZE);
    bgfx_touch(READ_VIEW);
    bgfx_blit(READ_VIEW, readback, 0, 0, 0, 0, texture, 0, 0, 0, 0, SIZE, SIZE, 1);
    int ready = bgfx_read_texture(readback, pixels, 0);
    backend.endFrame();
    for (int frame = 0; frame < 8 && bgfx_frame(false) < ready; frame++) {}
  }

  private int[] pixel(int x, int y) {
    int offset = (y * SIZE + x) * 4;
    return new int[] {
      Byte.toUnsignedInt(pixels.get(offset)),
      Byte.toUnsignedInt(pixels.get(offset + 1)),
      Byte.toUnsignedInt(pixels.get(offset + 2))
    };
  }

  private void report(String name, boolean ok) {
    if (!ok) failures++;
    System.out.printf("pixel %-4s %s%n", ok ? "ok" : "FAIL", name);
  }

  private void expect(String name, int red, int green, int blue, int[] actual) {
    boolean ok =
        Math.abs(actual[0] - red) <= 2
            && Math.abs(actual[1] - green) <= 2
            && Math.abs(actual[2] - blue) <= 2;
    if (!ok) failures++;
    System.out.printf(
        "pixel %-4s %-70s expected %3d,%3d,%3d got %3d,%3d,%3d%n",
        ok ? "ok" : "FAIL", name, red, green, blue, actual[0], actual[1], actual[2]);
  }

  private void close() {
    packs.close();
    post.close();
    for (Texture texture : new Texture[] {white, lightmap, atlas})
      if (texture != null) texture.close();
    if (BGFX_HANDLE_IS_VALID(readback)) bgfx_destroy_texture(readback);
    MemoryUtil.memFree(pixels);
  }
}
