package net.dx12mc.stability;

import static net.dx12mc.stability.VideoSettingsOriginTest.callersOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.invokes;
import static net.dx12mc.stability.VideoSettingsOriginTest.method;
import static net.dx12mc.stability.VideoSettingsOriginTest.withoutDescriptors;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodNode;

class EndPortalOriginTest {
  private static final Path JAVA = Path.of("src", "main", "java", "net", "dx12mc");
  private static final String RENDER_TYPE = "net/minecraft/client/renderer/RenderType";
  private static final String GAME_RENDERER = "net/minecraft/client/renderer/GameRenderer";
  private static final String SHADER_INSTANCE = "net/minecraft/client/renderer/ShaderInstance";

  @Test
  void bothPortalRenderersDrawTheEndPortalRenderTypesAndThePoemBackgroundIsAPlainBlit() {
    MethodNode poemBackground =
        method("net/minecraft/client/gui/screens/WinScreen", "renderBg", null);
    assertTrue(invokes(poemBackground, "net/minecraft/client/gui/GuiGraphics", "blit", null));
    assertFalse(invokes(poemBackground, RENDER_TYPE, "endPortal", null));
    assertFalse(
        invokes(poemBackground, "net/minecraft/client/gui/GuiGraphics", "fillRenderType", null));
    assertTrue(
        invokes(
            method(
                "net/minecraft/client/renderer/blockentity/TheEndPortalRenderer",
                "renderType",
                null),
            RENDER_TYPE,
            "endPortal",
            null));
    assertTrue(
        invokes(
            method(
                "net/minecraft/client/renderer/blockentity/TheEndGatewayRenderer",
                "renderType",
                null),
            RENDER_TYPE,
            "endGateway",
            null));
  }

  @Test
  void bothRenderTypesRunTheirShaderThroughRenderStateShardAndGameTimeComesFromRenderSystem() {
    for (String getter :
        new String[] {"getRendertypeEndPortalShader", "getRendertypeEndGatewayShader"}) {
      assertTrue(
          withoutDescriptors(
                  callersOf(GAME_RENDERER + "." + getter + "()L" + SHADER_INSTANCE + ";"))
              .contains("net/minecraft/client/renderer/RenderStateShard.<clinit>"),
          getter);
    }
    assertEquals(
        Set.of(
            "com/mojang/blaze3d/vertex/VertexBuffer._drawWithShader",
            "net/minecraft/client/renderer/LevelRenderer.renderChunkLayer"),
        withoutDescriptors(
            callersOf("com/mojang/blaze3d/systems/RenderSystem.getShaderGameTime()F")));
    assertTrue(
        withoutDescriptors(
                callersOf(
                    "com/mojang/blaze3d/vertex/BufferUploader.drawWithShader("
                        + "Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"))
            .contains("net/minecraft/client/renderer/RenderType.end"));
  }

  @Test
  void vanillaShaderReadsExactlyTwoSamplersAndTheLayerMathMatchesTheD3D12Port() throws IOException {
    String vanilla = vanilla("core/rendertype_end_portal.fsh");
    List<String> samplers = new ArrayList<>();
    Matcher sampler = Pattern.compile("uniform sampler2D ([A-Za-z0-9]+);").matcher(vanilla);
    while (sampler.find()) samplers.add(sampler.group(1));
    assertEquals(List.of("Sampler0", "Sampler1"), samplers);
    assertTrue(vanilla.contains("textureProj(Sampler0, texProj0).rgb * COLORS[0]"));
    assertTrue(
        vanilla.contains("textureProj(Sampler1, texProj0 * end_portal_layer(float(i + 1)))"));
    assertTrue(vanilla.contains("1.0, 0.0, 0.0, 17.0 / layer,"));
    assertTrue(vanilla.contains("0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),"));
    assertTrue(vanilla.contains("radians((layer * layer * 4321.0 + layer * 9.0) * 2.0)"));
    assertTrue(vanilla.contains("mat2((4.5 - layer / 4.0) * 2.0)"));
    assertTrue(vanilla.contains("0.5, 0.0, 0.0, 0.25,"));
    String port = Files.readString(Path.of("src", "main", "shaders", "fs_end_portal.sc"));
    assertEquals(colors(vanilla), colors(port));
    assertTrue(port.contains("fmod((layer * layer * 4321.0 + layer * 9.0) * 2.0, 360.0)"));
    assertTrue(port.contains("(4.5 - layer / 4.0) * 2.0"));
    assertTrue(port.contains("17.0 / layer * p.w"));
    assertTrue(port.contains("(2.0 + layer / 1.5) * (u_portal.x * 1.5) * p.w"));
    String vertex = vanilla("core/rendertype_end_portal.vsh");
    assertTrue(vertex.contains("projection_from_position(gl_Position)"));
    assertTrue(
        vanilla("include/projection.glsl")
            .contains(
                "projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);"));
  }

