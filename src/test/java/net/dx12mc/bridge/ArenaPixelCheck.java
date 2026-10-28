package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Packing;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.bgfx.VertexArena;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PipelineSet;
import org.lwjgl.bgfx.BGFXStats;
import org.lwjgl.system.MemoryUtil;

final class ArenaPixelCheck {
  private static final int SIZE = 64;
  private static final int READ_VIEW = 200;
  private static final int BOTH =
      ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT;
  private final Backend backend;
  private final PipelineSet pipelines;
  private final RenderState state = new RenderState();
  private final DrawContext context = new DrawContext();
  private final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
  private Texture white;
  private Texture lightmap;
  private short readback = BGFX_INVALID_HANDLE;
  private int failures;

  private ArenaPixelCheck(Backend backend, PipelineSet pipelines) {
    this.backend = backend;
    this.pipelines = pipelines;
  }

  static int run(Backend backend, PipelineSet pipelines) {
    ArenaPixelCheck check = new ArenaPixelCheck(backend, pipelines);
    try {
      check.create();
      check.arenaPagesDrawLikeSharedQuadMeshes();
      check.sortedArenaIndicesFollowEveryUpdate();
      check.arenasReleaseEverythingOnClose();
      return check.failures;
    } finally {
      check.close();
    }
  }

  private void create() {
    white = Texture.create(1, 1, 0);
    white.sampler(BGFX_SAMPLER_POINT, 0.0f);
    white.update(0, 0, 0, 1, 1, new byte[] {-1, -1, -1, -1});
    lightmap = Texture.create(16, 16, 0);
    lightmap.sampler(BGFX_SAMPLER_UVW_CLAMP, 0.0f);
    byte[] lit = new byte[16 * 16 * 4];
    Arrays.fill(lit, (byte) -1);
    lightmap.update(0, 0, 0, 16, 16, lit);
    readback =
        bgfx_create_texture_2d(
            SIZE,
            SIZE,
            false,
            1,
            BGFX_TEXTURE_FORMAT_RGBA8,
            BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK,
            null);
  }

  private void arenaPagesDrawLikeSharedQuadMeshes() {
    VertexArena arena = new VertexArena(pipelines.layout(), Packing.VERTEX_STRIDE);
    Target target = Target.create(SIZE, SIZE, true);
    Batch left = quads(-1.0f, 0.0f, 0xFF2040FF);
    Batch right = quads(0.0f, 1.0f, 0xFF20C040);
    Mesh filler = null;
    Mesh leftArena = null;
    Mesh rightArena = null;
    Mesh reference = null;
    try {
      filler = Mesh.createArena(quads(-1.0f, -0.5f, 0xFFFFFFFF), arena);
      leftArena = Mesh.createArena(left, arena);
      rightArena = Mesh.createArena(right, arena);
      reference = Mesh.createSharedQuads(left, pipelines.layout());
      frame(arena);
      begin(target);
      draw(leftArena);
      draw(rightArena);
      arena.flush();
      read(target.colorTexture());
      int[] arenaLeft = pixel(16, 32);
      int[] arenaRight = pixel(48, 32);
      begin(target);
      draw(reference);
      read(target.colorTexture());
      expect(
          "arena page at a non-zero start vertex draws like its own buffer",
          pixel(16, 32),
          arenaLeft);
      report("two arena pages built in one frame took one upload", arena.chunkCount() == 1);
      expectColor("second arena page in the same block", 0xFF, 0x20, 0xC0, arenaRight);
      filler.close();
      filler = null;
      frame(arena);
      Mesh reuse = Mesh.createArena(right, arena);
      report("a range freed by a closed page is reused after the frame boundary", reuse != null);
      if (reuse != null) reuse.close();
    } finally {
      for (Mesh mesh : new Mesh[] {filler, leftArena, rightArena, reference})
        if (mesh != null) mesh.close();
      frame(arena);
      arena.close();
      target.close();
    }
  }

