package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memUTF16;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.pipeline.PipelineSet;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.User32;
import org.lwjgl.system.windows.WNDCLASSEX;
import org.lwjgl.system.windows.WindowsLibrary;

public final class RenderBench {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1009;
  private static final int WARMUP = 120;
  private static final int FRAMES = 360;

  private RenderBench() {}

  public static void main(String[] args) {
    long hwnd = createWindow();
    Backend backend = new Backend();
    backend.initialize(hwnd, new FrameSize(WIDTH, HEIGHT), 0x18202AFF, false);
    System.out.println("renderer=" + backend.rendererName());
    PipelineSet pipelines = new PipelineSet();
    pipelines.start();
    Texture atlas = Texture.create(1024, 1024, 4);
    atlas.sampler(BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT, 4.0f);
    byte[] pixels = new byte[1024 * 1024 * 4];
    new Random(1).nextBytes(pixels);
    atlas.update(0, 0, 0, 1024, 1024, pixels);
    Texture light = Texture.create(16, 16, 0);
    light.sampler(BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP, 0.0f);
    light.update(0, 0, 0, 16, 16, new byte[16 * 16 * 4]);
    RenderState renderState = new RenderState();
    renderState.cull(true);
    DrawContext context =
        terrainContext(
            new DrawContext()
                .set(
                    DrawContext.Kind.TEXTURE_LIGHT,
                    atlas,
                    light,
                    null,
                    false,
                    renderState.snapshot(WIDTH, HEIGHT)));

    String filter = args.length > 0 ? args[0] : "";
    if (filter.equals("verify")) {
      verify(backend, pipelines, context);
      exhaustTransient(backend, pipelines, context);
      int failures = PixelCheck.run(backend, pipelines);
      System.out.println("pixel checks failed: " + failures);
      pipelines.close();
      atlas.close();
      light.close();
      backend.close();
      return;
    }
    if (filter.equals("frame")) {
      FrameBench.run(backend, pipelines, args);
      pipelines.close();
      atlas.close();
      light.close();
      backend.close();
      return;
    }
    int totalVertices = args.length > 1 ? Integer.parseInt(args[1]) : 2_000_000;
    String[] names = {
      "350 draws",
      "1400 draws",
      "8 draws",
      "20 draws",
      "700 draws",
      "350 draws 16-bit",
      "350 draws +8 tex",
      "350 draws +32 tex",
      "350 draws +128 tex",
      "350 draws 1x1024 tex",
      "350 draws 1x512 tex",
      "350 draws 4x256 tex",
      "350 draws 32x64 tex",
      "350 draws 8x1024x16 tex",
      "350 draws animated atlas",
      "900 draws",
      "1100 draws",
      "2800 draws",
      "350 draws rebuild 4 pages",
      "350 draws rebuild 16 pages",
      "350 draws rebuild 64 pages",
      "350 draws + 300 transient",
      "350 draws + 1000 transient",
      "350 draws + 4 sort updates",
      "350 draws + 16 sort updates"
    };
    int[] rebuilds = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 16, 64, 0, 0, 0, 0};
    net.dx12mc.bridge.texture.AtlasAnimation animation =
        new net.dx12mc.bridge.texture.AtlasAnimation(1024, 4, 40);
    int[] drawCounts = {
      350, 1400, 8, 20, 700, 350, 350, 350, 350, 350, 350, 350, 350, 350, 350, 900, 1100, 2800, 350,
      350, 350, 350, 350, 350, 350
    };
    int[] updatesPerFrame = {
      0, 0, 0, 0, 0, 0, 8, 32, 128, 1, 1, 4, 32, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    int[] updateWidth = {
      16, 16, 16, 16, 16, 16, 16, 16, 16, 1024, 512, 256, 64, 1024, 16, 16, 16, 16, 16, 16, 16, 16,
      16, 16, 16
    };
    int[] updateHeight = {
      16, 16, 16, 16, 16, 16, 16, 16, 16, 1024, 512, 256, 64, 16, 16, 16, 16, 16, 16, 16, 16, 16,
      16, 16, 16
    };
    int[] transients = new int[names.length];
    transients[names.length - 4] = 300;
    transients[names.length - 3] = 1000;
    int[] sortUpdates = new int[names.length];
    sortUpdates[names.length - 2] = 4;
    sortUpdates[names.length - 1] = 16;
    for (int i = 0; i < names.length; i++) {
      if (!filter.isEmpty() && !names[i].contains(filter)) continue;
      run(
          names[i],
          drawCounts[i],
          totalVertices,
          names[i].contains("16-bit"),
          updatesPerFrame[i],
          updateWidth[i],
          updateHeight[i],
          rebuilds[i],
          transients[i],
          sortUpdates[i],
          names[i].contains("animated") ? animation : null,
          atlas,
          backend,
          pipelines,
          context);
    }
    pipelines.close();
    atlas.close();
    light.close();
    backend.close();
  }

