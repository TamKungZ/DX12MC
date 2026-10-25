package net.dx12mc.bridge.mesh;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.dx12mc.bridge.Slot;
import net.dx12mc.bridge.Stats;
import net.dx12mc.graphics.bgfx.Mesh;
import net.dx12mc.graphics.bgfx.Packing;
import org.joml.Matrix4f;
import org.lwjgl.bgfx.BGFXVertexLayout;

public final class SectionRegionBatcher implements AutoCloseable {
  private static final int RUN_FIELDS = 4;
  private static final float[] EMPTY_FLOATS = new float[0];
  private static final short[] EMPTY_SHORTS = new short[0];
  private static final byte[] EMPTY_BYTES = new byte[0];
  private static final int REGION_XZ_BLOCKS = sectionAligned("dx12mc.region.xzBlocks", 32);
  private static final int REGION_Y_BLOCKS = sectionAligned("dx12mc.region.yBlocks", 16);
  private static final int MAX_REGIONS =
      Math.max(128, Integer.getInteger("dx12mc.region.cache.max", 2048));
  private static final long MAX_RESIDENT_BYTES =
      Math.max(64L << 20, Long.getLong("dx12mc.region.cache.bytes", 256L << 20));
  private static final long MAX_PAGE_BYTES =
      Math.max(1L << 20, Long.getLong("dx12mc.region.page.bytes", 8L << 20));
  private static final long STALE_FRAMES =
      Math.max(60L, Long.getLong("dx12mc.region.cache.staleFrames", 300L));

  private final Stats stats;
  private final IdentityHashMap<Object, Layer> layers = new IdentityHashMap<>();
  private final IdentityHashMap<Slot, Region> owners = new IdentityHashMap<>();
  private final ArrayList<Region> regions = new ArrayList<>();
  private final ArrayList<Region> touched = new ArrayList<>();
  private final Assembler assembler = new Assembler();
  private final Matrix4f layerClipTransform = new Matrix4f();
  private final Matrix4f transformScratch = new Matrix4f();
  private Layer currentLayer;
  private boolean active;
  private double cameraX;
  private double cameraY;
  private double cameraZ;
  private long frame = 1L;
  private long pass;
  private long residentBytes;

  public SectionRegionBatcher(Stats stats) {
    this.stats = stats;
  }

  public void begin(
      Object layerToken,
      boolean batchable,
      Matrix4f layerClipTransform,
      double cameraX,
      double cameraY,
      double cameraZ) {
    end();
    if (!batchable || layerToken == null || layerClipTransform == null) return;
    active = true;
    pass++;
    currentLayer = layers.computeIfAbsent(layerToken, ignored -> new Layer());
    this.layerClipTransform.set(layerClipTransform);
    this.cameraX = cameraX;
    this.cameraY = cameraY;
    this.cameraZ = cameraZ;
  }

  public boolean active() {
    return active;
  }

  public boolean collect(Slot slot, Batch batch, int originX, int originY, int originZ) {
    if (!active || slot == null || batch == null || slot.dynamic()) return false;
    int regionX = Math.floorDiv(originX, REGION_XZ_BLOCKS) * REGION_XZ_BLOCKS;
    int regionY = Math.floorDiv(originY, REGION_Y_BLOCKS) * REGION_Y_BLOCKS;
    int regionZ = Math.floorDiv(originZ, REGION_XZ_BLOCKS) * REGION_XZ_BLOCKS;
    RegionKey key = new RegionKey(regionX, regionY, regionZ);
    Region region = currentLayer.regions.get(key);
    if (region == null) {
      if (!makeRegionCapacity()) return false;
      region = new Region(currentLayer, key);
      currentLayer.regions.put(key, region);
      regions.add(region);
      publishResidentStats();
    }
    if (region.visiblePass != pass) {
      region.visiblePass = pass;
      region.visible.clear();
      touched.add(region);
    }
    region.lastUsedFrame = frame;
    Region previousOwner = owners.get(slot);
    if (previousOwner != null && previousOwner != region) removeMember(previousOwner, slot);
    Member member = region.find(slot);
    if (member == null) {
      member = new Member(slot, batch, originX, originY, originZ);
      region.members.add(member);
      owners.put(slot, region);
      region.dirty = true;
    } else if (member.batch != batch
        || member.originX != originX
        || member.originY != originY
        || member.originZ != originZ) {
      member.batch = batch;
      member.originX = originX;
      member.originY = originY;
      member.originZ = originZ;
      region.dirty = true;
    }
    member.visibleStamp = pass;
    region.visible.add(member);
    stats.regionSectionCollected();
    return true;
  }

