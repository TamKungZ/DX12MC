package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.Window;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {

  @Inject(method = "close", at = @At("HEAD"))
  private void dx12mc$close(CallbackInfo ci) {
    DX12MC.runtime().close();
  }
}
