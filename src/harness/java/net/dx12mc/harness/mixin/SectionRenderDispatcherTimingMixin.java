package net.dx12mc.harness.mixin;

import net.dx12mc.harness.Timing;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherTimingMixin {
  @Inject(method = "uploadAllPendingUploads", at = @At("HEAD"))
  private void dx12mcHarness$uploadsBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.PENDING_UPLOADS);
  }

  @Inject(method = "uploadAllPendingUploads", at = @At("RETURN"))
  private void dx12mcHarness$uploadsEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.PENDING_UPLOADS);
  }
}
