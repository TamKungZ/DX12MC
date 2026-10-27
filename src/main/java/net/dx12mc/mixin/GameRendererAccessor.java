package net.dx12mc.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

  @Accessor("blurEffect")
  PostChain dx12mc$blurEffect();

  @Accessor("effectActive")
  boolean dx12mc$effectActive();
}
