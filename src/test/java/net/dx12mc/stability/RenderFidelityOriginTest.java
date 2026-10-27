package net.dx12mc.stability;

import static net.dx12mc.stability.VideoSettingsOriginTest.callersOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.indexOf;
import static net.dx12mc.stability.VideoSettingsOriginTest.invokes;
import static net.dx12mc.stability.VideoSettingsOriginTest.method;
import static net.dx12mc.stability.VideoSettingsOriginTest.readsStatic;
import static net.dx12mc.stability.VideoSettingsOriginTest.withoutDescriptors;
import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodNode;

class RenderFidelityOriginTest {
  private static final Path JAVA = Path.of("src", "main", "java", "net", "dx12mc");
  private static final String GAME_RENDERER = "net/minecraft/client/renderer/GameRenderer";
  private static final String LEVEL_RENDERER = "net/minecraft/client/renderer/LevelRenderer";
  private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
  private static final String GL_STATE = "com/mojang/blaze3d/platform/GlStateManager";
  private static final String RENDER_TARGET = "com/mojang/blaze3d/pipeline/RenderTarget";
  private static final String POST_CHAIN = "net/minecraft/client/renderer/PostChain";

  @Test
  void handAndGuiDepthClearsReachTheGlClearDx12mcTranslates() throws IOException {
    MethodNode renderLevel = method(GAME_RENDERER, "renderLevel", null);
    int clear = indexOf(renderLevel, RENDER_SYSTEM, "clear");
    int hand = indexOf(renderLevel, GAME_RENDERER, "renderItemInHand");
    assertTrue(clear >= 0 && hand > clear, "vanilla clears depth right before the hand");
    MethodNode render = method(GAME_RENDERER, "render", null);
    int guiClear = indexOf(render, RENDER_SYSTEM, "clear");
    int gui = indexOf(render, "net/minecraft/client/gui/Gui", "render");
    assertTrue(guiClear >= 0 && gui > guiClear, "vanilla clears depth before the GUI");
    assertTrue(invokes(method(RENDER_SYSTEM, "clear", "(IZ)V"), GL_STATE, "_clear", "(IZ)V"));
    String mixin = read("mixin/GlStateManagerMixin.java");
    assertTrue(mixin.contains("method = \"_clear(IZ)V\""));
    assertTrue(mixin.contains("if (DX12MC.runtime().clear(mask)) ci.cancel();"));
    assertTrue(read("render/RendererLifecycle.java").contains("backend.clear(mask, rgba, depth)"));
    assertTrue(read("graphics/pipeline/ShaderPipeline.java").contains("ViewSequence.current()"));
    assertFalse(read("graphics/pipeline/ShaderPipeline.java").contains("Backend.MAIN_VIEW"));
  }

  @Test
  void levelClearUsesTheFogColourVanillaSetsAsClearColour() throws IOException {
    MethodNode renderLevel = method(LEVEL_RENDERER, "renderLevel", null);
    int setupColor =
        indexOf(renderLevel, "net/minecraft/client/renderer/FogRenderer", "setupColor");
    int clear = indexOf(renderLevel, RENDER_SYSTEM, "clear");
    assertTrue(setupColor >= 0 && clear > setupColor);
    assertTrue(
        invokes(
            method("net/minecraft/client/renderer/FogRenderer", "setupColor", null),
            RENDER_SYSTEM,
            "clearColor",
            "(FFFF)V"));
    assertTrue(
        invokes(method(RENDER_SYSTEM, "clearColor", "(FFFF)V"), GL_STATE, "_clearColor", null));
    String mixin = read("mixin/GlStateManagerMixin.java");
    assertTrue(mixin.contains("method = \"_clearColor(FFFF)V\""));
    assertTrue(mixin.contains("method = \"_clearDepth(D)V\""));
  }

