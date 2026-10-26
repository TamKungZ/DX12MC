package net.dx12mc.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {

  @Accessor(value = "apiDescription", remap = false)
  static void dx12mc$setApiDescription(String description) {
    throw new AssertionError();
  }
}
