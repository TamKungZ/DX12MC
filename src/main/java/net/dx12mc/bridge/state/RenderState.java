package net.dx12mc.bridge.state;

public final class RenderState {
  private static final int GL_ZERO = 0;
  private static final int GL_ONE = 1;
  private static final int GL_NEVER = 0x0200;
  private static final int GL_LESS = 0x0201;
  private static final int GL_EQUAL = 0x0202;
  private static final int GL_LEQUAL = 0x0203;
  private static final int GL_GREATER = 0x0204;
  private static final int GL_NOTEQUAL = 0x0205;
  private static final int GL_GEQUAL = 0x0206;
  private static final int GL_ALWAYS = 0x0207;
  private static final int GL_SRC_COLOR = 0x0300;
  private static final int GL_ONE_MINUS_SRC_COLOR = 0x0301;
  private static final int GL_SRC_ALPHA = 0x0302;
  private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
  private static final int GL_DST_ALPHA = 0x0304;
  private static final int GL_ONE_MINUS_DST_ALPHA = 0x0305;
  private static final int GL_DST_COLOR = 0x0306;
  private static final int GL_ONE_MINUS_DST_COLOR = 0x0307;
  private static final int GL_SRC_ALPHA_SATURATE = 0x0308;
  private static final int GL_FUNC_ADD = 0x8006;
  private static final int GL_MIN = 0x8007;
  private static final int GL_MAX = 0x8008;
  private static final int GL_FUNC_SUBTRACT = 0x800A;
  private static final int GL_FUNC_REVERSE_SUBTRACT = 0x800B;
  private static final int GL_POINT = 0x1B00;
  private static final int GL_LINE = 0x1B01;
  private static final int GL_FILL = 0x1B02;
  private static final long WRITE_R = 0x0000000000000001L;
  private static final long WRITE_G = 0x0000000000000002L;
  private static final long WRITE_B = 0x0000000000000004L;
  private static final long WRITE_A = 0x0000000000000008L;
  private static final long WRITE_Z = 0x0000004000000000L;
  private static final long DEPTH_LESS = 0x0000000000000010L;
  private static final long DEPTH_LEQUAL = 0x0000000000000020L;
  private static final long DEPTH_EQUAL = 0x0000000000000030L;
  private static final long DEPTH_GEQUAL = 0x0000000000000040L;
  private static final long DEPTH_GREATER = 0x0000000000000050L;
  private static final long DEPTH_NOTEQUAL = 0x0000000000000060L;
  private static final long DEPTH_NEVER = 0x0000000000000070L;
  private static final long DEPTH_ALWAYS = 0x0000000000000080L;
  private static final long BLEND_ZERO = 0x0000000000001000L;
  private static final long BLEND_ONE = 0x0000000000002000L;
  private static final long BLEND_SRC_COLOR = 0x0000000000003000L;
  private static final long BLEND_INV_SRC_COLOR = 0x0000000000004000L;
  private static final long BLEND_SRC_ALPHA = 0x0000000000005000L;
  private static final long BLEND_INV_SRC_ALPHA = 0x0000000000006000L;
  private static final long BLEND_DST_ALPHA = 0x0000000000007000L;
  private static final long BLEND_INV_DST_ALPHA = 0x0000000000008000L;
  private static final long BLEND_DST_COLOR = 0x0000000000009000L;
  private static final long BLEND_INV_DST_COLOR = 0x000000000000A000L;
  private static final long BLEND_SRC_ALPHA_SAT = 0x000000000000B000L;
  private static final long EQUATION_SUB = 0x0000000010000000L;
  private static final long EQUATION_REVSUB = 0x0000000020000000L;
  private static final long EQUATION_MIN = 0x0000000030000000L;
  private static final long EQUATION_MAX = 0x0000000040000000L;
  private static final long CULL_CW = 0x0000001000000000L;
  private static final long MSAA = 0x0100000000000000L;
  private static final long IMMEDIATE_COMPAT_BASE =
      WRITE_R | WRITE_G | WRITE_B | WRITE_A | WRITE_Z | DEPTH_LEQUAL | MSAA;
  private static final long IMMEDIATE_COMPAT_ALPHA_BLEND = 0x0000000006565000L;
  private boolean depthTest = true;
  private int depthFunc = GL_LEQUAL;
  private boolean depthWrite = true;
  private boolean blend;
  private int srcRgb = GL_ONE;
  private int dstRgb = GL_ZERO;
  private int srcAlpha = GL_ONE;
  private int dstAlpha = GL_ZERO;
  private int blendEquation = GL_FUNC_ADD;
  private boolean cull;
  private boolean writeR = true, writeG = true, writeB = true, writeA = true;
  private boolean scissor;
  private int scissorX, scissorY, scissorWidth, scissorHeight;
  private boolean polygonOffset;
  private float polygonOffsetFactor;
  private float polygonOffsetUnits;
  private int polygonMode = GL_FILL;
  private boolean colorLogic;
  private int logicOp = 0x1503;
  private long revision;