  private static void run(
      String name,
      int draws,
      int totalVertices,
      boolean split16,
      int updatesPerFrame,
      int updateWidth,
      int updateHeight,
      int rebuildsPerFrame,
      int transientDraws,
      int sortUpdatesPerFrame,
      net.dx12mc.bridge.texture.AtlasAnimation animation,
      Texture atlas,
      Backend backend,
      PipelineSet pipelines,
      DrawContext context) {
    int verticesPerDraw = Math.max(4, totalVertices / draws / 4 * 4);
    if (split16) verticesPerDraw = Math.min(verticesPerDraw, 65532);
    int flushEvery = (int) Math.max(1, (8L << 20) / ((long) verticesPerDraw * 40));
    List<Mesh> meshes = new ArrayList<>();
    Random random = new Random(7);
    for (int d = 0; d < draws; d++) {
      meshes.add(
          makeMesh(
              grid(random, verticesPerDraw),
              pipelines,
              System.getProperty("bench.kind", "SORTABLE")));
      if ((d + 1) % flushEvery == 0) {
        backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
        backend.endFrame();
      }
    }
    String kindName = System.getProperty("bench.kind", "SORTABLE");
    Mesh.Kind kind =
        kindName.equals("SHARED_QUADS") ? Mesh.Kind.SHARED_QUADS : Mesh.Kind.valueOf(kindName);
    Batch sortBatch = sortUpdatesPerFrame > 0 ? grid(new Random(7), verticesPerDraw) : null;
    Batch smallBatch = transientDraws > 0 ? grid(random, 40) : null;
    List<Mesh> pending = new ArrayList<>();
    Batch rebuildBatch = rebuildsPerFrame > 0 ? grid(random, 8000) : null;
    byte[] tile = new byte[updateWidth * updateHeight * 4];
    float[] matrix = context.matrix();
    Matrix4f projection =
        new Matrix4f().perspective((float) Math.toRadians(70), WIDTH / (float) HEIGHT, 0.05f, 400f);
    Matrix4f view = new Matrix4f();
    Matrix4f clip = new Matrix4f();
    Matrix4f depth = new Matrix4f();
    double cpu = 0;
    double gpu = 0;
    double waitSubmit = 0;
    double waitRender = 0;
    long start = 0;
    for (int frame = 0; frame < WARMUP + FRAMES; frame++) {
      if (frame == WARMUP) start = System.nanoTime();
      backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
      view.identity().translate(0, -2, -8).rotateY(frame * 0.001f);
      for (int d = 0; d < draws; d++) {
        clip.set(projection).mul(view).translate((d % 40) * 2f, 0f, -(d / 40) * 2f);
        depth.identity().m22(0.5f).m32(0.5f).mul(clip).get(matrix);
        boolean ok = pipelines.submitSection(meshes.get(d), context);
        if (!ok) throw new IllegalStateException("submit rejected");
      }
      pipelines.endSectionLayer();
      for (int t = 0; t < transientDraws; t++) {
        clip.set(projection).mul(view).translate((t % 40) * 0.5f, 1f, -(t / 40) * 0.5f);
        depth.identity().m22(0.5f).m32(0.5f).mul(clip).get(matrix);
        if (!pipelines.submit(smallBatch, context)) {
          throw new IllegalStateException("transient submit rejected");
        }
      }
      for (int u = 0; u < sortUpdatesPerFrame; u++) {
        meshes.get(u).updateIndices(sortBatch);
      }
      if (rebuildsPerFrame > 0) {
        for (Mesh old : pending) old.close();
        pending.clear();
        for (int r = 0; r < rebuildsPerFrame; r++) {
          pending.add(makeMesh(rebuildBatch, pipelines, kind.name()));
        }
      }
      for (int u = 0; u < updatesPerFrame; u++) {
        atlas.update(0, 0, (u * 16) % (1024 - updateHeight + 1), updateWidth, updateHeight, tile);
      }
      if (animation != null && frame % 3 == 0) {
        for (net.dx12mc.bridge.texture.Image.Upload upload : animation.frameUploads()) {
          atlas.update(
              upload.level(),
              upload.x(),
              upload.y(),
              upload.width(),
              upload.height(),
              upload.rgba());
        }
      }
      backend.endFrame();
      if (frame >= WARMUP) {
        Backend.FrameTiming t = backend.frameTiming();
        cpu += t.cpuMillis();
        gpu += t.gpuMillis();
        waitSubmit += t.waitSubmitMillis();
        waitRender += t.waitRenderMillis();
      }
    }
    double ms = (System.nanoTime() - start) / 1e6 / FRAMES;
    if (animation != null) System.out.println("uploads in last animated frame: see frame time");
    System.out.printf(
        "%-18s draws=%5d verts/draw=%7d total=%8d | frame %.2f ms (%.0f fps) | bgfx cpu %.2f gpu"
            + " %.2f waitSubmit %.2f waitRender %.2f%n",
        name,
        draws,
        verticesPerDraw,
        (long) draws * verticesPerDraw,
        ms,
        1000.0 / ms,
        cpu / FRAMES,
        gpu / FRAMES,
        waitSubmit / FRAMES,
        waitRender / FRAMES);
    for (Mesh mesh : meshes) mesh.close();
    for (Mesh mesh : pending) mesh.close();
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    backend.endFrame();
  }

