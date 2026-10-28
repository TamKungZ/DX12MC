package net.dx12mc.pack;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class PackDirectory {
  private PackDirectory() {}

  public static File root() {
    String path = System.getProperty("dx12mc.testPacks", "build/test-packs");
    return new File(path);
  }

  public static PackInput read(String pack) {
    return read(root(), pack);
  }

  public static PackInput read(File root, String pack) {
    File assets = new File(root, pack + "/assets");
    File[] namespaces = assets.listFiles(file -> new File(file, "dx12mc/pack.json").isFile());
    if (namespaces == null || namespaces.length != 1) {
      throw new IllegalStateException("compiled test pack " + pack + " not found under " + root);
    }
    File base = new File(namespaces[0], "dx12mc");
    try {
      PackManifest manifest =
          PackManifest.parse(
              Files.readString(new File(base, "pack.json").toPath(), StandardCharsets.UTF_8));
      Map<String, byte[]> shaders = new HashMap<>();
      for (PackManifest.Program program : manifest.programs().values()) {
        if (program.vertex() != null) shaders.put(program.vertex(), bytes(base, program.vertex()));
        shaders.put(program.fragment(), bytes(base, program.fragment()));
      }
      Map<String, RgbaImage> images = new HashMap<>();
      for (PackManifest.Texture texture : manifest.textures().values()) {
        images.put(texture.file(), image(new File(base, texture.file())));
      }
      return new PackInput(namespaces[0].getName(), "test/" + pack, manifest, shaders, images);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static PackPlan plan(String pack) {
    return PackPlan.validate(read(pack), PackSet.passVertexBinary(), "1.21.1");
  }

  public static RgbaImage image(File file) throws IOException {
    BufferedImage image = ImageIO.read(file);
    byte[] rgba = new byte[image.getWidth() * image.getHeight() * 4];
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int argb = image.getRGB(x, y);
        int offset = (y * image.getWidth() + x) * 4;
        rgba[offset] = (byte) (argb >>> 16);
        rgba[offset + 1] = (byte) (argb >>> 8);
        rgba[offset + 2] = (byte) argb;
        rgba[offset + 3] = (byte) (argb >>> 24);
      }
    }
    return new RgbaImage(image.getWidth(), image.getHeight(), rgba);
  }

  private static byte[] bytes(File base, String path) throws IOException {
    return Files.readAllBytes(new File(base, path).toPath());
  }
}
