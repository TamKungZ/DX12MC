package net.dx12mc.bridge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.mesh.Encoder;
import net.dx12mc.bridge.mesh.MeshStore;
import net.dx12mc.bridge.mesh.SectionRegionBatcher;
import net.dx12mc.bridge.texture.Registry;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.StagingBackpressureException;
import net.dx12mc.graphics.pipeline.PipelineSet;
import org.joml.Matrix4f;

public final class Bridge implements AutoCloseable {
  private final Stats stats;
  private final MeshStore meshes;
  private final SectionRegionBatcher regions;
  private final Registry textures;
  private final DrawContextFactory contexts;
  private final PipelineSet pipelines = new PipelineSet();
  private final Encoder.Scratch immediateScratch = new Encoder.Scratch();
  private DrawContext activeSectionContext;
  private String semanticIntent = "frame";
  private boolean sectionIntent;

  public Bridge(Stats stats) {
    this.stats = stats;
    this.meshes = new MeshStore(stats);
    this.regions = new SectionRegionBatcher(stats);
    this.textures = new Registry(stats);
    this.contexts = new DrawContextFactory(textures);
  }

  public void start() {
    pipelines.start();
    textures.start();
  }

  public void capture(Slot slot, MeshData mesh, VertexBuffer.Usage usage) {
    regions.invalidate(slot);
    meshes.capture(slot, mesh, usage, DrawContextFactory.shaderName());
  }

  public void updateIndices(Slot slot, ByteBuffer source) {
    meshes.updateIndices(slot, source);
  }

  public void semanticIntent(String intent) {
    this.semanticIntent = intent == null || intent.isBlank() ? "frame" : intent;
    boolean nextSectionIntent = semanticIntent.startsWith("level.section-layer");
    if (sectionIntent && !nextSectionIntent) pipelines.endSectionLayer();
    this.sectionIntent = nextSectionIntent;
    contexts.semanticIntent(semanticIntent);
  }

  public void beginSectionLayer(
      Object layerToken,
      boolean regionBatchable,
      Matrix4f layerClipTransform,
      double cameraX,
      double cameraY,
      double cameraZ) {
    activeSectionContext = null;
    regions.begin(layerToken, regionBatchable, layerClipTransform, cameraX, cameraY, cameraZ);
  }

  public void bindTexture(int id) {
    textures.bind(id);
  }

  public void depthTest(boolean enabled) {
    contexts.state().depthTest(enabled);
  }

  public void depthFunc(int func) {
    contexts.state().depthFunc(func);
  }

  public void depthMask(boolean write) {
    contexts.state().depthMask(write);
  }

  public void blend(boolean enabled) {
    contexts.state().blend(enabled);
  }