  private static void verify(Backend backend, PipelineSet pipelines, DrawContext context) {
    new Matrix4f().identity().get(context.matrix());
    String[] kinds = {"SORTABLE", "POOLED", "SHARED_QUADS"};
    for (String kind : kinds) {
      for (int vertices : new int[] {4000, 70000}) {
        Batch batch = grid(new Random(5), vertices);
        Mesh mesh = makeMesh(batch, pipelines, kind);
        backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
        backend.endFrame();
        long whole = triangles(backend, pipelines, context, mesh);
        int quadsFrom = batch.indexCount() / 6 / 4 * 6;
        mesh.drawRange(quadsFrom, batch.indexCount() / 6 / 2 * 6);
        long ranged = triangles(backend, pipelines, context, mesh);
        mesh.drawAll();
        System.out.printf(
            "verify %-13s vertices=%6d expected whole=%d got %d | range expected=%d got %d%n",
            kind, vertices, batch.indexCount() / 3, whole, batch.indexCount() / 6 / 2 * 2, ranged);
        mesh.close();
      }
    }
  }

  private static void exhaustTransient(
      Backend backend, PipelineSet pipelines, DrawContext context) {
    new Matrix4f().identity().get(context.matrix());
    Batch big = grid(new Random(9), 4000);
    Batch small = grid(new Random(9), 4);
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    boolean beforeBig =
        net.dx12mc.graphics.bgfx.TransientPressure.insufficient(
            pipelines.layout(), 4000, big.indexCount(), false);
    int accepted = 0;
    while (accepted < 100_000 && pipelines.submit(big, context)) {
      accepted++;
    }
    boolean afterBig =
        net.dx12mc.graphics.bgfx.TransientPressure.insufficient(
            pipelines.layout(), 4000, big.indexCount(), false);
    boolean afterSmall =
        net.dx12mc.graphics.bgfx.TransientPressure.insufficient(
            pipelines.layout(), 4, small.indexCount(), false);
    backend.endFrame();
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    boolean recovered = pipelines.submit(big, context);
    backend.endFrame();
    System.out.printf(
        "transient exhaustion: accepted=%d (%.1f MiB vertices) insufficientBefore=%b"
            + " insufficientAfterBig=%b insufficientAfterSmall=%b recoveredNextFrame=%b%n",
        accepted,
        accepted * 4000.0 * net.dx12mc.graphics.bgfx.Packing.VERTEX_STRIDE / 1048576.0,
        beforeBig,
        afterBig,
        afterSmall,
        recovered);
  }