  @Test
  void samplerStateIsTheGlTextureParameterVanillaSets() throws IOException {
    assertTrue(
        invokes(
            method("net/minecraft/client/renderer/LightTexture", "turnOnLightLayer", "()V"),
            RENDER_SYSTEM,
            "texParameter",
            "(III)V"));
    assertTrue(
        invokes(
            method(RENDER_SYSTEM, "texParameter", "(III)V"), GL_STATE, "_texParameter", "(III)V"));
    assertTrue(
        invokes(
            method("com/mojang/blaze3d/platform/NativeImage", "setFilter", "(ZZ)V"),
            GL_STATE,
            "_texParameter",
            "(III)V"));
    assertTrue(
        invokes(
            method(
                "com/mojang/blaze3d/platform/TextureUtil",
                "prepareImage",
                "(Lcom/mojang/blaze3d/platform/NativeImage$InternalGlFormat;IIII)V"),
            GL_STATE,
            "_texParameter",
            null));
    String mixin = read("mixin/GlStateManagerMixin.java");
    assertTrue(mixin.contains("method = \"_texParameter(III)V\""));
    assertTrue(mixin.contains("method = \"_texParameter(IIF)V\""));
    String entry = read("bridge/texture/TextureEntry.java");
    assertTrue(
        entry.contains("texture.sampler(sampler.bgfxFlags(), sampler.maxLod(image.maxLevel()))"));
    assertFalse(read("graphics/bgfx/Texture.java").contains("boolean blur"));
  }

  @Test
  void fogInputsAreTheRenderSystemFogStateVanillaShadersRead() throws IOException {
    MethodNode setupFog = method("net/minecraft/client/renderer/FogRenderer", "setupFog", null);
    assertTrue(invokes(setupFog, RENDER_SYSTEM, "setShaderFogStart", "(F)V"));
    assertTrue(invokes(setupFog, RENDER_SYSTEM, "setShaderFogEnd", "(F)V"));
    assertTrue(invokes(setupFog, RENDER_SYSTEM, "setShaderFogShape", null));
    MethodNode defaults =
        method("net/minecraft/client/renderer/ShaderInstance", "setDefaultUniforms", null);
    assertTrue(invokes(defaults, RENDER_SYSTEM, "getShaderFogStart", "()F"));
    assertTrue(invokes(defaults, RENDER_SYSTEM, "getShaderFogEnd", "()F"));
    assertTrue(invokes(defaults, RENDER_SYSTEM, "getShaderFogColor", "()[F"));
    assertTrue(invokes(defaults, RENDER_SYSTEM, "getShaderFogShape", null));
    String factory = read("bridge/DrawContextFactory.java");
    assertTrue(factory.contains("RenderSystem.getShaderFogStart()"));
    assertTrue(factory.contains("RenderSystem.getShaderFogEnd()"));
    assertTrue(factory.contains("RenderSystem.getShaderFogShape().getIndex()"));
    assertTrue(factory.contains("RenderSystem.getShaderFogColor()"));
  }

  @Test
  void entityLightingReadsTheLightDirectionsVanillaUploads() throws IOException {
    assertTrue(
        invokes(
            method("com/mojang/blaze3d/platform/Lighting", "setupFor3DItems", "()V"),
            RENDER_SYSTEM,
            "setupGui3DDiffuseLighting",
            null));
    assertTrue(
        invokes(
            method(GL_STATE, "setupGui3DDiffuseLighting", null),
            GL_STATE,
            "setupLevelDiffuseLighting",
            null));
    assertTrue(
        invokes(
            method(
                GL_STATE,
                "setupLevelDiffuseLighting",
                "(Lorg/joml/Vector3f;Lorg/joml/Vector3f;Lorg/joml/Matrix4f;)V"),
            RENDER_SYSTEM,
            "setShaderLights",
            null));
    assertTrue(
        readsStatic(
            method(RENDER_SYSTEM, "setupShaderLights", null),
            RENDER_SYSTEM,
            "shaderLightDirections"));
    assertTrue(
        invokes(
            method(
                "net/minecraft/client/renderer/texture/OverlayTexture", "setupOverlayColor", "()V"),
            RENDER_SYSTEM,
            "setupOverlayColor",
            "(II)V"));
    assertTrue(
        invokes(
            method(RENDER_SYSTEM, "setupOverlayColor", "(II)V"),
            RENDER_SYSTEM,
            "setShaderTexture",
            "(II)V"));
    assertTrue(read("mixin/RenderSystemAccessor.java").contains("\"shaderLightDirections\""));
    assertTrue(read("bridge/DrawContextFactory.java").contains("RenderSystem.getShaderTexture(1)"));
  }

