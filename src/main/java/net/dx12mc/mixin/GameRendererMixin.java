package net.dx12mc.mixin;

import java.nio.file.Path;
import net.dx12mc.DX12MC;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

  @Shadow private Minecraft minecraft;

  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void dx12mc$render(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
    DX12MC.runtime().onRenderHead(this.minecraft);
    if (DX12MC.runtime().shouldCancelGameRenderer()) {
      ci.cancel();
    }
  }

  @Inject(
      method = "takeAutoScreenshot(Ljava/nio/file/Path;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"),
      cancellable = true)
  private void dx12mc$captureWorldIconFromD3D12(Path path, CallbackInfo ci) {
    if (!DX12MC.runtime().ownsScreenCapture() || DX12MC.runtime().hasCapturedScreenshot()) return;
    ci.cancel();
    GameRendererInvoker self = (GameRendererInvoker) this;
    DX12MC
        .runtime()
        .requestScreenCapture(
            () -> self.dx12mc$takeAutoScreenshot(path),
            reason -> DX12MC.LOGGER.warn("World icon capture failed: {}", reason));
  }
}
