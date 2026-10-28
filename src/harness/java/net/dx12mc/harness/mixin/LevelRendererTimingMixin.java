package net.dx12mc.harness.mixin;

import net.dx12mc.harness.Timing;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimingMixin {
  @Inject(method = "renderLevel", at = @At("HEAD"))
  private void dx12mcHarness$levelBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.RENDER_LEVEL);
  }

  @Inject(method = "renderLevel", at = @At("RETURN"))
  private void dx12mcHarness$levelEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.RENDER_LEVEL);
  }

  @Inject(method = "setupRender", at = @At("HEAD"))
  private void dx12mcHarness$setupBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.SETUP_RENDER);
  }

  @Inject(method = "setupRender", at = @At("RETURN"))
  private void dx12mcHarness$setupEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.SETUP_RENDER);
  }

  @Inject(method = "compileSections", at = @At("HEAD"))
  private void dx12mcHarness$compileBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.COMPILE_SECTIONS);
  }

  @Inject(method = "compileSections", at = @At("RETURN"))
  private void dx12mcHarness$compileEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.COMPILE_SECTIONS);
  }

  @Inject(method = "renderSectionLayer", at = @At("HEAD"))
  private void dx12mcHarness$layerBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.SECTION_LAYERS);
  }

  @Inject(method = "renderSectionLayer", at = @At("RETURN"))
  private void dx12mcHarness$layerEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.SECTION_LAYERS);
  }

  @Inject(method = "renderEntity", at = @At("HEAD"))
  private void dx12mcHarness$entityBegin(CallbackInfo ci) {
    Timing.begin(Timing.Part.ENTITIES);
  }

  @Inject(method = "renderEntity", at = @At("RETURN"))
  private void dx12mcHarness$entityEnd(CallbackInfo ci) {
    Timing.end(Timing.Part.ENTITIES);
  }
}
