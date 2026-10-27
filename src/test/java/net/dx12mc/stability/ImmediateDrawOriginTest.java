package net.dx12mc.stability;

import static net.dx12mc.stability.VideoSettingsOriginTest.callersOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.invokes;
import static net.dx12mc.stability.VideoSettingsOriginTest.method;
import static net.dx12mc.stability.VideoSettingsOriginTest.withoutDescriptors;
import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.dx12mc.bridge.mesh.Encoder;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodNode;

class ImmediateDrawOriginTest {
  private static final String UPLOADER = "com/mojang/blaze3d/vertex/BufferUploader";
  private static final String RENDERED = "com/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer";
  private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
  private static final String DRAW_DESCRIPTOR = "(L" + RENDERED + ";)V";

  @Test
  void vanillaSkipsEmptyBuffersBeforeAnyDrawSoTheHookMustLeaveThemAlone() throws IOException {
    MethodNode upload =
        method(UPLOADER, "upload", "(L" + RENDERED + ";)Lcom/mojang/blaze3d/vertex/VertexBuffer;");
    assertTrue(invokes(upload, RENDERED, "isEmpty", "()Z"));
    assertTrue(invokes(upload, RENDERED, "release", "()V"));
    assertTrue(
        invokes(method(UPLOADER, "_drawWithShader", DRAW_DESCRIPTOR), UPLOADER, "upload", null));
    assertTrue(invokes(method(UPLOADER, "draw", DRAW_DESCRIPTOR), UPLOADER, "upload", null));
    MethodNode entry = method(UPLOADER, "drawWithShader", DRAW_DESCRIPTOR);
    assertTrue(invokes(entry, RENDER_SYSTEM, "isOnRenderThreadOrInit", "()Z"));
    assertTrue(invokes(entry, RENDER_SYSTEM, "recordRenderCall", null));
    assertEquals(
        Set.of(UPLOADER + ".drawWithShader"),
        withoutDescriptors(callersOf(UPLOADER + "._drawWithShader" + DRAW_DESCRIPTOR)));

    String mixin =
        Files.readString(
            Path.of("src", "main", "java", "net", "dx12mc", "mixin", "BufferUploaderMixin.java"));
    assertTrue(mixin.contains("method = \"_drawWithShader\""));
    assertTrue(mixin.contains("method = \"draw\""));
    assertTrue(mixin.indexOf("mesh.isEmpty()") >= 0);
    assertTrue(mixin.indexOf("mesh.isEmpty()") < mixin.indexOf("drawImmediate(mesh)"));
  }

  @Test
  void aBuilderEndedWithoutVerticesIsEmptyAndEncodesToNothing() {
    BufferBuilder builder = new BufferBuilder(64);
    builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    BufferBuilder.RenderedBuffer rendered = builder.end();
    try {
      assertTrue(rendered.isEmpty());
      assertNull(Encoder.encode(rendered));
    } finally {
      rendered.release();
    }
  }
}