  @Test
  void bothPortalProgramsAreTheSameVanillaShaderFiles() throws IOException {
    for (String program : new String[] {"rendertype_end_portal", "rendertype_end_gateway"}) {
      String json = vanilla("core/" + program + ".json");
      assertTrue(json.contains("\"vertex\": \"rendertype_end_portal\""), program);
      assertTrue(json.contains("\"fragment\": \"rendertype_end_portal\""), program);
      assertTrue(
          json.contains("{ \"name\": \"Sampler0\" }")
              && json.contains("{ \"name\": \"Sampler1\" }"));
    }
  }

  @Test
  void d3d12DrawBindsBothSamplersAndFeedsGameTimeAndLayersForEveryEndPortalDraw()
      throws IOException {
    String factory = read("bridge/DrawContextFactory.java");
    assertTrue(factory.contains("if (semantics.endPortal()) return DrawContext.Kind.END_PORTAL;"));
    assertTrue(factory.contains("!batch.hasUv0() && !semantics.endPortal()"));
    assertTrue(
        factory.contains(
            "kind == DrawContext.Kind.END_PORTAL\n            ? resolveRequired(1, shader)"));
    assertTrue(
        factory.contains(
            "ShaderInputs.portal(semantics, RenderSystem.getShaderGameTime(),"
                + " context.portalParams);"));
    String pipeline = read("graphics/pipeline/ShaderPipeline.java");
    assertTrue(
        pipeline.contains("kind == DrawContext.Kind.END_PORTAL) return valid(context.overlay());"));
    assertTrue(
        pipeline.contains(
            "kind == DrawContext.Kind.ENTITY || kind == DrawContext.Kind.END_PORTAL"));
    assertTrue(pipeline.contains("bgfx_set_texture(1, uniforms.overlay"));
    assertTrue(
        read("graphics/pipeline/Uniforms.java")
            .contains("bgfx_set_uniform(portal, context.portalParams(), 1);"));
    assertTrue(
        read("graphics/pipeline/PipelineSet.java")
            .contains(
                "DrawContext.Kind.END_PORTAL, layout, \"vs_end_portal\", \"fs_end_portal\","
                    + " \"fs_end_portal\""));
  }

  private static List<String> colors(String source) {
    List<String> colors = new ArrayList<>();
    Matcher matcher = Pattern.compile("vec3[(]([0-9.]+), ([0-9.]+), ([0-9.]+)[)]").matcher(source);
    while (matcher.find())
      colors.add(matcher.group(1) + "," + matcher.group(2) + "," + matcher.group(3));
    assertEquals(16, colors.size());
    return colors;
  }

  private static String read(String relative) throws IOException {
    return Files.readString(JAVA.resolve(relative)).replace("\r\n", "\n");
  }

  private static String vanilla(String path) throws IOException {
    try (InputStream input =
        Options.class.getClassLoader().getResourceAsStream("assets/minecraft/shaders/" + path)) {
      assertNotNull(input, path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }
}