  private static long triangles(
      Backend backend, PipelineSet pipelines, DrawContext context, Mesh mesh) {
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    pipelines.submitSection(mesh, context);
    pipelines.endSectionLayer();
    backend.endFrame();
    backend.beginFrame(new FrameSize(WIDTH, HEIGHT));
    backend.endFrame();
    return org.lwjgl.bgfx.BGFX.bgfx_get_stats().numPrims(0);
  }

  private static DrawContext terrainContext(DrawContext context) {
    new Matrix4f().identity().get(context.textureMatrix());
    new Matrix4f().identity().get(context.fogModel());
    float[] tint = context.tint();
    tint[0] = tint[1] = tint[2] = tint[3] = 1.0f;
    float[] fog = context.fogParams();
    fog[0] = 160.0f;
    fog[1] = 192.0f;
    fog[2] = 1.0f;
    fog[3] = 1.0f;
    float[] fogColor = context.fogColor();
    fogColor[0] = 0.6f;
    fogColor[1] = 0.7f;
    fogColor[2] = 1.0f;
    fogColor[3] = 1.0f;
    context.lightParams()[0] = 1.0f;
    context.textureLod()[0] = 4.0f;
    return context;
  }

  private static Mesh makeMesh(Batch batch, PipelineSet pipelines, String kind) {
    if (kind.equals("SHARED_QUADS")) return Mesh.createSharedQuads(batch, pipelines.layout());
    return Mesh.create(batch, pipelines.layout(), Mesh.Kind.valueOf(kind));
  }

  private static Batch grid(Random random, int vertices) {
    int quads = vertices / 4;
    float[] positions = new float[vertices * 3];
    int[] colors = new int[vertices];
    float[] uv0 = new float[vertices * 2];
    short[] uv2 = new short[vertices * 2];
    int[] indices = new int[quads * 6];
    for (int q = 0; q < quads; q++) {
      float x = (q % 64) * 0.25f;
      float z = (q / 64 % 64) * 0.25f;
      float y = (q / 4096) * 0.25f;
      for (int c = 0; c < 4; c++) {
        int v = q * 4 + c;
        positions[v * 3] = x + (c == 1 || c == 2 ? 0.2f : 0f);
        positions[v * 3 + 1] = y;
        positions[v * 3 + 2] = z + (c >= 2 ? 0.2f : 0f);
        colors[v] = 0xFFFFFFFF;
        uv0[v * 2] = random.nextFloat();
        uv0[v * 2 + 1] = random.nextFloat();
        uv2[v * 2] = 240;
        uv2[v * 2 + 1] = 240;
      }
      int b = q * 4;
      int i = q * 6;
      indices[i] = b;
      indices[i + 1] = b + 1;
      indices[i + 2] = b + 2;
      indices[i + 3] = b;
      indices[i + 4] = b + 2;
      indices[i + 5] = b + 3;
    }
    return new Batch(
        positions,
        colors,
        uv0,
        true,
        new short[0],
        false,
        uv2,
        true,
        new byte[0],
        false,
        indices,
        vertices,
        VertexFormat.Mode.QUADS);
  }

  private static long createWindow() {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ByteBuffer className = memUTF16("dx12mc.bench", true);
      long instance = WindowsLibrary.HINSTANCE;
      WNDCLASSEX wc =
          WNDCLASSEX
              .calloc(stack)
              .cbSize(WNDCLASSEX.SIZEOF)
              .lpfnWndProc(
                  (window, message, wParam, lParam) ->
                      User32.DefWindowProc(window, message, wParam, lParam))
              .hInstance(instance)
              .lpszClassName(className);
      if (User32.RegisterClassEx(wc) == 0) {
        throw new IllegalStateException("RegisterClassEx failed");
      }
      long hwnd =
          User32.CreateWindowEx(
              0,
              className,
              memUTF16("dx12mc bench", true),
              0x10CF0000,
              100,
              100,
              WIDTH,
              HEIGHT,
              NULL,
              NULL,
              instance,
              NULL);
      if (hwnd == NULL) throw new IllegalStateException("CreateWindowEx failed");
      User32.ShowWindow(hwnd, 5);
      return hwnd;
    }
  }
}