  public void prepare(BGFXVertexLayout layout, MeshStore meshes) {
    if (!active) return;
    for (Region region : touched) {
      purgeInvalidMembers(region);
      if (!region.dirty) continue;
      if (rebuild(region, layout)) {
        for (Member member : region.members) meshes.discardGpuCopy(member.slot);
        stats.regionRebuilt();
      } else {
        stats.regionRebuildDeferred();
      }
    }
    enforceResidentBudget();
  }

  public int touchedCount() {
    return touched.size();
  }

  public Region touched(int index) {
    return touched.get(index);
  }

  public Matrix4f transform(Region region) {
    return transform(region.key.x, region.key.y, region.key.z);
  }

  public Matrix4f transform(Member member) {
    return transform(member.originX, member.originY, member.originZ);
  }

  public void invalidate(Slot slot) {
    if (slot == null) return;
    Region owner = owners.remove(slot);
    if (owner != null) removeMember(owner, slot);
  }

  public void end() {
    active = false;
    currentLayer = null;
    touched.clear();
    cameraX = cameraY = cameraZ = 0.0;
  }

  public void onFrameBoundary() {
    frame++;
    for (int i = regions.size() - 1; i >= 0; i--) {
      Region region = regions.get(i);
      if (frame - region.lastUsedFrame > STALE_FRAMES) evict(region);
    }
    enforceResidentBudget();
  }

  @Override
  public void close() {
    end();
    for (int i = regions.size() - 1; i >= 0; i--) evict(regions.get(i));
    layers.clear();
    owners.clear();
    residentBytes = 0L;
    publishResidentStats();
  }

  private boolean rebuild(Region region, BGFXVertexLayout layout) {
    if (region.members.isEmpty()) {
      replacePages(region, new ArrayList<>());
      region.dirty = false;
      return true;
    }
    ArrayList<Page> fresh = new ArrayList<>();
    try {
      int start = 0;
      while (start < region.members.size()) {
        int end = pageEnd(region.members, start);
        Batch combined =
            assembler.assemble(
                region.members, start, end, region.key.x, region.key.y, region.key.z);
        int firstIndex = 0;
        for (int i = start; i < end; i++) {
          Member member = region.members.get(i);
          member.pageIndex = fresh.size();
          member.firstIndex = firstIndex;
          firstIndex += member.batch.indexCount();
        }
        Mesh mesh = Mesh.create(combined, layout);
        fresh.add(new Page(mesh, combined.vertexCount(), combined.indexCount()));
        start = end;
      }
    } catch (IllegalStateException failure) {
      closePages(fresh);
      return false;
    }
    if (!replacePages(region, fresh)) {
      closePages(fresh);
      return false;
    }
    region.dirty = false;
    return true;
  }

  private int pageEnd(List<Member> members, int start) {
    long bytes = 0L;
    int end = start;
    while (end < members.size()) {
      Batch batch = members.get(end).batch;
      long next = (long) Packing.vertexBytes(batch) + (long) batch.indexCount() * Integer.BYTES;
      if (end > start && bytes + next > MAX_PAGE_BYTES) break;
      bytes += next;
      end++;
    }
    return end;
  }

  private boolean replacePages(Region region, ArrayList<Page> replacement) {
    long replacementBytes = 0L;
    for (Page page : replacement) replacementBytes += page.mesh.bytes();
    while (residentBytes - region.bytes + replacementBytes > MAX_RESIDENT_BYTES) {
      Region oldest = oldestEvictable(region);
      if (oldest == null) return false;
      evict(oldest);
    }
    residentBytes -= region.bytes;
    closePages(region.pages);
    region.pages = replacement;
    region.bytes = replacementBytes;
    residentBytes += region.bytes;
    publishResidentStats();
    return true;
  }

  private void purgeInvalidMembers(Region region) {
    for (int i = region.members.size() - 1; i >= 0; i--) {
      Member member = region.members.get(i);
      if (member.slot.batch() == member.batch) continue;
      region.members.remove(i);
      region.visible.remove(member);
      owners.remove(member.slot, region);
      region.dirty = true;
    }
  }

  private void removeMember(Region region, Slot slot) {
    for (int i = region.members.size() - 1; i >= 0; i--) {
      Member member = region.members.get(i);
      if (member.slot != slot) continue;
      region.members.remove(i);
      region.visible.remove(member);
      region.dirty = true;
      break;
    }
    owners.remove(slot, region);
  }

