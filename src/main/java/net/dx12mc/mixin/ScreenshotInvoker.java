package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screenshot.class)
public interface ScreenshotInvoker {

  @Invoker("_grab")
  static void dx12mc$grab(
      File gameDirectory, String name, RenderTarget renderTarget, Consumer<Component> messages) {
    throw new AssertionError();
  }
}