  private void sortedArenaIndicesFollowEveryUpdate() {
    VertexArena vertices = new VertexArena(pipelines.layout(), Packing.VERTEX_STRIDE);
    VertexArena indices = VertexArena.indices32();
    Target target = Target.create(SIZE, SIZE, true);
    Batch twoQuads = twoQuads();
    Mesh mesh = null;
    try {
      mesh = Mesh.createSortableArena(twoQuads, vertices, indices);
      frame(vertices, indices);
      begin(target);
      draw(mesh);
      flush(vertices, indices);
      read(target.colorTexture());
      expectColor("sortable arena mesh: first quad", 0xFF, 0x00, 0x00, pixel(16, 32));
      expectColor("sortable arena mesh: second quad", 0xFF, 0xFF, 0x00, pixel(48, 32));
      int[] onlySecond = {4, 5, 6, 4, 6, 7};
      mesh.updateIndices(twoQuads.withIndices(onlySecond));
      begin(target);
      draw(mesh);
      flush(vertices, indices);
      read(target.colorTexture());
      expectColor("after an index update the first quad is gone", 0x00, 0x00, 0x00, pixel(16, 32));
      expectColor("after an index update the second quad stays", 0xFF, 0xFF, 0x00, pixel(48, 32));
    } finally {
      if (mesh != null) mesh.close();
      frame(vertices, indices);
      vertices.close();
      indices.close();
      target.close();
    }
  }

  private void arenasReleaseEverythingOnClose() {
    frame();
    int[] before = handles();
    VertexArena vertices = new VertexArena(pipelines.layout(), Packing.VERTEX_STRIDE);
    VertexArena indices = VertexArena.indices32();
    Mesh page = Mesh.createArena(quads(-1, 1, -1), vertices);
    Mesh sorted = Mesh.createSortableArena(twoQuads(), vertices, indices);
    frame(vertices, indices);
    page.close();
    sorted.close();
    page.close();
    frame(vertices, indices);
    vertices.close();
    indices.close();
    frame();
    frame();
    int[] after = handles();
    report(
        "arena pages and arenas leave no bgfx buffer behind: before "
            + Arrays.toString(before)
            + " after "
            + Arrays.toString(after),
        Arrays.equals(before, after));
  }

  private int[] handles() {
    BGFXStats stats = bgfx_get_stats();
    return new int[] {stats.numDynamicVertexBuffers(), stats.numDynamicIndexBuffers()};
  }

  private void frame(VertexArena... arenas) {
    backend.beginFrame(new FrameSize(SIZE, SIZE), null);
    flush(arenas);
    backend.endFrame();
    for (VertexArena arena : arenas) arena.onFrameBoundary();
  }

  private static void flush(VertexArena... arenas) {
    for (VertexArena arena : arenas) arena.flush();
  }

  private void begin(Target target) {
    backend.beginFrame(new FrameSize(SIZE, SIZE), null);
    backend.bindTarget(target);
    backend.clear(BOTH, 0x000000FF, 1.0f);
  }

  private void draw(Mesh mesh) {
    ShaderSemantics semantics = ShaderSemantics.of("rendertype_solid");
    PixelCheck.reset(context, semantics);
    ShaderInputs.fog(semantics, Float.MAX_VALUE, 0, 0, context.fogParams());
    ShaderInputs.light(semantics, true, false, false, context.lightParams());
    state.depthTest(true);
    state.depthMask(true);
    context.set(
        DrawContext.Kind.TEXTURE_LIGHT, white, lightmap, null, false, state.snapshot(SIZE, SIZE));
    if (!pipelines.submit(mesh, context))
      throw new IllegalStateException("arena check draw was rejected");
  }

  private static Batch quads(float x0, float x1, int abgr) {
    float[] positions = {x0, -1, 0.5f, x1, -1, 0.5f, x1, 1, 0.5f, x0, 1, 0.5f};
    float[] uv0 = {0, 0, 1, 0, 1, 1, 0, 1};
    short light = (short) 0xF0;
    short[] uv2 = {light, light, light, light, light, light, light, light};
    return PixelCheck.batch(positions, abgr, uv0, null, uv2, null);
  }

  private static Batch twoQuads() {
    float[] positions = {
      -1, -1, 0.5f, 0, -1, 0.5f, 0, 1, 0.5f, -1, 1, 0.5f,
      0, -1, 0.5f, 1, -1, 0.5f, 1, 1, 0.5f, 0, 1, 0.5f
    };
    int[] colors = {
      0xFF0000FF, 0xFF0000FF, 0xFF0000FF, 0xFF0000FF, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000
    };
    float[] uv0 = new float[16];
    short[] uv2 = new short[16];
    Arrays.fill(uv2, (short) 0xF0);
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
        new int[] {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7},
        8,
        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
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

  private void expect(String name, int[] expected, int[] actual) {
    expectColor(name, expected[0], expected[1], expected[2], actual);
  }

  private void expectColor(String name, int red, int green, int blue, int[] actual) {
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
    if (white != null) white.close();
    if (lightmap != null) lightmap.close();
    if (BGFX_HANDLE_IS_VALID(readback)) bgfx_destroy_texture(readback);
    MemoryUtil.memFree(pixels);
  }
}
