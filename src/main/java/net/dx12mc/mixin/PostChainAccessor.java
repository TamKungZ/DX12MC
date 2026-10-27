package net.dx12mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostChain.class)
public interface PostChainAccessor {

  @Accessor("passes")
  List<PostPass> dx12mc$passes();

  @Accessor("screenTarget")
  RenderTarget dx12mc$screenTarget();

  @Accessor("time")
  float dx12mc$time();

  @Accessor("time")
  void dx12mc$setTime(float time);
}
