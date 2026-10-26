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

  @Inject(method = "_texParameter(III)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$texParameter(int target, int pname, int value, CallbackInfo ci) {
    DX12MC.runtime().texParameter(target, pname, value);
  }

  @Inject(method = "_texParameter(IIF)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$texParameterFloat(
      int target, int pname, float value, CallbackInfo ci) {
    DX12MC.runtime().texParameter(target, pname, value);
  }

  @Inject(method = "_clearColor(FFFF)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$clearColor(
      float red, float green, float blue, float alpha, CallbackInfo ci) {
    DX12MC.runtime().clearColor(red, green, blue, alpha);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_clearDepth(D)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$clearDepth(double depth, CallbackInfo ci) {
    DX12MC.runtime().clearDepth(depth);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_clear(IZ)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$clear(int mask, boolean checkError, CallbackInfo ci) {
    if (DX12MC.runtime().clear(mask)) ci.cancel();
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

  @Inject(method = "_enableDepthTest()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enableDepth(CallbackInfo ci) {
    DX12MC.runtime().depthTest(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disableDepthTest()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disableDepth(CallbackInfo ci) {
    DX12MC.runtime().depthTest(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_depthFunc(I)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$depthFunc(int func, CallbackInfo ci) {
    DX12MC.runtime().depthFunc(func);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_depthMask(Z)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$depthMask(boolean write, CallbackInfo ci) {
    DX12MC.runtime().depthMask(write);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_enableBlend()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enableBlend(CallbackInfo ci) {
    DX12MC.runtime().blend(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disableBlend()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disableBlend(CallbackInfo ci) {
    DX12MC.runtime().blend(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_blendFunc(II)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$blendFunc(int src, int dst, CallbackInfo ci) {
    DX12MC.runtime().blendFunc(src, dst);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$blendFuncSeparate(
      int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
    DX12MC.runtime().blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_blendEquation(I)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$blendEquation(int equation, CallbackInfo ci) {
    DX12MC.runtime().blendEquation(equation);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_enableCull()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enableCull(CallbackInfo ci) {
    DX12MC.runtime().cull(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disableCull()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disableCull(CallbackInfo ci) {
    DX12MC.runtime().cull(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_colorMask(ZZZZ)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$colorMask(
      boolean red, boolean green, boolean blue, boolean alpha, CallbackInfo ci) {
    DX12MC.runtime().colorMask(red, green, blue, alpha);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_enableScissorTest()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enableScissor(CallbackInfo ci) {
    DX12MC.runtime().scissor(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disableScissorTest()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disableScissor(CallbackInfo ci) {
    DX12MC.runtime().scissor(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_scissorBox(IIII)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$scissorBox(int x, int y, int width, int height, CallbackInfo ci) {
    DX12MC.runtime().scissorBox(x, y, width, height);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_enablePolygonOffset()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enablePolygonOffset(CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disablePolygonOffset()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disablePolygonOffset(CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_polygonOffset(FF)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$polygonOffset(float factor, float units, CallbackInfo ci) {
    DX12MC.runtime().polygonOffset(factor, units);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_polygonMode(II)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$polygonMode(int face, int mode, CallbackInfo ci) {
    DX12MC.runtime().polygonMode(face, mode);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_enableColorLogicOp()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$enableColorLogic(CallbackInfo ci) {
    DX12MC.runtime().colorLogic(true);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_disableColorLogicOp()V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$disableColorLogic(CallbackInfo ci) {
    DX12MC.runtime().colorLogic(false);
    dx12mc$suppressLegacyState(ci);
  }

  @Inject(method = "_logicOp(I)V", at = @At("HEAD"), remap = false, cancellable = true)
  private static void dx12mc$logicOp(int op, CallbackInfo ci) {
    DX12MC.runtime().logicOp(op);
    dx12mc$suppressLegacyState(ci);
  }

  private static void dx12mc$suppressLegacyState(CallbackInfo ci) {
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
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
