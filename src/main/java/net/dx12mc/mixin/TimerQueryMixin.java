package net.dx12mc.mixin;

import com.mojang.blaze3d.systems.TimerQuery;
import net.dx12mc.DX12MC;
import net.dx12mc.client.Runtime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TimerQuery.class)
public abstract class TimerQueryMixin {

  @Shadow private int nextQueryName;

  @Inject(method = "beginProfile()V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$beginNativeProfile(CallbackInfo ci) {
    if (DX12MC.runtime().ownsGpuTiming()) ci.cancel();
  }

  @Inject(
      method = "endProfile()Lcom/mojang/blaze3d/systems/TimerQuery$FrameProfile;",
      at = @At("HEAD"),
      cancellable = true)
  private void dx12mc$endNativeProfile(CallbackInfoReturnable<TimerQuery.FrameProfile> cir) {
    if (nextQueryName != 0 || !DX12MC.runtime().ownsGpuTiming()) return;
    cir.setReturnValue(FrameProfileInvoker.dx12mc$create(Runtime.NATIVE_GPU_PROFILE));
  }
}
