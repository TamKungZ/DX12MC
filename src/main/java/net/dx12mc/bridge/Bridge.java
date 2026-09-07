package net.dx12mc.bridge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.dx12mc.bridge.mesh.Batch;
import net.dx12mc.bridge.mesh.Encoder;
import net.dx12mc.bridge.shader.ShaderFamily;
import net.dx12mc.bridge.state.RenderState;
import net.dx12mc.bridge.texture.Registry;
import net.dx12mc.graphics.bgfx.Layout;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.StagingBackpressureException;
import net.dx12mc.graphics.bgfx.Texture;
import net.dx12mc.graphics.pipeline.ColorPipeline;
import net.dx12mc.graphics.pipeline.LightmapTexturePipeline;
import net.dx12mc.graphics.pipeline.TexturePipeline;
import org.joml.Matrix4f;

public final class Bridge implements AutoCloseable {
  private final Stats stats;
  private static final int MAX_RESIDENT_STATIC_MESHES =
      Math.max(256, Integer.getInteger("dx12mc.mesh.cache.max", 4096));
  private static final long MAX_RESIDENT_STATIC_BYTES =
      Math.max(64L << 20, Long.getLong("dx12mc.mesh.cache.bytes", 256L << 20));
  private final Set<Slot> slots = Collections.newSetFromMap(new IdentityHashMap<>());
  private final LinkedHashMap<Slot, ResidentMesh> residentMeshes =
      new LinkedHashMap<>(256, 0.75f, true);
  private long residentMeshBytes;
  private long cpuMeshBytes;
  private long frameBoundary;
  private boolean staticAllocationBackoff;
  private final Registry textures;
  private final RenderState renderState = new RenderState();
  private final float[] matrix = new float[16];
  private final float[] textureMatrix = new float[16];
  private final float[] tint = new float[4];
  private final Matrix4f mvpScratch = new Matrix4f();
  private final Matrix4f clipScratch = new Matrix4f();
  private final DrawContext drawContext = new DrawContext();
  private String classifiedShaderName;
  private ShaderFamily classifiedShaderFamily = ShaderFamily.BASIC;
  private boolean classifiedShaderCutout;
  private String semanticIntent = "frame";
  private boolean sectionIntent;
  private boolean skyIntent;
  private Layout layout;
  private ColorPipeline color;
  private TexturePipeline texture;
  private LightmapTexturePipeline lightmapTexture;

  public Bridge(Stats stats) {
    this.stats = stats;
    this.textures = new Registry(stats);
  }

  public void start() {
    if (layout != null) return;
    layout = new Layout();
    color = new ColorPipeline(layout);
    texture = new TexturePipeline(layout);
    lightmapTexture = new LightmapTexturePipeline(layout);
    textures.start();
  }

  public void capture(Slot slot, MeshData mesh, VertexBuffer.Usage usage) {
    forgetResident(slot);
    if (slot == null) throw unsupported("VertexBuffer slot is null");
    if (mesh == null) throw unsupported("VertexBuffer upload supplied null MeshData");
    Batch batch = Encoder.encode(mesh);
    stats.captured(batch);
    if (batch == null) {
      MeshData.DrawState state = mesh.drawState();
      Batch previous = slot.batch();
      if (previous != null) {
        cpuMeshBytes = Math.max(0L, cpuMeshBytes - previous.cpuBytes());
        stats.meshCpuBytes(cpuMeshBytes);
      }
      slot.clearCpu();
      throw unsupported(
          "Unsupported VertexBuffer MeshData: " + describe(state) + ", shader=" + shaderName());
    }
    MeshData.DrawState state = mesh.drawState();
    long before = slot.batch() == null ? 0L : slot.batch().cpuBytes();
    slot.capture(
        batch, usage == VertexBuffer.Usage.DYNAMIC, state.indexType(), batch.indices().length);
    cpuMeshBytes += batch.cpuBytes() - before;
    stats.meshCpuBytes(cpuMeshBytes);
    slots.add(slot);
  }

