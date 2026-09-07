package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {

  @Inject(method = "_bindTexture(I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$bind(int texture, CallbackInfo ci) {
    DX12MC.runtime().bindTexture(texture);
  }

  @Inject(method = "_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$observeTextureStorage(
      int target,
      int level,
      int internalFormat,
      int width,
      int height,
      int border,
      int format,
      int type,
      IntBuffer pixels,
      CallbackInfo ci) {
    if (target == 0x0DE1 && width > 0 && height > 0) {
      DX12MC.runtime().mirrorBoundOpenGlTextureAllocation(level, width, height);
    }
  }

  @Inject(method = "_deleteTexture(I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$delete(int texture, CallbackInfo ci) {
    DX12MC.runtime().deleteTexture(texture);
  }

  @Inject(method = "_deleteTextures([I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$deleteMany(int[] textures, CallbackInfo ci) {
    if (textures == null) return;
    for (int texture : textures) DX12MC.runtime().deleteTexture(texture);
  }

  @Inject(
      method = "_glBufferData(ILjava/nio/ByteBuffer;I)V",
      at = @At("HEAD"),
      remap = false,
      cancellable = true)
  private static void dx12mc$suppressLegacyVertexBufferData(
      int target, ByteBuffer data, int usage, CallbackInfo ci) {
    long bytes = data == null ? 0L : data.remaining();
    if (DX12MC.runtime().suppressLegacyVertexBufferStorage(target, bytes)) ci.cancel();
  }

  @Inject(method = "_glBufferData(IJI)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$suppressLegacyVertexBufferAllocation(
      int target, long size, int usage, CallbackInfo ci) {
    if (DX12MC.runtime().suppressLegacyVertexBufferStorage(target, size)) ci.cancel();
  }

  @Inject(method = "_enableDepthTest()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enableDepth(CallbackInfo ci) {
    DX12MC.runtime().depthTest(true);
  }

  @Inject(method = "_disableDepthTest()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disableDepth(CallbackInfo ci) {
    DX12MC.runtime().depthTest(false);
  }

  @Inject(method = "_depthFunc(I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$depthFunc(int func, CallbackInfo ci) {
    DX12MC.runtime().depthFunc(func);
  }

  @Inject(method = "_depthMask(Z)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$depthMask(boolean write, CallbackInfo ci) {
    DX12MC.runtime().depthMask(write);
  }

  @Inject(method = "_enableBlend()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enableBlend(CallbackInfo ci) {
    DX12MC.runtime().blend(true);
  }

  @Inject(method = "_disableBlend()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disableBlend(CallbackInfo ci) {
    DX12MC.runtime().blend(false);
  }

  @Inject(method = "_blendFunc(II)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$blendFunc(int src, int dst, CallbackInfo ci) {
    DX12MC.runtime().blendFunc(src, dst);
  }

  @Inject(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$blendFuncSeparate(
      int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
    DX12MC.runtime().blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
  }

  @Inject(method = "_blendEquation(I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$blendEquation(int equation, CallbackInfo ci) {
    DX12MC.runtime().blendEquation(equation);
  }

  @Inject(method = "_enableCull()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enableCull(CallbackInfo ci) {
    DX12MC.runtime().cull(true);
  }

  @Inject(method = "_disableCull()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disableCull(CallbackInfo ci) {
    DX12MC.runtime().cull(false);
  }

  @Inject(method = "_colorMask(ZZZZ)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$colorMask(
      boolean red, boolean green, boolean blue, boolean alpha, CallbackInfo ci) {
    DX12MC.runtime().colorMask(red, green, blue, alpha);
  }

  @Inject(method = "_enableScissorTest()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enableScissor(CallbackInfo ci) {
    DX12MC.runtime().scissor(true);
  }

  @Inject(method = "_disableScissorTest()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disableScissor(CallbackInfo ci) {
    DX12MC.runtime().scissor(false);
  }

  @Inject(method = "_scissorBox(IIII)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$scissorBox(int x, int y, int width, int height, CallbackInfo ci) {
    DX12MC.runtime().scissorBox(x, y, width, height);
  }

  @Inject(method = "_enablePolygonOffset()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enablePolygonOffset(CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(true);
  }

  @Inject(method = "_disablePolygonOffset()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disablePolygonOffset(CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(false);
  }

  @Inject(method = "_polygonOffset(FF)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$polygonOffset(float factor, float units, CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(factor, units);
  }

  @Inject(method = "_polygonMode(II)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$polygonMode(int face, int mode, CallbackInfo ci) {
    DX12MC.runtime().polygonMode(face, mode);
  }

  @Inject(method = "_enableColorLogicOp()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$enableColorLogic(CallbackInfo ci) {
    DX12MC.runtime().colorLogic(true);
  }

  @Inject(method = "_disableColorLogicOp()V", at = @At("HEAD"), remap = false)
  private static void dx12mc$disableColorLogic(CallbackInfo ci) {
    DX12MC.runtime().colorLogic(false);
  }

  @Inject(method = "_logicOp(I)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$logicOp(int op, CallbackInfo ci) {
    DX12MC.runtime().logicOp(op);
  }

  @Inject(method = "_drawElements(IIIJ)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardDrawElements(
      int mode, int count, int type, long indices, CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlDrawAllowed(
            "GlStateManager._drawElements(mode="
                + mode
                + ", count="
                + count
                + ", type="
                + type
                + ", indices=0x"
                + Long.toHexString(indices)
                + ")");
  }

  @Inject(method = "_glDrawPixels(IIIIJ)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardDrawPixels(
      int width, int height, int format, int type, long pixels, CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlDrawAllowed("GlStateManager._glDrawPixels(" + width + "x" + height + ")");
  }

  @Inject(method = "_glCopyTexSubImage2D(IIIIIIII)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardCopyTexSubImage(
      int target,
      int level,
      int xOffset,
      int yOffset,
      int x,
      int y,
      int width,
      int height,
      CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlReadbackAllowed(
            "GlStateManager._glCopyTexSubImage2D(level="
                + level
                + ", size="
                + width
                + "x"
                + height
                + ")");
  }

  @Inject(method = "_glBlitFrameBuffer(IIIIIIIIII)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardFramebufferBlit(
      int srcX0,
      int srcY0,
      int srcX1,
      int srcY1,
      int dstX0,
      int dstY0,
      int dstX1,
      int dstY1,
      int mask,
      int filter,
      CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlReadbackAllowed(
            "GlStateManager._glBlitFrameBuffer(mask=0x" + Integer.toHexString(mask) + ")");
  }

  @Inject(method = "_getTexImage(IIIIJ)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardGetTexImage(
      int target, int level, int format, int type, long pixels, CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlReadbackAllowed("GlStateManager._getTexImage(level=" + level + ")");
  }

  @Inject(method = "_readPixels(IIIIIILjava/nio/ByteBuffer;)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardReadPixelsBuffer(
      int x,
      int y,
      int width,
      int height,
      int format,
      int type,
      ByteBuffer pixels,
      CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlReadbackAllowed(
            "GlStateManager._readPixels(ByteBuffer, " + width + "x" + height + ")");
  }

  @Inject(method = "_readPixels(IIIIIIJ)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$guardReadPixelsPointer(
      int x, int y, int width, int height, int format, int type, long pixels, CallbackInfo ci) {
    DX12MC
        .runtime()
        .assertOpenGlReadbackAllowed(
            "GlStateManager._readPixels(pointer, " + width + "x" + height + ")");
  }
}
