package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.shader.ShaderInputs;
import net.dx12mc.bridge.shader.ShaderSemantics;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.ScreenCapture;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PipelineSet;
import net.dx12mc.graphics.pipeline.PostPipeline;
import net.dx12mc.graphics.pipeline.PostPipeline.Effect;
import net.dx12mc.render.ScreenImages;
import org.lwjgl.system.MemoryUtil;

final class PostPixelCheck {
  private static final int SIZE = 64;
  private static final int READ_VIEW = 200;
  private static final int BOTH =
      ViewSequence.GL_COLOR_BUFFER_BIT | ViewSequence.GL_DEPTH_BUFFER_BIT;
  private final Backend backend;
  private final PipelineSet pipelines;
  private final PostPipeline post = new PostPipeline();
  private final RenderState state = new RenderState();
  private final DrawContext context = new DrawContext();
  private final float[] values = new float[PostPipeline.UNIFORM_FLOATS];
  private final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
  private short readback = BGFX_INVALID_HANDLE;
  private int failures;

  private PostPixelCheck(Backend backend, PipelineSet pipelines) {
    this.backend = backend;
    this.pipelines = pipelines;
  }

  static int run(Backend backend, PipelineSet pipelines) {
    PostPixelCheck check = new PostPixelCheck(backend, pipelines);
    try {
      check.create();
      check.renderTargetRoundTripKeepsOrientation();
      check.boxBlurMatchesTheVanillaFormulaAcrossAnEdge();
      check.entityOutlineMaskMarksBothSidesOfTheEdgeOnly();
      check.spectatorColourPassesMatchTheVanillaArithmetic();
      check.resizeResetAndDoubleCloseKeepTargetsUsable();
      check.redirectedMainFrameReachesTheBackbuffer();
      return check.failures;
    } finally {
      check.close();
    }
  }

  private void create() {
    post.start();
    readback =
        bgfx_create_texture_2d(
            SIZE,
            SIZE,
            false,
            1,
            BGFX_TEXTURE_FORMAT_RGBA8,
            BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK,
            null);
    if (!BGFX_HANDLE_IS_VALID(readback)) {
      throw new IllegalStateException("bgfx could not create the post pixel check readback");
    }
  }

  private void renderTargetRoundTripKeepsOrientation() {
    Target a = Target.create(SIZE, SIZE, true);
    Target b = Target.create(SIZE, SIZE, true);
    try {
      begin();
      into(a, 0x336699FF);
      draw(rect(-1.0f, 0.0f, 0.0f, 1.0f, 0xFF0000FF));
      into(b, 0x00000000);
      blit(a, b, false);
      read(b);
      expect("target round trip: top-left quad", 255, 0, 0, pixel(8, 8));
      expect(
          "target round trip: bottom-right keeps the clear colour",
          0x33,
          0x66,
          0x99,
          pixel(56, 56));
      expect("target round trip: top-right keeps the clear colour", 0x33, 0x66, 0x99, pixel(56, 8));
      expect(
          "target round trip: bottom-left keeps the clear colour", 0x33, 0x66, 0x99, pixel(8, 56));
    } finally {
      a.close();
      b.close();
    }
  }

  private void boxBlurMatchesTheVanillaFormulaAcrossAnEdge() {
    Target a = Target.create(SIZE, SIZE, true);
    Target b = Target.create(SIZE, SIZE, true);
    try {
      begin();
      into(a, 0x000000FF);
      draw(rect(-1.0f, -1.0f, 0.0f, 1.0f, 0xFF0000FF));
      into(b, 0x00000000);
      sizes(a, b);
      values[4] = 1.0f;
      values[5] = 0.0f;
      values[6] = 5.0f;
      values[7] = 1.0f;
      pass(Effect.BOX_BLUR, a, b, true);
      read(b);
      for (int x : new int[] {4, 26, 28, 30, 32, 34, 36, 40, 60}) {
        expect(
            "box blur at x=" + x + " follows the shader formula",
            reference(x, 5.0f),
            0,
            0,
            pixel(x, 32));
      }
    } finally {
      a.close();
      b.close();
    }
  }

  private static int reference(int x, float radius) {
    float actual = Math.round(radius);
    float sum = 0.0f;
    for (float a = -actual + 0.5f; a <= actual; a += 2.0f) sum += edge(x + 0.5f + a);
    sum += edge(x + 0.5f + actual) / 2.0f;
    return Math.round(sum / (actual + 0.5f) * 255.0f);
  }

