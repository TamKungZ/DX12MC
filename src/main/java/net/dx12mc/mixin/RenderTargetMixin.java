package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.dx12mc.DX12MC;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

  @Inject(method = "bindWrite(Z)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyBindWrite(boolean viewport, CallbackInfo ci) {
    Minecraft minecraft = Minecraft.getInstance();
    DX12MC
        .runtime()
        .bindRenderTarget(minecraft == null || (Object) this == minecraft.getMainRenderTarget());
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
  }

  @Inject(method = "unbindWrite()V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyUnbindWrite(CallbackInfo ci) {
    DX12MC.runtime().bindRenderTarget(true);
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
  }

  @Inject(method = "clear(Z)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyClear(boolean checkError, CallbackInfo ci) {
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
  }

  @Inject(method = "blitToScreen(IIZ)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyScreenBlit(
      int width, int height, boolean disableBlend, CallbackInfo ci) {
    if (DX12MC.runtime().suppressVanillaFramebufferBlit()) ci.cancel();
  }
}
