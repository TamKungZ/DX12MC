package net.dx12mc.harness.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.dx12mc.harness.Timing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSystem.class, priority = 500)
public abstract class RenderSystemTimingMixin {
  @Inject(method = "flipFrame(J)V", remap = false, at = @At("HEAD"))
  private static void dx12mcHarness$flipBegin(long window, CallbackInfo ci) {
    Timing.begin(Timing.Part.FLIP_FRAME);
  }

  @Inject(method = "flipFrame(J)V", remap = false, at = @At("RETURN"))
  private static void dx12mcHarness$flipEnd(long window, CallbackInfo ci) {
    Timing.end(Timing.Part.FLIP_FRAME);
  }
}
