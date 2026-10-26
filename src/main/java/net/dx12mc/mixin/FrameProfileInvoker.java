package net.dx12mc.mixin;

import com.mojang.blaze3d.systems.TimerQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TimerQuery.FrameProfile.class)
public interface FrameProfileInvoker {

  @Invoker("<init>")
  static TimerQuery.FrameProfile dx12mc$create(int queryName) {
    throw new AssertionError();
  }
}