  @Test
  void offscreenTargetDrawsAndPostChainsRunOnNativeTargets() throws IOException {
    assertTrue(
        withoutDescriptors(callersOf(RENDER_TARGET + ".bindWrite(Z)V"))
            .contains("net/minecraft/client/renderer/RenderStateShard.<clinit>"),
        "RenderStateShard output targets bind offscreen render targets");
    assertTrue(
        withoutDescriptors(callersOf(POST_CHAIN + ".process(F)V"))
            .contains(GAME_RENDERER + ".render"));
    assertTrue(
        withoutDescriptors(callersOf(POST_CHAIN + ".process(F)V"))
            .contains(LEVEL_RENDERER + ".renderLevel"));
    assertTrue(
        withoutDescriptors(callersOf(RENDER_TARGET + ".blitToScreen(IIZ)V"))
            .contains(LEVEL_RENDERER + ".doEntityOutline"),
        "the entity outline target is composited by RenderTarget.blitToScreen");
    String target = read("mixin/RenderTargetMixin.java");
    assertTrue(target.contains("DX12MC.runtime().bindRenderTarget((RenderTarget) (Object) this)"));
    assertTrue(target.contains("DX12MC.runtime().bindRenderTarget(null)"));
    assertTrue(target.contains("releaseRenderTarget((RenderTarget) (Object) this)"));
    assertTrue(target.contains("blitRenderTarget((RenderTarget) (Object) this, disableBlend)"));
    assertTrue(target.contains("(Object) this == minecraft.getMainRenderTarget()"));
    String chain = read("mixin/PostChainMixin.java");
    assertTrue(chain.contains("method = \"process(F)V\""));
    assertTrue(chain.contains("processPostChain((PostChain) (Object) this, partialTick)"));
    String controller = read("render/Controller.java");
    assertFalse(controller.contains("offscreenTargetBound"));
    assertTrue(controller.contains("private void syncTarget()"));
    assertTrue(controller.contains("deferClear(boundTarget, mask)"));
  }

  @Test
  void unicodeGlyphTexturesAreRedChannelTexturesSampledWithIntensity() throws IOException {
    assertEquals(0x1903, NativeImage.Format.LUMINANCE.glFormat(), "LUMINANCE uploads as GL_RED");
    assertTrue(
        invokes(
            method("net/minecraft/client/gui/font/FontSet", "stitch", null),
            "net/minecraft/client/gui/font/GlyphRenderTypes",
            "createForIntensityTexture",
            null));
    assertTrue(read("bridge/texture/Pixels.java").contains("writeRedTexel(target, offset"));
  }

  @Test
  void everyNewMixinTargetExistsInTheMappedClasses() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    Class<?> gl = Class.forName("com.mojang.blaze3d.platform.GlStateManager", false, loader);
    assertNotNull(gl.getDeclaredMethod("_clear", int.class, boolean.class));
    assertNotNull(
        gl.getDeclaredMethod("_clearColor", float.class, float.class, float.class, float.class));
    assertNotNull(gl.getDeclaredMethod("_clearDepth", double.class));
    assertNotNull(gl.getDeclaredMethod("_texParameter", int.class, int.class, int.class));
    assertNotNull(gl.getDeclaredMethod("_texParameter", int.class, int.class, float.class));
    Class<?> post = Class.forName("net.minecraft.client.renderer.PostChain", false, loader);
    assertNotNull(post.getDeclaredMethod("process", float.class));
    Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem", false, loader);
    assertEquals(
        org.joml.Vector3f[].class,
        renderSystem.getDeclaredField("shaderLightDirections").getType());
    Class<?> target = Class.forName("com.mojang.blaze3d.pipeline.RenderTarget", false, loader);
    assertNotNull(target.getDeclaredMethod("bindWrite", boolean.class));
    assertNotNull(target.getDeclaredMethod("unbindWrite"));
  }

  private static String read(String relative) throws IOException {
    return Files.readString(JAVA.resolve(relative)).replace("\r\n", "\n");
  }
}
