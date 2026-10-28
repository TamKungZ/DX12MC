package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ShaderPackHookTest {
  private static String read(String path) throws IOException {
    return Files.readString(Path.of(path))
        .replace("\r\n", "\n")
        .replaceAll("\\(\\s+", "(")
        .replaceAll("([^\\s])\\n\\s+([^\\s/@}])", "$1 $2");
  }

  private static String method(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, signature);
    return source.substring(start, source.indexOf("\n  }\n", start));
  }

  @Test
  void worldHooksReturnBeforeDoingWorkWhenNoPackIsActive() throws IOException {
    String controller = read("src/main/java/net/dx12mc/render/Controller.java");
    for (String hook :
        new String[] {
          "public void beginWorld(", "public void endWorld()", "public void afterLevel()"
        }) {
      String body = method(controller, hook);
      assertTrue(
          body.contains(
              "if (packHost == null || !packHost.active() || !lifecycle.active()) return;"),
          hook + " must return first when no pack is active");
    }
    assertTrue(controller.contains("if (packHost.active()) beginPackSectionLayer(renderType);"));
    String ensureFrame = method(controller, "private void ensureFrame(Minecraft minecraft)");
    assertTrue(ensureFrame.contains("if (packHost.active()) {"));
    assertTrue(ensureFrame.contains("if (main == null && needsReadableMain(minecraft))"));
  }

  @Test
  void overridesAreResolvedOncePerShaderChangeNotPerDraw() throws IOException {
    String factory = read("src/main/java/net/dx12mc/bridge/DrawContextFactory.java");
    String classify = method(factory, "private void classifyShader(String shader)");
    assertTrue(classify.contains("if (Objects.equals(classifiedShaderName, shader)) return;"));
    assertTrue(classify.contains("override = packs.override(shader);"));
    String packs = read("src/main/java/net/dx12mc/pack/PackSet.java");
    assertTrue(
        method(packs, "public PackProgram override(String shaderName)")
            .contains("if (overrides.isEmpty() || shaderName == null) return null;"));
  }

  @Test
  void packFailuresDisableThePacksInsteadOfEndingTheGame() throws IOException {
    String controller = read("src/main/java/net/dx12mc/render/Controller.java");
    String stage = method(controller, "private void runPackStage(String stage)");
    assertTrue(stage.contains("packHost.failed("));
    assertTrue(
        controller.contains(
            "packHost.failed(\"the shader pack scene target could not be created: \""));
    String host = read("src/main/java/net/dx12mc/render/ShaderPackHost.java");
    assertTrue(host.contains("failed(\"applying shader packs failed: \""));
    assertTrue(host.contains("failed(\"material atlas upload failed: \""));
  }

  @Test
  void serverPacksAreDeniedUnlessAllowed() throws IOException {
    String loader = read("src/main/java/net/dx12mc/client/ShaderPackLoader.java");
    assertTrue(loader.contains("Boolean.getBoolean(\"dx12mc.pack.allowServer\")"));
    assertTrue(
        loader.contains("source.location().source() == PackSource.SERVER && !ALLOW_SERVER_PACKS"));
    assertTrue(loader.contains("source.getResource(PackType.CLIENT_RESOURCES"));
  }

  @Test
  void theNewMixinsAreRegistered() throws IOException {
    String mixins = read("src/main/resources/dx12mc.mixins.json");
    assertTrue(mixins.contains("\"TextureAtlasMixin\""));
    assertTrue(mixins.contains("\"ReloadableResourceManagerMixin\""));
    String level = read("src/main/java/net/dx12mc/mixin/LevelRendererMixin.java");
    assertTrue(level.contains("@Inject(method = \"renderLevel\", at = @At(\"HEAD\"))"));
    assertTrue(level.contains("@Inject(method = \"renderLevel\", at = @At(\"RETURN\"))"));
    String game = read("src/main/java/net/dx12mc/mixin/GameRendererMixin.java");
    assertTrue(game.contains("@Inject(method = \"renderLevel\", at = @At(\"RETURN\"))"));
  }
}
