package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin {

  @Inject(method = "_upload", at = @At("HEAD"))
  private void dx12mc$upload(
      int level,
      int offsetX,
      int offsetY,
      int unpackSkipPixels,
      int unpackSkipRows,
      int width,
      int height,
      boolean blur,
      boolean clamp,
      boolean mipmap,
      boolean close,
      CallbackInfo ci) {
    DX12MC
        .runtime()
        .uploadTexture(
            (NativeImage) (Object) this,
            level,
            offsetX,
            offsetY,
            unpackSkipPixels,
            unpackSkipRows,
            width,
            height,
            blur,
            clamp,
            mipmap);
  }
}