  private boolean makeRegionCapacity() {
    if (regions.size() < MAX_REGIONS) return true;
    Region oldest = oldestEvictable(null);
    if (oldest == null) return false;
    evict(oldest);
    return true;
  }

  private void enforceResidentBudget() {
    while (residentBytes > MAX_RESIDENT_BYTES) {
      Region oldest = oldestEvictable(null);
      if (oldest == null) return;
      evict(oldest);
    }
  }

  private Region oldestEvictable(Region excluded) {
    Region oldest = null;
    for (Region candidate : regions) {
      if (candidate == excluded
          || candidate.visiblePass == pass
          || candidate.lastUsedFrame >= frame - 1L) continue;
      if (oldest == null || candidate.lastUsedFrame < oldest.lastUsedFrame) oldest = candidate;
    }
    return oldest;
  }

  private void evict(Region region) {
    residentBytes -= region.bytes;
    closePages(region.pages);
    for (Member member : region.members) owners.remove(member.slot, region);
    region.layer.regions.remove(region.key);
    regions.remove(region);
    region.members.clear();
    region.visible.clear();
    region.pages.clear();
    region.bytes = 0L;
    publishResidentStats();
  }

  private Matrix4f transform(int originX, int originY, int originZ) {
    return transformScratch
        .set(layerClipTransform)
        .translate(
            (float) (originX - cameraX), (float) (originY - cameraY), (float) (originZ - cameraZ));
  }

  private void publishResidentStats() {
    stats.regionResident(regions.size(), Math.max(0L, residentBytes));
  }

  private static void closePages(List<Page> pages) {
    for (Page page : pages) page.mesh.close();
  }

  private static int sectionAligned(String property, int fallback) {
    int requested = Math.max(16, Integer.getInteger(property, fallback));
    return requested / 16 * 16;
  }

  private static final class Layer {
    private final LinkedHashMap<RegionKey, Region> regions = new LinkedHashMap<>();
  }

  private record RegionKey(int x, int y, int z) {}

  public static final class Region {
    private final Layer layer;
    private final RegionKey key;
    private final ArrayList<Member> members = new ArrayList<>();
    private final ArrayList<Member> visible = new ArrayList<>();
    private ArrayList<Page> pages = new ArrayList<>();
    private int[] runs = new int[RUN_FIELDS * 8];
    private boolean dirty = true;
    private long visiblePass = Long.MIN_VALUE;
    private long lastUsedFrame;
    private long bytes;

    private Region(Layer layer, RegionKey key) {
      this.layer = layer;
      this.key = key;
    }

    private Member find(Slot slot) {
      for (Member member : members) if (member.slot == slot) return member;
      return null;
    }

    public boolean ready() {
      return !dirty && !pages.isEmpty();
    }

    public int pageCount() {
      return pages.size();
    }

    public Page page(int index) {
      return pages.get(index);
    }

    public int visibleCount() {
      return visible.size();
    }

    public int visibleRuns() {
      int count = 0;
      Member previous = null;
      for (Member member : members) {
        if (member.visibleStamp != visiblePass) {
          previous = null;
          continue;
        }
        if (previous == null
            || previous.pageIndex != member.pageIndex
            || previous.firstIndex + previous.batch.indexCount() != member.firstIndex) {
          if (runs.length < (count + 1) * RUN_FIELDS) {
            runs = Arrays.copyOf(runs, runs.length * 2);
          }
          int base = count * RUN_FIELDS;
          runs[base] = member.pageIndex;
          runs[base + 1] = member.firstIndex;
          runs[base + 2] = 0;
          runs[base + 3] = 0;
          count++;
        }
        int base = (count - 1) * RUN_FIELDS;
        runs[base + 2] += member.batch.indexCount();
        runs[base + 3] += member.batch.vertexCount();
        previous = member;
      }
      return count;
    }

    public int runPage(int run) {
      return runs[run * RUN_FIELDS];
    }

    public int runFirstIndex(int run) {
      return runs[run * RUN_FIELDS + 1];
    }

    public int runIndexCount(int run) {
      return runs[run * RUN_FIELDS + 2];
    }

    public int runVertexCount(int run) {
      return runs[run * RUN_FIELDS + 3];
    }

    public Member visible(int index) {
      return visible.get(index);
    }
  }

  public static final class Member {
    private final Slot slot;
    private Batch batch;
    private int originX;
    private int originY;
    private int originZ;
    private int pageIndex;
    private int firstIndex;
    private long visibleStamp = Long.MIN_VALUE;

