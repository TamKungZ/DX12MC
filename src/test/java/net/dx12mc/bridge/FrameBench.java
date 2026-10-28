package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.BenchViews;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.ScreenCapture;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PipelineSet;
import net.dx12mc.graphics.pipeline.PostPipeline;
import net.dx12mc.pack.PackDirectory;
import net.dx12mc.pack.PackSet;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.bgfx.BGFXStats;
import org.lwjgl.bgfx.BGFXViewStats;

final class FrameBench {
  static final int WIDTH = 1920;
  static final int HEIGHT = 1009;
  private static final int RENDER_DISTANCE = 12;
  private static final int REGION_XZ = Integer.getInteger("bench.regionXz", 32);
  private static final int REGION_Y = Integer.getInteger("bench.regionY", 16);
  private static final int SEA_LEVEL = 62;
  private static final int CAVE_PER_MILLE = Integer.getInteger("bench.caves", 10);
  private static final int ATLAS = 1024;
  private static final float CAMERA_X = 0.5f;
  private static final float CAMERA_Z = 0.5f;
  private static final int GL_SRC_ALPHA = 0x0302;
  private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
  private static final int GL_ONE_MINUS_DST_COLOR = 0x0307;
  private static final int GL_ONE_MINUS_SRC_COLOR = 0x0301;

  private final Backend backend;
  private final PipelineSet pipelines;
  private final boolean profile;
  private final boolean f3;
  private final String effect;
  private final String pack;
  private PackSet packs;
  private final PostPipeline post = new PostPipeline();
  private final float[] postValues = new float[PostPipeline.UNIFORM_FLOATS];
  private Target mainTarget;
  private Target swapTarget;
  private final Texture atlas = Texture.create(ATLAS, ATLAS, 4);
  private final Texture lightmap = Texture.create(16, 16, 0);
  private final Texture entityTexture = Texture.create(64, 64, 0);
  private final Texture overlay = Texture.create(16, 16, 0);
  private final Texture font = Texture.create(256, 256, 0);
  private final Texture widgets = Texture.create(256, 256, 0);
  private final Texture cloudTexture = Texture.create(256, 256, 0);
  private final List<Page> solid = new ArrayList<>();
  private final List<Page> cutout = new ArrayList<>();
  private final List<Page> translucent = new ArrayList<>();
  private Mesh clouds;
  private final List<String> passNames = new ArrayList<>();
  private final Map<String, double[]> passGpu = new LinkedHashMap<>();
  private final Matrix4f world = new Matrix4f();
  private final Matrix4f fogModel = new Matrix4f();
  private final Matrix4f scratch = new Matrix4f();
  private final FrustumIntersection frustum = new FrustumIntersection();
  private final float cameraY;
  private int drawnPages;
  private long drawnVertices;

  record Page(Mesh mesh, int x, int y, int z, int vertices, int[] facings) {}

  private static final boolean FACING = Boolean.getBoolean("bench.facing");
  private static final int[] FACING_ORDER =
      Boolean.getBoolean("bench.facingSingle")
          ? new int[] {3, 1, 5, 6, 2, 4, 0}
          : new int[] {0, 2, 4, 6, 5, 3, 1};

  private FrameBench(
      Backend backend,
      PipelineSet pipelines,
      boolean profile,
      boolean f3,
      String effect,
      String pack) {
    this.backend = backend;
    this.effect = effect;
    this.pack = pack;
    this.pipelines = pipelines;
    this.profile = profile;
    this.f3 = f3;
    cameraY = height(0, 0) + 19.7f;
  }

  static void run(Backend backend, PipelineSet pipelines, String[] args) {
    boolean profile = has(args, "profile");
    int repeats = 3;
    for (String arg : args)
      if (arg.startsWith("repeat=")) repeats = Integer.parseInt(arg.substring(7));
    String effect = null;
    String compare = null;
    String pack = null;
    for (String arg : args) {
      if (arg.startsWith("pack=")) pack = arg.substring(5);
      if (arg.startsWith("effect=")) effect = arg.substring(7);
      if (arg.startsWith("compare=")) compare = arg.substring(8);
    }
    FrameBench bench = new FrameBench(backend, pipelines, profile, has(args, "f3"), effect, pack);
    try {
      bench.build();
      bench.startPost();
      for (String arg : args) {
        if (arg.startsWith("capture=")) {
          File file = new File("build/frame-bench/" + arg.substring(8) + ".png");
          bench.capture(file);
          if (compare != null)
            compareImages(file, new File("build/frame-bench/" + compare + ".png"));
        }
      }
      for (int run = 0; run < repeats; run++) bench.measure(run);
    } finally {
      bench.close();
    }
  }

  private static void compareImages(File actual, File expected) {
    try {
      BufferedImage a = ImageIO.read(actual);
      BufferedImage b = ImageIO.read(expected);
      if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
        System.out.println("frame compare FAIL: sizes differ");
        return;
      }
      long different = 0;
      for (int y = 0; y < a.getHeight(); y++) {
        for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) different++;
      }
      System.out.println(
          "frame compare "
              + actual.getName()
              + " vs "
              + expected.getName()
              + ": "
              + different
              + " differing pixels of "
              + (long) a.getWidth() * a.getHeight());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static boolean has(String[] args, String flag) {
    for (String arg : args) if (arg.equals(flag)) return true;
    return false;
  }

  private void measure(int run) {
    int warmup = 120;
    int frames = 600;
    passGpu.clear();
    bgfx_set_debug(profile ? BGFX_DEBUG_PROFILER : BGFX_DEBUG_NONE);
    double gpu = 0;
    double cpu = 0;
    double waitRender = 0;
    double submit = 0;
    long primitives = 0;
    long start = 0;
    for (int frame = 0; frame < warmup + frames; frame++) {
      if (frame == warmup) start = System.nanoTime();
      frame();
      if (frame >= warmup) {
        Backend.FrameTiming timing = backend.frameTiming();
        gpu += timing.gpuMillis();
        cpu += timing.cpuMillis();
        waitRender += timing.waitRenderMillis();
        BGFXStats stats = bgfx_get_stats();
        submit += (stats.cpuTimeEnd() - stats.cpuTimeBegin()) * 1000.0 / stats.cpuTimerFreq();
        primitives = stats.numPrims(0);
        if (profile) recordViews();
      }
    }
    double ms = (System.nanoTime() - start) / 1e6 / frames;
    System.out.printf(
        "frame run %d%s%s: pages %d, vertices %d | frame %.3f ms (%.0f fps) | bgfx gpu %.3f ms cpu"
            + " %.3f waitRender %.3f renderThreadSubmit %.3f%n",
        run,
        profile ? " profile" : "",
        f3 ? " f3" : "",
        drawnPages,
        drawnVertices,
        ms,
        1000.0 / ms,
        gpu / frames,
        cpu / frames,
        waitRender / frames,
        submit / frames);
    System.out.println("  triangles submitted per frame: " + primitives);
    if (profile) {
      StringBuilder line = new StringBuilder("  per pass gpu ms:");
      for (Map.Entry<String, double[]> entry : passGpu.entrySet()) {
        line.append(String.format(" %s %.3f", entry.getKey(), entry.getValue()[0] / frames));
      }
      System.out.println(line);
    }
    bgfx_set_debug(BGFX_DEBUG_NONE);
  }

