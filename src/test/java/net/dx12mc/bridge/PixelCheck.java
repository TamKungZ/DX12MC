package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.ScreenCapture;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PipelineSet;
import net.dx12mc.render.ScreenImages;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

final class PixelCheck {
  private static final int SIZE = 64;
  private static final int VIEWS = 8;
  private static final int READ_VIEW = 200;
  private final Backend backend;
  private final PipelineSet pipelines;
  private final RenderState state = new RenderState();
  private final DrawContext context = new DrawContext();
  private short color = BGFX_INVALID_HANDLE;
  private short depth = BGFX_INVALID_HANDLE;
  private short frameBuffer = BGFX_INVALID_HANDLE;
  private short readback = BGFX_INVALID_HANDLE;
  private final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
  private int failures;

  private PixelCheck(Backend backend, PipelineSet pipelines) {
    this.backend = backend;
    this.pipelines = pipelines;
  }

  static int run(Backend backend, PipelineSet pipelines) {
    PixelCheck check = new PixelCheck(backend, pipelines);
    try {
      check.create();
      check.handDepthClear();
      check.guiAlphaDiscardWithBlendOff();
      check.linearFog();
      check.terrainLightmapIsFilteredAndEntityLightmapIsFetched();
      check.entityDiffuseLight();
      check.minifiedTerrainClampsToTheLastUploadedMipLevel();
      check.screenSpaceLineCoversItsWidth();
      check.debugLinePrimitiveIsRasterised();
      check.textHighlightOrReverseLogicOp();
      check.screenCaptureReturnsThePresentedBackbuffer();
      check.measureFullHdScreenCapture();
      return check.failures;
    } finally {
      check.close();
    }
  }

  private void handDepthClear() {
    begin(0x000000FF);
    draw(quad(0.2f, 0xFF0000FF), colorContext());
    backend.clear(ViewSequence.GL_DEPTH_BUFFER_BIT, 0, 1.0f);
    draw(quad(0.8f, 0x00FF00FF), colorContext());
    expect("depth clear lets the hand draw over nearer world geometry", 0, 255, 0, read());
    begin(0x000000FF);
    draw(quad(0.2f, 0xFF0000FF), colorContext());
    draw(quad(0.8f, 0x00FF00FF), colorContext());
    expect("without the clear the farther quad is hidden", 255, 0, 0, read());
  }

  private void guiAlphaDiscardWithBlendOff() {
    Texture texture = Texture.create(2, 1, 0);
    try {
      texture.sampler(BGFX_SAMPLER_POINT | BGFX_SAMPLER_UVW_CLAMP, 0.0f);
      texture.update(0, 0, 0, 2, 1, new byte[] {-1, -1, -1, 0, -1, -1, -1, -1});
      begin(0xFF0000FF);
      state.blend(false);
      ShaderSemantics gui = ShaderSemantics.of("position_tex");
      DrawContext guiContext = textureContext(texture, gui);
      draw(texturedQuad(0.5f, 0xFFFFFFFF, 0.0f, 1.0f), guiContext);
      readFrame();
      int[] left = pixel(SIZE / 4, SIZE / 2);
      int[] right = pixel(3 * SIZE / 4, SIZE / 2);
      expect("transparent GUI texel is discarded, not drawn opaque", 255, 0, 0, left);
      expect("opaque GUI texel is drawn", 255, 255, 255, right);
    } finally {
      texture.close();
    }
  }

  private void linearFog() {
    begin(0x000000FF);
    DrawContext fog = colorContext();
    Matrix4f clip = new Matrix4f().scaling(1.0f / 60.0f, 1.0f / 60.0f, 1.0f / 200.0f);
    clip.get(fog.matrix());
    new Matrix4f(clip).invert().get(fog.fogModel());
    ShaderInputs.fog(ShaderSemantics.of("position"), 50.0f, 150.0f, 0, fog.fogParams());
    float[] fogColor = fog.fogColor();
    fogColor[0] = 0.0f;
    fogColor[1] = 0.0f;
    fogColor[2] = 1.0f;
    fogColor[3] = 1.0f;
    float z = (float) Math.sqrt(100.0 * 100.0 - 2.0 * 60.0 * 60.0);
    draw(quad(-60.0f, 60.0f, z, 0xFFFFFFFF), fog);
    expect(
        "vanilla linear_fog at the midpoint mixes half of the fog colour", 128, 128, 255, read());
  }

