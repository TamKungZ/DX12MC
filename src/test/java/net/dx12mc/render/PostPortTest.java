package net.dx12mc.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dx12mc.graphics.pipeline.PostPipeline.Effect;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;

class PostPortTest {
  private static final List<String> SUPPORTED_CHAINS =
      List.of("entity_outline", "creeper", "invert", "spider");

  @Test
  void everyPassOfTheSupportedVanillaChainsHasANativePort() throws IOException {
    for (String chain : SUPPORTED_CHAINS) {
      JsonObject json = json("shaders/post/" + chain + ".json");
      for (JsonElement pass : json.getAsJsonArray("passes")) {
        String name = pass.getAsJsonObject().get("name").getAsString();
        assertNotNull(Effect.byName(name), chain + " pass " + name);
      }
    }
  }

  @Test
  void transparencyIsTheOnlyVanillaChainWithoutANativePort() throws IOException {
    JsonObject json = json("shaders/post/transparency.json");
    Set<String> unported = new HashSet<>();
    for (JsonElement pass : json.getAsJsonArray("passes")) {
      String name = pass.getAsJsonObject().get("name").getAsString();
      if (Effect.byName(name) == null) unported.add(name);
    }
    assertEquals(Set.of("transparency"), unported);
  }

  @Test
  void everyEffectNamesAVanillaProgramAndTheUniformsTheProcessorReads() throws IOException {
    String source =
        Files.readString(
            Path.of("src", "main", "java", "net", "dx12mc", "render", "PostProcessor.java"));
    Matcher matcher = Pattern.compile("read\\(fx, \"(\\w+)\"").matcher(source);
    Set<String> read = new HashSet<>();
    while (matcher.find()) read.add(matcher.group(1));
    Set<String> declared = new HashSet<>();
    for (Effect effect : Effect.values()) {
      JsonObject program = json("shaders/program/" + effect.vanillaName() + ".json");
      for (JsonElement uniform : program.getAsJsonArray("uniforms")) {
        declared.add(uniform.getAsJsonObject().get("name").getAsString());
      }
      assertEquals(
          blendsWithSourceAlpha(program),
          effect.alphaBlend(),
          effect.vanillaName() + " blend function");
    }
    assertFalse(read.isEmpty());
    for (String name : read) assertTrue(declared.contains(name), name);
  }

  @Test
  void postPassHasNoLinearFilterFlagSoTheInputTargetsFilterModeDecidesSampling() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    Class<?> pass = Class.forName("net.minecraft.client.renderer.PostPass", false, loader);
    for (java.lang.reflect.Method method : pass.getDeclaredMethods()) {
      assertNotEquals("getFilterMode", method.getName());
    }
    String source =
        Files.readString(
            Path.of("src", "main", "java", "net", "dx12mc", "render", "PostProcessor.java"));
    assertTrue(source.contains("in.filterMode == GL_LINEAR"));
    assertTrue(source.contains("dx12mc$clearChannels()"));
    assertTrue(source.contains("partialTick < lastStamp"));
  }

  @Test
  void menuBlurDoesNotExistInThisVersion() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    Class<?> renderer = Class.forName("net.minecraft.client.renderer.GameRenderer", false, loader);
    for (java.lang.reflect.Method method : renderer.getDeclaredMethods()) {
      assertNotEquals("processBlurEffect", method.getName());
    }
    for (Field field : renderer.getDeclaredFields()) assertNotEquals("blurEffect", field.getName());
    assertEquals(
        Set.of(),
        Set.of(Options.class.getDeclaredMethods()).stream()
            .map(java.lang.reflect.Method::getName)
            .filter(name -> name.equals("menuBackgroundBlurriness"))
            .collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void accessorFieldsExistInTheMappedClasses() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    field(loader, "net.minecraft.client.renderer.PostChain", "passes");
    field(loader, "net.minecraft.client.renderer.PostChain", "screenTarget");
    field(loader, "net.minecraft.client.renderer.PostChain", "time");
    field(loader, "net.minecraft.client.renderer.PostChain", "lastStamp");
    field(loader, "net.minecraft.client.renderer.PostPass", "auxAssets");
    field(loader, "net.minecraft.client.renderer.PostPass", "auxNames");
    field(loader, "net.minecraft.client.renderer.PostPass", "auxWidths");
    field(loader, "net.minecraft.client.renderer.PostPass", "auxHeights");
    field(loader, "com.mojang.blaze3d.pipeline.RenderTarget", "clearChannels");
    field(loader, "net.minecraft.client.renderer.GameRenderer", "effectActive");
  }

  private static void field(ClassLoader loader, String type, String name) throws Exception {
    Field field = Class.forName(type, false, loader).getDeclaredField(name);
    assertNotNull(field, type + "." + name);
  }

  private static boolean blendsWithSourceAlpha(JsonObject program) {
    JsonObject blend = program.getAsJsonObject("blend");
    return blend != null && "srcalpha".equals(blend.get("srcrgb").getAsString());
  }

  private static JsonObject json(String path) throws IOException {
    try (InputStream input =
        Options.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path)) {
      assertNotNull(input, path);
      JsonElement element =
          JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      return element.getAsJsonObject();
    }
  }
}
