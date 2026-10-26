package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.GlUtil;
import net.dx12mc.DX12MC;
import net.dx12mc.graphics.RendererInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlUtil.class)
public abstract class GlUtilMixin {

  @Inject(method = "getVendor()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
  private static void dx12mc$vendor(CallbackInfoReturnable<String> cir) {
    RendererInfo renderer = DX12MC.runtime().rendererInfo();
    if (renderer != null) cir.setReturnValue(renderer.vendor());
  }

  @Inject(method = "getRenderer()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
  private static void dx12mc$renderer(CallbackInfoReturnable<String> cir) {
    RendererInfo renderer = DX12MC.runtime().rendererInfo();
    if (renderer != null) cir.setReturnValue(renderer.renderer());
  }

  @Inject(method = "getOpenGLVersion()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
  private static void dx12mc$apiVersion(CallbackInfoReturnable<String> cir) {
    RendererInfo renderer = DX12MC.runtime().rendererInfo();
    if (renderer != null) cir.setReturnValue(renderer.apiVersion());
  }
}