  private void terrainLightmapIsFilteredAndEntityLightmapIsFetched() {
    Texture white = Texture.create(1, 1, 0);
    Texture lightmap = Texture.create(16, 16, 0);
    try {
      white.sampler(BGFX_SAMPLER_POINT, 0.0f);
      white.update(0, 0, 0, 1, 1, new byte[] {-1, -1, -1, -1});
      lightmap.sampler(BGFX_SAMPLER_UVW_CLAMP, 0.0f);
      byte[] texels = new byte[16 * 16 * 4];
      for (int y = 0; y < 16; y++) {
        for (int x = 0; x < 16; x++) {
          int offset = (y * 16 + x) * 4;
          texels[offset] = texels[offset + 1] = texels[offset + 2] = (byte) (x * 16);
          texels[offset + 3] = -1;
        }
      }
      lightmap.update(0, 0, 0, 16, 16, texels);
      for (String program : new String[] {"rendertype_solid", "particle"}) {
        begin(0x000000FF);
        ShaderSemantics semantics = ShaderSemantics.of(program);
        DrawContext lit = textureContext(white, semantics);
        lit.set(DrawContext.Kind.TEXTURE_LIGHT, white, lightmap, null, false, lit.state());
        ShaderInputs.light(semantics, true, false, false, lit.lightParams());
        draw(lightQuad(128), lit);
        int expected = program.equals("rendertype_solid") ? 120 : 128;
        expect(program + " lightmap at UV2=128", expected, expected, expected, read());
      }
    } finally {
      white.close();
      lightmap.close();
    }
  }

  private void entityDiffuseLight() {
    Texture white = Texture.create(1, 1, 0);
    try {
      white.sampler(BGFX_SAMPLER_POINT, 0.0f);
      white.update(0, 0, 0, 1, 1, new byte[] {-1, -1, -1, -1});
      begin(0x000000FF);
      ShaderSemantics entity = ShaderSemantics.of("rendertype_entity_solid");
      DrawContext lit = textureContext(white, entity);
      lit.set(DrawContext.Kind.ENTITY, white, null, null, false, lit.state());
      ShaderInputs.light(entity, false, false, true, lit.lightParams());
      lit.lightDirection0()[1] = 1.0f;
      draw(entityQuad(new byte[] {127, 0, 0}), lit);
      expect(
          "normal facing away from both lights gets the 0.4 ambient term", 102, 102, 102, read());
      begin(0x000000FF);
      draw(entityQuad(new byte[] {0, 127, 0}), lit);
      expect("normal facing Light0 is fully lit", 255, 255, 255, read());
    } finally {
      white.close();
    }
  }

  private void minifiedTerrainClampsToTheLastUploadedMipLevel() {
    Texture atlas = Texture.create(16, 16, 4);
    try {
      atlas.update(0, 0, 0, 16, 16, solid(16, 0xFF, 0x00, 0x00));
      atlas.update(1, 0, 0, 8, 8, solid(8, 0x00, 0xFF, 0x00));
      atlas.sampler(BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT, 1.0f);
      begin(0x000000FF);
      ShaderSemantics terrain = ShaderSemantics.of("rendertype_solid");
      DrawContext minified = textureContext(atlas, terrain);
      minified.textureLod()[0] = atlas.maxLod();
      float half = 1.0f / 32.0f;
      float[] positions = {
        -half, -half, 0.5f, half, -half, 0.5f, half, half, 0.5f, -half, half, 0.5f
      };
      float[] uv0 = {0, 0, 1, 0, 1, 1, 0, 1};
      draw(batch(positions, 0xFFFFFFFF, uv0, null, null, null), minified);
      expect("LOD above GL_TEXTURE_MAX_LEVEL samples the last uploaded level", 0, 255, 0, read());
    } finally {
      atlas.close();
    }
  }

  private static byte[] solid(int size, int red, int green, int blue) {
    byte[] texels = new byte[size * size * 4];
    for (int i = 0; i < texels.length; i += 4) {
      texels[i] = (byte) red;
      texels[i + 1] = (byte) green;
      texels[i + 2] = (byte) blue;
      texels[i + 3] = -1;
    }
    return texels;
  }

