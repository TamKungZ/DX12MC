package net.dx12mc.mixin;

import net.dx12mc.DX12MC;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostChain.class)
public abstract class PostChainMixin {

  @Inject(method = "process(F)V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$suppressOpenGlPostChain(float partialTick, CallbackInfo ci) {
    if (DX12MC.runtime().suppressPostChain()) ci.cancel();
  }
}
