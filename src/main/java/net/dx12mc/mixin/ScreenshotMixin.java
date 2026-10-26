package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.util.function.Consumer;
import net.dx12mc.DX12MC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

  @Inject(
      method =
          "_grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private static void dx12mc$grabPresentedFrame(
      File gameDirectory,
      String name,
      RenderTarget renderTarget,
      Consumer<Component> messages,
      CallbackInfo ci) {
    if (!DX12MC.runtime().ownsScreenCapture() || DX12MC.runtime().hasCapturedScreenshot()) return;
    ci.cancel();
    if (renderTarget != Minecraft.getInstance().getMainRenderTarget()) {
      DX12MC.LOGGER.warn("DX12MC cannot capture an offscreen render target screenshot");
      messages.accept(
          Component.translatable("screenshot.failure", "offscreen render targets are not drawn"));
      return;
    }
    DX12MC
        .runtime()
        .requestScreenCapture(
            () -> ScreenshotInvoker.dx12mc$grab(gameDirectory, name, renderTarget, messages),
            reason -> messages.accept(Component.translatable("screenshot.failure", reason)));
  }

  @Inject(
      method =
          "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;",
      at = @At("HEAD"),
      cancellable = true)
  private static void dx12mc$presentedFrame(
      RenderTarget renderTarget, CallbackInfoReturnable<NativeImage> cir) {
    NativeImage captured = DX12MC.runtime().takeCapturedScreenshot();
    if (captured != null) cir.setReturnValue(captured);
  }
}
