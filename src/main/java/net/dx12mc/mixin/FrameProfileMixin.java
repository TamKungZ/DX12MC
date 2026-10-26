package net.dx12mc.mixin;

import com.mojang.blaze3d.systems.TimerQuery;
import net.dx12mc.DX12MC;
import net.dx12mc.client.Runtime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TimerQuery.FrameProfile.class)
public abstract class FrameProfileMixin {

  @Shadow @Final private int queryName;
  @Shadow private long result;

  @Inject(method = "cancel()V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$cancelNativeProfile(CallbackInfo ci) {
    if (queryName != Runtime.NATIVE_GPU_PROFILE) return;
    if (result == 0L) result = -1L;
    ci.cancel();
  }

  @Inject(method = "isDone()Z", at = @At("HEAD"), cancellable = true)
  private void dx12mc$nativeProfileDone(CallbackInfoReturnable<Boolean> cir) {
    if (queryName != Runtime.NATIVE_GPU_PROFILE) return;
    if (result == 0L) result = DX12MC.runtime().gpuFrameNanos();
    cir.setReturnValue(true);
  }

  @Inject(method = "get()J", at = @At("HEAD"), cancellable = true)
  private void dx12mc$nativeProfileResult(CallbackInfoReturnable<Long> cir) {
    if (queryName != Runtime.NATIVE_GPU_PROFILE) return;
    if (result == 0L) result = DX12MC.runtime().gpuFrameNanos();
    cir.setReturnValue(Math.max(0L, result));
  }
}