  private void screenSpaceLineCoversItsWidth() {
    begin(0x000000FF);
    DrawContext line = colorContext();
    line.set(DrawContext.Kind.LINE, null, null, null, false, line.state());
    DrawContextFactory.lineParameters(4.0f, SIZE, SIZE, line.lineParams());
    float[] positions = {-1, 0, 0.5f, -1, 0, 0.5f, 1, 0, 0.5f, 1, 0, 0.5f};
    byte[] direction = {127, 0, 0, 127, 0, 0, 127, 0, 0, 127, 0, 0};
    int[] colors = {0x00FF00FF, 0x00FF00FF, 0x00FF00FF, 0x00FF00FF};
    Batch batch =
        new Batch(
            positions,
            colors,
            new float[0],
            false,
            new short[0],
            false,
            new short[0],
            false,
            direction,
            true,
            new int[] {0, 1, 2, 3, 2, 1},
            4,
            VertexFormat.Mode.LINES);
    draw(batch, line);
    readFrame();
    expect("a 4 px rendertype_lines segment covers the row it crosses", 0, 255, 0, pixel(10, 31));
    expect("the same segment covers the row below its centre", 0, 255, 0, pixel(10, 32));
    expect("pixels outside the line width stay untouched", 0, 0, 0, pixel(10, 26));
  }

  private void debugLinePrimitiveIsRasterised() {
    begin(0x000000FF);
    DrawContext debug = colorContext();
    debug.primitive = DrawContextFactory.primitive(VertexFormat.Mode.DEBUG_LINES);
    float y = 1.0f - 2.0f * 40.5f / SIZE;
    Batch batch =
        new Batch(
            new float[] {-1, y, 0.5f, 1, y, 0.5f},
            new int[] {0xFFFF00FF, 0xFFFF00FF},
            new float[0],
            false,
            new short[0],
            false,
            new short[0],
            false,
            new byte[0],
            false,
            new int[] {0, 1},
            2,
            VertexFormat.Mode.DEBUG_LINES);
    draw(batch, debug);
    debug.primitive = 0L;
    readFrame();
    expect("DEBUG_LINES draws a line primitive on its row", 255, 255, 0, pixel(20, 40));
    expect("and nothing beside it", 0, 0, 0, pixel(20, 44));
  }

  private void textHighlightOrReverseLogicOp() {
    ShaderSemantics highlight = ShaderSemantics.of("rendertype_gui_text_highlight");
    for (int alpha : new int[] {0xFF, 0x00}) {
      begin(0x336699FF);
      state.depthTest(false);
      state.depthMask(true);
      state.blend(true);
      state.blendFuncSeparate(0x0302, 0x0303, 0x0001, 0x0303);
      state.colorLogic(true);
      state.logicOp(0x150B);
      reset(context, highlight);
      ShaderInputs.fog(highlight, 0, 1, 0, context.fogParams());
      context.set(
          DrawContext.Kind.COLOR,
          null,
          null,
          null,
          highlight.discards(),
          state.snapshot(SIZE, SIZE));
      draw(quad(0.5f, 0x0000FF00 | alpha), context);
      state.colorLogic(false);
      state.logicOp(0x1503);
      state.blend(false);
      if (alpha == 0xFF) {
        expect("EditBox highlight 0xFF0000FF is OR_REVERSE: src | ~dst", 0xCC, 0x99, 0xFF, read());
      } else {
        expect("a zero-alpha highlight is discarded by both passes", 0x33, 0x66, 0x99, read());
      }
    }
  }

  private void screenCaptureReturnsThePresentedBackbuffer() {
    backend.beginFrame(new FrameSize(SIZE + 16, SIZE + 16));
    backend.endFrame();
    backbufferFrame(0x336699FF, 0xFF0000FF);
    boolean refused = !backend.requestScreenCapture();
    backend.endFrame();
    expect(
        "no capture is requested before the reset swap chain has presented a frame",
        1,
        1,
        1,
        new int[] {refused ? 1 : 0, refused ? 1 : 0, refused ? 1 : 0});
    backbufferFrame(0x000000FF, 0x0000FFFF);
    boolean requested = backend.requestScreenCapture();
    backend.endFrame();
    ScreenCapture.Frame frame = backend.takeScreenCapture();
    for (int wait = 0; requested && wait < 10 && frame == null; wait++) {
      bgfx_frame(false);
      frame = backend.takeScreenCapture();
    }
    if (frame == null) {
      failures++;
      System.out.println("pixel FAIL screen capture callback never delivered a frame");
      return;
    }
    try (NativeImage image = ScreenImages.toNativeImage(frame)) {
      int[] size = {image.getWidth(), image.getHeight(), 0};
      expect("screenshot size matches the backbuffer", SIZE, SIZE, 0, size);
      expect(
          "capture holds the last presented frame, like vanilla's grab after present",
          255,
          0,
          0,
          abgr(image.getPixelRGBA(4, 4)));
      expect(
          "clear colour keeps its channel order",
          0x33,
          0x66,
          0x99,
          abgr(image.getPixelRGBA(60, 60)));
      int alpha = image.getPixelRGBA(60, 60) >>> 24;
      expect(
          "screenshot alpha is opaque like downloadTexture(0, true)",
          255,
          255,
          255,
          new int[] {alpha, alpha, alpha});
    } finally {
      ScreenCapture.free(frame);
    }
  }