  public void updateIndices(Slot slot, ByteBuffer source) {
    if (slot == null) throw unsupported("Index update slot is null");
    Batch batch = slot.batch();
    if (batch == null) {
      stats.indexUpdate(false);
      throw unsupported("Index update arrived before a translatable D3D12 mesh was captured");
    }
    int[] indices =
        Encoder.decodeIndexUpdate(source, slot.indexType(), slot.indexCount(), batch.vertexCount());
    if (indices == null) {
      if (batch.sourceMode() == VertexFormat.Mode.QUADS
          && batch.indices().length == slot.indexCount()) {
        stats.staleSortedIndexUpdate();
        return;
      }
      stats.indexUpdate(false);
      throw unsupported(
          "Unsupported or invalid non-quad index update: type="
              + slot.indexType()
              + ", indexCount="
              + slot.indexCount()
              + ", vertexCount="
              + batch.vertexCount()
              + ", sourceBytes="
              + (source == null ? -1 : source.remaining()));
    }
    long before = batch.cpuBytes();
    boolean accepted = slot.replaceIndices(indices);
    if (!accepted || slot.batch() == null) {
      stats.indexUpdate(false);
      throw unsupported("Decoded index update could not replace the native CPU order");
    }
    cpuMeshBytes += slot.batch().cpuBytes() - before;
    stats.meshCpuBytes(cpuMeshBytes);
    stats.indexUpdate(true);
  }

  public void semanticIntent(String intent) {
    this.semanticIntent = intent == null || intent.isBlank() ? "frame" : intent;
    this.sectionIntent = semanticIntent.startsWith("level.section-layer");
    this.skyIntent = semanticIntent.startsWith("level.sky");
  }

  public void bindTexture(int id) {
    textures.bind(id);
  }

  public void depthTest(boolean enabled) {
    renderState.depthTest(enabled);
  }

  public void depthFunc(int func) {
    renderState.depthFunc(func);
  }

  public void depthMask(boolean write) {
    renderState.depthMask(write);
  }

  public void blend(boolean enabled) {
    renderState.blend(enabled);
  }

  public void blendFunc(int src, int dst) {
    renderState.blendFunc(src, dst);
  }

