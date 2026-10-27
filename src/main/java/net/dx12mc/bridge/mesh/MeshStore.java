package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import net.dx12mc.bridge.UnsupportedRenderPathException;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.StagingBackpressureException;
import org.lwjgl.bgfx.BGFXVertexLayout;

public final class MeshStore implements AutoCloseable {
  private static final int MAX_RESIDENT_STATIC_MESHES =
      Math.max(256, Integer.getInteger("dx12mc.mesh.cache.max", 4096));
  private static final long MAX_RESIDENT_STATIC_BYTES =
      Math.max(64L << 20, Long.getLong("dx12mc.mesh.cache.bytes", 256L << 20));

  private static final int MAX_CREATES_PER_FRAME =
      Math.max(1, Integer.getInteger("dx12mc.mesh.createsPerFrame", 4));
  private static final int MAX_INDEX_UPDATES_PER_FRAME =
      Math.max(1, Integer.getInteger("dx12mc.mesh.indexUpdatesPerFrame", 4));

  private final Stats stats;
  private final Set<Slot> slots = Collections.newSetFromMap(new IdentityHashMap<>());
  private final LinkedHashMap<Slot, ResidentMesh> residents = new LinkedHashMap<>(256, 0.75f, true);
  private long residentBytes;
  private long cpuBytes;
  private long frameBoundary;
  private boolean allocationBackoff;
  private final FrameBudget creates = new FrameBudget(MAX_CREATES_PER_FRAME);
  private final FrameBudget indexUpdates = new FrameBudget(MAX_INDEX_UPDATES_PER_FRAME);

  public MeshStore(Stats stats) {
    this.stats = stats;
  }

  public void capture(
      Slot slot, BufferBuilder.RenderedBuffer mesh, VertexBuffer.Usage usage, String shaderName) {
    forgetResident(slot);
    if (slot == null) throw unsupported("VertexBuffer slot is null");
    if (mesh == null)
      throw unsupported("VertexBuffer upload supplied null BufferBuilder.RenderedBuffer");
    Batch batch = Encoder.encode(mesh);
    stats.captured(batch);
    if (batch == null) {
      BufferBuilder.DrawState state = mesh.drawState();
      Batch previous = slot.batch();
      if (previous != null) {
        cpuBytes = Math.max(0L, cpuBytes - previous.cpuBytes());
        stats.meshCpuBytes(cpuBytes);
      }
      slot.clearCpu();
      throw unsupported(
          "Unsupported VertexBuffer BufferBuilder.RenderedBuffer: "
              + describe(state)
              + ", shader="
              + shaderName);
    }
    BufferBuilder.DrawState state = mesh.drawState();
    long before = slot.batch() == null ? 0L : slot.batch().cpuBytes();
    slot.capture(batch, usage == VertexBuffer.Usage.DYNAMIC, state.indexType(), batch.indexCount());
    cpuBytes += batch.cpuBytes() - before;
    stats.meshCpuBytes(cpuBytes);
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
          && batch.indexCount() == slot.indexCount()) {
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
    if (!slot.replaceIndices(indices) || slot.batch() == null) {
      stats.indexUpdate(false);
      throw unsupported("Decoded index update could not replace the native CPU order");
    }
    cpuBytes += slot.batch().cpuBytes() - before;
    stats.meshCpuBytes(cpuBytes);
    stats.indexUpdate(true);
  }

  public Mesh resident(Slot slot, BGFXVertexLayout layout) {
    Mesh gpu = syncIndices(slot, slot.mesh());
    if (gpu != null && gpu.valid()) {
      touch(slot);
      return gpu;
    }
    if (creates.exhausted()) return null;
    if (allocationBackoff || !ensureCapacity(slot, Mesh.estimatedBytes(slot.batch()))) {
      allocationBackoff = true;
      return null;
    }
    try {
      gpu = Mesh.create(slot.batch(), layout);
      creates.tryTake();
      slot.mesh(gpu);
      touch(slot);
      return gpu;
    } catch (IllegalStateException exhausted) {
      allocationBackoff = true;
      stats.residentMeshRetry();
      return null;
    }
  }

