package net.dx12mc.bridge;

import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.bgfx.Texture;

public final class DrawContext {
  private boolean textured;
  private Texture texture;
  private boolean lightmapped;
  private Texture lightmap;
  private boolean cutout;
  private RenderState.Snapshot state;

  DrawContext set(
      boolean textured,
      Texture texture,
      boolean lightmapped,
      Texture lightmap,
      boolean cutout,
      RenderState.Snapshot state) {
    this.textured = textured;
    this.texture = texture;
    this.lightmapped = lightmapped;
    this.lightmap = lightmap;
    this.cutout = cutout;
    this.state = state;
    return this;
  }

  public boolean textured() {
    return textured;
  }

  public Texture texture() {
    return texture;
  }

  public boolean lightmapped() {
    return lightmapped;
  }

  public Texture lightmap() {
    return lightmap;
  }

  public boolean cutout() {
    return cutout;
  }

  public RenderState.Snapshot state() {
    return state;
  }
}