  public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    renderState.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
  }

  public void blendEquation(int equation) {
    renderState.blendEquation(equation);
  }

  public void cull(boolean enabled) {
    renderState.cull(enabled);
  }

  public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    renderState.colorMask(red, green, blue, alpha);
  }

  public void scissor(boolean enabled) {
    renderState.scissor(enabled);
  }

  public void scissorBox(int x, int y, int width, int height) {
    renderState.scissorBox(x, y, width, height);
  }

  public void polygonOffset(boolean enabled) {
    renderState.polygonOffset(enabled);
  }

  public void polygonOffset(float factor, float units) {
    renderState.polygonOffset(factor, units);
  }

  public void polygonMode(int face, int mode) {
    renderState.polygonMode(face, mode);
  }

  public void colorLogic(boolean enabled) {
    renderState.colorLogic(enabled);
  }

  public void logicOp(int op) {
    renderState.logicOp(op);
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
      Mesh gpu = syncResidentIndices(slot, slot.mesh());
      if (gpu == null || !gpu.valid()) {
        if (staticAllocationBackoff) {
          stats.nativeStreamingSpill();
          success = submitTransient(batch, context);
          if (!success && sectionIntent) {
            stats.deferredSectionDraw();
            return true;
          }
          stats.submitted(batch, success, true, context.textured(), context.lightmapped());
          if (!success) {
            throw unsupported(
                "Native transient stream exhausted while static allocation is backing off: mode="
                    + batch.sourceMode()
                    + ", vertices="
                    + batch.vertexCount()
                    + ", indices="
                    + batch.indices().length
                    + ", shader="
                    + shaderName());
          }
          return true;
        }
        if (!ensureResidentCapacity(slot, Mesh.estimatedBytes(batch))) {
          staticAllocationBackoff = true;
          stats.nativeStreamingSpill();
          success = submitTransient(batch, context);
          if (!success && sectionIntent) {
            stats.deferredSectionDraw();
            return true;
          }
          stats.submitted(batch, success, true, context.textured(), context.lightmapped());
          if (!success) {
            throw unsupported(
                "Resident D3D12 working set is protected and native transient spill was"
                    + " unavailable: mode="
                    + batch.sourceMode()
                    + ", vertices="
                    + batch.vertexCount()
                    + ", indices="
                    + batch.indices().length
                    + ", shader="
                    + shaderName());
          }
          return true;
        }
        try {
          gpu = Mesh.create(batch, layout.value());
          slot.mesh(gpu);
          touchResident(slot);
        } catch (IllegalStateException exhausted) {
          staticAllocationBackoff = true;
          stats.residentMeshRetry();
          stats.nativeStreamingSpill();
          success = submitTransient(batch, context);
          if (!success && sectionIntent) {
            stats.deferredSectionDraw();
            return true;
          }
          stats.submitted(batch, success, true, context.textured(), context.lightmapped());
          if (!success) {
            throw unsupported(
                "Static D3D12 mesh allocation failed and native transient spill was unavailable:"
                    + " mode="
                    + batch.sourceMode()
                    + ", vertices="
                    + batch.vertexCount()
                    + ", indices="
                    + batch.indices().length
                    + ", shader="
                    + shaderName());
          }
          return true;
        }
      } else {
        touchResident(slot);
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
              + batch.indices().length
              + ", textured="
              + context.textured()
              + ", shader="
              + shaderName());
    }
    return true;
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
    String activeShader = shaderName();
    if (isOmittableNativeLine(drawState, activeShader)) {
      stats.omittedNativeLineOverlay();
      return true;
    }
    Batch batch = Encoder.encode(mesh);
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
              + batch.indices().length
              + ", textured="
              + context.textured()
              + ", shader="
              + shaderName());
    }
    return true;
  }

  public void onFrameBoundary() {
    staticAllocationBackoff = false;
    frameBoundary++;
    textures.onFrameBoundary();
    stats.nativeStaging(
        net.dx12mc.graphics.bgfx.StagingMemory.outstandingBytes(),
        net.dx12mc.graphics.bgfx.StagingMemory.peakBytes());
  }

  public void release(Slot slot) {
    if (slot == null) return;
    forgetResident(slot);
    Batch batch = slot.batch();
    if (batch != null) {
      cpuMeshBytes = Math.max(0L, cpuMeshBytes - batch.cpuBytes());
      stats.meshCpuBytes(cpuMeshBytes);
    }
    slots.remove(slot);
    slot.release();
  }

  @Override
  public void close() {
    residentMeshes.clear();
    residentMeshBytes = 0L;
    staticAllocationBackoff = false;
    frameBoundary = 0L;
    for (Slot slot : slots.toArray(Slot[]::new)) slot.release();
    slots.clear();
    cpuMeshBytes = 0L;
    stats.residentMeshes(0, 0L);
    stats.meshCpuBytes(0L);
    textures.close();
    if (lightmapTexture != null) {
      lightmapTexture.close();
      lightmapTexture = null;
    }
    if (texture != null) {
      texture.close();
      texture = null;
    }
    if (color != null) {
      color.close();
      color = null;
    }
    if (layout != null) {
      layout.close();
      layout = null;
    }
  }

  private DrawContext context(
      Batch batch,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    prepareMatrix(modelView, projection);
    RenderSystem.getTextureMatrix().get(textureMatrix);
    float[] shaderColor = RenderSystem.getShaderColor();
    System.arraycopy(shaderColor, 0, tint, 0, tint.length);
    String shader = shaderName();
    classifyShader(shader);
    ShaderFamily family = classifiedShaderFamily;
    Texture sampled = resolveBaseTexture(batch, shader);
    Texture lightmap = resolveLightmap(batch, family);
    RenderState.Snapshot state =
        skyIntent
            ? renderState.skyCompatibilitySnapshot(framebufferWidth, framebufferHeight)
            : renderState.snapshot(framebufferWidth, framebufferHeight);
    return drawContext.set(
        batch.hasUv0(), sampled, lightmap != null, lightmap, classifiedShaderCutout, state);
  }

  private DrawContext contextImmediate(
      Batch batch,
      Matrix4f modelView,
      Matrix4f projection,
      int framebufferWidth,
      int framebufferHeight) {
    prepareMatrix(modelView, projection);
    RenderSystem.getTextureMatrix().get(textureMatrix);
    float[] shaderColor = RenderSystem.getShaderColor();
    System.arraycopy(shaderColor, 0, tint, 0, tint.length);
    String shader = shaderName();
    classifyShader(shader);
    Texture sampled = resolveBaseTexture(batch, shader);
    RenderState.Snapshot state =
        skyIntent
            ? renderState.skyCompatibilitySnapshot(framebufferWidth, framebufferHeight)
            : renderState.immediateCompatibilitySnapshot(framebufferWidth, framebufferHeight);
    return drawContext.set(batch.hasUv0(), sampled, false, null, classifiedShaderCutout, state);
  }

  private void prepareMatrix(Matrix4f modelView, Matrix4f projection) {
    mvpScratch.set(projection).mul(modelView);
    clipScratch.identity().m22(0.5f).m32(0.5f).mul(mvpScratch).get(matrix);
  }

  private void classifyShader(String shader) {
    if (Objects.equals(classifiedShaderName, shader)) return;
    classifiedShaderName = shader;
    classifiedShaderFamily = ShaderFamily.classify(shader);
    classifiedShaderCutout = isCutoutShader(shader);
  }

  private Texture resolveBaseTexture(Batch batch, String shader) {
    if (!batch.hasUv0()) return null;
    int textureId = RenderSystem.getShaderTexture(0);
    Texture sampled = textures.resolve(textureId);
    if (sampled == null) {
      throw unsupported(
          "Textured D3D12 draw has no usable texture mirror: textureId="
              + textureId
              + ", shader="
              + shader
              + ". OpenGL draw fallback is disabled.");
    }
    return sampled;
  }

  private Texture resolveLightmap(Batch batch, ShaderFamily family) {
    if (!batch.hasUv2() || !family.lightmapCandidate()) return null;
    int lightmapId = RenderSystem.getShaderTexture(2);
    if (lightmapId <= 0) return null;
    return textures.resolve(lightmapId);
  }

  private boolean submitTransient(Batch batch, DrawContext context) {
    if (!context.textured()) return color.submit(batch, matrix, tint, context.state());
    if (context.lightmapped()) {
      return lightmapTexture.submit(
          batch,
          context.texture(),
          context.lightmap(),
          matrix,
          textureMatrix,
          tint,
          context.state(),
          context.cutout());
    }
    return texture.submit(
        batch, context.texture(), matrix, textureMatrix, tint, context.state(), context.cutout());
  }

  private boolean submitPersistent(Mesh mesh, DrawContext context) {
    if (!context.textured()) return color.submit(mesh, matrix, tint, context.state());
    if (context.lightmapped()) {
      return lightmapTexture.submit(
          mesh,
          context.texture(),
          context.lightmap(),
          matrix,
          textureMatrix,
          tint,
          context.state(),
          context.cutout());
    }
    return texture.submit(
        mesh, context.texture(), matrix, textureMatrix, tint, context.state(), context.cutout());
  }

  private void touchResident(Slot slot) {
    if (slot == null || slot.mesh() == null || !slot.mesh().valid()) return;
    long bytes = slot.mesh().bytes();
    ResidentMesh resident = residentMeshes.get(slot);
    if (resident == null) {
      residentMeshes.put(slot, new ResidentMesh(bytes, frameBoundary));
      residentMeshBytes += bytes;
    } else {
      residentMeshBytes += bytes - resident.bytes();
      resident.touch(bytes, frameBoundary);
    }
    stats.residentMeshes(residentMeshes.size(), residentMeshBytes);
  }

  private void forgetResident(Slot slot) {
    if (slot == null) return;
    ResidentMesh removed = residentMeshes.remove(slot);
    if (removed != null) residentMeshBytes = Math.max(0L, residentMeshBytes - removed.bytes());
    stats.residentMeshes(residentMeshes.size(), residentMeshBytes);
  }

  private boolean ensureResidentCapacity(Slot incoming, long incomingBytes) {
    ResidentMesh existing = residentMeshes.remove(incoming);
    if (existing != null) residentMeshBytes = Math.max(0L, residentMeshBytes - existing.bytes());
    if (incomingBytes > MAX_RESIDENT_STATIC_BYTES) return false;
    while (!residentMeshes.isEmpty()
        && (residentMeshes.size() >= MAX_RESIDENT_STATIC_MESHES
            || residentMeshBytes + incomingBytes > MAX_RESIDENT_STATIC_BYTES)) {
      Map.Entry<Slot, ResidentMesh> eldest = null;
      for (Map.Entry<Slot, ResidentMesh> candidate : residentMeshes.entrySet()) {
        if (candidate.getValue().lastUsedFrame() < frameBoundary - 1L) {
          eldest = candidate;
          break;
        }
      }
      if (eldest == null) {
        stats.residentMeshes(residentMeshes.size(), residentMeshBytes);
        return false;
      }
      Slot victim = eldest.getKey();
      long bytes = eldest.getValue().bytes();
      residentMeshes.remove(victim);
      residentMeshBytes = Math.max(0L, residentMeshBytes - bytes);
      victim.releaseGpu();
      stats.residentMeshEvicted();
    }
    stats.residentMeshes(residentMeshes.size(), residentMeshBytes);
    return residentMeshes.size() < MAX_RESIDENT_STATIC_MESHES
        && residentMeshBytes + incomingBytes <= MAX_RESIDENT_STATIC_BYTES;
  }

  private Mesh syncResidentIndices(Slot slot, Mesh gpu) {
    if (gpu == null || !gpu.valid() || !slot.indicesDirty()) return gpu;
    try {
      gpu.updateIndices(slot.batch());
      slot.indicesUploaded();
      touchResident(slot);
      stats.inPlaceGpuIndexUpdate();
      return gpu;
    } catch (StagingBackpressureException pressure) {
      stats.deferredGpuIndexUpdate();
      return gpu;
    } catch (IllegalStateException invalidResident) {
      forgetResident(slot);
      slot.releaseGpu();
      stats.deferredGpuIndexRebuild();
      return null;
    }
  }

  private void requireStarted() {
    if (layout == null || color == null || texture == null || lightmapTexture == null) {
      throw unsupported("D3D12 bridge draw attempted before native pipeline initialization");
    }
  }

  private static boolean isCutoutShader(String shader) {
    if (shader == null) return false;
    String n = shader.toLowerCase(java.util.Locale.ROOT);
    return n.contains("cutout");
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

  private static String shaderName() {
    try {
      var shader = RenderSystem.getShader();
      return shader == null ? "<none>" : shader.getName();
    } catch (Throwable ignored) {
      return "<unavailable>";
    }
  }

  private static UnsupportedRenderPathException unsupported(String message) {
    return new UnsupportedRenderPathException(message);
  }

  private static final class DrawContext {
    private boolean textured;
    private Texture texture;
    private boolean lightmapped;
    private Texture lightmap;
    private boolean cutout;
    private RenderState.Snapshot state;

    private DrawContext set(
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

    private boolean textured() {
      return textured;
    }

    private Texture texture() {
      return texture;
    }

    private boolean lightmapped() {
      return lightmapped;
    }

    private Texture lightmap() {
      return lightmap;
    }

    private boolean cutout() {
      return cutout;
    }

    private RenderState.Snapshot state() {
      return state;
    }
  }

  private static final class ResidentMesh {
    private long bytes;
    private long lastUsedFrame;

    private ResidentMesh(long bytes, long lastUsedFrame) {
      this.bytes = bytes;
      this.lastUsedFrame = lastUsedFrame;
    }

    private long bytes() {
      return bytes;
    }

    private long lastUsedFrame() {
      return lastUsedFrame;
    }

    private void touch(long bytes, long lastUsedFrame) {
      this.bytes = bytes;
      this.lastUsedFrame = lastUsedFrame;
    }
  }
}
