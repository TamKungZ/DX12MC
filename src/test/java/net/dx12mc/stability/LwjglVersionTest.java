package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LwjglVersionTest {
  private static final Map<String, String> LAUNCHER_LWJGL =
      Map.of("1.20.1", "3.3.1", "1.21.1", "3.3.3");

  @Test
  void bgfxMatchesTheLwjglVersionTheMinecraftLauncherSupplies() throws IOException {
    Properties properties = properties();
    String minecraft = properties.getProperty("minecraft_version");
    String lwjgl = properties.getProperty("lwjgl_version");
    assertEquals(LAUNCHER_LWJGL.get(minecraft), lwjgl, "LWJGL supplied by Minecraft " + minecraft);
  }

  @Test
  void shadercIsPinnedForTheLwjglVersionInUse() throws IOException {
    Properties properties = properties();
    String lwjgl = properties.getProperty("lwjgl_version");
    assertTrue(properties.getProperty("shaderc_sha256_" + lwjgl, "").matches("[0-9a-f]{64}"));
  }

  @Test
  void theBgfxBindingsOnTheClasspathAreTheNestedLwjglVersion() throws Exception {
    String lwjgl = properties().getProperty("lwjgl_version");
    Path jar =
        Path.of(
            Class.forName("org.lwjgl.bgfx.BGFX")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    assertEquals("lwjgl-bgfx-" + lwjgl + ".jar", jar.getFileName().toString());
    assertTrue(Files.readString(Path.of("THIRD_PARTY.md")).contains("bgfx bindings " + lwjgl));
  }

  @Test
  void everyLwjglModuleOnTheClasspathIsTheSameVersion() throws IOException {
    String lwjgl = properties().getProperty("lwjgl_version");
    Pattern module =
        Pattern.compile("lwjgl(?:-[a-z0-9]+)?-(\\d+\\.\\d+\\.\\d+)(?:-natives-[a-z0-9-]+)?\\.jar");
    Set<String> versions = new TreeSet<>();
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      Matcher matcher = module.matcher(Path.of(entry).getFileName().toString());
      if (matcher.matches()) versions.add(matcher.group(1));
    }
    assertEquals(Set.of(lwjgl), versions);
  }

  private static Properties properties() throws IOException {
    Properties properties = new Properties();
    properties.load(Files.newBufferedReader(Path.of("gradle.properties")));
    return properties;
  }
}
