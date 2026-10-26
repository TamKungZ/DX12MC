package net.dx12mc.mixin;

import java.nio.file.Path;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

  @Invoker("takeAutoScreenshot")
  void dx12mc$takeAutoScreenshot(Path path);
}
