package net.dx12mc.mixin;

import com.mojang.blaze3d.platform.Window;
import net.dx12mc.DX12MC;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {

  @Inject(method = "close", at = @At("HEAD"))
  private void dx12mc$close(CallbackInfo ci) {
    DX12MC.runtime().close();
  }

  @Redirect(
      method = "updateVsync(Z)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V",
              remap = false))
  private void dx12mc$applyVsync(int interval) {
    if (DX12MC.runtime().openGlPresentationAllowed()) {
      GLFW.glfwSwapInterval(interval);
      return;
    }
    DX12MC.runtime().vsync(interval != 0);
  }
}
