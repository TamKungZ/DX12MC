package net.dx12mc.stability;

import static net.dx12mc.stability.VideoSettingsOriginTest.callersOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.invokes;
import static net.dx12mc.stability.VideoSettingsOriginTest.method;
import static net.dx12mc.stability.VideoSettingsOriginTest.readsStatic;
import static net.dx12mc.stability.VideoSettingsOriginTest.withoutDescriptors;
import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class ColorLogicOriginTest {
  private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
  private static final String LOGIC_OP = "com/mojang/blaze3d/platform/GlStateManager$LogicOp";
  private static final String SHARD = "net/minecraft/client/renderer/RenderStateShard";
  private static final String RENDER_TYPE = "net/minecraft/client/renderer/RenderType";

  @Test
  void textHighlightIsTheOnlyVanillaColorLogicUserAndItUsesOrReverse() throws IOException {
    Set<String> enablers = withoutDescriptors(callersOf(RENDER_SYSTEM + ".enableColorLogicOp()V"));
    assertFalse(enablers.isEmpty());
    for (String caller : enablers) assertTrue(caller.startsWith(SHARD + "."), caller);
    Set<String> ops =
        withoutDescriptors(callersOf(RENDER_SYSTEM + ".logicOp(L" + LOGIC_OP + ";)V"));
    assertEquals(enablers, ops);
    int setups = 0;
    for (MethodNode candidate : shard().methods) {
      if (!invokes(candidate, RENDER_SYSTEM, "logicOp", null)) continue;
      setups++;
      assertTrue(readsStatic(candidate, LOGIC_OP, "OR_REVERSE"), candidate.name);
    }
    assertEquals(1, setups);
    assertEquals(0x150B, GlStateManager.LogicOp.OR_REVERSE.value);
    assertTrue(
        readsStatic(method(RENDER_TYPE, "<clinit>", "()V"), RENDER_TYPE, "OR_REVERSE_COLOR_LOGIC"),
        "RenderType.GUI_TEXT_HIGHLIGHT");
  }

  private static ClassNode shard() throws IOException {
    try (InputStream input = Options.class.getClassLoader().getResourceAsStream(SHARD + ".class")) {
      assertNotNull(input, SHARD);
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
      return node;
    }
  }

  @Test
  void editBoxSelectionAndCreativeSearchDrawTheHighlightRenderType() {
    assertTrue(
        invokes(
            method("net/minecraft/client/gui/components/EditBox", "renderHighlight", null),
            RENDER_TYPE,
            "guiTextHighlight",
            null));
    assertTrue(
        invokes(
            method("net/minecraft/client/gui/components/MultiLineEditBox", "renderHighlight", null),
            RENDER_TYPE,
            "guiTextHighlight",
            null));
  }

  @Test
  void orReverseIsTranslatedWhileOtherLogicOpsStayFatal() throws IOException {
    Path java = Path.of("src", "main", "java", "net", "dx12mc");
    String state = Files.readString(java.resolve("bridge/state/RenderState.java"));
    assertTrue(state.contains("colorLogic && logicOp != GL_OR_REVERSE"));
    String pipeline = Files.readString(java.resolve("graphics/pipeline/ShaderPipeline.java"));
    assertTrue(
        pipeline.contains("bgfx_submit(ViewSequence.current(), handle, 0, BGFX_DISCARD_NONE)"));
  }
}