  private void recordViews() {
    BGFXStats stats = bgfx_get_stats();
    if (stats == null) return;
    BGFXViewStats.Buffer views = stats.viewStats();
    for (int i = 0; i < stats.numViews(); i++) {
      BGFXViewStats view = views.get(i);
      int id = view.view();
      if (id >= passNames.size()) continue;
      double ms =
          (view.gpuTimeEnd() - view.gpuTimeBegin()) * 1000.0 / Math.max(1L, stats.gpuTimerFreq());
      passGpu.computeIfAbsent(passNames.get(id), key -> new double[1])[0] += ms;
    }
  }

  private void frame() {
    FrameSize size = new FrameSize(WIDTH, HEIGHT);
    passNames.clear();
    Target scene = packs != null ? packs.scene(WIDTH, HEIGHT) : null;
    backend.beginFrame(size, scene != null ? scene : effect == null ? null : mainTarget);
    if (packs != null) packs.beginFrame();
    float[] fog = {0.02f, 0.03f, 0.06f, 1.0f};
    backend.clear(
        ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT,
        ViewSequence.rgba(fog[0], fog[1], fog[2], 1.0f),
        1.0f);
    camera(frameYaw());
    pass("sky");
    sky();
    if (packs != null && packs.gbuffer()) {
      backend.clearMainAttachments();
      backend.mainMrt(true);
      pipelines.mrt(true);
    }
    pass("solid");
    drawnPages = 0;
    drawnVertices = 0;
    terrain(solid, "rendertype_solid", false, fog);
    pass("cutout_mipped");
    terrain(cutout, "rendertype_cutout_mipped", false, fog);
    pass("entities");
    entities(fog);
    stage("before_translucent");
    pass("translucent");
    terrain(translucent, "rendertype_translucent", true, fog);
    pass("particles");
    particles(fog);
    pass("clouds");
    clouds(fog);
    if (Boolean.getBoolean("bench.portal")) {
      pass("portal");
      endPortal();
    }
    if (packs != null && packs.gbuffer()) {
      backend.mainMrt(false);
      pipelines.mrt(false);
    }
    stage("after_world");
    backend.clear(ViewSequence.GL_DEPTH_BUFFER_BIT, 0, 1.0f);
    pass("hand");
    hand();
    stage("before_gui");
    backend.clear(ViewSequence.GL_DEPTH_BUFFER_BIT, 0, 1.0f);
    pass("gui");
    gui();
    if (effect != null) postProcess();
    else if (scene != null) present(scene);
    backend.endFrame();
  }

  private void stage(String stage) {
    if (packs == null || !packs.hasStage(stage)) return;
    packs.runStage(stage, backend::bindTarget, post);
    backend.bindTarget(null);
  }

  private void present(Target scene) {
    backend.beginPresentView();
    sizes(scene, scene);
    postValues[4] = postValues[5] = postValues[6] = postValues[7] = 1.0f;
    if (!post.draw(
        PostPipeline.Effect.BLIT,
        scene.colorTexture(),
        BGFX_INVALID_HANDLE,
        false,
        postValues,
        post.blendState(false, false))) {
      throw new IllegalStateException("present blit rejected");
    }
  }

  private void postProcess() {
    if (effect.equals("blur")) {
      float[] multipliers = {1.0f, 1.0f, 0.5f, 0.5f, 0.25f, 0.25f};
      for (int i = 0; i < multipliers.length; i++) {
        boolean horizontal = i % 2 == 0;
        Target in = horizontal ? mainTarget : swapTarget;
        Target out = horizontal ? swapTarget : mainTarget;
        sizes(in, out);
        postValues[4] = horizontal ? 1.0f : 0.0f;
        postValues[5] = horizontal ? 0.0f : 1.0f;
        postValues[6] = 5.0f;
        postValues[7] = multipliers[i];
        postPass(PostPipeline.Effect.BOX_BLUR, in, out, horizontal ? null : mainTarget);
      }
    }
    backend.beginPresentView();
    sizes(mainTarget, mainTarget);
    postValues[4] = postValues[5] = postValues[6] = postValues[7] = 1.0f;
    if (!post.draw(
        PostPipeline.Effect.BLIT,
        mainTarget.colorTexture(),
        BGFX_INVALID_HANDLE,
        false,
        postValues,
        post.blendState(false, false))) {
      throw new IllegalStateException("present blit rejected");
    }
  }

  private void sizes(Target in, Target out) {
    Arrays.fill(postValues, 0.0f);
    postValues[0] = in.width();
    postValues[1] = in.height();
    postValues[2] = out.width();
    postValues[3] = out.height();
  }

  private void postPass(PostPipeline.Effect effect, Target in, Target out, Target main) {
    backend.bindTarget(main != null ? null : out);
    backend.clear(ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT, 0, 1.0f);
    if (!post.draw(
        effect,
        in.colorTexture(),
        BGFX_INVALID_HANDLE,
        true,
        postValues,
        post.blendState(false, true))) {
      throw new IllegalStateException("post pass rejected");
    }
  }

  private float frameYaw() {
    return 0.0f;
  }

  private void pass(String name) {
    if (profile && effect == null) BenchViews.split(WIDTH, HEIGHT);
    int view = BenchViews.current();
    while (passNames.size() <= view) passNames.add(name);
    passNames.set(view, name);
  }

  private void camera(float yaw) {
    Matrix4f projection =
        new Matrix4f()
            .perspective(
                (float) Math.toRadians(70.0),
                WIDTH / (float) HEIGHT,
                0.05f,
                RENDER_DISTANCE * 16 * 4.0f);
    projection.rotateX((float) Math.toRadians(35.2)).rotateY(yaw);
    frustum.set(projection);
    world.identity().m22(0.5f).m32(0.5f).mul(projection);
    fogModel.set(world).invert();
    if (packs != null) {
      packs
          .frame()
          .camera(
              projection, new Matrix4f(), CAMERA_X, cameraY, CAMERA_Z, 0.5f, 6000.0f, 0.0f, 0.0f);
      packs.frame().viewSize(WIDTH, HEIGHT);
    }
  }

