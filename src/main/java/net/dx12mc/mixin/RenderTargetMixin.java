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
    DX12MC.runtime().bindRenderTarget((RenderTarget) (Object) this);
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
  }

  @Inject(method = "unbindWrite()V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyUnbindWrite(CallbackInfo ci) {
    DX12MC.runtime().bindRenderTarget(null);
    if (DX12MC.runtime().suppressLegacyOpenGlDriverWork()) ci.cancel();
  }

  @Inject(method = "clear(Z)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressLegacyMainClear(boolean checkError, CallbackInfo ci) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft != null
        && (Object) this == minecraft.getMainRenderTarget()
        && DX12MC.runtime().suppressLegacyOpenGlDriverWork()) {
      ci.cancel();
    }
  }

  @Inject(method = "destroyBuffers()V", at = @At("HEAD"))
  private void dx12mc$releaseNativeTarget(CallbackInfo ci) {
    DX12MC.runtime().releaseRenderTarget((RenderTarget) (Object) this);
  }

  @Inject(method = "blitToScreen(IIZ)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$blitNatively(int width, int height, boolean disableBlend, CallbackInfo ci) {
    if (DX12MC.runtime().blitRenderTarget((RenderTarget) (Object) this, disableBlend)) ci.cancel();
  }
}