  private void measureFullHdScreenCapture() {
    FrameSize size = new FrameSize(1920, 1009);
    for (int frame = 0; frame < 3; frame++) {
      backend.beginFrame(size);
      for (int view = 0; view < VIEWS; view++)
        bgfx_set_view_frame_buffer(view, BGFX_INVALID_HANDLE);
      backend.endFrame();
    }
    backend.beginFrame(size);
    long requested = System.nanoTime();
    backend.requestScreenCapture();
    backend.endFrame();
    int frames = 1;
    ScreenCapture.Frame frame = backend.takeScreenCapture();
    while (frame == null && frames < 10) {
      bgfx_frame(false);
      frames++;
      frame = backend.takeScreenCapture();
    }
    long delivered = System.nanoTime();
    if (frame == null) {
      failures++;
      System.out.println("pixel FAIL 1920x1009 capture was not delivered");
      return;
    }
    try (NativeImage image = ScreenImages.toNativeImage(frame)) {
      long converted = System.nanoTime();
      System.out.printf(
          "capture 1920x1009: delivered after %d frame(s) in %.2f ms, conversion %.2f ms%n",
          frames, (delivered - requested) / 1e6, (converted - delivered) / 1e6);
    } finally {
      ScreenCapture.free(frame);
    }
  }

  private void backbufferFrame(int clearRgba, int quadRgba) {
    backend.beginFrame(new FrameSize(SIZE, SIZE));
    for (int view = 0; view < VIEWS; view++) bgfx_set_view_frame_buffer(view, BGFX_INVALID_HANDLE);
    backend.clear(
        ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT, clearRgba, 1.0f);
    state.blend(false);
    draw(quad(-1.0f, 0.0f, 0.5f, quadRgba), topLeft(colorContext()));
  }

  private static DrawContext topLeft(DrawContext context) {
    new Matrix4f().translation(0.0f, 1.0f, 0.0f).get(context.matrix());
    return context;
  }

  private static int[] abgr(int pixel) {
    return new int[] {pixel & 0xFF, pixel >>> 8 & 0xFF, pixel >>> 16 & 0xFF};
  }

  private void create() {
    color =
        bgfx_create_texture_2d(
            SIZE, SIZE, false, 1, BGFX_TEXTURE_FORMAT_RGBA8, BGFX_TEXTURE_RT, null);
    depth =
        bgfx_create_texture_2d(
            SIZE, SIZE, false, 1, BGFX_TEXTURE_FORMAT_D24S8, BGFX_TEXTURE_RT_WRITE_ONLY, null);
    frameBuffer = bgfx_create_frame_buffer_from_handles(new short[] {color, depth}, false);
    readback =
        bgfx_create_texture_2d(
            SIZE,
            SIZE,
            false,
            1,
            BGFX_TEXTURE_FORMAT_RGBA8,
            BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK,
            null);
    if (!BGFX_HANDLE_IS_VALID(frameBuffer) || !BGFX_HANDLE_IS_VALID(readback)) {
      throw new IllegalStateException("bgfx could not create the pixel check render target");
    }
  }

  private void begin(int clearRgba) {
    backend.beginFrame(new FrameSize(SIZE, SIZE));
    for (int view = 0; view < VIEWS; view++) bgfx_set_view_frame_buffer(view, frameBuffer);
    backend.clear(
        ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT, clearRgba, 1.0f);
    state.blend(false);
    state.depthTest(true);
    state.depthMask(true);
  }

  private int[] read() {
    readFrame();
    return pixel(SIZE / 2, SIZE / 2);
  }

