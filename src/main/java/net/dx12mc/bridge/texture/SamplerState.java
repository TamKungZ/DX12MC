package net.dx12mc.bridge.texture;

import static org.lwjgl.bgfx.BGFX.*;

public final class SamplerState {
  static final int GL_TEXTURE_2D = 0x0DE1;
  static final int GL_TEXTURE_MAG_FILTER = 0x2800;
  static final int GL_TEXTURE_MIN_FILTER = 0x2801;
  static final int GL_TEXTURE_WRAP_S = 0x2802;
  static final int GL_TEXTURE_WRAP_T = 0x2803;
  static final int GL_TEXTURE_MAX_LOD = 0x813B;
  static final int GL_TEXTURE_MAX_LEVEL = 0x813D;
  static final int GL_NEAREST = 0x2600;
  static final int GL_LINEAR = 0x2601;
  static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
  static final int GL_LINEAR_MIPMAP_NEAREST = 0x2701;
  static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
  static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
  static final int GL_REPEAT = 0x2901;
  static final int GL_CLAMP_TO_BORDER = 0x812D;
  static final int GL_CLAMP_TO_EDGE = 0x812F;
  static final int GL_MIRRORED_REPEAT = 0x8370;

  private int minFilter = GL_NEAREST_MIPMAP_LINEAR;
  private int magFilter = GL_LINEAR;
  private int wrapS = GL_REPEAT;
  private int wrapT = GL_REPEAT;
  private float maxLevel = 1000.0f;
  private float maxLod = 1000.0f;
  private long revision;

  public boolean parameter(int pname, float value) {
    switch (pname) {
      case GL_TEXTURE_MIN_FILTER -> minFilter = (int) value;
      case GL_TEXTURE_MAG_FILTER -> magFilter = (int) value;
      case GL_TEXTURE_WRAP_S -> wrapS = (int) value;
      case GL_TEXTURE_WRAP_T -> wrapT = (int) value;
      case GL_TEXTURE_MAX_LEVEL -> maxLevel = value;
      case GL_TEXTURE_MAX_LOD -> maxLod = value;
      default -> {
        return false;
      }
    }
    revision++;
    return true;
  }

  public long revision() {
    return revision;
  }

  public int bgfxFlags() {
    int flags = BGFX_SAMPLER_NONE;
    flags |=
        switch (minFilter) {
          case GL_NEAREST, GL_NEAREST_MIPMAP_LINEAR -> BGFX_SAMPLER_MIN_POINT;
          case GL_NEAREST_MIPMAP_NEAREST -> BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MIP_POINT;
          case GL_LINEAR_MIPMAP_NEAREST -> BGFX_SAMPLER_MIP_POINT;
          default -> BGFX_SAMPLER_NONE;
        };
    if (magFilter == GL_NEAREST) flags |= BGFX_SAMPLER_MAG_POINT;
    flags |= wrap(wrapS, BGFX_SAMPLER_U_CLAMP, BGFX_SAMPLER_U_MIRROR, BGFX_SAMPLER_U_BORDER);
    flags |= wrap(wrapT, BGFX_SAMPLER_V_CLAMP, BGFX_SAMPLER_V_MIRROR, BGFX_SAMPLER_V_BORDER);
    return flags;
  }

  public float maxLod(int allocatedMaxLevel) {
    if (minFilter == GL_NEAREST || minFilter == GL_LINEAR) return 0.0f;
    return Math.max(0.0f, Math.min(allocatedMaxLevel, Math.min(maxLevel, maxLod)));
  }

  private static int wrap(int mode, int clamp, int mirror, int border) {
    return switch (mode) {
      case GL_CLAMP_TO_EDGE -> clamp;
      case GL_MIRRORED_REPEAT -> mirror;
      case GL_CLAMP_TO_BORDER -> border;
      default -> BGFX_SAMPLER_NONE;
    };
  }
}