    Member(Slot slot, Batch batch, int originX, int originY, int originZ) {
      this.slot = slot;
      this.batch = batch;
      this.originX = originX;
      this.originY = originY;
      this.originZ = originZ;
    }

    public Slot slot() {
      return slot;
    }

    public Batch batch() {
      return batch;
    }
  }

  public static final class Page {
    private final Mesh mesh;
    private final int vertexCount;
    private final int indexCount;

    private Page(Mesh mesh, int vertexCount, int indexCount) {
      this.mesh = mesh;
      this.vertexCount = vertexCount;
      this.indexCount = indexCount;
    }

    public Mesh mesh() {
      return mesh;
    }

    public int vertexCount() {
      return vertexCount;
    }

    public int indexCount() {
      return indexCount;
    }
  }

  static final class Assembler {
    private float[] positions = new float[0];
    private int[] colors = new int[0];
    private float[] uv0 = new float[0];
    private short[] uv2 = new short[0];
    private int[] indices = new int[0];

    Batch assemble(
        List<Member> members, int start, int end, int regionX, int regionY, int regionZ) {
      int vertexCount = 0;
      int indexCount = 0;
      boolean hasUv0 = false;
      boolean hasUv2 = false;
      for (int i = start; i < end; i++) {
        Batch batch = members.get(i).batch;
        vertexCount = Math.addExact(vertexCount, batch.vertexCount());
        indexCount = Math.addExact(indexCount, batch.indexCount());
        hasUv0 |= batch.hasUv0();
        hasUv2 |= batch.hasUv2();
      }
      positions = grow(positions, Math.multiplyExact(vertexCount, 3));
      colors = grow(colors, vertexCount);
      if (hasUv0) uv0 = grow(uv0, Math.multiplyExact(vertexCount, 2));
      if (hasUv2) uv2 = grow(uv2, Math.multiplyExact(vertexCount, 2));
      indices = grow(indices, indexCount);
      int vertexBase = 0;
      int indexBase = 0;
      for (int i = start; i < end; i++) {
        Member member = members.get(i);
        Batch batch = member.batch;
        int positionBase = vertexBase * 3;
        float translateX = member.originX - regionX;
        float translateY = member.originY - regionY;
        float translateZ = member.originZ - regionZ;
        for (int vertex = 0; vertex < batch.vertexCount(); vertex++) {
          int source = vertex * 3;
          int target = positionBase + source;
          positions[target] = batch.positions()[source] + translateX;
          positions[target + 1] = batch.positions()[source + 1] + translateY;
          positions[target + 2] = batch.positions()[source + 2] + translateZ;
        }
        System.arraycopy(batch.colors(), 0, colors, vertexBase, batch.vertexCount());
        int uvBase = vertexBase * 2;
        int uvCount = batch.vertexCount() * 2;
        if (hasUv0) {
          if (batch.hasUv0()) System.arraycopy(batch.uv0(), 0, uv0, uvBase, uvCount);
          else Arrays.fill(uv0, uvBase, uvBase + uvCount, 0.0f);
        }
        if (hasUv2) {
          if (batch.hasUv2()) System.arraycopy(batch.uv2(), 0, uv2, uvBase, uvCount);
          else Arrays.fill(uv2, uvBase, uvBase + uvCount, (short) 0);
        }
        for (int index = 0; index < batch.indexCount(); index++) {
          indices[indexBase + index] = batch.indices()[index] + vertexBase;
        }
        vertexBase += batch.vertexCount();
        indexBase += batch.indexCount();
      }
      return new Batch(
          positions,
          colors,
          hasUv0 ? uv0 : EMPTY_FLOATS,
          hasUv0,
          EMPTY_SHORTS,
          false,
          hasUv2 ? uv2 : EMPTY_SHORTS,
          hasUv2,
          EMPTY_BYTES,
          false,
          indices,
          indexCount,
          vertexCount,
          VertexFormat.Mode.TRIANGLES);
    }

    private static float[] grow(float[] current, int required) {
      return current.length >= required ? current : new float[grownCapacity(required)];
    }

    private static int[] grow(int[] current, int required) {
      return current.length >= required ? current : new int[grownCapacity(required)];
    }

    private static short[] grow(short[] current, int required) {
      return current.length >= required ? current : new short[grownCapacity(required)];
    }

    private static int grownCapacity(int required) {
      if (required <= 1) return required;
      int highest = Integer.highestOneBit(required - 1);
      return highest > Integer.MAX_VALUE / 2 ? required : highest << 1;
    }
  }
}
