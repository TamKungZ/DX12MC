package net.dx12mc.bridge;

import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.graphics.bgfx.Texture;

public final class DrawContext {
  public enum Kind {
    COLOR(false, false),
    COLOR_LIGHT(false, true),
    TEXTURE(true, false),
    TEXTURE_LIGHT(true, true),
    ENTITY(true, true),
    LINE(false, false),
    END_PORTAL(true, false);

    private final boolean textured;
    private final boolean lightmapped;

    Kind(boolean textured, boolean lightmapped) {
      this.textured = textured;
      this.lightmapped = lightmapped;
    }

    public boolean textured() {
      return textured;
    }

    public boolean lightmapped() {
      return lightmapped;
    }
  }

  final float[] matrix = new float[16];
  final float[] textureMatrix = new float[16];
  final float[] tint = new float[4];
  final float[] fogModel = new float[16];
  final float[] fogView = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
  final float[] fogParams = new float[4];
  final float[] fogColor = new float[4];
  final float[] material = new float[4];
  final float[] lightParams = new float[4];
  final float[] textureLod = new float[4];
  final float[] lightDirection0 = new float[4];
  final float[] lightDirection1 = new float[4];
  final float[] lineParams = new float[4];
  final float[] portalParams = new float[4];
  long primitive;
  private Kind kind = Kind.COLOR;
  private Texture texture;
  private Texture lightmap;
  private Texture overlay;
  private boolean alphaTest;
  private RenderState.Snapshot state;

  public DrawContext set(
      Kind kind,
      Texture texture,
      Texture lightmap,
      Texture overlay,
      boolean alphaTest,
      RenderState.Snapshot state) {
    this.kind = kind;
    this.texture = texture;
    this.lightmap = lightmap;
    this.overlay = overlay;
    this.alphaTest = alphaTest;
    this.state = state;
    return this;
  }

  public Kind kind() {
    return kind;
  }

  public boolean textured() {
    return kind.textured();
  }

  public boolean lightmapped() {
    return kind.lightmapped();
  }

  public Texture texture() {
    return texture;
  }

  public Texture lightmap() {
    return lightmap;
  }

  public Texture overlay() {
    return overlay;
  }

  public boolean alphaTest() {
    return alphaTest;
  }

  public RenderState.Snapshot state() {
    return state;
  }

  public float[] matrix() {
    return matrix;
  }

  public float[] textureMatrix() {
    return textureMatrix;
  }

  public float[] tint() {
    return tint;
  }

  public float[] fogModel() {
    return fogModel;
  }

  public float[] fogView() {
    return fogView;
  }

  public float[] fogParams() {
    return fogParams;
  }

  public float[] fogColor() {
    return fogColor;
  }

  public float[] material() {
    return material;
  }

  public float[] lightParams() {
    return lightParams;
  }

  public float[] textureLod() {
    return textureLod;
  }

  public float[] lightDirection0() {
    return lightDirection0;
  }

  public float[] lightDirection1() {
    return lightDirection1;
  }

  public float[] lineParams() {
    return lineParams;
  }

  public float[] portalParams() {
    return portalParams;
  }

  public long primitive() {
    return primitive;
  }
}