  private static float edge(float texel) {
    float t = texel - 0.5f;
    int low = (int) Math.floor(t);
    float fraction = t - low;
    return lerp(red(low), red(low + 1), fraction);
  }

  private static float red(int index) {
    return Math.min(Math.max(index, 0), SIZE - 1) < SIZE / 2 ? 1.0f : 0.0f;
  }

  private static float lerp(float from, float to, float amount) {
    return from + (to - from) * amount;
  }

  private void entityOutlineMaskMarksBothSidesOfTheEdgeOnly() {
    Target a = Target.create(SIZE, SIZE, true);
    Target b = Target.create(SIZE, SIZE, true);
    try {
      begin();
      into(a, 0x00000000);
      draw(rect(-0.5f, -0.5f, 0.5f, 0.5f, 0xFF0000FF));
      into(b, 0x00000000);
      sizes(a, b);
      pass(Effect.ENTITY_OUTLINE, a, b, false);
      read(b);
      expectAlpha("outline: inside edge pixel", 255, pixel4(16, 32));
      expectAlpha("outline: outside edge pixel", 255, pixel4(15, 32));
      expectAlpha("outline: interior is empty", 0, pixel4(32, 32));
      expectAlpha("outline: far outside is empty", 0, pixel4(4, 4));
      expect("outline: edge colour is a fifth of the neighbour sum", 204, 0, 0, pixel(16, 32));
    } finally {
      a.close();
      b.close();
    }
  }

  private void spectatorColourPassesMatchTheVanillaArithmetic() {
    Target a = Target.create(SIZE, SIZE, true);
    Target b = Target.create(SIZE, SIZE, true);
    try {
      float[] in = {200 / 255.0f, 100 / 255.0f, 50 / 255.0f};

      begin();
      into(a, 0xC86432FF);
      into(b, 0x00000000);
      sizes(a, b);
      set(4, 0.3f, 0.59f, 0.11f, 1.8f);
      set(8, 0.0f, 0.0f, 0.0f, 0.0f);
      set(12, 0.3f, 0.59f, 0.11f, 0.0f);
      set(16, 0.0f, 0.0f, 0.0f, 0.0f);
      set(20, 0.0f, 0.0f, 0.0f, 0.0f);
      set(24, 1.0f, 1.0f, 1.0f, 0.0f);
      pass(Effect.COLOR_CONVOLVE, a, b, false);
      read(b);
      float green = 0.3f * in[0] + 0.59f * in[1] + 0.11f * in[2];
      float luma = green * 0.59f;
      float convolved = (green - luma) * 1.8f + luma;
      float dark = (0.0f - luma) * 1.8f + luma;
      expect(
          "creeper colour_convolve",
          channel(dark),
          channel(convolved),
          channel(dark),
          pixel(32, 32));

      begin();
      into(a, 0xC86432FF);
      into(b, 0x00000000);
      sizes(a, b);
      set(4, 16.0f, 1.5f, 4.0f, 0.0f);
      pass(Effect.BITS, a, b, false);
      read(b);
      float[] quantised = new float[3];
      for (int i = 0; i < 3; i++) quantised[i] = in[i] - frac(in[i] * 16.0f) / 16.0f;
      float bitsLuma = 0.3f * quantised[0] + 0.59f * quantised[1] + 0.11f * quantised[2];
      expect(
          "creeper bits",
          channel(bitsLuma + (quantised[0] - bitsLuma) * 1.5f),
          channel(bitsLuma + (quantised[1] - bitsLuma) * 1.5f),
          channel(bitsLuma + (quantised[2] - bitsLuma) * 1.5f),
          pixel(32, 32));

      begin();
      into(a, 0xC86432FF);
      into(b, 0x00000000);
      sizes(a, b);
      set(4, 0.8f, 0.0f, 0.0f, 0.0f);
      pass(Effect.INVERT, a, b, false);
      read(b);
      expect(
          "enderman invert",
          channel(in[0] + (1.0f - 2.0f * in[0]) * 0.8f),
          channel(in[1] + (1.0f - 2.0f * in[1]) * 0.8f),
          channel(in[2] + (1.0f - 2.0f * in[2]) * 0.8f),
          pixel(32, 32));
    } finally {
      a.close();
      b.close();
    }
  }

