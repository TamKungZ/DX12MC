package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.dx12mc.DX12MC;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
  private static final String UNSUPPORTED_MESSAGE =
      "DX12MC native mode cannot capture F2 screenshots yet; use Win+Shift+S.";

  @Inject(
      method =
          "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private static void dx12mc$suppressUnnamedNativeScreenshot(
      File gameDirectory,
      RenderTarget renderTarget,
      Consumer<Component> messageConsumer,
      CallbackInfo ci) {
    dx12mc$suppressNativeScreenshot(messageConsumer, ci);
  }

  @Inject(
      method =
          "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private static void dx12mc$suppressNamedNativeScreenshot(
      File gameDirectory,
      String name,
      RenderTarget renderTarget,
      Consumer<Component> messageConsumer,
      CallbackInfo ci) {
    dx12mc$suppressNativeScreenshot(messageConsumer, ci);
  }

  private static void dx12mc$suppressNativeScreenshot(
      Consumer<Component> messageConsumer, CallbackInfo ci) {
    if (!DX12MC.runtime().suppressManualScreenshotCapture()) return;
    DX12MC.LOGGER.warn(UNSUPPORTED_MESSAGE);
    if (messageConsumer != null) messageConsumer.accept(Component.literal(UNSUPPORTED_MESSAGE));
    ci.cancel();
  }
}
