package net.dx12mc.stability;

import static net.dx12mc.stability.VideoSettingsOriginTest.callersOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.indexOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.invokes;
import static net.dx12mc.stability.VideoSettingsOriginTest.method;
import static net.dx12mc.stability.VideoSettingsOriginTest.readsStatic;
import static net.dx12mc.stability.VideoSettingsOriginTest.withoutDescriptors;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodNode;

class LineAndScreenshotOriginTest {
  private static final Path JAVA = Path.of("src", "main", "java", "net", "dx12mc");
  private static final String MODE = "com/mojang/blaze3d/vertex/VertexFormat$Mode";
  private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
  private static final String SCREENSHOT = "net/minecraft/client/Screenshot";
  private static final String RENDER_TARGET = "Lcom/mojang/blaze3d/pipeline/RenderTarget;";

  @Test
  void blockOutlineAndF3GizmoAreQuadExpandedRendertypeLinesDraws() throws IOException {
    MethodNode renderLevel =
        method("net/minecraft/client/renderer/LevelRenderer", "renderLevel", null);
    assertTrue(invokes(renderLevel, "net/minecraft/client/renderer/RenderType", "lines", null));
    assertTrue(
        invokes(
            renderLevel, "net/minecraft/client/renderer/LevelRenderer", "renderHitOutline", null));
    assertTrue(
        invokes(
            method(RENDER_SYSTEM, "renderCrosshair", "(I)V"),
            "com/mojang/blaze3d/platform/GLX",
            "_renderCrosshair",
            null));
    MethodNode gizmo = method("com/mojang/blaze3d/platform/GLX", "_renderCrosshair", null);
    assertTrue(readsStatic(gizmo, MODE, "LINES"));
    assertTrue(invokes(gizmo, RENDER_SYSTEM, "lineWidth", "(F)V"));
    assertTrue(
        withoutDescriptors(
                callersOf(
                    "net/minecraft/client/renderer/GameRenderer.getRendertypeLinesShader()Lnet/minecraft/client/renderer/ShaderInstance;"))
            .contains("com/mojang/blaze3d/platform/GLX._renderCrosshair"));
    MethodNode endLastVertex =
        method("com/mojang/blaze3d/vertex/BufferBuilder", "endLastVertex", "()V");
    assertTrue(readsStatic(endLastVertex, MODE, "LINES"), "vanilla duplicates every line vertex");
    assertTrue(readsStatic(endLastVertex, MODE, "LINE_STRIP"));
    assertTrue(
        invokes(
            method("net/minecraft/client/renderer/ShaderInstance", "setDefaultUniforms", null),
            RENDER_SYSTEM,
            "getShaderLineWidth",
            "()F"));
    assertFalse(read("bridge/Bridge.java").contains("isOmittableNativeLine"));
    assertTrue(
        read("bridge/DrawContextFactory.java").contains("RenderSystem.getShaderLineWidth()"));
  }

  @Test
  void lineShaderReproducesRendertypeLinesVsh() throws IOException {
    String vanilla = vanilla("core/rendertype_lines.vsh");
    String line = Files.readString(Path.of("src", "main", "shaders", "vs_line.sc"));
    assertTrue(vanilla.contains("const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);"));
    assertTrue(read("bridge/DrawContextFactory.java").contains("1.0f - 1.0f / 256.0f"));
    assertTrue(vanilla.contains("* LineWidth / ScreenSize;"));
    assertTrue(line.contains("* u_lineParams.x / u_lineParams.yz;"));
    assertTrue(vanilla.contains("if (lineOffset.x < 0.0) {"));
    assertTrue(line.contains("if (lineOffset.x < 0.0) lineOffset *= -1.0;"));
    assertTrue(vanilla.contains("if (gl_VertexID % 2 == 0) {"));
    assertTrue(read("graphics/bgfx/Packing.java").contains("(vertex & 1) == 0 ? 1.0f : -1.0f"));
    assertTrue(vanilla.contains("vertexDistance = fog_distance(Position, FogShape);"));
  }

  @Test
  void screenshotReadbackOriginIsTakeScreenshotOfTheMainRenderTarget() throws IOException {
    assertTrue(
        invokes(
            method("net/minecraft/client/KeyboardHandler", "keyPress", null),
            SCREENSHOT,
            "grab",
            "(Ljava/io/File;" + RENDER_TARGET + "Ljava/util/function/Consumer;)V"));
    MethodNode grab =
        method(
            SCREENSHOT,
            "_grab",
            "(Ljava/io/File;Ljava/lang/String;"
                + RENDER_TARGET
                + "Ljava/util/function/Consumer;)V");
    assertTrue(indexOf(grab, SCREENSHOT, "takeScreenshot") >= 0);
    MethodNode take =
        method(
            SCREENSHOT,
            "takeScreenshot",
            "(" + RENDER_TARGET + ")Lcom/mojang/blaze3d/platform/NativeImage;");
    assertTrue(
        invokes(take, "com/mojang/blaze3d/platform/NativeImage", "downloadTexture", "(IZ)V"));
    assertTrue(invokes(take, "com/mojang/blaze3d/platform/NativeImage", "flipY", "()V"));
    assertEquals(
        java.util.Set.of(
            SCREENSHOT + "._grab", "net/minecraft/client/renderer/GameRenderer.takeAutoScreenshot"),
        withoutDescriptors(
            callersOf(
                SCREENSHOT
                    + ".takeScreenshot("
                    + RENDER_TARGET
                    + ")Lcom/mojang/blaze3d/platform/NativeImage;")));
    String mixin = read("mixin/ScreenshotMixin.java");
    assertTrue(mixin.contains("method =\n          \"_grab("));
    assertTrue(mixin.contains("\"takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)"));
    String controller = read("render/Controller.java");
    int request = controller.indexOf("lifecycle.requestScreenCapture(minecraft)");
    int present = controller.indexOf("lifecycle.present(minecraft)");
    assertTrue(
        request > 0 && present > request, "capture is requested in the frame being presented");
  }

  @Test
  void captureTargetsExistInTheMappedClasses() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot", false, loader);
    Class<?> target = Class.forName("com.mojang.blaze3d.pipeline.RenderTarget", false, loader);
    assertNotNull(
        screenshot.getDeclaredMethod(
            "_grab", java.io.File.class, String.class, target, java.util.function.Consumer.class));
    assertNotNull(screenshot.getDeclaredMethod("takeScreenshot", target));
    Class<?> gameRenderer =
        Class.forName("net.minecraft.client.renderer.GameRenderer", false, loader);
    assertNotNull(gameRenderer.getDeclaredMethod("takeAutoScreenshot", Path.class));
  }

  private static String read(String relative) throws IOException {
    return Files.readString(JAVA.resolve(relative)).replace("\r\n", "\n");
  }

  private static String vanilla(String path) throws IOException {
    try (InputStream input =
        Options.class.getClassLoader().getResourceAsStream("assets/minecraft/shaders/" + path)) {
      assertNotNull(input, path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