  private void resizeResetAndDoubleCloseKeepTargetsUsable() {
    Target a = Target.create(SIZE, SIZE, true);
    Target b = Target.create(SIZE, SIZE, true);
    Target small = Target.create(SIZE / 2, SIZE / 2, false);
    try {
      begin();
      into(a, 0x000000FF);
      draw(rect(-1.0f, -1.0f, 0.0f, 1.0f, 0xFF0000FF));
      backend.endFrame();

      backend.beginFrame(new FrameSize(SIZE + 16, SIZE + 16), null);
      backend.endFrame();

      backend.beginFrame(new FrameSize(SIZE, SIZE), null);
      into(small, 0x00FF00FF);
      into(b, 0x00000000);
      sizes(small, b);
      pass(Effect.BLIT, small, b, false, 1.0f);
      read(b);
      expect("half-size target stretches over a full-size target", 0, 255, 0, pixel(32, 32));

      begin();
      into(b, 0x00000000);
      sizes(a, b);
      pass(Effect.BLIT, a, b, false, 1.0f);
      read(b);
      expect("target contents survive a swap chain reset", 255, 0, 0, pixel(8, 32));
      expect("target contents survive a swap chain reset (dark half)", 0, 0, 0, pixel(56, 32));
    } finally {
      small.close();
      small.close();
      a.close();
      b.close();
      b.close();
    }
    if (small.valid() || b.valid()) {
      failures++;
      System.out.println("pixel FAIL a closed target still reports a valid handle");
    } else {
      System.out.println("pixel ok   closing a target twice releases its handles once");
    }
  }

  private void redirectedMainFrameReachesTheBackbuffer() {
    Target main = Target.create(SIZE, SIZE, true);
    try {
      for (int frame = 0; frame < 2; frame++) {
        backend.beginFrame(new FrameSize(SIZE, SIZE), main);
        backend.clear(BOTH, 0x336699FF, 1.0f);
        draw(rect(-1.0f, 0.0f, 0.0f, 1.0f, 0xFF0000FF));
        backend.beginPresentView();
        java.util.Arrays.fill(values, 0.0f);
        values[0] = values[2] = SIZE;
        values[1] = values[3] = SIZE;
        values[4] = values[5] = values[6] = values[7] = 1.0f;
        if (!post.draw(
            Effect.BLIT,
            main.colorTexture(),
            BGFX_INVALID_HANDLE,
            false,
            values,
            post.blendState(false, false))) {
          failures++;
          System.out.println("pixel FAIL present blit was rejected");
        }
        boolean requested = frame == 1 && backend.requestScreenCapture();
        backend.endFrame();
        if (frame == 1 && !requested) {
          failures++;
          System.out.println("pixel FAIL screen capture was not requested");
          return;
        }
      }
      ScreenCapture.Frame frame = backend.takeScreenCapture();
      for (int wait = 0; wait < 10 && frame == null; wait++) {
        bgfx_frame(false);
        frame = backend.takeScreenCapture();
      }
      if (frame == null) {
        failures++;
        System.out.println("pixel FAIL redirected frame capture was not delivered");
        return;
      }
      try (NativeImage image = ScreenImages.toNativeImage(frame)) {
        expect(
            "redirected main reaches the backbuffer: quad",
            255,
            0,
            0,
            abgr(image.getPixelRGBA(8, 8)));
        expect(
            "redirected main reaches the backbuffer: clear colour",
            0x33,
            0x66,
            0x99,
            abgr(image.getPixelRGBA(56, 56)));
      } finally {
        ScreenCapture.free(frame);
      }
    } finally {
      main.close();
    }
  }

  private static int[] abgr(int pixel) {
    return new int[] {pixel & 0xFF, pixel >>> 8 & 0xFF, pixel >>> 16 & 0xFF};
  }

  private void begin() {
    backend.beginFrame(new FrameSize(SIZE, SIZE), null);
    state.blend(false);
    state.depthTest(true);
    state.depthMask(true);
  }

  private void into(Target target, int clearRgba) {
    backend.bindTarget(target);
    backend.clear(target.hasDepth() ? BOTH : ViewSequence.GL_COLOR_BUFFER_BIT, clearRgba, 1.0f);
  }

