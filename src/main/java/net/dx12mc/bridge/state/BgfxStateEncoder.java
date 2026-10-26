package net.dx12mc.bridge.state;

final class BgfxStateEncoder {
  static final int GL_ZERO = 0;
  static final int GL_ONE = 1;
  static final int GL_LEQUAL = 0x0203;
  static final int GL_FUNC_ADD = 0x8006;

  private static final long WRITE_R = 0x0000000000000001L;
  private static final long WRITE_G = 0x0000000000000002L;
  private static final long WRITE_B = 0x0000000000000004L;
  private static final long WRITE_A = 0x0000000000000008L;
  private static final long WRITE_Z = 0x0000004000000000L;
  private static final long CULL_CW = 0x0000001000000000L;
  private static final long MSAA = 0x0100000000000000L;

  private BgfxStateEncoder() {}

  static long standard(
      boolean writeR,
      boolean writeG,
      boolean writeB,
      boolean writeA,
      boolean depthTest,
      int depthFunc,
      boolean depthWrite,
      boolean blend,
      int srcRgb,
      int dstRgb,
      int srcAlpha,
      int dstAlpha,
      int blendEquation,
      boolean cull) {
    long state = colorWrites(writeR, writeG, writeB, writeA) | MSAA;
    if (depthTest) {
      state |= depthBits(depthFunc);
      if (depthWrite) state |= WRITE_Z;
    }
    if (blend) state |= blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha) | equationBits(blendEquation);
    if (cull) state |= CULL_CW;
    return state;
  }

  static long sky(
      boolean writeR,
      boolean writeG,
      boolean writeB,
      boolean writeA,
      boolean blend,
      int srcRgb,
      int dstRgb,
      int srcAlpha,
      int dstAlpha,
      int blendEquation) {
    long state = colorWrites(writeR, writeG, writeB, writeA) | MSAA;
    if (blend) state |= blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha) | equationBits(blendEquation);
    return state;
  }

  // bgfx has no logic-op state. GL_OR_REVERSE (src | ~dst) is drawn as dst * (1 - src) followed by
  // a constant-white pass blended as 1 - dst; exact wherever each source channel is 0 or 1.
  static long orReverseMask(
      boolean writeR,
      boolean writeG,
      boolean writeB,
      boolean writeA,
      boolean depthTest,
      int depthFunc,
      boolean depthWrite,
      boolean cull) {
    return logicPass(writeR, writeG, writeB, writeA, depthTest, depthFunc, depthWrite, cull)
        | blendBits(GL_ZERO, 0x0301, GL_ZERO, 0x0303);
  }

  static long orReverseInvert(
      boolean writeR,
      boolean writeG,
      boolean writeB,
      boolean writeA,
      boolean depthTest,
      int depthFunc,
      boolean depthWrite,
      boolean cull) {
    return logicPass(
            writeR, writeG, writeB, writeA, depthTest, depthWrite ? 0x0202 : depthFunc, false, cull)
        | blendBits(0x0307, GL_ZERO, 0x0305, GL_ZERO);
  }

  private static long logicPass(
      boolean writeR,
      boolean writeG,
      boolean writeB,
      boolean writeA,
      boolean depthTest,
      int depthFunc,
      boolean depthWrite,
      boolean cull) {
    long state = colorWrites(writeR, writeG, writeB, writeA) | MSAA;
    if (depthTest) {
      state |= depthBits(depthFunc);
      if (depthWrite) state |= WRITE_Z;
    }
    if (cull) state |= CULL_CW;
    return state;
  }

  private static long colorWrites(boolean red, boolean green, boolean blue, boolean alpha) {
    long state = 0L;
    if (red) state |= WRITE_R;
    if (green) state |= WRITE_G;
    if (blue) state |= WRITE_B;
    if (alpha) state |= WRITE_A;
    return state;
  }

  private static long depthBits(int func) {
    return switch (func) {
      case 0x0201 -> 0x10L;
      case GL_LEQUAL -> 0x20L;
      case 0x0202 -> 0x30L;
      case 0x0206 -> 0x40L;
      case 0x0204 -> 0x50L;
      case 0x0205 -> 0x60L;
      case 0x0200 -> 0x70L;
      case 0x0207 -> 0x80L;
      default -> unsupported("depth func", func);
    };
  }

  private static long blendBits(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    long rgb = blendFactor(srcRgb) | (blendFactor(dstRgb) << 4);
    long alpha = blendFactor(srcAlpha) | (blendFactor(dstAlpha) << 4);
    return rgb | (alpha << 8);
  }

  private static long blendFactor(int factor) {
    return switch (factor) {
      case GL_ZERO -> 0x1000L;
      case GL_ONE -> 0x2000L;
      case 0x0300 -> 0x3000L;
      case 0x0301 -> 0x4000L;
      case 0x0302 -> 0x5000L;
      case 0x0303 -> 0x6000L;
      case 0x0304 -> 0x7000L;
      case 0x0305 -> 0x8000L;
      case 0x0306 -> 0x9000L;
      case 0x0307 -> 0xA000L;
      case 0x0308 -> 0xB000L;
      default -> unsupported("blend factor", factor);
    };
  }

  private static long equationBits(int equation) {
    long rgb =
        switch (equation) {
          case GL_FUNC_ADD -> 0L;
          case 0x800A -> 0x10000000L;
          case 0x800B -> 0x20000000L;
          case 0x8007 -> 0x30000000L;
          case 0x8008 -> 0x40000000L;
          default -> unsupported("blend equation", equation);
        };
    return rgb | (rgb << 3);
  }

  private static long unsupported(String value, int code) {
    throw new IllegalStateException(
        "Unsupported OpenGL " + value + " for D3D12: 0x" + Integer.toHexString(code));
  }
}