  public void blendFunc(int src, int dst) {
    contexts.state().blendFunc(src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    contexts.state().blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
  }

  public void blendEquation(int equation) {
    contexts.state().blendEquation(equation);
  }

  public void cull(boolean enabled) {
    contexts.state().cull(enabled);
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    contexts.state().colorMask(red, green, blue, alpha);
  }

  public void scissor(boolean enabled) {
    contexts.state().scissor(enabled);
  }

  public void scissorBox(int x, int y, int width, int height) {
    contexts.state().scissorBox(x, y, width, height);
  }

  public void polygonOffset(boolean enabled) {
    contexts.state().polygonOffset(enabled);
  }

  public void polygonOffset(float factor, float units) {
    contexts.state().polygonOffset(factor, units);
  }

  public void polygonMode(int face, int mode) {
    contexts.state().polygonMode(face, mode);
  }

  public void colorLogic(boolean enabled) {
    contexts.state().colorLogic(enabled);
  }

  public void logicOp(int op) {
    contexts.state().logicOp(op);
  }

  public void allocateTexture(int id, int maxLevel, int width, int height) {
    textures.allocate(id, maxLevel, width, height);
    stats.textureAllocated();
  }

  public void allocateBoundTextureLevel(int level, int width, int height) {
    textures.allocateBoundLevel(level, width, height);
    stats.textureAllocated();
  }

  public void uploadTexture(
      NativeImage image,
      int level,
      int offsetX,
      int offsetY,
      int skipPixels,
      int skipRows,
      int width,
      int height,
      boolean blur,
      boolean clamp,
      boolean mipmap) {
    boolean accepted =
        textures.upload(
            image,
            level,
            offsetX,
            offsetY,
            skipPixels,
            skipRows,
            width,
            height,
            blur,
            clamp,
            mipmap);
    stats.textureUpload(accepted);
    if (!accepted) {
      throw unsupported(
          "Texture upload could not be mirrored for D3D12: level="
              + level
              + ", dstOffset="
              + offsetX
              + ","
              + offsetY
              + ", size="
              + width
              + "x"
              + height
              + ", srcSkip="
              + skipPixels
              + ","
              + skipRows
              + ", source="
              + (image == null ? "null" : image.getWidth() + "x" + image.getHeight())
              + ". D3D12 texture mirroring rejected the upload; no OpenGL render fallback will be"
              + " used.");
    }
  }

  public void deleteTexture(int id) {
    textures.delete(id);
  }

  public long textureCpuBytes() {
    return textures.allocatedCpuBytes();
  }

  public boolean draw(
      Slot slot,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    requireStarted();
    if (slot == null || slot.batch() == null) {
      throw unsupported("VertexBuffer draw has no captured D3D12 mesh");
    }
    Batch batch = slot.batch();
    DrawContext context;
    try {
      context = context(batch, modelView, projection, framebufferWidth, framebufferHeight);
    } catch (StagingBackpressureException pressure) {
      if (sectionIntent) stats.deferredSectionDraw();
      return true;
    }
    if (context.state().scissorEmpty()) {
      stats.scissorSkippedDraw();
      return true;
    }
    boolean transientBuffer = slot.dynamic();
    boolean success;
    if (transientBuffer) {
      success = submitTransient(batch, context);
    } else {
      Mesh gpu = meshes.resident(slot, pipelines.layout());
      if (gpu == null || !gpu.valid()) {
        stats.nativeStreamingSpill();
        success = submitTransient(batch, context);
        if (!success && sectionIntent) {
          stats.deferredSectionDraw();
          return true;
        }
        stats.submitted(batch, success, true, context.textured(), context.lightmapped());
        if (!success) {
          throw unsupported(
              "Native transient stream exhausted while static allocation is backing off:"
                  + " mode="
                  + batch.sourceMode()
                  + ", vertices="
                  + batch.vertexCount()
                  + ", indices="
                  + batch.indexCount()
                  + ", shader="
                  + DrawContextFactory.shaderName());
        }
        return true;
      }
      success = submitPersistent(gpu, context);
    }
    stats.submitted(batch, success, transientBuffer, context.textured(), context.lightmapped());
    if (!success) {
      throw unsupported(
          "bgfx/D3D12 rejected VertexBuffer submission: mode="
              + batch.sourceMode()
              + ", vertices="
              + batch.vertexCount()
              + ", indices="
              + batch.indexCount()
              + ", textured="
              + context.textured()
              + ", shader="
              + DrawContextFactory.shaderName());
    }
    return true;
  }

  public boolean drawSection(
      Slot slot,
      Matrix4f clipTransform,
      int framebufferWidth,
      int framebufferHeight,
      int originX,
      int originY,
      int originZ) {
    requireStarted();
    if (slot == null || slot.batch() == null) {
      throw unsupported("Section VertexBuffer draw has no captured D3D12 mesh");
    }
    Batch batch = slot.batch();
    DrawContext context;
    try {
      context = contexts.createSection(batch, clipTransform, framebufferWidth, framebufferHeight);
    } catch (StagingBackpressureException pressure) {
      stats.deferredSectionDraw();
      return true;
    }
    if (context.state().scissorEmpty()) {
      stats.scissorSkippedDraw();
      return true;
    }
    activeSectionContext = context;
    if (regions.collect(slot, batch, originX, originY, originZ)) return true;
    if (slot.dynamic()) {
      boolean success = submitTransient(batch, context);
      stats.submitted(batch, success, true, context.textured(), context.lightmapped());
      if (!success) {
        throw unsupported(
            "bgfx/D3D12 rejected dynamic section submission: mode="
                + batch.sourceMode()
                + ", vertices="
                + batch.vertexCount()
                + ", indices="
                + batch.indexCount()
                + ", shader="
                + DrawContextFactory.shaderName());
      }
      return true;
    }
    Mesh gpu = meshes.resident(slot, pipelines.layout());
    if (gpu == null || !gpu.valid()) {
      stats.nativeStreamingSpill();
      boolean success = submitTransient(batch, context);
      if (!success) {
        stats.deferredSectionDraw();
        return true;
      }
      stats.submitted(batch, true, true, context.textured(), context.lightmapped());
      return true;
    }
    boolean success = submitPersistent(gpu, context);
    stats.submitted(batch, success, false, context.textured(), context.lightmapped());
    if (!success) {
      throw unsupported(
          "bgfx/D3D12 rejected section submission: mode="
              + batch.sourceMode()
              + ", vertices="
              + batch.vertexCount()
              + ", indices="
              + batch.indexCount()
              + ", shader="
              + DrawContextFactory.shaderName());
    }
    return true;
  }

  public void endSectionLayer() {
    if (regions.active()) flushSectionRegions();
    regions.end();
    activeSectionContext = null;
    pipelines.endSectionLayer();
  }

  public boolean drawImmediate(
      MeshData mesh,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    requireStarted();
    if (mesh == null) throw unsupported("BufferUploader supplied null MeshData");
    MeshData.DrawState drawState = mesh.drawState();
    String activeShader = DrawContextFactory.shaderName();
    if (isOmittableNativeLine(drawState, activeShader)) {
      stats.omittedNativeLineOverlay();
      return true;
    }
    Batch batch = Encoder.encodeReusable(mesh, immediateScratch);
    stats.immediateCaptured(batch);
    if (batch == null) {
      throw unsupported(
          "Unsupported immediate MeshData: " + describe(drawState) + ", shader=" + activeShader);
    }
    DrawContext context;
    try {
      context = contextImmediate(batch, modelView, projection, framebufferWidth, framebufferHeight);
    } catch (StagingBackpressureException pressure) {
      return true;
    }
    if (context.state().scissorEmpty()) {
      stats.scissorSkippedDraw();
      return true;
    }
    boolean success = submitTransient(batch, context);
    stats.submitted(batch, success, true, context.textured(), context.lightmapped());
    if (!success) {
      throw unsupported(
          "bgfx/D3D12 rejected immediate submission: mode="
              + batch.sourceMode()
              + ", vertices="
              + batch.vertexCount()
              + ", indices="
              + batch.indexCount()
              + ", textured="
              + context.textured()
              + ", shader="
              + DrawContextFactory.shaderName());
    }
    return true;
  }

  public void onFrameBoundary() {
    regions.onFrameBoundary();
    meshes.onFrameBoundary();
    textures.onFrameBoundary();
    stats.nativeStaging(
        net.dx12mc.graphics.bgfx.StagingMemory.outstandingBytes(),
        net.dx12mc.graphics.bgfx.StagingMemory.peakBytes());
  }

  public void release(Slot slot) {
    if (slot == null) return;
    regions.invalidate(slot);
    meshes.release(slot);
  }

  @Override
  public void close() {
    regions.close();
    meshes.close();
    textures.close();
    pipelines.close();
  }

  private DrawContext context(
      Batch batch,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    return contexts.create(
        batch, modelView, projection, framebufferWidth, framebufferHeight, false);
  }

  private DrawContext contextImmediate(
      Batch batch,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    return contexts.create(batch, modelView, projection, framebufferWidth, framebufferHeight, true);
  }

  private boolean submitTransient(Batch batch, DrawContext context) {
    if (sectionIntent) {
      return pipelines.submitSection(
          batch, context, contexts.matrix(), contexts.textureMatrix(), contexts.tint());
    }
    return pipelines.submit(
        batch, context, contexts.matrix(), contexts.textureMatrix(), contexts.tint());
  }

  private boolean submitPersistent(Mesh mesh, DrawContext context) {
    if (sectionIntent) {
      return pipelines.submitSection(
          mesh, context, contexts.matrix(), contexts.textureMatrix(), contexts.tint());
    }
    return pipelines.submit(
        mesh, context, contexts.matrix(), contexts.textureMatrix(), contexts.tint());
  }

  private void flushSectionRegions() {
    if (regions.touchedCount() == 0) return;
    if (activeSectionContext == null) {
      throw unsupported("Section-region flush has no prepared draw context");
    }
    regions.prepare(pipelines.layout(), meshes);
    DrawContext context = activeSectionContext;
    for (int regionIndex = 0; regionIndex < regions.touchedCount(); regionIndex++) {
      SectionRegionBatcher.Region region = regions.touched(regionIndex);
      if (!region.ready()) {
        submitVisibleRegionMembers(region, context);
        continue;
      }
      contexts.sectionMatrix(regions.transform(region));
      for (int pageIndex = 0; pageIndex < region.pageCount(); pageIndex++) {
        SectionRegionBatcher.Page page = region.page(pageIndex);
        boolean success = submitPersistent(page.mesh(), context);
        stats.submittedRegionPage(
            page.vertexCount(),
            page.indexCount(),
            success,
            context.textured(),
            context.lightmapped());
        if (!success) throw unsupported("bgfx/D3D12 rejected a compiled section-region page");
      }
    }
  }

  private void submitVisibleRegionMembers(SectionRegionBatcher.Region region, DrawContext context) {
    for (int memberIndex = 0; memberIndex < region.visibleCount(); memberIndex++) {
      SectionRegionBatcher.Member member = region.visible(memberIndex);
      contexts.sectionMatrix(regions.transform(member));
      Batch batch = member.batch();
      Mesh gpu = meshes.resident(member.slot(), pipelines.layout());
      boolean transientBuffer = gpu == null || !gpu.valid();
      boolean success =
          transientBuffer ? submitTransient(batch, context) : submitPersistent(gpu, context);
      if (transientBuffer) stats.nativeStreamingSpill();
      stats.submitted(batch, success, transientBuffer, context.textured(), context.lightmapped());
      if (!success) {
        stats.deferredSectionDraw();
      }
    }
  }

  private void requireStarted() {
    if (!pipelines.started()) {
      throw unsupported("D3D12 bridge draw attempted before native pipeline initialization");
    }
  }

  private static boolean isOmittableNativeLine(MeshData.DrawState state, String shader) {
    if (state == null || shader == null) return false;
    VertexFormat.Mode mode = state.mode();
    boolean thickLineMode =
        mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.DEBUG_LINES;
    return thickLineMode && shader.toLowerCase(java.util.Locale.ROOT).contains("rendertype_lines");
  }

  private static String describe(MeshData.DrawState state) {
    if (state == null) return "drawState=null";
    return "mode="
        + state.mode()
        + ", vertices="
        + state.vertexCount()
        + ", indices="
        + state.indexCount()
        + ", indexType="
        + state.indexType()
        + ", format="
        + state.format();
  }

  private static UnsupportedRenderPathException unsupported(String message) {
    return new UnsupportedRenderPathException(message);
  }
}
