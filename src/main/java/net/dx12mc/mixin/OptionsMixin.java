package net.dx12mc.mixin;

import java.util.List;
import net.dx12mc.DX12MC;
import net.dx12mc.client.VideoOptions;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Options.class)
public abstract class OptionsMixin {

  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/OptionInstance$AltEnum;<init>(Ljava/util/List;Ljava/util/List;Ljava/util/function/BooleanSupplier;Lnet/minecraft/client/OptionInstance$CycleableValueSet$ValueSetter;Lcom/mojang/serialization/Codec;)V"),
      index = 0)
  private List<?> dx12mc$graphicsModes(List<?> values) {
    return VideoOptions.graphicsModes(values, DX12MC.runtime().legacyPostProcessingAvailable());
  }
}
