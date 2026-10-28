package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Program;
import net.dx12mc.graphics.bgfx.ViewSequence;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.system.MemoryStack;

public final class PostPipeline implements AutoCloseable {
  public static final int UNIFORM_FLOATS = 28;
  private static final String SHADERS = "/assets/dx12mc/shaders/d3d12/";
  private static final long WRITE_ALL = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A;
  private static final long OPAQUE_STATE =
      WRITE_ALL | BGFX_STATE_DEPTH_TEST_ALWAYS | BGFX_STATE_WRITE_Z;
  private static final long ALPHA_STATE =
      OPAQUE_STATE
          | BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA);
  private static final long LINEAR = BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
  private static final long POINT = LINEAR | BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT;

  public enum Effect {
    BOX_BLUR("box_blur", "fs_post_box_blur", false),
    ENTITY_OUTLINE("entity_outline", "fs_post_entity_sobel", true),
    ENTITY_OUTLINE_BOX_BLUR("entity_outline_box_blur", "fs_post_outline_blur", false),
    BLIT("blit", "fs_post_blit", true),
    INVERT("invert", "fs_post_invert", false),
    COLOR_CONVOLVE("color_convolve", "fs_post_color_convolve", false),
    BITS("bits", "fs_post_bits", true),
    SPIDER("spider", "fs_post_spider", false);

    private final String vanillaName;
    private final String fragment;
    private final boolean alphaBlend;

    Effect(String vanillaName, String fragment, boolean alphaBlend) {
      this.vanillaName = vanillaName;
      this.fragment = fragment;
      this.alphaBlend = alphaBlend;
    }

    public String vanillaName() {
      return vanillaName;
    }

    public boolean alphaBlend() {
      return alphaBlend;
    }

    public static Effect byName(String name) {
      if (name == null) return null;
      String bare = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
      for (Effect effect : values()) if (effect.vanillaName.equals(bare)) return effect;
      return null;
    }
  }

  private final Program[] programs = new Program[Effect.values().length];
  private final short[] uniforms = new short[9];
  private Layout layout;

  public void start() {
    if (layout != null) return;
    try {
      layout = Layout.position();
      uniforms[0] = uniform("s_post0", BGFX_UNIFORM_TYPE_SAMPLER);
      uniforms[1] = uniform("s_post1", BGFX_UNIFORM_TYPE_SAMPLER);
      uniforms[2] = uniform("u_postSize", BGFX_UNIFORM_TYPE_VEC4);
      String[] names = {"u_postA", "u_postB", "u_postC", "u_postD", "u_postE", "u_postF"};
      for (int i = 0; i < names.length; i++) {
        uniforms[3 + i] = uniform(names[i], BGFX_UNIFORM_TYPE_VEC4);
      }
      for (Effect effect : Effect.values()) {
        programs[effect.ordinal()] =
            Program.load(SHADERS + "vs_post.bin", SHADERS + effect.fragment + ".bin");
      }
    } catch (Throwable throwable) {
      close();
      throw throwable;
    }
  }

  public boolean started() {
    return layout != null;
  }

  public long blendState(boolean alphaBlend, boolean depth) {
    long state = alphaBlend ? ALPHA_STATE : OPAQUE_STATE;
    return depth ? state : state & ~(BGFX_STATE_DEPTH_TEST_MASK | BGFX_STATE_WRITE_Z);
  }

  public boolean draw(
      Effect effect, short color, short aux, boolean linear, float[] values, long state) {
    if (!started()) throw new IllegalStateException("post pipeline is not started");
    if (!fullscreenTriangle()) return false;
    long flags = linear ? LINEAR : POINT;
    bgfx_set_texture(0, uniforms[0], color, (int) flags);
    if (BGFX_HANDLE_IS_VALID(aux)) bgfx_set_texture(1, uniforms[1], aux, (int) flags);
    bgfx_set_uniform(uniforms[2], slice(values, 0), 1);
    for (int i = 0; i < 6; i++) bgfx_set_uniform(uniforms[3 + i], slice(values, 4 + i * 4), 1);
    bgfx_set_state(state, 0);
    bgfx_submit(ViewSequence.current(), programs[effect.ordinal()].handle(), 0, BGFX_DISCARD_ALL);
    ViewSequence.used();
    return true;
  }

  public boolean drawProgram(short program, long state) {
    if (!started()) throw new IllegalStateException("post pipeline is not started");
    if (!fullscreenTriangle()) return false;
    bgfx_set_state(state, 0);
    bgfx_submit(ViewSequence.current(), program, 0, BGFX_DISCARD_ALL);
    ViewSequence.used();
    return true;
  }

  private boolean fullscreenTriangle() {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      BGFXTransientVertexBuffer vertices = BGFXTransientVertexBuffer.calloc(stack);
      if (bgfx_get_avail_transient_vertex_buffer(3, layout.value()) < 3) return false;
      bgfx_alloc_transient_vertex_buffer(vertices, 3, layout.value());
      ByteBuffer data = vertices.data().order(ByteOrder.nativeOrder());
      data.putFloat(-1.0f).putFloat(-1.0f).putFloat(0.0f);
      data.putFloat(3.0f).putFloat(-1.0f).putFloat(0.0f);
      data.putFloat(-1.0f).putFloat(3.0f).putFloat(0.0f);
      bgfx_set_transient_vertex_buffer(0, vertices, 0, 3);
      return true;
    }
  }

  private static float[] slice(float[] values, int offset) {
    float[] part = new float[4];
    System.arraycopy(values, offset, part, 0, 4);
    return part;
  }

  @Override
  public void close() {
    for (int i = 0; i < programs.length; i++) {
      if (programs[i] != null) {
        programs[i].close();
        programs[i] = null;
      }
    }
    for (int i = 0; i < uniforms.length; i++) {
      if (BGFX_HANDLE_IS_VALID(uniforms[i])) bgfx_destroy_uniform(uniforms[i]);
      uniforms[i] = BGFX_INVALID_HANDLE;
    }
    if (layout != null) {
      layout.close();
      layout = null;
    }
  }

  private static short uniform(String name, int type) {
    short handle = bgfx_create_uniform(name, type, 1);
    if (!BGFX_HANDLE_IS_VALID(handle)) {
      throw new IllegalStateException("Unable to create post-processing uniform " + name);
    }
    return handle;
  }
}
