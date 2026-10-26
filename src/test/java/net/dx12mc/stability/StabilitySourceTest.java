package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StabilitySourceTest {
  private static final Path JAVA = Path.of("src", "main", "java", "net", "dx12mc");

  @Test
  void nativePresentationNeverFallsBackToGlfwSwap() throws IOException {
    String renderSystem = read("mixin/RenderSystemMixin.java");
    String controller = read("render/Controller.java");
    String config = read("config/Config.java");
    String window = read("mixin/WindowMixin.java");
    assertTrue(renderSystem.contains("@Redirect"));
    assertTrue(renderSystem.contains("method = \"flipFrame(J)V\""));
    assertTrue(renderSystem.contains("remap = false"));
    assertTrue(renderSystem.contains("GLFW;glfwSwapBuffers(J)V"));
    assertTrue(renderSystem.contains("openGlPresentationAllowed()"));
    assertTrue(renderSystem.contains("DX12MC.runtime().presentAtSwap(minecraft)"));
    assertFalse(renderSystem.contains("d3d12Presented"));
    assertFalse(renderSystem.contains("if (!d3d12"));
    assertFalse(controller.contains("falling back"));
    assertFalse(controller.contains("rendering remains available"));
    assertTrue(config.contains("return probeMode()"));
    assertFalse(window.contains("updateDisplay"));
  }

  @Test
  void sectionLayerFlushFailuresGoThroughTheFatalPath() throws IOException {
    String controller = read("render/Controller.java");
    int start = controller.indexOf("public void endSectionLayer()");
    int end = controller.indexOf("public boolean drawImmediate(", start);
    String body = controller.substring(start, end);
    assertTrue(body.contains("bridge.endSectionLayer();"));
    assertTrue(
        body.contains("throw fatal(\"Native D3D12 section-layer flush failed.\", throwable);"));
  }

  @Test
  void nativeDrawFailuresAreFatalInsteadOfReturningToOpenGl() throws IOException {
    String controller = read("render/Controller.java");
    String bridge = read("bridge/Bridge.java") + read("bridge/DrawContextFactory.java");
    String config = read("config/Config.java");
    assertTrue(controller.contains("STRICT D3D12 POLICY"));
    assertTrue(controller.contains("throw fatal("));
    assertTrue(controller.contains("stats.cancelledVanillaDraw()"));
    assertTrue(bridge.contains("UnsupportedRenderPathException"));
    assertTrue(bridge.contains("OpenGL draw fallback is disabled"));
    String glState = read("mixin/GlStateManagerMixin.java");
    assertTrue(glState.contains("_drawElements(IIIJ)V"));
    assertFalse(glState.contains("_drawArrays(III)V"));
    assertTrue(glState.contains("assertOpenGlDrawAllowed"));
    assertTrue(glState.contains("_getTexImage(IIIIJ)V"));
    assertTrue(glState.contains("_readPixels(IIIIIILjava/nio/ByteBuffer;)V"));
    assertTrue(glState.contains("_glCopyTexSubImage2D(IIIIIIII)V"));
    assertTrue(glState.contains("_glBlitFrameBuffer(IIIIIIIIII)V"));
    assertTrue(glState.contains("assertOpenGlReadbackAllowed"));
    assertTrue(controller.contains("Raw OpenGL draw escaped the D3D12 bridge after takeover"));
    assertFalse(config.contains("dx12mc.bridge.cancel"));
    assertFalse(config.contains("dx12mc.present.exclusive"));
  }

  @Test
  void immediateMeshDataPathsAreCoveredWhenGlSwapIsSuppressed() throws IOException {
    String uploader = read("mixin/BufferUploaderMixin.java");
    assertTrue(uploader.contains("method = \"drawWithShader\""));
    assertTrue(uploader.contains("method = \"draw\""));
    assertTrue(uploader.contains("drawImmediate(mesh)"));
    assertTrue(uploader.contains("mesh.close()"));
    assertTrue(uploader.contains("ci.cancel()"));
  }

  @Test
  void vanillaFramebufferScreenBlitIsSuppressedInsteadOfBecomingARawGlDraw() throws IOException {
    String target = read("mixin/RenderTargetMixin.java");
    String controller = read("render/Controller.java");
    String mixins = Files.readString(Path.of("src", "main", "resources", "dx12mc.mixins.json"));
    assertTrue(target.contains("@Mixin(RenderTarget.class)"));
    assertTrue(target.contains("blitToScreen(IIZ)V"));
    assertTrue(target.contains("bindWrite(Z)V"));
    assertTrue(target.contains("unbindWrite()V"));
    assertTrue(target.contains("clear(Z)V"));
    assertTrue(target.contains("suppressVanillaFramebufferBlit()"));
    assertTrue(target.contains("suppressLegacyOpenGlDriverWork()"));
    assertTrue(target.contains("ci.cancel()"));
    assertTrue(controller.contains("state != State.ACTIVE"));
    assertTrue(controller.contains("suppressedOpenGlFramebufferBlit()"));
    assertTrue(mixins.contains("RenderTargetMixin"));
    assertFalse(target.contains("glReadPixels"));
    assertFalse(controller.contains("glReadPixels"));
  }

  @Test
  void nativeStagingIsBoundedAndSparseTextureUpdatesStaySparse() throws IOException {
    String texture = read("graphics/bgfx/Texture.java");
    String registry =
        read("bridge/texture/Registry.java") + read("bridge/texture/TextureEntry.java");
    String image = read("bridge/texture/Image.java") + read("bridge/texture/MipLevel.java");
    String mesh = read("graphics/bgfx/Mesh.java");
    String program = read("graphics/bgfx/Program.java");
    String staging = read("graphics/bgfx/StagingMemory.java");
    String bridge = read("bridge/Bridge.java");
    assertTrue(texture.contains("bgfx_create_texture_2d"));
    assertTrue(texture.contains("bgfx_update_texture_2d"));
    assertTrue(texture.contains("StagingMemory.allocate(bytes"));
    assertTrue(texture.contains("target.put(rgba, sourceOffset, bytes)"));
    assertFalse(texture.contains("bgfx_alloc(rgba.length)"));
    assertTrue(staging.contains("dx12mc.staging.maxOutstandingBytes"));
    assertTrue(staging.contains("bgfx_make_ref_release"));
    assertTrue(staging.contains("MemoryUtil.memAlloc"));
    assertTrue(staging.contains("MemoryUtil.nmemFree"));
    assertTrue(staging.contains("StagingBackpressureException"));
    assertTrue(bridge.contains("catch (StagingBackpressureException pressure)"));
    assertTrue(bridge.contains("stats.deferredSectionDraw()"));
    assertTrue(registry.contains("gpuRevision"));
    assertTrue(registry.contains("image.dirtyUploads()"));
    assertTrue(registry.contains("UploadQueue"));
    assertTrue(registry.contains("queue.next(MAX_UPLOAD_CHUNK_BYTES)"));
    assertTrue(registry.contains("deferredPressure = pressure"));
    assertTrue(registry.contains("queue.submitted(band)"));
    assertTrue(registry.contains("textureGpuDeferred"));
    assertTrue(registry.contains("if (usableGpu())"));
    assertTrue(registry.contains("throw pressure"));
    assertFalse(registry.contains("catch (Throwable ignored)"));
    assertTrue(image.contains("MAX_DIRTY_REGIONS"));
    assertTrue(image.contains("existing.touches(merged)"));
    assertTrue(image.contains("existing.union(merged)"));
    assertFalse(image.contains("private Dirty dirty;"));
    assertTrue(mesh.contains("StagingMemory.allocate"));
    assertTrue(mesh.contains("vertexBytes"));
    assertTrue(mesh.contains("indexBytes"));
    assertTrue(program.contains("StagingMemory.copy(bytes)"));
    assertFalse(mesh.contains("bgfx_alloc(vertexBytes)"));
    assertFalse(program.contains("bgfx_alloc(bytes.length)"));
  }

  @Test
  void legacyOpenGlResourceLifecycleIsPreservedExceptNativeOwnedVertexVboStorage()
      throws IOException {
    String nativeImage = read("mixin/NativeImageMixin.java");
    String textureUtil = read("mixin/TextureUtilMixin.java");
    String target = read("mixin/RenderTargetMixin.java");
    String glState = read("mixin/GlStateManagerMixin.java");
    String vertexBuffer = read("mixin/VertexBufferMixin.java");
    String controller = read("render/Controller.java");
    String registry = read("bridge/texture/Registry.java");
    assertFalse(nativeImage.contains("cancellable = true"));
    assertFalse(nativeImage.contains("ci.cancel()"));
    assertFalse(textureUtil.contains("cancellable = true"));
    assertFalse(textureUtil.contains("ci.cancel()"));
    assertTrue(glState.contains("_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V"));
    assertFalse(glState.contains("_texSubImage2D"));
    assertTrue(glState.contains("_glBufferData(ILjava/nio/ByteBuffer;I)V"));
    assertTrue(glState.contains("_glBufferData(IJI)V"));
    assertTrue(glState.contains("suppressLegacyVertexBufferStorage(target"));
    assertTrue(vertexBuffer.contains("beginLegacyVertexBufferUpload"));
    assertTrue(vertexBuffer.contains("endLegacyVertexBufferUpload"));
    assertTrue(controller.contains("legacyVertexBufferUploadDepth <= 0"));
    assertTrue(controller.contains("target != GL_ARRAY_BUFFER"));
    assertFalse(controller.contains("GL_ELEMENT_ARRAY_BUFFER"));
    assertFalse(target.contains("createBuffers(IIZ)V"));
    assertTrue(nativeImage.contains("uploadTexture("));
    assertTrue(textureUtil.contains("allocateTexture("));
    assertTrue(registry.contains("dx12mc.texture.maxTotalBytes"));
    assertTrue(registry.contains("ensureMaxLevel"));
    assertTrue(registry.contains("authoritativeDimensions"));
    assertTrue(registry.contains("growInferred"));
    assertFalse(registry.contains("Math.max(source.getWidth()"));
    String runtime = read("client/Runtime.java");
    assertFalse(runtime.contains("suppressOpenGlTextureStorage"));
    assertFalse(runtime.contains("suppressOpenGlBufferStorage"));
    assertTrue(runtime.contains("mirrorBoundOpenGlTextureAllocation"));
    assertTrue(glState.contains("_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V"));
    assertTrue(glState.contains("mirrorBoundOpenGlTextureAllocation"));
    assertFalse(controller.contains("vanillaVertexBufferUploadDepth"));
    assertFalse(controller.contains("legacyRenderTargetStorageDepth"));
  }

  @Test
  void fixedRenderStateIsMirroredIntoBgfxInsteadOfUsingOneHardCodedState() throws IOException {
    String glState = read("mixin/GlStateManagerMixin.java");
    String bridge = read("bridge/Bridge.java") + read("bridge/DrawContextFactory.java");
    String pipeline = read("graphics/pipeline/ShaderPipeline.java");
    String scene = read("debug/Scene.java") + read("debug/NativeOverlayRenderer.java");
    assertTrue(glState.contains("_enableDepthTest()V"));
    assertTrue(glState.contains("_depthFunc(I)V"));
    assertTrue(glState.contains("_depthMask(Z)V"));
    assertTrue(glState.contains("_enableBlend()V"));
    assertTrue(glState.contains("_blendFuncSeparate(IIII)V"));
    assertTrue(glState.contains("_blendEquation(I)V"));
    assertTrue(glState.contains("_enableCull()V"));
    assertTrue(glState.contains("_colorMask(ZZZZ)V"));
    assertTrue(glState.contains("_enableScissorTest()V"));
    assertTrue(glState.contains("_scissorBox(IIII)V"));
    assertTrue(glState.contains("_enablePolygonOffset()V"));
    assertTrue(glState.contains("_polygonOffset(FF)V"));
    assertTrue(glState.contains("_polygonMode(II)V"));
    assertTrue(glState.contains("_enableColorLogicOp()V"));
    assertTrue(glState.contains("_logicOp(I)V"));
    assertTrue(glState.contains("dx12mc$suppressLegacyState(ci)"));
    assertTrue(glState.contains("suppressLegacyOpenGlDriverWork()"));
    assertTrue(bridge.contains("renderState.snapshot(width, height)"));
    assertTrue(pipeline.contains("bgfx_set_state(state.bgfxState() | context.primitive(), 0)"));
    assertTrue(pipeline.contains("bgfx_set_scissor"));
    assertFalse(pipeline.contains("State.DEFAULT"));
    assertFalse(bridge.contains("immediateCompatibilitySnapshot"));
    assertTrue(scene.contains("Raw OpenGL draw escapes detected"));
    assertTrue(scene.contains("Fully clipped draws skipped before GPU submit"));
    String state =
        read("bridge/state/RenderState.java") + read("bridge/state/BgfxStateEncoder.java");
    assertTrue(state.contains("if (depthTest)"));
    assertTrue(state.contains("if (depthWrite) state |= WRITE_Z"));
    assertTrue(state.contains("return rgb | (rgb << 3)"));
    assertTrue(state.contains("validateUnsupportedState()"));
  }

  @Test
  void legacyOpenGlMenuBlurCannotOverwriteTheD3D12Backbuffer() throws IOException {
    String gameRenderer = read("mixin/GameRendererMixin.java");
    String controller = read("render/Controller.java");
    String runtime = read("client/Runtime.java");
    assertTrue(gameRenderer.contains("processBlurEffect(F)V"));
    assertFalse(gameRenderer.contains("method = \"renderBlur(F)V\""));
    assertTrue(gameRenderer.contains("suppressLegacyBlurPostProcess()"));
    assertTrue(gameRenderer.contains("ci.cancel()"));
    assertTrue(controller.contains("public boolean suppressLegacyBlurPostProcess()"));
    assertTrue(controller.contains("stats.suppressedLegacyBlur()"));
    assertTrue(runtime.contains("suppressLegacyBlurPostProcess"));
    assertFalse(controller.contains("OpenGL blur fallback"));
  }

  @Test
  void extendedNativeVertexPathCarriesLightmapOverlayAndNormals() throws IOException {
    String encoder = read("bridge/mesh/Encoder.java");
    String batch = read("bridge/mesh/Batch.java");
    String layout = read("graphics/bgfx/Layout.java");
    String packing = read("graphics/bgfx/Packing.java");
    String bridge =
        read("bridge/Bridge.java")
            + read("bridge/DrawContextFactory.java")
            + read("bridge/DrawContext.java")
            + read("bridge/mesh/MeshStore.java");
    String pipeline = read("graphics/pipeline/ShaderPipeline.java");
    assertTrue(encoder.contains("VertexFormatElement.UV1"));
    assertTrue(encoder.contains("VertexFormatElement.UV2"));
    assertTrue(encoder.contains("VertexFormatElement.NORMAL"));
    assertTrue(batch.contains("boolean hasUv2"));
    assertTrue(batch.contains("boolean hasNormal"));
    assertTrue(layout.contains("BGFX_ATTRIB_TEXCOORD2"));
    assertTrue(layout.contains("if (entity)"));
    assertTrue(layout.contains("BGFX_ATTRIB_TEXCOORD1"));
    assertTrue(layout.contains("BGFX_ATTRIB_NORMAL"));
    assertTrue(packing.contains("VERTEX_STRIDE = 28"));
    assertTrue(packing.contains("ENTITY_VERTEX_STRIDE = 40"));
    assertTrue(layout.contains("BGFX_ATTRIB_TEXCOORD2, 2, BGFX_ATTRIB_TYPE_INT16, false, true"));
    assertFalse(encoder.contains("retainExtendedAttributes"));
    assertTrue(bridge.contains("ShaderSemantics.of(shader)"));
    assertTrue(bridge.contains("RenderSystem.getShaderTexture(2)"));
    assertTrue(bridge.contains("RenderSystem.getShaderTexture(1)"));
    assertTrue(pipeline.contains("bgfx_set_texture(2"));
    assertTrue(pipeline.contains("bgfx_set_texture(1"));
  }

  @Test
  void nativeDrawHotPathReusesScratchObjectsAndDisablesDiagnosticOverlayByDefault()
      throws IOException {
    String bridge =
        read("bridge/Bridge.java")
            + read("bridge/DrawContextFactory.java")
            + read("bridge/DrawContext.java")
            + read("bridge/mesh/Encoder.java")
            + read("bridge/mesh/MeshStore.java")
            + read("bridge/texture/Registry.java")
            + read("bridge/texture/TextureEntry.java")
            + read("graphics/bgfx/Packing.java");
    String state =
        read("bridge/state/RenderState.java") + read("bridge/state/BgfxStateEncoder.java");
    String config = read("config/Config.java");
    String scene = read("debug/Scene.java") + read("debug/NativeOverlayRenderer.java");
    assertTrue(bridge.contains("mvpScratch.set(projection);"));
    assertTrue(bridge.contains("mvpScratch.mul(modelView);"));
    assertTrue(bridge.contains("private final DrawContext context = new DrawContext()"));
    assertTrue(bridge.contains("ResidentMesh resident = residents.get(slot)"));
    assertTrue(bridge.contains("resident.lastUsedFrame = frameBoundary"));
    assertFalse(bridge.contains("new Matrix4f(projection)"));
    assertFalse(bridge.contains("return new DrawContext("));
    assertFalse(bridge.contains("residents.put(slot, new ResidentMesh(slot.mesh().bytes()"));
    assertTrue(state.contains("cachedStandard"));
    assertFalse(state.contains("cachedImmediate"));
    assertTrue(state.contains("cachedSky"));
    assertTrue(state.contains("snapshot.revision() == revision"));
    assertTrue(config.contains("System.getProperty(\"dx12mc.overlay\", \"false\")"));
    assertFalse(config.contains("dx12mc.vsync"));
    assertTrue(scene.contains("dx12mc.overlay.refreshMillis"));
    assertTrue(scene.contains("Previous frame CPU/GPU"));
    assertTrue(scene.contains("GC total / since overlay refresh"));
    assertTrue(bridge.contains("createSection"));
    assertTrue(bridge.contains("sectionContextPrepared"));
    assertTrue(bridge.contains("if (sectionContextPrepared)"));
    assertTrue(bridge.contains("prepareSharedUniforms"));
    assertTrue(bridge.contains("currentIfReady"));
    assertTrue(bridge.contains("index32(Batch batch)"));
    assertTrue(bridge.contains("Encoder.encodeReusable(mesh, immediateScratch)"));
    assertTrue(bridge.contains("private final Encoder.Scratch immediateScratch"));
    assertTrue(bridge.contains("dx12mc.immediate.scratch.elements"));
    assertTrue(bridge.contains("batch.indexCount()"));
    String controller = read("render/Controller.java");
    assertTrue(controller.contains("if (debug.shouldRender(config.mode(), config.overlay()))"));
    assertTrue(controller.contains("private FrameSize frameSize(Window window)"));
    assertTrue(controller.contains("performance.record(backend)"));
    assertTrue(read("render/PerformanceTelemetry.java").contains("DX12MC PERF"));
  }

  @Test
  void compiledChunkSectionDrawsCannotEscapeThroughRawVertexBufferDraw() throws IOException {
    String level = read("mixin/LevelRendererMixin.java");
    String vertexBuffer = read("mixin/VertexBufferMixin.java");
    String runtime = read("client/Runtime.java");
    String controller = read("render/Controller.java") + read("render/SectionRenderContext.java");
    String mixins = Files.readString(Path.of("src", "main", "resources", "dx12mc.mixins.json"));
    assertTrue(level.contains("renderSectionLayer"));
    assertTrue(level.contains("Uniform;set(FFF)V"));
    assertTrue(level.contains("Uniform;upload()V"));
    assertTrue(level.contains("VertexBuffer;bind()V"));
    assertTrue(level.contains("VertexBuffer;unbind()V"));
    assertTrue(level.contains("beginSectionLayer"));
    assertTrue(level.contains("sectionChunkOffset"));
    assertTrue(level.contains("suppressLegacySectionDriverCalls"));
    assertTrue(level.contains("endSectionLayer"));
    assertTrue(vertexBuffer.contains("method = \"draw()V\""));
    assertTrue(vertexBuffer.contains("drawSection(dx12mc$slot())"));
    assertTrue(runtime.contains("drawSection(Slot slot)"));
    assertTrue(controller.contains("drawClipTransform"));
    assertTrue(controller.contains("layerClipTransform"));
    assertTrue(controller.contains(".mul(projection)"));
    assertTrue(controller.contains(".mul(modelView)"));
    assertTrue(controller.contains("bridge.drawSection"));
    assertFalse(controller.contains("new Matrix4f(modelView)"));
    assertTrue(controller.contains("translate(offsetX, offsetY, offsetZ)"));
    assertTrue(mixins.contains("LevelRendererMixin"));
    assertTrue(level.contains("if (!DX12MC.runtime().suppressLegacySectionDriverCalls())"));
    assertTrue(runtime.contains("suppressLegacySectionDriverCalls"));
    assertTrue(controller.contains("lifecycle.active() && section.active()"));
    String pipelines =
        read("graphics/pipeline/PipelineSet.java") + read("graphics/pipeline/ShaderPipeline.java");
    assertTrue(pipelines.contains("SECTION_GEOMETRY_DISCARD"));
    assertTrue(pipelines.contains("BGFX_DISCARD_INDEX_BUFFER | BGFX_DISCARD_VERTEX_STREAMS"));
    assertTrue(pipelines.contains("if (!reuseState)"));
    assertTrue(pipelines.contains("bgfx_discard(BGFX_DISCARD_ALL)"));
    assertTrue(pipelines.contains("preparedSectionPipeline"));
    assertTrue(read("bridge/Bridge.java").contains("pipelines.endSectionLayer()"));
  }

  @Test
  void automaticWorldIconIsCapturedFromTheD3D12Backbuffer() throws IOException {
    String gameRenderer = read("mixin/GameRendererMixin.java");
    String controller = read("render/Controller.java");
    String glState = read("mixin/GlStateManagerMixin.java");
    assertTrue(gameRenderer.contains("takeAutoScreenshot(Ljava/nio/file/Path;)V"));
    assertTrue(gameRenderer.contains("Screenshot;takeScreenshot("));
    assertTrue(gameRenderer.contains("self.dx12mc$takeAutoScreenshot(path)"));
    assertFalse(gameRenderer.contains("tryTakeScreenshotIfNeeded"));
    assertTrue(controller.contains("lifecycle.requestScreenCapture(minecraft)"));
    assertTrue(glState.contains("_getTexImage(IIIIJ)V"));
    assertTrue(glState.contains("assertOpenGlReadbackAllowed"));
    assertTrue(controller.contains("A D3D12 readback path is not implemented yet"));
  }

  @Test
  void manualScreenshotReadsTheD3D12BackbufferThroughVanillaGrab() throws IOException {
    String screenshot = read("mixin/ScreenshotMixin.java");
    String controller = read("render/Controller.java");
    String mixins = Files.readString(Path.of("src", "main", "resources", "dx12mc.mixins.json"));
    assertTrue(screenshot.contains("@Mixin(Screenshot.class)"));
    assertTrue(
        screenshot.contains(
            "ScreenshotInvoker.dx12mc$grab(gameDirectory, name, renderTarget, messages)"));
    assertTrue(screenshot.contains("DX12MC.runtime().takeCapturedScreenshot()"));
    assertFalse(screenshot.contains("Win+Shift+S"));
    assertTrue(controller.contains("backend.requestScreenCapture()"));
    assertTrue(controller.contains("ScreenImages.toNativeImage(frame)"));
    assertTrue(mixins.contains("ScreenshotMixin"));
    assertTrue(mixins.contains("ScreenshotInvoker"));
  }

  @Test
  void worldVisibilityKeepsSpecialImmediateBlendAndLinesDoNotCrashWorldEntry() throws IOException {
    String state =
        read("bridge/state/RenderState.java") + read("bridge/state/BgfxStateEncoder.java");
    String bridge = read("bridge/Bridge.java");
    String stats = read("bridge/Stats.java");
    String scene = read("debug/Scene.java") + read("debug/NativeOverlayRenderer.java");
    assertTrue(state.contains("if (blend)"));
    assertTrue(state.contains("blendBits(srcRgb, dstRgb, srcAlpha, dstAlpha)"));
    assertFalse(state.contains("IMMEDIATE_COMPAT_ALPHA_BLEND"));
    assertFalse(bridge.contains("isOmittableNativeLine"));
    assertFalse(bridge.contains("omittedNativeLineOverlay"));
  }

  private static String read(String relative) throws IOException {
    String source = Files.readString(JAVA.resolve(relative));
    if (relative.equals("render/Controller.java")) {
      return source + Files.readString(JAVA.resolve("render/RendererLifecycle.java"));
    }
    return source;
  }

  @org.junit.jupiter.api.Test
  void persistentSectionMeshesUseBoundedResidencyAndNativeStreamingInsteadOfHandleThrash()
      throws Exception {
    String bridge =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/Bridge.java"));
    String meshStore =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/mesh/MeshStore.java"));
    String batch =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/mesh/Batch.java"));
    String gl =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/mixin/GlStateManagerMixin.java"));
    String vb =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/mixin/VertexBufferMixin.java"));
    String mesh =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/graphics/bgfx/Mesh.java"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("MAX_RESIDENT_STATIC_MESHES"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("MAX_RESIDENT_STATIC_BYTES"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("residentBytes"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("ensureCapacity"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("allocationBackoff"));
    org.junit.jupiter.api.Assertions.assertTrue(
        meshStore.contains("lastUsedFrame < frameBoundary - 1L"));
    org.junit.jupiter.api.Assertions.assertTrue(bridge.contains("nativeStreamingSpill"));
    org.junit.jupiter.api.Assertions.assertTrue(bridge.contains("deferredSectionDraw"));
    org.junit.jupiter.api.Assertions.assertFalse(bridge.contains("emergencyEvict"));
    org.junit.jupiter.api.Assertions.assertFalse(mesh.contains("throw new OutOfMemoryError"));
    org.junit.jupiter.api.Assertions.assertTrue(batch.contains("short[] uv1"));
    org.junit.jupiter.api.Assertions.assertTrue(batch.contains("short[] uv2"));
    org.junit.jupiter.api.Assertions.assertTrue(batch.contains("byte[] normals"));
    org.junit.jupiter.api.Assertions.assertTrue(batch.contains("cpuBytes()"));
    org.junit.jupiter.api.Assertions.assertTrue(vb.contains("beginLegacyVertexBufferUpload"));
    org.junit.jupiter.api.Assertions.assertTrue(
        gl.contains("_glBufferData(ILjava/nio/ByteBuffer;I)V"));
    org.junit.jupiter.api.Assertions.assertTrue(gl.contains("_glBufferData(IJI)V"));
    org.junit.jupiter.api.Assertions.assertTrue(
        gl.contains("suppressLegacyVertexBufferStorage(target"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("MAX_RESIDENT_STATIC_BYTES"));
  }

  @org.junit.jupiter.api.Test
  void transparencyResortsUpdateOnlyTheNativeIndexBufferAndCannotCrashOnStaleSortResults()
      throws Exception {
    String bridge =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/Bridge.java"));
    String meshStore =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/mesh/MeshStore.java"));
    String encoder =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/mesh/Encoder.java"));
    String mesh =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/graphics/bgfx/Mesh.java"));
    String slot =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/Slot.java"));
    org.junit.jupiter.api.Assertions.assertTrue(encoder.contains("decodeIndexUpdate"));
    org.junit.jupiter.api.Assertions.assertTrue(encoder.contains("remaining == shortBytes"));
    org.junit.jupiter.api.Assertions.assertTrue(encoder.contains("remaining == intBytes"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("staleSortedIndexUpdate"));
    org.junit.jupiter.api.Assertions.assertTrue(
        meshStore.contains("gpu.updateIndices(slot.batch())"));
    org.junit.jupiter.api.Assertions.assertTrue(meshStore.contains("syncIndices"));
    org.junit.jupiter.api.Assertions.assertTrue(
        meshStore.contains("catch (StagingBackpressureException pressure)"));
    org.junit.jupiter.api.Assertions.assertTrue(
        meshStore.contains("stats.deferredGpuIndexUpdate()"));
    org.junit.jupiter.api.Assertions.assertTrue(slot.contains("indicesDirty"));
    org.junit.jupiter.api.Assertions.assertTrue(slot.contains("indicesUploaded"));
    org.junit.jupiter.api.Assertions.assertFalse(
        meshStore.contains(
            "public void updateIndices(Slot slot, ByteBuffer source) {\n"
                + "        forgetResident(slot);"));
    org.junit.jupiter.api.Assertions.assertTrue(
        mesh.contains("bgfx_create_dynamic_index_buffer_mem"));
    org.junit.jupiter.api.Assertions.assertTrue(mesh.contains("bgfx_update_dynamic_index_buffer"));
    org.junit.jupiter.api.Assertions.assertTrue(mesh.contains("bgfx_set_dynamic_index_buffer"));
    org.junit.jupiter.api.Assertions.assertTrue(mesh.contains("bgfx_destroy_dynamic_index_buffer"));
    org.junit.jupiter.api.Assertions.assertFalse(
        slot.contains(
            "replaceIndices(int[] indices) {\n"
                + "        if (batch == null || indices == null) {\n"
                + "            return false;\n"
                + "        }\n"
                + "        releaseGpu();"));
  }

  @org.junit.jupiter.api.Test
  void skyIsSemanticAndCannotWriteWorldDepth() throws Exception {
    String mixin =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/net/dx12mc/mixin/LevelRendererMixin.java"));
    String state =
        java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/net/dx12mc/bridge/state/RenderState.java"))
            + java.nio.file.Files.readString(
                java.nio.file.Path.of(
                    "src/main/java/net/dx12mc/bridge/state/BgfxStateEncoder.java"));
    org.junit.jupiter.api.Assertions.assertTrue(
        mixin.contains(
            "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"));
    org.junit.jupiter.api.Assertions.assertTrue(state.contains("skyCompatibilitySnapshot"));
    org.junit.jupiter.api.Assertions.assertTrue(state.contains("static long sky("));
  }
}
