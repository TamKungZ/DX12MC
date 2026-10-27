package net.dx12mc.graphics.pipeline;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.DrawContext;

final class Uniforms implements AutoCloseable {
  private final short[] handles = new short[16];
  final short mvp = create(0, "u_mvp", BGFX_UNIFORM_TYPE_MAT4);
  final short color = create(1, "u_color", BGFX_UNIFORM_TYPE_VEC4);
  final short textureMatrix = create(2, "u_texMatrix", BGFX_UNIFORM_TYPE_MAT4);
  final short fogModel = create(3, "u_fogModel", BGFX_UNIFORM_TYPE_MAT4);
  final short fogParams = create(4, "u_fogParams", BGFX_UNIFORM_TYPE_VEC4);
  final short fogColor = create(5, "u_fogColor", BGFX_UNIFORM_TYPE_VEC4);
  final short material = create(6, "u_material", BGFX_UNIFORM_TYPE_VEC4);
  final short lightParams = create(7, "u_lightParams", BGFX_UNIFORM_TYPE_VEC4);
  final short textureLod = create(8, "u_texLod", BGFX_UNIFORM_TYPE_VEC4);
  final short lightDirection0 = create(9, "u_lightDir0", BGFX_UNIFORM_TYPE_VEC4);
  final short lightDirection1 = create(10, "u_lightDir1", BGFX_UNIFORM_TYPE_VEC4);
  final short texture = create(11, "s_texColor", BGFX_UNIFORM_TYPE_SAMPLER);
  final short overlay = create(12, "s_overlay", BGFX_UNIFORM_TYPE_SAMPLER);
  final short lightmap = create(13, "s_lightmap", BGFX_UNIFORM_TYPE_SAMPLER);
  final short lineParams = create(14, "u_lineParams", BGFX_UNIFORM_TYPE_VEC4);
  final short portal = create(15, "u_portal", BGFX_UNIFORM_TYPE_VEC4);

  Uniforms() {
    for (int i = 0; i < handles.length; i++) {
      if (!BGFX_HANDLE_IS_VALID(handles[i])) {
        close();
        throw new IllegalStateException("Unable to create D3D12 pipeline uniforms");
      }
    }
  }

  void perDraw(DrawContext context) {
    bgfx_set_uniform(mvp, context.matrix(), 1);
  }

  void shared(DrawContext context) {
    bgfx_set_uniform(color, context.tint(), 1);
    bgfx_set_uniform(fogModel, context.fogModel(), 1);
    bgfx_set_uniform(fogParams, context.fogParams(), 1);
    bgfx_set_uniform(fogColor, context.fogColor(), 1);
    if (context.alphaTest() || context.textured()) {
      bgfx_set_uniform(material, context.material(), 1);
    }
    if (context.textured()) {
      bgfx_set_uniform(textureMatrix, context.textureMatrix(), 1);
      bgfx_set_uniform(textureLod, context.textureLod(), 1);
    }
    if (context.lightmapped()) bgfx_set_uniform(lightParams, context.lightParams(), 1);
    if (context.kind() == DrawContext.Kind.ENTITY) {
      bgfx_set_uniform(lightDirection0, context.lightDirection0(), 1);
      bgfx_set_uniform(lightDirection1, context.lightDirection1(), 1);
    }
    if (context.kind() == DrawContext.Kind.LINE) {
      bgfx_set_uniform(lineParams, context.lineParams(), 1);
    }
    if (context.kind() == DrawContext.Kind.END_PORTAL) {
      bgfx_set_uniform(portal, context.portalParams(), 1);
    }
  }

  @Override
  public void close() {
    for (int i = 0; i < handles.length; i++) {
      if (BGFX_HANDLE_IS_VALID(handles[i])) bgfx_destroy_uniform(handles[i]);
      handles[i] = BGFX_INVALID_HANDLE;
    }
  }

  private short create(int index, String name, int type) {
    short handle = bgfx_create_uniform(name, type, 1);
    handles[index] = handle;
    return handle;
  }
}