  public void depthTest(boolean enabled) {
    if (depthTest != enabled) {
      depthTest = enabled;
      revision++;
    }
  }

  public void depthFunc(int func) {
    if (depthFunc != func) {
      depthFunc = func;
      revision++;
    }
  }

  public void depthMask(boolean write) {
    if (depthWrite != write) {
      depthWrite = write;
      revision++;
    }
  }

  public void blend(boolean enabled) {
    if (blend != enabled) {
      blend = enabled;
      revision++;
    }
  }

  public void blendFunc(int src, int dst) {
    blendFuncSeparate(src, dst, src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    if (this.srcRgb != srcRgb
        || this.dstRgb != dstRgb
        || this.srcAlpha != srcAlpha
        || this.dstAlpha != dstAlpha) {
      this.srcRgb = srcRgb;
      this.dstRgb = dstRgb;
      this.srcAlpha = srcAlpha;
      this.dstAlpha = dstAlpha;
      revision++;
    }
  }

  public void blendEquation(int equation) {
    if (blendEquation != equation) {
      blendEquation = equation;
      revision++;
    }
  }

  public void cull(boolean enabled) {
    if (cull != enabled) {
      cull = enabled;
      revision++;
    }
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    if (writeR != red || writeG != green || writeB != blue || writeA != alpha) {
      writeR = red;
      writeG = green;
      writeB = blue;
      writeA = alpha;
      revision++;
    }
  }

  public void scissor(boolean enabled) {
    if (scissor != enabled) {
      scissor = enabled;
      revision++;
    }
  }

  public void scissorBox(int x, int y, int width, int height) {
    if (scissorX != x || scissorY != y || scissorWidth != width || scissorHeight != height) {
      scissorX = x;
      scissorY = y;
      scissorWidth = width;
      scissorHeight = height;
      revision++;
    }
  }

  public void polygonOffset(boolean enabled) {
    if (polygonOffset != enabled) {
      polygonOffset = enabled;
      revision++;
    }
  }

  public void polygonOffset(float factor, float units) {
    if (Float.compare(polygonOffsetFactor, factor) != 0
        || Float.compare(polygonOffsetUnits, units) != 0) {
      polygonOffsetFactor = factor;
      polygonOffsetUnits = units;
      revision++;
    }
  }

  public void polygonMode(int face, int mode) {
    if (polygonMode != mode) {
      polygonMode = mode;
      revision++;
    }
  }

  public void colorLogic(boolean enabled) {
    if (colorLogic != enabled) {
      colorLogic = enabled;
      revision++;
    }
  }

  public void logicOp(int op) {
    if (logicOp != op) {
      logicOp = op;
      revision++;
    }
  }

  public Snapshot snapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    long state = MSAA;
    if (writeR) state |= WRITE_R;
    if (writeG) state |= WRITE_G;
    if (writeB) state |= WRITE_B;
    if (writeA) state |= WRITE_A;
    if (depthTest) {
      state |= depthBits(depthFunc);
      if (depthWrite) state |= WRITE_Z;
    }
    if (blend) state |= blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha) | equationBits(blendEquation);
    if (cull) state |= CULL_CW;
    ScissorRect rect = clippedScissor(framebufferWidth, framebufferHeight);
    return new Snapshot(
        state,
        scissor,
        scissor && rect.empty(),
        rect.x(),
        rect.y(),
        rect.width(),
        rect.height(),
        revision,
        depthTest,
        depthWrite,
        blend,
        cull);
  }

  public Snapshot immediateCompatibilitySnapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    ScissorRect rect = clippedScissor(framebufferWidth, framebufferHeight);
    long state = IMMEDIATE_COMPAT_BASE;
    if (blend) {
      state |= blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha) | equationBits(blendEquation);
    } else {
      state |= IMMEDIATE_COMPAT_ALPHA_BLEND;
    }
    return new Snapshot(
        state,
        scissor,
        scissor && rect.empty(),
        rect.x(),
        rect.y(),
        rect.width(),
        rect.height(),
        revision,
        true,
        true,
        true,
        false);
  }

  public Snapshot skyCompatibilitySnapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    ScissorRect rect = clippedScissor(framebufferWidth, framebufferHeight);
    long state = MSAA;
    if (writeR) state |= WRITE_R;
    if (writeG) state |= WRITE_G;
    if (writeB) state |= WRITE_B;
    if (writeA) state |= WRITE_A;
    if (blend) state |= blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha) | equationBits(blendEquation);
    return new Snapshot(
        state,
        scissor,
        scissor && rect.empty(),
        rect.x(),
        rect.y(),
        rect.width(),
        rect.height(),
        revision,
        false,
        false,
        blend,
        false);
  }

  private void validateUnsupportedState() {
    if (polygonOffset) {
      throw new IllegalStateException(
          "OpenGL polygon offset/depth bias is active but D3D12 translation is not implemented:"
              + " factor="
              + polygonOffsetFactor
              + ", units="
              + polygonOffsetUnits);
    }
    if (polygonMode != GL_FILL) {
      String mode =
          polygonMode == GL_LINE
              ? "GL_LINE"
              : polygonMode == GL_POINT ? "GL_POINT" : "0x" + Integer.toHexString(polygonMode);
      throw new IllegalStateException(
          "OpenGL polygon mode "
              + mode
              + " is active but the D3D12 primitive/raster translation is not implemented");
    }
    if (colorLogic) {
      throw new IllegalStateException(
          "OpenGL color logic operation is active but D3D12 translation is not implemented: op=0x"
              + Integer.toHexString(logicOp));
    }
  }

  private ScissorRect clippedScissor(int framebufferWidth, int framebufferHeight) {
    if (!scissor) return new ScissorRect(0, 0, 0, 0, false);
    int fbWidth = Math.max(0, framebufferWidth);
    int fbHeight = Math.max(0, framebufferHeight);
    long left = scissorX;
    long right = (long) scissorX + Math.max(0, scissorWidth);
    long bottom = scissorY;
    long top = (long) scissorY + Math.max(0, scissorHeight);
    long clippedLeft = clamp(left, 0, fbWidth);
    long clippedRight = clamp(right, 0, fbWidth);
    long clippedBottom = clamp(bottom, 0, fbHeight);
    long clippedTop = clamp(top, 0, fbHeight);
    if (clippedRight <= clippedLeft || clippedTop <= clippedBottom) {
      return new ScissorRect(0, 0, 0, 0, true);
    }
    long topLeftY = (long) fbHeight - clippedTop;
    return new ScissorRect(
        clamp16(clippedLeft),
        clamp16(topLeftY),
        clamp16(clippedRight - clippedLeft),
        clamp16(clippedTop - clippedBottom),
        false);
  }

  private static long depthBits(int func) {
    return switch (func) {
      case GL_LESS -> DEPTH_LESS;
      case GL_LEQUAL -> DEPTH_LEQUAL;
      case GL_EQUAL -> DEPTH_EQUAL;
      case GL_GEQUAL -> DEPTH_GEQUAL;
      case GL_GREATER -> DEPTH_GREATER;
      case GL_NOTEQUAL -> DEPTH_NOTEQUAL;
      case GL_NEVER -> DEPTH_NEVER;
      case GL_ALWAYS -> DEPTH_ALWAYS;
      default ->
          throw new IllegalStateException(
              "Unsupported OpenGL depth func for D3D12: 0x" + Integer.toHexString(func));
    };
  }

  private static long blendBits(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    long sRgb = blendFactor(srcRgb);
    long dRgb = blendFactor(dstRgb);
    long sA = blendFactor(srcAlpha);
    long dA = blendFactor(dstAlpha);
    return (sRgb | (dRgb << 4)) | ((sA | (dA << 4)) << 8);
  }

  private static long blendFactor(int factor) {
    return switch (factor) {
      case GL_ZERO -> BLEND_ZERO;
      case GL_ONE -> BLEND_ONE;
      case GL_SRC_COLOR -> BLEND_SRC_COLOR;
      case GL_ONE_MINUS_SRC_COLOR -> BLEND_INV_SRC_COLOR;
      case GL_SRC_ALPHA -> BLEND_SRC_ALPHA;
      case GL_ONE_MINUS_SRC_ALPHA -> BLEND_INV_SRC_ALPHA;
      case GL_DST_ALPHA -> BLEND_DST_ALPHA;
      case GL_ONE_MINUS_DST_ALPHA -> BLEND_INV_DST_ALPHA;
      case GL_DST_COLOR -> BLEND_DST_COLOR;
      case GL_ONE_MINUS_DST_COLOR -> BLEND_INV_DST_COLOR;
      case GL_SRC_ALPHA_SATURATE -> BLEND_SRC_ALPHA_SAT;
      default ->
          throw new IllegalStateException(
              "Unsupported OpenGL blend factor for D3D12: 0x" + Integer.toHexString(factor));
    };
  }

  private static long equationBits(int equation) {
    long rgb =
        switch (equation) {
          case GL_FUNC_ADD -> 0L;
          case GL_FUNC_SUBTRACT -> EQUATION_SUB;
          case GL_FUNC_REVERSE_SUBTRACT -> EQUATION_REVSUB;
          case GL_MIN -> EQUATION_MIN;
          case GL_MAX -> EQUATION_MAX;
          default ->
              throw new IllegalStateException(
                  "Unsupported OpenGL blend equation for D3D12: 0x"
                      + Integer.toHexString(equation));
        };
    return rgb | (rgb << 3);
  }

  private static long clamp(long value, long min, long max) {
    return Math.max(min, Math.min(max, value));
  }

  private static int clamp16(long value) {
    return (int) Math.max(0, Math.min(0xFFFFL, value));
  }

  private record ScissorRect(int x, int y, int width, int height, boolean empty) {}

  public record Snapshot(
      long bgfxState,
      boolean scissorEnabled,
      boolean scissorEmpty,
      int scissorX,
      int scissorY,
      int scissorWidth,
      int scissorHeight,
      long revision,
      boolean depthTest,
      boolean depthWrite,
      boolean blend,
      boolean cull) {}
}
