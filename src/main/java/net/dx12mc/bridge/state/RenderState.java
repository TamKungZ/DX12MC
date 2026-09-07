package net.dx12mc.bridge.state;

public final class RenderState {
  private static final int GL_POINT = 0x1B00;
  private static final int GL_LINE = 0x1B01;
  private static final int GL_FILL = 0x1B02;

  private boolean depthTest = true;
  private int depthFunc = BgfxStateEncoder.GL_LEQUAL;
  private boolean depthWrite = true;
  private boolean blend;
  private int srcRgb = BgfxStateEncoder.GL_ONE;
  private int dstRgb = BgfxStateEncoder.GL_ZERO;
  private int srcAlpha = BgfxStateEncoder.GL_ONE;
  private int dstAlpha = BgfxStateEncoder.GL_ZERO;
  private int blendEquation = BgfxStateEncoder.GL_FUNC_ADD;
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
  private Snapshot cachedStandard;
  private Snapshot cachedImmediate;
  private Snapshot cachedSky;
  private int cachedStandardWidth, cachedStandardHeight;
  private int cachedImmediateWidth, cachedImmediateHeight;
  private int cachedSkyWidth, cachedSkyHeight;

  public void depthTest(boolean enabled) {
    if (depthTest != enabled) {
      depthTest = enabled;
      changed();
    }
  }

  public void depthFunc(int func) {
    if (depthFunc != func) {
      depthFunc = func;
      changed();
    }
  }

  public void depthMask(boolean write) {
    if (depthWrite != write) {
      depthWrite = write;
      changed();
    }
  }

  public void blend(boolean enabled) {
    if (blend != enabled) {
      blend = enabled;
      changed();
    }
  }

  public void blendFunc(int src, int dst) {
    blendFuncSeparate(src, dst, src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    if (this.srcRgb == srcRgb
        && this.dstRgb == dstRgb
        && this.srcAlpha == srcAlpha
        && this.dstAlpha == dstAlpha) return;
    this.srcRgb = srcRgb;
    this.dstRgb = dstRgb;
    this.srcAlpha = srcAlpha;
    this.dstAlpha = dstAlpha;
    changed();
  }

  public void blendEquation(int equation) {
    if (blendEquation != equation) {
      blendEquation = equation;
      changed();
    }
  }

  public void cull(boolean enabled) {
    if (cull != enabled) {
      cull = enabled;
      changed();
    }
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    if (writeR == red && writeG == green && writeB == blue && writeA == alpha) return;
    writeR = red;
    writeG = green;
    writeB = blue;
    writeA = alpha;
    changed();
  }

  public void scissor(boolean enabled) {
    if (scissor != enabled) {
      scissor = enabled;
      changed();
    }
  }

  public void scissorBox(int x, int y, int width, int height) {
    if (scissorX == x && scissorY == y && scissorWidth == width && scissorHeight == height) return;
    scissorX = x;
    scissorY = y;
    scissorWidth = width;
    scissorHeight = height;
    changed();
  }

  public void polygonOffset(boolean enabled) {
    if (polygonOffset != enabled) {
      polygonOffset = enabled;
      changed();
    }
  }

  public void polygonOffset(float factor, float units) {
    if (Float.compare(polygonOffsetFactor, factor) == 0
        && Float.compare(polygonOffsetUnits, units) == 0) return;
    polygonOffsetFactor = factor;
    polygonOffsetUnits = units;
    changed();
  }

  public void polygonMode(int face, int mode) {
    if (polygonMode != mode) {
      polygonMode = mode;
      changed();
    }
  }

  public void colorLogic(boolean enabled) {
    if (colorLogic != enabled) {
      colorLogic = enabled;
      changed();
    }
  }

  public void logicOp(int op) {
    if (logicOp != op) {
      logicOp = op;
      changed();
    }
  }

  public Snapshot snapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    if (matches(
        cachedStandard,
        cachedStandardWidth,
        cachedStandardHeight,
        framebufferWidth,
        framebufferHeight)) {
      return cachedStandard;
    }
    cachedStandardWidth = framebufferWidth;
    cachedStandardHeight = framebufferHeight;
    cachedStandard =
        snapshot(
            BgfxStateEncoder.standard(
                writeR,
                writeG,
                writeB,
                writeA,
                depthTest,
                depthFunc,
                depthWrite,
                blend,
                srcRgb,
                dstRgb,
                srcAlpha,
                dstAlpha,
                blendEquation,
                cull),
            framebufferWidth,
            framebufferHeight,
            depthTest,
            depthWrite,
            blend,
            cull);
    return cachedStandard;
  }

  public Snapshot immediateCompatibilitySnapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    if (matches(
        cachedImmediate,
        cachedImmediateWidth,
        cachedImmediateHeight,
        framebufferWidth,
        framebufferHeight)) {
      return cachedImmediate;
    }
    cachedImmediateWidth = framebufferWidth;
    cachedImmediateHeight = framebufferHeight;
    cachedImmediate =
        snapshot(
            BgfxStateEncoder.immediate(blend, srcRgb, dstRgb, srcAlpha, dstAlpha, blendEquation),
            framebufferWidth,
            framebufferHeight,
            true,
            true,
            true,
            false);
    return cachedImmediate;
  }

  public Snapshot skyCompatibilitySnapshot(int framebufferWidth, int framebufferHeight) {
    validateUnsupportedState();
    if (matches(cachedSky, cachedSkyWidth, cachedSkyHeight, framebufferWidth, framebufferHeight)) {
      return cachedSky;
    }
    cachedSkyWidth = framebufferWidth;
    cachedSkyHeight = framebufferHeight;
    cachedSky =
        snapshot(
            BgfxStateEncoder.sky(
                writeR,
                writeG,
                writeB,
                writeA,
                blend,
                srcRgb,
                dstRgb,
                srcAlpha,
                dstAlpha,
                blendEquation),
            framebufferWidth,
            framebufferHeight,
            false,
            false,
            blend,
            false);
    return cachedSky;
  }

  private Snapshot snapshot(
      long bgfxState,
      int framebufferWidth,
      int framebufferHeight,
      boolean depthTest,
      boolean depthWrite,
      boolean blend,
      boolean cull) {
    ScissorRect rect =
        ScissorRect.clip(
            scissor,
            scissorX,
            scissorY,
            scissorWidth,
            scissorHeight,
            framebufferWidth,
            framebufferHeight);
    return new Snapshot(
        bgfxState,
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

  private boolean matches(
      Snapshot snapshot, int width, int height, int expectedWidth, int expectedHeight) {
    return snapshot != null
        && snapshot.revision() == revision
        && width == expectedWidth
        && height == expectedHeight;
  }

  private void changed() {
    revision++;
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
