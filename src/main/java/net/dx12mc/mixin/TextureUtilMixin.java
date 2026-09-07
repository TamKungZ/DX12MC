package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.TextureUtil;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public abstract class TextureUtilMixin {

  @Inject(method = "prepareImage(III)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$allocate(int id, int width, int height, CallbackInfo ci) {
    DX12MC.runtime().allocateTexture(id, 0, width, height);
  }

  @Inject(method = "prepareImage(IIII)V", at = @At("HEAD"), remap = false)
  private static void dx12mc$allocateMipmapped(
      int id, int maxLevel, int width, int height, CallbackInfo ci) {
    DX12MC.runtime().allocateTexture(id, maxLevel, width, height);
  }
}