  private void readFrame() {
    bgfx_set_view_rect(READ_VIEW, 0, 0, SIZE, SIZE);
    bgfx_touch(READ_VIEW);
    bgfx_blit(READ_VIEW, readback, 0, 0, 0, 0, color, 0, 0, 0, 0, SIZE, SIZE, 1);
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

  private void draw(Batch batch, DrawContext draw) {
    if (!pipelines.submit(batch, draw)) throw new IllegalStateException("pixel check draw failed");
  }

  private DrawContext colorContext() {
    reset(context, ShaderSemantics.of("position_color"));
    ShaderInputs.fog(ShaderSemantics.of("position_color"), 0, 1, 0, context.fogParams());
    return context.set(DrawContext.Kind.COLOR, null, null, null, false, state.snapshot(SIZE, SIZE));
  }

  private DrawContext textureContext(Texture texture, ShaderSemantics semantics) {
    reset(context, semantics);
    ShaderInputs.fog(semantics, Float.MAX_VALUE, 0, 0, context.fogParams());
    return context.set(
        DrawContext.Kind.TEXTURE,
        texture,
        null,
        null,
        semantics.discards(),
        state.snapshot(SIZE, SIZE));
  }

  private static void reset(DrawContext context, ShaderSemantics semantics) {
    new Matrix4f().get(context.matrix());
    new Matrix4f().get(context.textureMatrix());
    new Matrix4f().get(context.fogModel());
    Arrays.fill(context.tint(), 1.0f);
    Arrays.fill(context.fogColor(), 0.0f);
    Arrays.fill(context.lightParams(), 0.0f);
    Arrays.fill(context.textureLod(), 0.0f);
    Arrays.fill(context.lightDirection0(), 0.0f);
    Arrays.fill(context.lightDirection1(), 0.0f);
    ShaderInputs.material(semantics, context.material());
  }

  private static Batch quad(float z, int rgba) {
    return quad(-1.0f, 1.0f, z, rgba);
  }

  private static Batch quad(float min, float max, float z, int rgba) {
    float[] positions = {min, min, z, max, min, z, max, max, z, min, max, z};
    return batch(positions, rgba, null, null, null, null);
  }

  private static Batch texturedQuad(float z, int rgba, float u0, float u1) {
    float[] positions = {-1, -1, z, 1, -1, z, 1, 1, z, -1, 1, z};
    float[] uv0 = {u0, 0, u1, 0, u1, 1, u0, 1};
    return batch(positions, rgba, uv0, null, null, null);
  }

  private static Batch lightQuad(int uv2) {
    float[] positions = {-1, -1, 0.5f, 1, -1, 0.5f, 1, 1, 0.5f, -1, 1, 0.5f};
    float[] uv0 = {0, 0, 1, 0, 1, 1, 0, 1};
    short light = (short) uv2;
    short[] lightUv = {light, 0, light, 0, light, 0, light, 0};
    return batch(positions, 0xFFFFFFFF, uv0, null, lightUv, null);
  }

  private static Batch entityQuad(byte[] normal) {
    float[] positions = {-1, -1, 0.5f, 1, -1, 0.5f, 1, 1, 0.5f, -1, 1, 0.5f};
    float[] uv0 = {0, 0, 1, 0, 1, 1, 0, 1};
    byte[] normals = new byte[12];
    for (int vertex = 0; vertex < 4; vertex++) System.arraycopy(normal, 0, normals, vertex * 3, 3);
    return batch(positions, 0xFFFFFFFF, uv0, new short[8], new short[8], normals);
  }

  private static Batch batch(
      float[] positions, int rgba, float[] uv0, short[] uv1, short[] uv2, byte[] normals) {
    int[] colors = {rgba, rgba, rgba, rgba};
    return new Batch(
        positions,
        colors,
        uv0 == null ? new float[0] : uv0,
        uv0 != null,
        uv1 == null ? new short[0] : uv1,
        uv1 != null,
        uv2 == null ? new short[0] : uv2,
        uv2 != null,
        normals == null ? new byte[0] : normals,
        normals != null,
        new int[] {0, 1, 2, 0, 2, 3},
        4,
        VertexFormat.Mode.QUADS);
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
    for (int view = 0; view < VIEWS; view++) bgfx_set_view_frame_buffer(view, BGFX_INVALID_HANDLE);
    if (BGFX_HANDLE_IS_VALID(frameBuffer)) bgfx_destroy_frame_buffer(frameBuffer);
    if (BGFX_HANDLE_IS_VALID(color)) bgfx_destroy_texture(color);
    if (BGFX_HANDLE_IS_VALID(depth)) bgfx_destroy_texture(depth);
    if (BGFX_HANDLE_IS_VALID(readback)) bgfx_destroy_texture(readback);
    MemoryUtil.memFree(pixels);
  }
}
