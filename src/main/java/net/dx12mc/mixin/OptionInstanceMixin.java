package net.dx12mc.mixin;

import java.util.function.Consumer;
import net.dx12mc.DX12MC;
import net.dx12mc.client.VideoOptions;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin {

  @Inject(
      method =
          "createButton(Lnet/minecraft/client/Options;IIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/AbstractWidget;",
      at = @At("RETURN"))
  private void dx12mc$disableUnavailableOption(
      Options options,
      int x,
      int y,
      int width,
      Consumer<?> onValueChange,
      CallbackInfoReturnable<AbstractWidget> cir) {
    String reason =
        VideoOptions.unavailableReason(
            options,
            (OptionInstance<?>) (Object) this,
            DX12MC.runtime().legacyPostProcessingAvailable());
    if (reason == null) return;
    AbstractWidget button = cir.getReturnValue();
    button.active = false;
    button.setTooltip(Tooltip.create(Component.literal(reason)));
  }
}
