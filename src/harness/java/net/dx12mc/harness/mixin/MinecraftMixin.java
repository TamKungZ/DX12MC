package net.dx12mc.harness.mixin;

import net.dx12mc.harness.FlightHarnessHook;
import net.dx12mc.harness.Timing;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  @Inject(method = "runTick", at = @At("HEAD"))
  private void dx12mcHarness$frame(boolean advanceGameTime, CallbackInfo ci) {
    FlightHarnessHook.frame((Minecraft) (Object) this);
    Timing.begin(Timing.Part.FRAME);
  }

  @Inject(method = "runTick", at = @At("RETURN"))
  private void dx12mcHarness$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
    Timing.end(Timing.Part.FRAME);
  }

  @Inject(method = "tick", at = @At("HEAD"))
  private void dx12mcHarness$tickBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.CLIENT_TICK);
  }

  @Inject(method = "tick", at = @At("RETURN"))
  private void dx12mcHarness$tickEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.CLIENT_TICK);
  }
}
