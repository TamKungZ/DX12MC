package net.dx12mc.mixin;

import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface PostPassAccessor {

  @Accessor("auxAssets")
  List<IntSupplier> dx12mc$auxAssets();

  @Accessor("auxNames")
  List<String> dx12mc$auxNames();

  @Accessor("auxWidths")
  List<Integer> dx12mc$auxWidths();

  @Accessor("auxHeights")
  List<Integer> dx12mc$auxHeights();
}
