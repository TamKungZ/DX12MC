package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

  @Inject(method = "blitToScreen(IIZ)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyScreenBlit(
      int width, int height, boolean disableBlend, CallbackInfo ci) {
    if (DX12MC.runtime().suppressVanillaFramebufferBlit()) ci.cancel();
  }
}