  private void terrain(List<Page> pages, String program, boolean farFirst, float[] fogColor) {
    ShaderSemantics semantics = ShaderSemantics.of(program);
    RenderState state = new RenderState();
    state.cull(true);
    if (Boolean.getBoolean("bench.scissor")) {
      state.scissor(true);
      state.scissorBox(0, 0, 1, 1);
    }
    if (farFirst) {
      state.blend(true);
      state.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, GL_ONE_MINUS_SRC_ALPHA);
    }
    DrawContext context = new DrawContext();
    prepare(context, semantics, fogColor, 172.8f, 192.0f, 1);
    ShaderInputs.light(semantics, true, false, false, context.lightParams());
    context.textureLod()[0] = atlas.maxLod();
    context.override = packs == null ? null : packs.override(program);
    context.set(
        DrawContext.Kind.TEXTURE_LIGHT,
        atlas,
        lightmap,
        null,
        semantics.discards(),
        state.snapshot(WIDTH, HEIGHT));
    int count = pages.size();
    for (int i = 0; i < count; i++) {
      Page page = pages.get(farFirst ? count - 1 - i : i);
      float x = page.x() - CAMERA_X;
      float y = page.y() - cameraY;
      float z = page.z() - CAMERA_Z;
      if (!frustum.testAab(x, y, z, x + REGION_XZ, y + REGION_Y, z + REGION_XZ)) continue;
      scratch.set(world).translate(x, y, z).get(context.matrix());
      drawnPages++;
      if (page.facings() != null) {
        drawFacing(page, x, y, z, context);
        continue;
      }
      int split = Integer.getInteger("bench.split", 1);
      int quads = page.vertices() / 4;
      for (int part = 0; part < split; part++) {
        int from = quads * part / split;
        int to = quads * (part + 1) / split;
        if (split > 1) page.mesh().drawRange(from * 6, (to - from) * 6);
        if (!pipelines.submitSection(page.mesh(), context)) {
          throw new IllegalStateException("terrain page rejected");
        }
      }
      if (split > 1) page.mesh().drawAll();
      drawnVertices += page.vertices();
    }
    pipelines.endSectionLayer();
  }

  private void drawFacing(Page page, float x, float y, float z, DrawContext context) {
    boolean[] visible = {
      x < 0, x + REGION_XZ > 0, y < 0, y + REGION_Y > 0, z < 0, z + REGION_XZ > 0, true
    };
    int[] start = page.facings();
    if (Boolean.getBoolean("bench.facingSingle")) {
      int first = -1;
      int end = -1;
      for (int o = 0; o < FACING_ORDER.length; o++) {
        if (!visible[FACING_ORDER[o]] || start[o + 1] == start[o]) continue;
        if (first < 0) first = start[o];
        end = start[o + 1];
      }
      if (first >= 0) {
        page.mesh().drawRange(first * 6, (end - first) * 6);
        if (!pipelines.submitSection(page.mesh(), context)) {
          throw new IllegalStateException("terrain range rejected");
        }
        page.mesh().drawAll();
        drawnVertices += (end - first) * 4L;
      }
      return;
    }
    int runFirst = -1;
    int runEnd = -1;
    for (int o = 0; o <= FACING_ORDER.length; o++) {
      int bucket = o < FACING_ORDER.length ? FACING_ORDER[o] : -1;
      boolean draw = bucket >= 0 && visible[bucket];
      if (draw && runFirst < 0) runFirst = start[o];
      if (draw) runEnd = start[o + 1];
      if (!draw && runFirst >= 0) {
        if (runEnd > runFirst) {
          page.mesh().drawRange(runFirst * 6, (runEnd - runFirst) * 6);
          if (!pipelines.submitSection(page.mesh(), context)) {
            throw new IllegalStateException("terrain range rejected");
          }
          drawnVertices += (runEnd - runFirst) * 4L;
        }
        runFirst = -1;
      }
    }
    page.mesh().drawAll();
  }

  private void sky() {
    RenderState state = new RenderState();
    state.depthTest(false);
    state.depthMask(false);
    DrawContext context = new DrawContext();
    prepare(context, ShaderSemantics.of("position"), new float[4], 0.0f, 1.0f, 0);
    context.fogParams()[3] = 0.0f;
    context.set(DrawContext.Kind.COLOR, null, null, null, false, state.snapshot(WIDTH, HEIGHT));
    scratch.set(world).get(context.matrix());
    draw(skyDisc(), context);
    state.blend(true);
    state.blendFuncSeparate(GL_SRC_ALPHA, 1, 1, 0);
    context.set(DrawContext.Kind.COLOR, null, null, null, false, state.snapshot(WIDTH, HEIGHT));
    draw(stars, context);
  }

  private void entities(float[] fogColor) {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_entity_cutout_no_cull");
    RenderState state = new RenderState();
    DrawContext context = new DrawContext();
    prepare(context, semantics, fogColor, 172.8f, 192.0f, 1);
    ShaderInputs.light(semantics, true, true, true, context.lightParams());
    context.lightDirection0()[1] = 1.0f;
    context.lightDirection1()[0] = 1.0f;
    context.set(
        DrawContext.Kind.ENTITY,
        entityTexture,
        lightmap,
        overlay,
        semantics.discards(),
        state.snapshot(WIDTH, HEIGHT));
    for (int e = 0; e < entityPositions.length; e++) {
      float[] p = entityPositions[e];
      scratch.set(world).translate(p[0] - CAMERA_X, p[1] - cameraY, p[2] - CAMERA_Z);
      scratch.get(context.matrix());
      draw(entityMesh, context);
    }
  }

  private void particles(float[] fogColor) {
    ShaderSemantics semantics = ShaderSemantics.of("particle");
    RenderState state = new RenderState();
    state.blend(true);
    state.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, GL_ONE_MINUS_SRC_ALPHA);
    DrawContext context = new DrawContext();
    prepare(context, semantics, fogColor, 172.8f, 192.0f, 1);
    ShaderInputs.light(semantics, true, false, false, context.lightParams());
    context.set(
        DrawContext.Kind.TEXTURE_LIGHT,
        atlas,
        lightmap,
        null,
        semantics.discards(),
        state.snapshot(WIDTH, HEIGHT));
    scratch.set(world).translate(-CAMERA_X, -cameraY, -CAMERA_Z).get(context.matrix());
    draw(particleMesh, context);
  }

  private void clouds(float[] fogColor) {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_clouds");
    RenderState state = new RenderState();
    state.cull(true);
    state.blend(true);
    state.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, GL_ONE_MINUS_SRC_ALPHA);
    DrawContext context = new DrawContext();
    prepare(context, semantics, fogColor, 0.0f, 384.0f, 0);
    scratch.set(world).translate(-CAMERA_X - 96, 192.33f - cameraY, -CAMERA_Z - 96);
    scratch.get(context.matrix());
    state.colorMask(false, false, false, false);
    context.set(
        DrawContext.Kind.TEXTURE,
        cloudTexture,
        null,
        null,
        semantics.discards(),
        state.snapshot(WIDTH, HEIGHT));
    pipelines.submit(clouds, context);
    state.colorMask(true, true, true, true);
    context.set(
        DrawContext.Kind.TEXTURE,
        cloudTexture,
        null,
        null,
        semantics.discards(),
        state.snapshot(WIDTH, HEIGHT));
    pipelines.submit(clouds, context);
  }

  private void hand() {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_entity_solid");
    RenderState state = new RenderState();
    state.cull(true);
    DrawContext context = new DrawContext();
    prepare(context, semantics, new float[4], 0.0f, 1.0f, 0);
    context.fogParams()[3] = 0.0f;
    ShaderInputs.light(semantics, true, true, true, context.lightParams());
    context.lightDirection0()[1] = 1.0f;
    context.set(
        DrawContext.Kind.ENTITY, atlas, lightmap, overlay, false, state.snapshot(WIDTH, HEIGHT));
    Matrix4f projection =
        new Matrix4f()
            .perspective((float) Math.toRadians(70.0), WIDTH / (float) HEIGHT, 0.05f, 768f);
    new Matrix4f()
        .m22(0.5f)
        .m32(0.5f)
        .mul(projection)
        .translate(0.56f, -0.52f, -0.72f)
        .rotateY(0.8f)
        .scale(0.4f)
        .get(context.matrix());
    draw(handMesh, context);
  }

  private void endPortal() {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_end_gateway");
    RenderState state = new RenderState();
    state.depthTest(false);
    DrawContext context = new DrawContext();
    prepare(context, semantics, new float[4], 0.0f, 1.0f, 0);
    ShaderInputs.portal(semantics, 0.3f, context.portalParams());
    context.set(
        DrawContext.Kind.END_PORTAL,
        cloudTexture,
        null,
        entityTexture,
        false,
        state.snapshot(WIDTH, HEIGHT));
    new Matrix4f().m22(0.5f).m32(0.5f).get(context.matrix());
    draw(
        plain(new float[] {-1, -1, 0.5f, 1, -1, 0.5f, 1, 1, 0.5f, -1, 1, 0.5f}, 0xFFFFFFFF),
        context);
  }

  private void gui() {
    Matrix4f ortho =
        new Matrix4f()
            .m22(0.5f)
            .m32(0.5f)
            .mul(new Matrix4f().setOrtho(0, WIDTH / 2.0f, HEIGHT / 2.0f, 0, 1000, 21000))
            .translate(0, 0, -11000);
    RenderState state = new RenderState();
    state.depthTest(false);
    state.blend(true);
    state.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, GL_ONE_MINUS_SRC_ALPHA);
    ShaderSemantics guiTex = ShaderSemantics.of("position_tex");
    DrawContext context = new DrawContext();
    prepare(context, guiTex, new float[4], Float.MAX_VALUE, 0.0f, 0);
    context.set(
        DrawContext.Kind.TEXTURE,
        widgets,
        null,
        null,
        guiTex.discards(),
        state.snapshot(WIDTH, HEIGHT));
    ortho.get(context.matrix());
    draw(hotbar, context);
    ShaderSemantics item = ShaderSemantics.of("rendertype_entity_solid");
    RenderState itemState = new RenderState();
    DrawContext itemContext = new DrawContext();
    prepare(itemContext, item, new float[4], 0.0f, 1.0f, 0);
    itemContext.fogParams()[3] = 0.0f;
    ShaderInputs.light(item, true, true, true, itemContext.lightParams());
    itemContext.lightDirection0()[1] = 1.0f;
    itemContext.set(
        DrawContext.Kind.ENTITY,
        atlas,
        lightmap,
        overlay,
        false,
        itemState.snapshot(WIDTH, HEIGHT));
    for (int slot = 0; slot < 9; slot++) {
      new Matrix4f(ortho)
          .translate(WIDTH / 4.0f - 80 + slot * 20, HEIGHT / 2.0f - 11, 150)
          .scale(16, -16, 16)
          .rotateX(0.52f)
          .rotateY(0.785f)
          .get(itemContext.matrix());
      draw(itemCube, itemContext);
    }
    if (f3) {
      ShaderSemantics fill = ShaderSemantics.of("position_color");
      DrawContext fillContext = new DrawContext();
      prepare(fillContext, fill, new float[4], Float.MAX_VALUE, 0.0f, 0);
      fillContext.set(
          DrawContext.Kind.COLOR, null, null, null, fill.discards(), state.snapshot(WIDTH, HEIGHT));
      ortho.get(fillContext.matrix());
      draw(f3Backgrounds, fillContext);
      ShaderSemantics text = ShaderSemantics.of("rendertype_text");
      DrawContext textContext = new DrawContext();
      prepare(textContext, text, new float[4], Float.MAX_VALUE, 0.0f, 0);
      ShaderInputs.light(text, true, false, false, textContext.lightParams());
      textContext.set(
          DrawContext.Kind.TEXTURE_LIGHT,
          font,
          lightmap,
          null,
          text.discards(),
          state.snapshot(WIDTH, HEIGHT));
      ortho.get(textContext.matrix());
      draw(f3Text, textContext);
    }
    RenderState crosshair = new RenderState();
    crosshair.depthTest(false);
    crosshair.blend(true);
    crosshair.blendFuncSeparate(GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR, 1, 0);
    context.set(
        DrawContext.Kind.TEXTURE,
        widgets,
        null,
        null,
        guiTex.discards(),
        crosshair.snapshot(WIDTH, HEIGHT));
    ortho.get(context.matrix());
    draw(crosshairQuad, context);
  }

  private void draw(Batch batch, DrawContext context) {
    if (!pipelines.submit(batch, context)) throw new IllegalStateException("bench draw rejected");
  }

  private static void prepare(
      DrawContext context,
      ShaderSemantics semantics,
      float[] fogColor,
      float fogStart,
      float fogEnd,
      int shape) {
    new Matrix4f().get(context.textureMatrix());
    Arrays.fill(context.tint(), 1.0f);
    System.arraycopy(fogColor, 0, context.fogColor(), 0, 4);
    ShaderInputs.material(semantics, context.material());
    ShaderInputs.fog(semantics, fogStart, fogEnd, shape, context.fogParams());
    context.textureLod()[0] = 0.0f;
  }

  private Batch stars;
  private Batch entityMesh;
  private Batch particleMesh;
  private Batch handMesh;
  private Batch hotbar;
  private Batch itemCube;
  private Batch f3Backgrounds;
  private Batch f3Text;
  private Batch crosshairQuad;
  private float[][] entityPositions;

  private void build() {
    textures();
    heightOrigin = RENDER_DISTANCE * 16 + 2;
    heightSize = heightOrigin * 2 + 1;
    heights = new int[heightSize * heightSize];
    for (int x = -heightOrigin; x <= heightOrigin; x++) {
      for (int z = -heightOrigin; z <= heightOrigin; z++) {
        heights[(x + heightOrigin) * heightSize + z + heightOrigin] = height(x, z);
      }
    }
    int regions = (RENDER_DISTANCE * 16 + REGION_XZ - 1) / REGION_XZ;
    long vertices = 0;
    for (int rx = -regions; rx < regions; rx++) {
      for (int rz = -regions; rz < regions; rz++) {
        for (int ry = 0; ry < 96 / REGION_Y; ry++) {
          Quads solidQuads = new Quads();
          Quads cutoutQuads = new Quads();
          Quads waterQuads = new Quads();
          region(
              rx * REGION_XZ, ry * REGION_Y, rz * REGION_XZ, solidQuads, cutoutQuads, waterQuads);
          vertices += add(solid, solidQuads, rx, ry, rz);
          vertices += add(cutout, cutoutQuads, rx, ry, rz);
          vertices += add(translucent, waterQuads, rx, ry, rz);
          flushStaging();
        }
      }
    }
    sortNearFirst(solid);
    sortNearFirst(cutout);
    sortNearFirst(translucent);
    System.out.printf(
        "frame world: %d solid, %d cutout, %d translucent pages, %d vertices%n",
        solid.size(), cutout.size(), translucent.size(), vertices);
    clouds = buildClouds();
    stars = buildStars();
    entityPositions = new float[24][];
    for (int e = 0; e < entityPositions.length; e++) {
      float x = (e % 6) * 7 - 18;
      float z = -12 - (e / 6) * 9;
      entityPositions[e] = new float[] {x, height((int) x, (int) z) + 1, z};
    }
    entityMesh = buildEntity();
    particleMesh = buildParticles();
    handMesh = buildEntity();
    itemCube = buildEntity();
    hotbar = guiQuads(new float[][] {{WIDTH / 4.0f - 91, HEIGHT / 2.0f - 22, 182, 22}});
    crosshairQuad = guiQuads(new float[][] {{WIDTH / 4.0f - 7.5f, HEIGHT / 4.0f - 7.5f, 15, 15}});
    float[][] lines = new float[64][];
    List<float[]> glyphs = new ArrayList<>();
    for (int line = 0; line < lines.length; line++) {
      boolean right = line >= 28;
      int row = right ? line - 28 : line;
      int chars = 30 + (line * 7) % 40;
      float x = right ? WIDTH / 2.0f - 2 - chars * 6 : 2;
      float y = 2 + row * 9;
      lines[line] = new float[] {x - 1, y - 1, chars * 6 + 1, 9};
      for (int c = 0; c < chars; c++) glyphs.add(new float[] {x + c * 6, y, 5, 8});
    }
    f3Backgrounds = colorQuads(lines, 0x50505090);
    f3Text = guiQuads(glyphs.toArray(new float[0][]));
  }

  private void textures() {
    java.util.Random random = new java.util.Random(11);
    atlas.sampler(BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT, 4.0f);
    for (int level = 0; level <= 4; level++) {
      int size = ATLAS >> level;
      byte[] texels = new byte[size * size * 4];
      random.nextBytes(texels);
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          int offset = (y * size + x) * 4;
          boolean cutoutTile = y * ATLAS / size >= ATLAS / 2;
          texels[offset + 3] = cutoutTile && (texels[offset + 3] & 1) == 0 ? 0 : (byte) -1;
        }
      }
      atlas.update(level, 0, 0, size, size, texels);
    }
    byte[] light = new byte[16 * 16 * 4];
    for (int i = 0; i < 256; i++) {
      byte value = (byte) (40 + (i % 16) * 13);
      light[i * 4] = light[i * 4 + 1] = light[i * 4 + 2] = value;
      light[i * 4 + 3] = -1;
    }
    lightmap.sampler(BGFX_SAMPLER_UVW_CLAMP, 0.0f);
    lightmap.update(0, 0, 0, 16, 16, light);
    for (Texture texture : new Texture[] {entityTexture, font, widgets, cloudTexture}) {
      int size = texture == entityTexture ? 64 : 256;
      byte[] texels = new byte[size * size * 4];
      random.nextBytes(texels);
      if (texture == cloudTexture) {
        for (int i = 3; i < texels.length; i += 4) texels[i] = (byte) 0xCC;
      }
      texture.sampler(BGFX_SAMPLER_POINT, 0.0f);
      texture.update(0, 0, 0, size, size, texels);
    }
    byte[] white = new byte[16 * 16 * 4];
    Arrays.fill(white, (byte) -1);
    overlay.sampler(BGFX_SAMPLER_POINT | BGFX_SAMPLER_UVW_CLAMP, 0.0f);
    overlay.update(0, 0, 0, 16, 16, white);
  }

  private void flushStaging() {
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    backend.endFrame();
  }

  private long add(List<Page> pages, Quads quads, int rx, int ry, int rz) {
    if (quads.vertices == 0) return 0;
    int maxVertices = 0x10000 - 4;
    for (int first = 0; first < quads.vertices; first += maxVertices) {
      int count = Math.min(maxVertices, quads.vertices - first);
      int[] facings = null;
      if (FACING && pages != translucent) facings = quads.sortByFacing(first, count);
      Batch batch = quads.batch(first, count);
      pages.add(
          new Page(
              Mesh.createSharedQuads(batch, pipelines.layout()),
              rx * REGION_XZ,
              ry * REGION_Y,
              rz * REGION_XZ,
              count,
              facings));
    }
    return quads.vertices;
  }

  private static void sortNearFirst(List<Page> pages) {
    pages.sort((a, b) -> Float.compare(distance(a), distance(b)));
  }

  private static float distance(Page page) {
    float x = page.x() + REGION_XZ / 2.0f;
    float z = page.z() + REGION_XZ / 2.0f;
    return x * x + z * z;
  }

  static int height(int x, int z) {
    double h =
        68
            + 7.0 * Math.sin(x / 23.0) * Math.cos(z / 19.0)
            + 4.0 * Math.sin((x + z) / 11.0)
            + 2.0 * Math.sin(x / 5.3 + Math.cos(z / 7.1));
    return (int) Math.floor(h + (hash(x, 0, z) & 1));
  }

  private static int hash(int x, int y, int z) {
    int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
    h ^= h >>> 13;
    h *= 0x5bd1e995;
    return (h ^ (h >>> 15)) & 0x7FFFFFFF;
  }

  private int[] heights;
  private int heightOrigin;
  private int heightSize;

  private int cachedHeight(int x, int z) {
    return heights[(x + heightOrigin) * heightSize + z + heightOrigin];
  }

  private boolean tree(int x, int z) {
    return cachedHeight(x, z) >= SEA_LEVEL && hash(x, 3, z) % 1000 < 4;
  }

  private void region(int x0, int y0, int z0, Quads solid, Quads cutout, Quads water) {
    int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    int[] sideFaces = {0, 1, 4, 5};
    int limit = RENDER_DISTANCE * 16;
    for (int x = x0; x < x0 + REGION_XZ; x++) {
      for (int z = z0; z < z0 + REGION_XZ; z++) {
        if (Math.abs(x) > limit || Math.abs(z) > limit) continue;
        int h = cachedHeight(x, z);
        int lx = x - x0;
        int lz = z - z0;
        int top = Math.min(y0 + REGION_Y, h + 8);
        for (int y = Math.max(y0, 1); y < top; y++) {
          int ly = y - y0;
          if (y == h) solid.face(lx, ly, lz, 2, sprite(x, y, z, false), 0xFFFFFFFF);
          if (y <= h) {
            for (int d = 0; d < 4; d++) {
              if (cachedHeight(x + neighbours[d][0], z + neighbours[d][1]) < y) {
                solid.face(lx, ly, lz, sideFaces[d], sprite(x, y, z, false), 0xCCCCCCFF);
              }
            }
          }
          if (y < h - 3 && y > 6 && hash(x, y, z) % 1000 < CAVE_PER_MILLE) {
            for (int face = 0; face < 6; face++) {
              solid.inwardFace(lx, ly, lz, face, sprite(x, y, z, false), 0x999999FF);
            }
          }
          if (h < SEA_LEVEL && y == SEA_LEVEL) water.top(lx, ly, lz, 14.0f / 16.0f, 3, 0x3F76E4C0);
          if (h >= SEA_LEVEL && y == h + 1 && hash(x, 7, z) % 100 < 30 && !tree(x, z)) {
            cutout.cross(lx, ly, lz, sprite(x, y, z, true));
          }
          if (tree(x, z) && y > h && y <= h + 4) {
            for (int face : sideFaces) solid.face(lx, ly, lz, face, sprite(x, y, z, false), -1);
          }
          for (int tx = -1; tx <= 1; tx++) {
            for (int tz = -1; tz <= 1; tz++) {
              if (!tree(x - tx, z - tz)) continue;
              int ch = cachedHeight(x - tx, z - tz);
              if (y < ch + 4 || y > ch + 6) continue;
              for (int face = 0; face < 6; face++) {
                cutout.face(lx, ly, lz, face, sprite(x, y, z, true), 0x55AA44FF);
              }
            }
          }
        }
      }
    }
  }

  private static int sprite(int x, int y, int z, boolean cutout) {
    int tile = hash(x, y, z) % 1024;
    return cutout ? 2048 + tile : tile;
  }

  private Mesh buildClouds() {
    Quads quads = new Quads();
    for (int cx = 0; cx < 192; cx += 12) {
      for (int cz = 0; cz < 192; cz += 12) {
        if (hash(cx, 1, cz) % 100 >= 45) continue;
        quads.box(cx, 0, cz, 12, 4, 12);
      }
    }
    return Mesh.createSharedQuads(quads.batch(0, quads.vertices), pipelines.layout());
  }

  private static Batch buildStars() {
    java.util.Random random = new java.util.Random(10842);
    int count = 1500;
    float[] positions = new float[count * 12];
    for (int s = 0; s < count; s++) {
      double x = random.nextFloat() * 2 - 1;
      double y = random.nextFloat() * 2 - 1;
      double z = random.nextFloat() * 2 - 1;
      double length = Math.sqrt(x * x + y * y + z * z);
      double size = 0.15 + random.nextFloat() * 0.1;
      x = x / length * 100;
      y = Math.abs(y / length * 100);
      z = z / length * 100;
      for (int c = 0; c < 4; c++) {
        positions[s * 12 + c * 3] = (float) (x + ((c == 1 || c == 2) ? size : -size));
        positions[s * 12 + c * 3 + 1] = (float) y;
        positions[s * 12 + c * 3 + 2] = (float) (z + (c >= 2 ? size : -size));
      }
    }
    return plain(positions, 0xFFFFFFA0);
  }

  private static Batch skyDisc() {
    float[] positions = {
      -512, 16, -512, 512, 16, -512, 512, 16, 512, -512, 16, 512,
    };
    return plain(positions, 0x1A2440FF);
  }

  private static Batch plain(float[] positions, int rgba) {
    int vertices = positions.length / 3;
    int[] colors = new int[vertices];
    Arrays.fill(colors, rgba);
    return new Batch(
        positions,
        colors,
        new float[0],
        false,
        new short[0],
        false,
        new short[0],
        false,
        new byte[0],
        false,
        quadIndices(vertices),
        vertices,
        VertexFormat.Mode.QUADS);
  }

  private static int[] quadIndices(int vertices) {
    int[] indices = new int[vertices / 4 * 6];
    for (int q = 0; q < vertices / 4; q++) {
      int b = q * 4;
      int i = q * 6;
      indices[i] = b;
      indices[i + 1] = b + 1;
      indices[i + 2] = b + 2;
      indices[i + 3] = b;
      indices[i + 4] = b + 2;
      indices[i + 5] = b + 3;
    }
    return indices;
  }

  private static Batch buildEntity() {
    Quads quads = new Quads();
    quads.box(-0.3f, 0, -0.3f, 0.6f, 0.9f, 0.6f);
    quads.box(-0.25f, 0.9f, -0.25f, 0.5f, 0.5f, 0.5f);
    quads.box(-0.4f, 0.2f, -0.1f, 0.1f, 0.6f, 0.2f);
    quads.box(0.3f, 0.2f, -0.1f, 0.1f, 0.6f, 0.2f);
    quads.box(-0.2f, -0.6f, -0.1f, 0.15f, 0.6f, 0.2f);
    quads.box(0.05f, -0.6f, -0.1f, 0.15f, 0.6f, 0.2f);
    return quads.entityBatch();
  }

  private Batch buildParticles() {
    Quads quads = new Quads();
    java.util.Random random = new java.util.Random(3);
    for (int p = 0; p < 300; p++) {
      float x = random.nextFloat() * 40 - 20;
      float z = -random.nextFloat() * 40;
      float y = height((int) x, (int) z) + 1 + random.nextFloat() * 3;
      quads.quad(x, y, z, 0.1f, 0.1f, 2048 + p);
    }
    return quads.batch(0, quads.vertices);
  }

  private static Batch guiQuads(float[][] rects) {
    Quads quads = new Quads();
    for (float[] r : rects) quads.rect(r[0], r[1], r[2], r[3]);
    return quads.batch(0, quads.vertices);
  }

  private static Batch colorQuads(float[][] rects, int rgba) {
    Quads quads = new Quads();
    for (float[] r : rects) quads.rect(r[0], r[1], r[2], r[3]);
    int[] colors = new int[quads.vertices];
    Arrays.fill(colors, rgba);
    return new Batch(
        Arrays.copyOf(quads.positions, quads.vertices * 3),
        colors,
        new float[0],
        false,
        new short[0],
        false,
        new short[0],
        false,
        new byte[0],
        false,
        quadIndices(quads.vertices),
        quads.vertices,
        VertexFormat.Mode.QUADS);
  }

  private void capture(File file) {
    for (int i = 0; i < 4; i++) frame();
    if (!backend.requestScreenCapture()) frame();
    backend.requestScreenCapture();
    frame();
    ScreenCapture.Frame captured = null;
    for (int i = 0; i < 10 && captured == null; i++) {
      bgfx_frame(false);
      captured = backend.takeScreenCapture();
    }
    if (captured == null) {
      System.out.println("frame capture not delivered");
      return;
    }
    try {
      BufferedImage image =
          new BufferedImage(captured.width(), captured.height(), BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < captured.height(); y++) {
        int row = (captured.yflip() ? captured.height() - 1 - y : y) * captured.pitch();
        for (int x = 0; x < captured.width(); x++) {
          int o = row + x * 4;
          int b = Byte.toUnsignedInt(captured.bgra().get(o));
          int g = Byte.toUnsignedInt(captured.bgra().get(o + 1));
          int r = Byte.toUnsignedInt(captured.bgra().get(o + 2));
          image.setRGB(x, y, r << 16 | g << 8 | b);
        }
      }
      file.getParentFile().mkdirs();
      ImageIO.write(image, "png", file);
      System.out.println("frame capture written to " + file.getAbsolutePath());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    } finally {
      ScreenCapture.free(captured);
    }
  }

  private void startPost() {
    if (pack != null) {
      post.start();
      packs = new PackSet();
      packs.apply(
          List.of(PackDirectory.plan(pack)),
          List.of(),
          (name, values) -> {
            while (values.hasRemaining()) values.put(1.0f);
            return true;
          },
          WIDTH,
          HEIGHT);
      System.out.println("frame pack " + pack + ": " + packs.statuses());
      return;
    }
    if (effect == null) return;
    post.start();
    mainTarget = Target.create(WIDTH, HEIGHT, true);
    swapTarget = Target.create(WIDTH, HEIGHT, true);
  }

  private void close() {
    if (packs != null) packs.close();
    pipelines.releaseMrt();
    if (mainTarget != null) mainTarget.close();
    if (swapTarget != null) swapTarget.close();
    post.close();
    for (List<Page> pages : List.of(solid, cutout, translucent)) {
      for (Page page : pages) page.mesh().close();
    }
    if (clouds != null) clouds.close();
    for (Texture texture :
        new Texture[] {atlas, lightmap, entityTexture, overlay, font, widgets, cloudTexture}) {
      texture.close();
    }
  }

  private static final class Quads {
    float[] positions = new float[1024 * 3];
    int[] colors = new int[1024];
    float[] uv = new float[1024 * 2];
    short[] light = new short[1024 * 2];
    int vertices;

    private void vertex(float x, float y, float z, float u, float v, int rgba) {
      if (vertices == colors.length) {
        int capacity = colors.length * 2;
        positions = Arrays.copyOf(positions, capacity * 3);
        colors = Arrays.copyOf(colors, capacity);
        uv = Arrays.copyOf(uv, capacity * 2);
        light = Arrays.copyOf(light, capacity * 2);
      }
      positions[vertices * 3] = x;
      positions[vertices * 3 + 1] = y;
      positions[vertices * 3 + 2] = z;
      colors[vertices] = rgba;
      uv[vertices * 2] = u;
      uv[vertices * 2 + 1] = v;
      light[vertices * 2] = (short) (hashLight(x, z) * 16);
      light[vertices * 2 + 1] = 240;
      vertices++;
    }

    private static int hashLight(float x, float z) {
      return ((int) (x * 3 + z * 5) & 7);
    }

    private void corners(float[][] c, int sprite, int rgba) {
      float u0 = (sprite % 64) * 16.0f / ATLAS;
      float v0 = (sprite / 64) * 16.0f / ATLAS;
      float s = 16.0f / ATLAS;
      vertex(c[0][0], c[0][1], c[0][2], u0, v0, rgba);
      vertex(c[1][0], c[1][1], c[1][2], u0, v0 + s, rgba);
      vertex(c[2][0], c[2][1], c[2][2], u0 + s, v0 + s, rgba);
      vertex(c[3][0], c[3][1], c[3][2], u0 + s, v0, rgba);
    }

    private static final float[][] NORMALS = {
      {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    void face(float x, float y, float z, int face, int sprite, int rgba) {
      corners(oriented(cube(x, y, z, 1, 1, 1, face), NORMALS[face]), sprite, rgba);
    }

    void inwardFace(float x, float y, float z, int face, int sprite, int rgba) {
      float[] n = NORMALS[face];
      corners(
          oriented(cube(x, y, z, 1, 1, 1, face), new float[] {-n[0], -n[1], -n[2]}), sprite, rgba);
    }

    void top(float x, float y, float z, float height, int sprite, int rgba) {
      float[][] c = {
        {x, y + height, z},
        {x, y + height, z + 1},
        {x + 1, y + height, z + 1},
        {x + 1, y + height, z}
      };
      corners(oriented(c, NORMALS[2]), sprite, rgba);
    }

    void cross(float x, float y, float z, int sprite) {
      float[][] a = {{x, y, z}, {x, y + 1, z}, {x + 1, y + 1, z + 1}, {x + 1, y, z + 1}};
      float[][] b = {{x + 1, y, z}, {x + 1, y + 1, z}, {x, y + 1, z + 1}, {x, y, z + 1}};
      for (float[][] plane : new float[][][] {a, b}) {
        corners(plane, sprite, 0x66AA44FF);
        float[][] back = {plane[3], plane[2], plane[1], plane[0]};
        corners(back, sprite, 0x66AA44FF);
      }
    }

    void box(float x, float y, float z, float w, float h, float d) {
      for (int face = 0; face < 6; face++) {
        corners(oriented(cube(x, y, z, w, h, d, face), NORMALS[face]), face, 0xFFFFFFFF);
      }
    }

    void quad(float x, float y, float z, float w, float h, int sprite) {
      float[][] c = {{x, y, z}, {x, y + h, z}, {x + w, y + h, z}, {x + w, y, z}};
      corners(oriented(c, NORMALS[4]), sprite, 0xFFFFFFFF);
    }

    void rect(float x, float y, float w, float h) {
      vertex(x, y, 0, 0, 0, 0xFFFFFFFF);
      vertex(x, y + h, 0, 0, 1, 0xFFFFFFFF);
      vertex(x + w, y + h, 0, 1, 1, 0xFFFFFFFF);
      vertex(x + w, y, 0, 1, 0, 0xFFFFFFFF);
    }

    private static float[][] cube(float x, float y, float z, float w, float h, float d, int face) {
      float x1 = x + w;
      float y1 = y + h;
      float z1 = z + d;
      return switch (face) {
        case 0 -> new float[][] {{x1, y, z}, {x1, y1, z}, {x1, y1, z1}, {x1, y, z1}};
        case 1 -> new float[][] {{x, y, z}, {x, y1, z}, {x, y1, z1}, {x, y, z1}};
        case 2 -> new float[][] {{x, y1, z}, {x, y1, z1}, {x1, y1, z1}, {x1, y1, z}};
        case 3 -> new float[][] {{x, y, z}, {x, y, z1}, {x1, y, z1}, {x1, y, z}};
        case 4 -> new float[][] {{x, y, z1}, {x, y1, z1}, {x1, y1, z1}, {x1, y, z1}};
        default -> new float[][] {{x, y, z}, {x, y1, z}, {x1, y1, z}, {x1, y, z}};
      };
    }

    private static float[][] oriented(float[][] c, float[] normal) {
      float ax = c[1][0] - c[0][0];
      float ay = c[1][1] - c[0][1];
      float az = c[1][2] - c[0][2];
      float bx = c[2][0] - c[0][0];
      float by = c[2][1] - c[0][1];
      float bz = c[2][2] - c[0][2];
      float nx = ay * bz - az * by;
      float ny = az * bx - ax * bz;
      float nz = ax * by - ay * bx;
      if (nx * normal[0] + ny * normal[1] + nz * normal[2] >= 0) return c;
      return new float[][] {c[3], c[2], c[1], c[0]};
    }

    int[] sortByFacing(int first, int count) {
      int quads = count / 4;
      int[] bucket = new int[quads];
      int[] sizes = new int[7];
      for (int q = 0; q < quads; q++) {
        bucket[q] = facing(first + q * 4);
        sizes[bucket[q]]++;
      }
      int[] start = new int[FACING_ORDER.length + 1];
      int[] cursor = new int[7];
      for (int o = 0; o < FACING_ORDER.length; o++) {
        cursor[FACING_ORDER[o]] = start[o];
        start[o + 1] = start[o] + sizes[FACING_ORDER[o]];
      }
      float[] p = Arrays.copyOfRange(positions, first * 3, (first + count) * 3);
      int[] c = Arrays.copyOfRange(colors, first, first + count);
      float[] t = Arrays.copyOfRange(uv, first * 2, (first + count) * 2);
      short[] l = Arrays.copyOfRange(light, first * 2, (first + count) * 2);
      for (int q = 0; q < quads; q++) {
        int target = first + cursor[bucket[q]]++ * 4;
        System.arraycopy(p, q * 12, positions, target * 3, 12);
        System.arraycopy(c, q * 4, colors, target, 4);
        System.arraycopy(t, q * 8, uv, target * 2, 8);
        System.arraycopy(l, q * 8, light, target * 2, 8);
      }
      return start;
    }

    private int facing(int vertex) {
      for (int axis = 0; axis < 3; axis++) {
        float value = positions[vertex * 3 + axis];
        boolean planar = true;
        for (int c = 1; c < 4; c++) planar &= positions[(vertex + c) * 3 + axis] == value;
        if (!planar) continue;
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float ea = positions[(vertex + 1) * 3 + a] - positions[vertex * 3 + a];
        float eb = positions[(vertex + 1) * 3 + b] - positions[vertex * 3 + b];
        float fa = positions[(vertex + 2) * 3 + a] - positions[vertex * 3 + a];
        float fb = positions[(vertex + 2) * 3 + b] - positions[vertex * 3 + b];
        float normal = ea * fb - eb * fa;
        if (normal == 0) return 6;
        return axis * 2 + (normal > 0 ? 0 : 1);
      }
      return 6;
    }

    Batch batch(int first, int count) {
      return new Batch(
          Arrays.copyOfRange(positions, first * 3, (first + count) * 3),
          Arrays.copyOfRange(colors, first, first + count),
          Arrays.copyOfRange(uv, first * 2, (first + count) * 2),
          true,
          new short[0],
          false,
          Arrays.copyOfRange(light, first * 2, (first + count) * 2),
          true,
          new byte[0],
          false,
          quadIndices(count),
          count,
          VertexFormat.Mode.QUADS);
    }

    Batch entityBatch() {
      byte[] normals = new byte[vertices * 3];
      for (int v = 0; v < vertices; v++) normals[v * 3 + 1] = 127;
      short[] overlayUv = new short[vertices * 2];
      Arrays.fill(overlayUv, (short) 10);
      return new Batch(
          Arrays.copyOf(positions, vertices * 3),
          Arrays.copyOf(colors, vertices),
          Arrays.copyOf(uv, vertices * 2),
          true,
          overlayUv,
          true,
          Arrays.copyOf(light, vertices * 2),
          true,
          normals,
          true,
          quadIndices(vertices),
          vertices,
          VertexFormat.Mode.QUADS);
    }
  }
}
