package net.dx12mc.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.dx12mc.DX12MC;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {

  @Redirect(
      method = "flipFrame(J)V",
      remap = false,
      at =
          @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
  private static void dx12mc$present(long window) {
    if (DX12MC.runtime().openGlPresentationAllowed()) {
      GLFW.glfwSwapBuffers(window);
      return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null) {
      throw new IllegalStateException("DX12MC cannot present D3D12: Minecraft instance is null");
    }
    DX12MC.runtime().presentAtSwap(minecraft);
  }
}