  public void release(Slot slot) {
    if (slot == null) return;
    forgetResident(slot);
    Batch batch = slot.batch();
    if (batch != null) {
      cpuBytes = Math.max(0L, cpuBytes - batch.cpuBytes());
      stats.meshCpuBytes(cpuBytes);
    }
    slots.remove(slot);
    slot.release();
  }

  public void discardGpuCopy(Slot slot) {
    if (slot == null) return;
    forgetResident(slot);
    slot.releaseGpu();
  }

  public void onFrameBoundary() {
    allocationBackoff = false;
    creates.reset();
    indexUpdates.reset();
    frameBoundary++;
  }

  @Override
  public void close() {
    residents.clear();
    residentBytes = 0L;
    allocationBackoff = false;
    frameBoundary = 0L;
    for (Slot slot : slots.toArray(Slot[]::new)) slot.release();
    slots.clear();
    cpuBytes = 0L;
    stats.residentMeshes(0, 0L);
    stats.meshCpuBytes(0L);
  }

  private Mesh syncIndices(Slot slot, Mesh gpu) {
    if (gpu == null || !gpu.valid() || !slot.indicesDirty()) return gpu;
    if (!indexUpdates.tryTake()) {
      stats.deferredGpuIndexUpdate();
      return gpu;
    }
    try {
      gpu.updateIndices(slot.batch());
      slot.indicesUploaded();
      touch(slot);
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

  private void touch(Slot slot) {
    if (slot == null || slot.mesh() == null || !slot.mesh().valid()) return;
    long bytes = slot.mesh().bytes();
    ResidentMesh resident = residents.get(slot);
    if (resident == null) {
      residents.put(slot, new ResidentMesh(bytes, frameBoundary));
      residentBytes += bytes;
      publishResidentStats();
    } else {
      if (resident.bytes != bytes) {
        residentBytes += bytes - resident.bytes;
        resident.bytes = bytes;
        publishResidentStats();
      }
      resident.lastUsedFrame = frameBoundary;
    }
  }

  private void forgetResident(Slot slot) {
    if (slot == null) return;
    ResidentMesh removed = residents.remove(slot);
    if (removed != null) {
      residentBytes = Math.max(0L, residentBytes - removed.bytes);
      publishResidentStats();
    }
  }

  private boolean ensureCapacity(Slot incoming, long incomingBytes) {
    ResidentMesh existing = residents.remove(incoming);
    if (existing != null) residentBytes = Math.max(0L, residentBytes - existing.bytes);
    if (incomingBytes > MAX_RESIDENT_STATIC_BYTES) return false;
    while (!residents.isEmpty()
        && (residents.size() >= MAX_RESIDENT_STATIC_MESHES
            || residentBytes + incomingBytes > MAX_RESIDENT_STATIC_BYTES)) {
      Map.Entry<Slot, ResidentMesh> eldest = evictableEldest();
      if (eldest == null) {
        publishResidentStats();
        return false;
      }
      Slot victim = eldest.getKey();
      residentBytes = Math.max(0L, residentBytes - eldest.getValue().bytes);
      residents.remove(victim);
      victim.releaseGpu();
      stats.residentMeshEvicted();
    }
    publishResidentStats();
    return residents.size() < MAX_RESIDENT_STATIC_MESHES
        && residentBytes + incomingBytes <= MAX_RESIDENT_STATIC_BYTES;
  }

  private Map.Entry<Slot, ResidentMesh> evictableEldest() {
    for (Map.Entry<Slot, ResidentMesh> candidate : residents.entrySet()) {
      if (candidate.getValue().lastUsedFrame < frameBoundary - 1L) return candidate;
    }
    return null;
  }

  private void publishResidentStats() {
    stats.residentMeshes(residents.size(), residentBytes);
  }

  private static String describe(BufferBuilder.DrawState state) {
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

  private static final class ResidentMesh {
    private long bytes;
    private long lastUsedFrame;

    private ResidentMesh(long bytes, long lastUsedFrame) {
      this.bytes = bytes;
      this.lastUsedFrame = lastUsedFrame;
    }
  }
}
