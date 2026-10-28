package net.dx12mc.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.dx12mc.DX12MC;
import net.dx12mc.api.PackStatus;
import net.dx12mc.pack.InvalidPackException;
import net.dx12mc.pack.PackInput;
import net.dx12mc.pack.PackManifest;
import net.dx12mc.pack.PackPlan;
import net.dx12mc.pack.PackSet;
import net.dx12mc.pack.RgbaImage;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ShaderPackLoader implements PreparableReloadListener {
  static final boolean ALLOW_SERVER_PACKS = Boolean.getBoolean("dx12mc.pack.allowServer");
  private static final String MANIFEST = "dx12mc/pack.json";
  private static final int MAX_COMPANION_SIZE = 1024;
  private static final long MAX_FILE_BYTES = 16L << 20;

  public record Result(
      List<PackPlan> plans,
      List<PackStatus> rejected,
      Map<String, RgbaImage> normals,
      Map<String, RgbaImage> speculars) {}

  @Override
  public CompletableFuture<Void> reload(
      PreparationBarrier barrier,
      ResourceManager manager,
      ProfilerFiller preparationProfiler,
      ProfilerFiller reloadProfiler,
      Executor background,
      Executor game) {
    return CompletableFuture.supplyAsync(() -> load(manager), background)
        .thenCompose(barrier::wait)
        .thenAcceptAsync(result -> DX12MC.runtime().shaderPacksLoaded(result), game);
  }

  @Override
  public String getName() {
    return "DX12MC shader packs";
  }

  static Result load(ResourceManager manager) {
    List<PackResources> order = manager.listPacks().toList();
    List<Found> found = new ArrayList<>();
    for (String namespace : manager.getNamespaces()) {
      Optional<Resource> manifest =
          manager.getResource(ResourceLocation.fromNamespaceAndPath(namespace, MANIFEST));
      manifest.ifPresent(
          resource -> found.add(new Found(namespace, resource, order.indexOf(resource.source()))));
    }
    found.sort((a, b) -> Integer.compare(b.priority, a.priority));
    List<PackPlan> plans = new ArrayList<>();
    List<PackStatus> rejected = new ArrayList<>();
    boolean materials = false;
    String line = SharedConstants.getCurrentVersion().getName();
    for (Found pack : found) {
      PackResources source = pack.resource.source();
      String sourceName = source.packId();
      if (source.location().source() == PackSource.SERVER && !ALLOW_SERVER_PACKS) {
        String message =
            "supplied by a server; set -Ddx12mc.pack.allowServer=true to allow server shader packs";
        rejected.add(new PackStatus(pack.namespace, sourceName, PackStatus.State.DENIED, message));
        DX12MC.LOGGER.warn(
            "DX12MC shader pack {} from {} is not loaded: {}", pack.namespace, sourceName, message);
        continue;
      }
      try {
        PackManifest manifest =
            PackManifest.parse(new String(read(pack.resource.open()), StandardCharsets.UTF_8));
        Map<String, byte[]> shaders = new HashMap<>();
        Map<String, RgbaImage> images = new HashMap<>();
        for (PackManifest.Program program : manifest.programs().values()) {
          PackManifest.Program files = program.forLine(line);
          if (files.vertex() != null)
            shaders.put(files.vertex(), file(source, pack.namespace, files.vertex()));
          shaders.put(files.fragment(), file(source, pack.namespace, files.fragment()));
        }
        for (PackManifest.Texture texture : manifest.textures().values()) {
          images.put(texture.file(), image(file(source, pack.namespace, texture.file())));
        }
        PackPlan plan =
            PackPlan.load(
                new PackInput(pack.namespace, sourceName, manifest, shaders, images),
                PackSet.passVertexBinary(),
                line,
                rejected);
        if (plan != null) {
          plans.add(plan);
          materials |= manifest.materials() != null;
        }
      } catch (InvalidPackException | IOException | IllegalArgumentException exception) {
        String message =
            exception.getMessage() == null ? exception.toString() : exception.getMessage();
        PackPlan.reject(pack.namespace, sourceName, message, rejected);
      }
    }
    Map<String, RgbaImage> normals = Map.of();
    Map<String, RgbaImage> speculars = Map.of();
    if (materials) {
      normals = companions(manager, "_n.png");
      speculars = companions(manager, "_s.png");
    }
    return new Result(List.copyOf(plans), List.copyOf(rejected), normals, speculars);
  }

  private record Found(String namespace, Resource resource, int priority) {}

  private static Map<String, RgbaImage> companions(ResourceManager manager, String suffix) {
    Map<String, RgbaImage> images = new HashMap<>();
    for (Map.Entry<ResourceLocation, Resource> entry :
        manager
            .listResources("textures", location -> location.getPath().endsWith(suffix))
            .entrySet()) {
      if (entry.getValue().source().location().source() == PackSource.SERVER && !ALLOW_SERVER_PACKS)
        continue;
      ResourceLocation location = entry.getKey();
      String path = location.getPath();
      String sprite = path.substring("textures/".length(), path.length() - suffix.length());
      try {
        RgbaImage image = image(read(entry.getValue().open()));
        if (image.width() <= MAX_COMPANION_SIZE && image.height() <= MAX_COMPANION_SIZE * 32) {
          images.put(location.getNamespace() + ":" + sprite, image);
        }
      } catch (IOException | IllegalArgumentException exception) {
        DX12MC.LOGGER.warn("Material texture {} is ignored: {}", location, exception.getMessage());
      }
    }
    return Map.copyOf(images);
  }

  private static byte[] file(PackResources source, String namespace, String path)
      throws IOException {
    IoSupplier<InputStream> supplier =
        source.getResource(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.fromNamespaceAndPath(namespace, "dx12mc/" + path));
    if (supplier == null)
      throw new InvalidPackException("missing file dx12mc/" + path + " in the pack");
    return read(supplier.get());
  }

  private static byte[] read(InputStream stream) throws IOException {
    try (InputStream input = stream) {
      byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
      if (bytes.length > MAX_FILE_BYTES)
        throw new InvalidPackException("a pack file is larger than 16 MiB");
      return bytes;
    }
  }

  static RgbaImage image(byte[] png) throws IOException {
    try (NativeImage image =
        NativeImage.read(NativeImage.Format.RGBA, new java.io.ByteArrayInputStream(png))) {
      int width = image.getWidth();
      int height = image.getHeight();
      byte[] rgba = new byte[width * height * 4];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int abgr = image.getPixelRGBA(x, y);
          int offset = (y * width + x) * 4;
          rgba[offset] = (byte) abgr;
          rgba[offset + 1] = (byte) (abgr >>> 8);
          rgba[offset + 2] = (byte) (abgr >>> 16);
          rgba[offset + 3] = (byte) (abgr >>> 24);
        }
      }
      return new RgbaImage(width, height, rgba);
    }
  }
}
