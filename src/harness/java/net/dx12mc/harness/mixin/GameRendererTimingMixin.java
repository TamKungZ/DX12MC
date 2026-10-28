package net.dx12mc.harness.mixin;

import net.dx12mc.harness.Timing;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererTimingMixin {
  @Inject(method = "render", at = @At("HEAD"))
  private void dx12mcHarness$renderBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.GAME_RENDER);
  }

  @Inject(method = "render", at = @At("RETURN"))
  private void dx12mcHarness$renderEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.GAME_RENDER);
  }
}
