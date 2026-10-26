package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.minecraft.client.Options;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class VideoSettingsOriginTest {
  private static final Path JAVA = Path.of("src", "main", "java", "net", "dx12mc");
  private static final String OPTIONS = "net/minecraft/client/Options";
  private static final String WINDOW = "com/mojang/blaze3d/platform/Window";
  private static final String LEVEL_RENDERER = "net/minecraft/client/renderer/LevelRenderer";
  private static final String MINECRAFT = "net/minecraft/client/Minecraft";
  private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
  private static final String GL_UTIL = "com/mojang/blaze3d/platform/GlUtil";
  private static final String TIMER_QUERY = "com/mojang/blaze3d/systems/TimerQuery";
  private static final String FRAME_PROFILE = "com/mojang/blaze3d/systems/TimerQuery$FrameProfile";
  private static final Set<String> OPENGL_ONLY_CLASSES =
      Set.of(
          "com/mojang/blaze3d/platform/GlStateManager",
          GL_UTIL,
          "com/mojang/blaze3d/platform/GLX",
          TIMER_QUERY,
          FRAME_PROFILE,
          "com/mojang/blaze3d/pipeline/RenderTarget",
          "com/mojang/blaze3d/shaders/Uniform",
          "net/minecraft/client/renderer/ShaderInstance",
          "net/minecraft/client/renderer/PostChain",
          "net/minecraft/client/renderer/PostPass");
  private static Map<String, Set<String>> callers;

  @TestFactory
  Stream<DynamicTest> everyVideoOptionReachesItsApplierThroughItsOwnButtonPath() {
    return Stream.of(
        option("biomeBlendRadius", "options.biomeBlendRadius", LEVEL_RENDERER, "allChanged", "()V"),
        option("ambientOcclusion", "options.ao", LEVEL_RENDERER, "allChanged", "()V"),
        option("renderDistance", "options.renderDistance", LEVEL_RENDERER, "needsUpdate", "()V"),
        option("framerateLimit", "options.framerateLimit", WINDOW, "setFramerateLimit", "(I)V"),
        option("enableVsync", "options.vsync", WINDOW, "updateVsync", "(Z)V"),
        option("fullscreen", "options.fullscreen", WINDOW, "toggleFullScreen", "()V"),
        option("guiScale", "options.guiScale", MINECRAFT, "resizeDisplay", "()V"),
        option(
            "glintStrength", "options.glintStrength", RENDER_SYSTEM, "setShaderGlintAlpha", "(D)V"),
        option("cloudStatus", "options.renderClouds", MINECRAFT, "useShaderTransparency", "()Z"),
        noApplier("prioritizeChunkUpdates", "options.prioritizeChunkUpdates"),
        noApplier("simulationDistance", "options.simulationDistance"),
        noApplier("bobView", "options.viewBobbing"),
        noApplier("attackIndicator", "options.attackIndicator"),
        noApplier("gamma", "options.gamma"),
        noApplier("particles", "options.particles"),
        noApplier("mipmapLevels", "options.mipmapLevels"),
        noApplier("entityShadows", "options.entityShadows"),
        noApplier("screenEffectScale", "options.screenEffectScale"),
        noApplier("entityDistanceScaling", "options.entityDistanceScaling"),
        noApplier("fovEffectScale", "options.fovEffectScale"),
        noApplier("showAutosaveIndicator", "options.autosaveIndicator"),
        noApplier("glintSpeed", "options.glintSpeed"),
        noApplier("menuBackgroundBlurriness", "options.accessibility.menu_background_blurriness"));
  }

  @TestFactory
  Stream<DynamicTest> videoOptionValuesAreOnlyReadOutsideTheReplacedOpenGlBackend() {
    return Stream.of(
            "biomeBlendRadius",
            "ambientOcclusion",
            "renderDistance",
            "prioritizeChunkUpdates",
            "simulationDistance",
            "framerateLimit",
            "enableVsync",
            "bobView",
            "guiScale",
            "attackIndicator",
            "gamma",
            "cloudStatus",
            "fullscreen",
            "particles",
            "mipmapLevels",
            "entityShadows",
            "screenEffectScale",
            "entityDistanceScaling",
            "fovEffectScale",
            "showAutosaveIndicator",
            "glintSpeed",
            "glintStrength",
            "menuBackgroundBlurriness",
            "graphicsMode")
        .map(
            accessor ->
                DynamicTest.dynamicTest(
                    accessor,
                    () -> {
                      Set<String> readers =
                          callersOf(
                              OPTIONS + "." + accessor + "()Lnet/minecraft/client/OptionInstance;");
                      assertFalse(readers.isEmpty(), accessor + " has no reader");
                      for (String reader : readers) {
                        String owner = reader.substring(0, reader.indexOf('.'));
                        assertFalse(
                            OPENGL_ONLY_CLASSES.contains(owner),
                            accessor + " is read by OpenGL-only code " + reader);
                      }
                    }));
  }

  @Test
  void vsyncButtonDrivesTheBgfxResetFlagsThroughWindowUpdateVsync() throws IOException {
    assertEquals(
        1, count(method(WINDOW, "updateVsync", "(Z)V"), "org/lwjgl/glfw/GLFW", "glfwSwapInterval"));
    assertTrue(invokes(method(MINECRAFT, "<init>", null), WINDOW, "updateVsync", "(Z)V"));
    assertTrue(invokes(method(WINDOW, "updateFullscreen", "(Z)V"), WINDOW, "updateVsync", "(Z)V"));
    String window = read("mixin/WindowMixin.java");
    assertTrue(window.contains("method = \"updateVsync(Z)V\""));
    assertTrue(window.contains("target = \"Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V\""));
    assertTrue(window.contains("DX12MC.runtime().vsync(interval != 0)"));
    assertTrue(read("client/Runtime.java").contains("controller.vsync(enabled)"));
    assertTrue(read("render/Controller.java").contains("lifecycle.vsync(enabled)"));
    assertTrue(read("render/RendererLifecycle.java").contains("backend.vsync(enabled)"));
    String backend = read("graphics/bgfx/Backend.java");
    assertTrue(backend.contains("swapChain.vsync(enabled)"));
    assertTrue(backend.contains(".reset(swapChain.resetFlags())"));
    assertTrue(backend.contains("bgfx_reset(size.width(), size.height(), swapChain.resetFlags()"));
    String config = read("config/Config.java");
    assertFalse(config.contains("vsync"));
  }

  @Test
  void maxFramerateIsAppliedAfterTheD3D12PresentInsideUpdateDisplay() {
    MethodNode runTick = method(MINECRAFT, "runTick", "(Z)V");
    int present = indexOf(runTick, WINDOW, "updateDisplay");
    int limit = indexOf(runTick, RENDER_SYSTEM, "limitDisplayFPS");
    assertTrue(present >= 0 && limit > present);
    assertTrue(invokes(method(WINDOW, "updateDisplay", "()V"), RENDER_SYSTEM, "flipFrame", "(J)V"));
  }

  @Test
  void graphicsButtonValueSetIsTheOneOptionsMixinModifies() throws IOException {
    MethodNode init = method(OPTIONS, "<init>", null);
    String altEnum =
        "(Ljava/util/List;Ljava/util/List;Ljava/util/function/BooleanSupplier;"
            + "Lnet/minecraft/client/OptionInstance$CycleableValueSet$ValueSetter;"
            + "Lcom/mojang/serialization/Codec;)V";
    assertEquals(1, count(init, "net/minecraft/client/OptionInstance$AltEnum", "<init>"));
    assertTrue(invokes(init, "net/minecraft/client/OptionInstance$AltEnum", "<init>", altEnum));
    String mixin = read("mixin/OptionsMixin.java");
    assertTrue(mixin.contains("OptionInstance$AltEnum;<init>" + altEnum));
    assertTrue(mixin.contains("index = 0"));
    assertTrue(mixin.contains("legacyPostProcessingAvailable()"));
    assertTrue(
        invokes(
            method(LEVEL_RENDERER, "allChanged", "()V"), LEVEL_RENDERER, "graphicsChanged", "()V"));
    assertTrue(
        invokes(
            method(LEVEL_RENDERER, "graphicsChanged", "()V"),
            MINECRAFT,
            "useShaderTransparency",
            "()Z"));
    assertTrue(
        invokes(
            method("com/mojang/blaze3d/pipeline/RenderTarget", "copyDepthFrom", null),
            "com/mojang/blaze3d/platform/GlStateManager",
            "_glBlitFrameBuffer",
            "(IIIIIIIIII)V"));
  }

  @Test
  void mipmapLevelsReachTheTextureMirrorAsTheAuthoritativeMipCount() throws IOException {
    MethodNode removed =
        method("net/minecraft/client/gui/screens/options/VideoSettingsScreen", "removed", "()V");
    assertTrue(invokes(removed, MINECRAFT, "updateMaxMipLevel", "(I)V"));
    assertTrue(invokes(removed, MINECRAFT, "delayTextureReload", null));
    assertTrue(
        invokes(
            method("net/minecraft/client/renderer/texture/TextureAtlas", "upload", null),
            "com/mojang/blaze3d/platform/TextureUtil",
            "prepareImage",
            "(IIII)V"));
    String textureUtil = read("mixin/TextureUtilMixin.java");
    assertTrue(textureUtil.contains("prepareImage(IIII)V"));
    assertTrue(textureUtil.contains("allocateTexture(id, maxLevel, width, height)"));
    String registry = read("bridge/texture/Registry.java");
    assertTrue(registry.contains("setAuthoritativeMaxLevel(existing, maxLevel)"));
    assertTrue(registry.contains("entry.image.setMaxLevel(maxLevel)"));
  }

  @Test
  void menuBlurButtonAndItsOnlyConsumerShareOnePredicate() throws IOException {
    assertEquals(
        Set.of("net/minecraft/client/renderer/GameRenderer.processBlurEffect(F)V"),
        callersOf(OPTIONS + ".getMenuBackgroundBlurriness()I"));
    String gameRenderer = read("mixin/GameRendererMixin.java");
    assertTrue(gameRenderer.contains("processBlurEffect(F)V"));
    assertTrue(gameRenderer.contains("suppressLegacyBlurPostProcess()"));
    String controller = read("render/Controller.java");
    int start = controller.indexOf("public boolean suppressLegacyBlurPostProcess()");
    assertTrue(
        controller
            .substring(start, controller.indexOf('}', start))
            .contains("config.legacyPostProcessingAvailable()"));
    String button = read("mixin/OptionInstanceMixin.java");
    assertTrue(button.contains("DX12MC.runtime().legacyPostProcessingAvailable()"));
    assertTrue(button.contains("button.active = false"));
  }

  @Test
  void glintStrengthHadOnlyTheOpenGlUniformAsConsumerAndNowFeedsTheD3D12Tint() throws IOException {
    assertEquals(
        Set.of("net/minecraft/client/renderer/ShaderInstance.setDefaultUniforms"),
        withoutDescriptors(callersOf(RENDER_SYSTEM + ".getShaderGlintAlpha()F")));
    String drawContext = read("bridge/DrawContextFactory.java");
    assertTrue(
        drawContext.contains(
            "if (classifiedShaderGlint) applyGlintAlpha(RenderSystem.getShaderGlintAlpha());"));
  }

  @Test
  void f3RendererLinesAndWarnlistReadTheGlUtilQueriesDx12mcAnswers() throws IOException {
    for (String query : List.of("getVendor", "getRenderer", "getOpenGLVersion")) {
      assertTrue(
          invokes(
              method(GL_UTIL, query, "()Ljava/lang/String;"),
              "com/mojang/blaze3d/platform/GlStateManager",
              "_getString",
              "(I)Ljava/lang/String;"),
          query + " is no longer a GL query");
      Set<String> readers =
          withoutDescriptors(callersOf(GL_UTIL + "." + query + "()Ljava/lang/String;"));
      assertEquals(
          Set.of(
              "net/minecraft/client/gui/components/DebugScreenOverlay.getSystemInformation",
              "net/minecraft/client/renderer/GpuWarnlistManager$Preparations.apply"),
          readers,
          query);
    }
    String glUtil = read("mixin/GlUtilMixin.java");
    assertTrue(glUtil.contains("getVendor()Ljava/lang/String;"));
    assertTrue(glUtil.contains("getRenderer()Ljava/lang/String;"));
    assertTrue(glUtil.contains("getOpenGLVersion()Ljava/lang/String;"));
    assertTrue(glUtil.contains("DX12MC.runtime().rendererInfo()"));
  }

  @Test
  void crashReportApiDescriptionIsRewrittenWhenD3D12TakesOver() throws IOException {
    MethodNode getter = method(RENDER_SYSTEM, "getApiDescription", "()Ljava/lang/String;");
    assertTrue(readsStatic(getter, RENDER_SYSTEM, "apiDescription"));
    assertTrue(
        writesStatic(
            method(RENDER_SYSTEM, "initRenderer", "(IZ)V"), RENDER_SYSTEM, "apiDescription"));
    String lifecycle = read("render/RendererLifecycle.java");
    int active = lifecycle.indexOf("state = State.ACTIVE;\n    RenderSystemAccessor");
    assertTrue(active > 0);
    assertTrue(
        lifecycle.contains(
            "RenderSystemAccessor.dx12mc$setApiDescription(rendererInfo.apiDescription());"));
    assertTrue(read("mixin/RenderSystemAccessor.java").contains("\"apiDescription\""));
  }

  @Test
  void gpuUtilizationTimerProfilesAreOnlyDrivenByMinecraftRunTick() throws IOException {
    assertEquals(
        Set.of(MINECRAFT + ".runTick"),
        withoutDescriptors(callersOf(TIMER_QUERY + ".beginProfile()V")));
    assertEquals(
        Set.of(MINECRAFT + ".runTick"),
        withoutDescriptors(callersOf(TIMER_QUERY + ".endProfile()L" + FRAME_PROFILE + ";")));
    assertTrue(
        withoutDescriptors(callersOf(FRAME_PROFILE + ".get()J")).contains(MINECRAFT + ".runTick"));
    String timer = read("mixin/TimerQueryMixin.java") + read("mixin/FrameProfileMixin.java");
    assertTrue(timer.contains("DX12MC.runtime().ownsGpuTiming()"));
    assertTrue(timer.contains("DX12MC.runtime().gpuFrameNanos()"));
    assertTrue(read("graphics/bgfx/Backend.java").contains("BGFXStats.ngpuTimeEnd(stats)"));
  }

  @Test
  void everyNewMixinTargetExistsInTheMappedMinecraftClasses() throws Exception {
    ClassLoader loader = Options.class.getClassLoader();
    Class<?> window = Class.forName("com.mojang.blaze3d.platform.Window", false, loader);
    assertNotNull(window.getDeclaredMethod("updateVsync", boolean.class));
    Class<?> glUtil = Class.forName("com.mojang.blaze3d.platform.GlUtil", false, loader);
    assertNotNull(glUtil.getDeclaredMethod("getVendor"));
    assertNotNull(glUtil.getDeclaredMethod("getRenderer"));
    assertNotNull(glUtil.getDeclaredMethod("getOpenGLVersion"));
    Class<?> timer = Class.forName("com.mojang.blaze3d.systems.TimerQuery", false, loader);
    assertNotNull(timer.getDeclaredMethod("beginProfile"));
    assertNotNull(timer.getDeclaredMethod("endProfile"));
    assertEquals(int.class, timer.getDeclaredField("nextQueryName").getType());
    Class<?> profile =
        Class.forName("com.mojang.blaze3d.systems.TimerQuery$FrameProfile", false, loader);
    assertNotNull(profile.getDeclaredConstructor(int.class));
    assertNotNull(profile.getDeclaredMethod("isDone"));
    assertNotNull(profile.getDeclaredMethod("get"));
    assertNotNull(profile.getDeclaredMethod("cancel"));
    assertEquals(int.class, profile.getDeclaredField("queryName").getType());
    assertEquals(long.class, profile.getDeclaredField("result").getType());
    Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem", false, loader);
    assertEquals(String.class, renderSystem.getDeclaredField("apiDescription").getType());
    Class<?> optionInstance = Class.forName("net.minecraft.client.OptionInstance", false, loader);
    assertNotNull(
        optionInstance.getDeclaredMethod(
            "createButton", Options.class, int.class, int.class, int.class, Consumer.class));
  }

  @Test
  void noMixinReplacesTheOutputOfAUiFormattingMethod() throws IOException {
    List<String> forbiddenTargets =
        List.of(
            "@Mixin(DebugScreenOverlay.class)",
            "@Mixin(Font.class)",
            "@Mixin(GuiGraphics.class)",
            "@Mixin(OptionsList.class)",
            "@Mixin(CycleButton.class)",
            "getGameInformation",
            "getSystemInformation",
            "drawGameInformation",
            "drawSystemInformation",
            "fpsString",
            "genericValueLabel",
            "percentValueLabel",
            "updateMessage",
            "getMessage",
            "Ljava/lang/String;format",
            "Ljava/util/List;add",
            "ModifyReturnValue");
    try (Stream<Path> files = Files.list(JAVA.resolve("mixin"))) {
      for (Path file : files.toList()) {
        String source = Files.readString(file);
        for (String forbidden : forbiddenTargets) {
          assertFalse(
              source.contains(forbidden),
              file.getFileName() + " targets UI formatting through " + forbidden);
        }
      }
    }
  }

  private static DynamicTest option(
      String field, String caption, String owner, String name, String descriptor) {
    return DynamicTest.dynamicTest(
        field,
        () -> {
          Handle callback = callback(field, caption);
          assertNotNull(callback, field + " has no value callback");
          assertTrue(
              reaches(callback, owner, name, descriptor),
              field + " callback no longer reaches " + owner + "." + name + descriptor);
        });
  }

  private static DynamicTest noApplier(String field, String caption) {
    return DynamicTest.dynamicTest(
        field,
        () -> {
          Handle callback = callback(field, caption);
          if (callback == null) return;
          MethodNode lambda = method(OPTIONS, callback.getName(), callback.getDesc());
          for (AbstractInsnNode insn : lambda.instructions) {
            if (insn instanceof MethodInsnNode call) {
              fail(field + " callback now applies " + call.owner + "." + call.name + call.desc);
            }
          }
        });
  }

  private static Handle callback(String field, String caption) {
    MethodNode init = method(OPTIONS, "<init>", null);
    AbstractInsnNode put = null;
    for (AbstractInsnNode insn : init.instructions) {
      if (insn instanceof FieldInsnNode f
          && f.getOpcode() == Opcodes.PUTFIELD
          && f.owner.equals(OPTIONS)
          && f.name.equals(field)) {
        put = insn;
        break;
      }
    }
    assertNotNull(put, field + " is no longer assigned in Options.<init>");
    boolean captionSeen = false;
    Handle consumer = null;
    for (AbstractInsnNode insn = put.getPrevious(); insn != null; insn = insn.getPrevious()) {
      if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD) break;
      if (consumer == null
          && insn instanceof InvokeDynamicInsnNode indy
          && indy.desc.endsWith(")Ljava/util/function/Consumer;")) {
        consumer = (Handle) indy.bsmArgs[1];
      }
      if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && caption.equals(ldc.cst)) {
        captionSeen = true;
      }
    }
    assertTrue(captionSeen, field + " button caption is no longer " + caption);
    return consumer;
  }

  private static boolean reaches(Handle callback, String owner, String name, String descriptor) {
    if (callback.getOwner().equals(owner)
        && callback.getName().equals(name)
        && callback.getDesc().equals(descriptor)) return true;
    if (!callback.getOwner().equals(OPTIONS)) return false;
    return invokes(
        method(OPTIONS, callback.getName(), callback.getDesc()), owner, name, descriptor);
  }

  private static boolean invokes(MethodNode method, String owner, String name, String descriptor) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof MethodInsnNode call
          && call.owner.equals(owner)
          && call.name.equals(name)
          && (descriptor == null || call.desc.equals(descriptor))) return true;
    }
    return false;
  }

  private static int count(MethodNode method, String owner, String name) {
    int count = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name))
        count++;
    }
    return count;
  }

  private static int indexOf(MethodNode method, String owner, String name) {
    int index = 0;
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name))
        return index;
      index++;
    }
    return -1;
  }

  private static boolean readsStatic(MethodNode method, String owner, String field) {
    return accessesStatic(method, owner, field, Opcodes.GETSTATIC);
  }

  private static boolean writesStatic(MethodNode method, String owner, String field) {
    return accessesStatic(method, owner, field, Opcodes.PUTSTATIC);
  }

  private static boolean accessesStatic(MethodNode method, String owner, String field, int opcode) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof FieldInsnNode f
          && f.getOpcode() == opcode
          && f.owner.equals(owner)
          && f.name.equals(field)) return true;
    }
    return false;
  }

  private static MethodNode method(String owner, String name, String descriptor) {
    ClassNode node = classNode(owner);
    List<MethodNode> matches = new ArrayList<>();
    for (MethodNode method : node.methods) {
      if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor)))
        matches.add(method);
    }
    assertEquals(1, matches.size(), owner + "." + name + (descriptor == null ? "" : descriptor));
    return matches.get(0);
  }

  private static ClassNode classNode(String internalName) {
    try (InputStream input =
        Options.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
      assertNotNull(input, internalName);
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
      return node;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static Set<String> withoutDescriptors(Set<String> callers) {
    Set<String> names = new TreeSet<>();
    for (String caller : callers) names.add(caller.substring(0, caller.indexOf('(')));
    return names;
  }

  private static synchronized Set<String> callersOf(String target) {
    if (callers == null) callers = indexMinecraftCalls();
    return callers.getOrDefault(target, Set.of());
  }

  private static Map<String, Set<String>> indexMinecraftCalls() {
    Map<String, Set<String>> index = new HashMap<>();
    Map<String, String> lambdaParents = new HashMap<>();
    Path jar;
    try {
      jar = Path.of(Options.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
    try (JarFile file = new JarFile(jar.toFile())) {
      Enumeration<JarEntry> entries = file.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!name.endsWith(".class")
            || !(name.startsWith("net/minecraft/") || name.startsWith("com/mojang/"))) continue;
        try (InputStream input = file.getInputStream(entry)) {
          new ClassReader(input)
              .accept(new CallIndexer(index, lambdaParents), ClassReader.SKIP_FRAMES);
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    Map<String, Set<String>> resolved = new HashMap<>();
    index.forEach(
        (target, sites) -> {
          Set<String> enclosing = new TreeSet<>();
          for (String site : sites) {
            String caller = site;
            while (lambdaParents.containsKey(caller)) caller = lambdaParents.get(caller);
            enclosing.add(caller);
          }
          resolved.put(target, enclosing);
        });
    return resolved;
  }

  private static String read(String relative) throws IOException {
    return Files.readString(JAVA.resolve(relative)).replace("\r\n", "\n");
  }

  private static final class CallIndexer extends ClassVisitor {
    private final Map<String, Set<String>> index;
    private final Map<String, String> lambdaParents;
    private final Map<String, String> pendingParents = new HashMap<>();
    private final Set<String> synthetic = new TreeSet<>();
    private String owner;

    CallIndexer(Map<String, Set<String>> index, Map<String, String> lambdaParents) {
      super(Opcodes.ASM9);
      this.index = index;
      this.lambdaParents = lambdaParents;
    }

    @Override
    public void visitEnd() {
      pendingParents.forEach(
          (lambda, parent) -> {
            if (synthetic.contains(lambda)) lambdaParents.put(lambda, parent);
          });
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      owner = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      String caller = owner + "." + name + descriptor;
      if ((access & Opcodes.ACC_SYNTHETIC) != 0) synthetic.add(caller);
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public void visitMethodInsn(
            int opcode, String target, String method, String desc, boolean isInterface) {
          record(target + "." + method + desc);
        }

        @Override
        public void visitInvokeDynamicInsn(
            String method, String desc, Handle bootstrap, Object... arguments) {
          for (Object argument : arguments) {
            if (argument instanceof Handle handle) {
              String target = handle.getOwner() + "." + handle.getName() + handle.getDesc();
              record(target);
              if (handle.getOwner().equals(owner)) pendingParents.put(target, caller);
            }
          }
        }

        private void record(String target) {
          index.computeIfAbsent(target, ignored -> new TreeSet<>()).add(caller);
        }
      };
    }
  }
}