  private void sizes(Target in, Target out) {
    java.util.Arrays.fill(values, 0.0f);
    values[0] = in.width();
    values[1] = in.height();
    values[2] = out.width();
    values[3] = out.height();
  }

  private void set(int offset, float x, float y, float z, float w) {
    values[offset] = x;
    values[offset + 1] = y;
    values[offset + 2] = z;
    values[offset + 3] = w;
  }

  private void blit(Target in, Target out, boolean linear) {
    sizes(in, out);
    pass(Effect.BLIT, in, out, linear, 1.0f);
  }

  private void pass(Effect effect, Target in, Target out, boolean linear) {
    pass(effect, in, out, linear, 0.0f);
  }

  private void pass(Effect effect, Target in, Target out, boolean linear, float modulate) {
    if (modulate != 0.0f) set(4, modulate, modulate, modulate, modulate);
    if (!post.draw(
        effect,
        in.colorTexture(),
        BGFX_INVALID_HANDLE,
        linear,
        values,
        post.blendState(effect.alphaBlend(), out.hasDepth()))) {
      throw new IllegalStateException("post pass was rejected");
    }
  }

  private void draw(Batch batch) {
    if (!pipelines.submit(batch, colorContext())) {
      throw new IllegalStateException("post pixel check draw failed");
    }
  }

  private DrawContext colorContext() {
    PixelCheck.reset(context, ShaderSemantics.of("position_color"));
    ShaderInputs.fog(ShaderSemantics.of("position_color"), 0, 1, 0, context.fogParams());
    return context.set(DrawContext.Kind.COLOR, null, null, null, false, state.snapshot(SIZE, SIZE));
  }

  private static Batch rect(float x0, float y0, float x1, float y1, int rgba) {
    float[] positions = {x0, y0, 0.5f, x1, y0, 0.5f, x1, y1, 0.5f, x0, y1, 0.5f};
    return PixelCheck.batch(positions, rgba, null, null, null, null);
  }

  private void read(Target target) {
    bgfx_set_view_rect(READ_VIEW, 0, 0, SIZE, SIZE);
    bgfx_touch(READ_VIEW);
    bgfx_blit(READ_VIEW, readback, 0, 0, 0, 0, target.colorTexture(), 0, 0, 0, 0, SIZE, SIZE, 1);
    int ready = bgfx_read_texture(readback, pixels, 0);
    backend.endFrame();
    for (int frame = 0; frame < 8 && bgfx_frame(false) < ready; frame++) {}
  }

  private int[] pixel(int x, int y) {
    int[] rgba = pixel4(x, y);
    return new int[] {rgba[0], rgba[1], rgba[2]};
  }

  private int[] pixel4(int x, int y) {
    int offset = (y * SIZE + x) * 4;
    return new int[] {
      Byte.toUnsignedInt(pixels.get(offset)),
      Byte.toUnsignedInt(pixels.get(offset + 1)),
      Byte.toUnsignedInt(pixels.get(offset + 2)),
      Byte.toUnsignedInt(pixels.get(offset + 3))
    };
  }

  private static int channel(float value) {
    return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
  }

  private static float frac(float value) {
    return value - (float) Math.floor(value);
  }

  private void expectAlpha(String name, int alpha, int[] actual) {
    boolean ok = Math.abs(actual[3] - alpha) <= 2;
    if (!ok) failures++;
    System.out.printf(
        "pixel %-4s %-70s expected alpha %3d got %3d%n",
        ok ? "ok" : "FAIL", name, alpha, actual[3]);
  }

  private void expect(String name, int red, int green, int blue, int[] actual) {
    boolean ok =
        Math.abs(actual[0] - red) <= 3
            && Math.abs(actual[1] - green) <= 3
            && Math.abs(actual[2] - blue) <= 3;
    if (!ok) failures++;
    System.out.printf(
        "pixel %-4s %-70s expected %3d,%3d,%3d got %3d,%3d,%3d%n",
        ok ? "ok" : "FAIL", name, red, green, blue, actual[0], actual[1], actual[2]);
  }

  private void close() {
    post.close();
    if (BGFX_HANDLE_IS_VALID(readback)) bgfx_destroy_texture(readback);
    MemoryUtil.memFree(pixels);
  }
}
