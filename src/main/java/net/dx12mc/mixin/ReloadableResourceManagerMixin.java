package net.dx12mc.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.dx12mc.DX12MC;
import net.dx12mc.client.ShaderPackLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
  @Shadow @Final private PackType type;
  @Shadow @Final private List<PreparableReloadListener> listeners;

  @Inject(method = "createReload", at = @At("HEAD"))
  private void dx12mc$registerShaderPacks(
      Executor background,
      Executor game,
      CompletableFuture<Unit> waitingFor,
      List<PackResources> packs,
      CallbackInfoReturnable<?> cir) {
    if (type != PackType.CLIENT_RESOURCES || !DX12MC.runtime().shaderPacksEnabled()) return;
    for (PreparableReloadListener listener : listeners) {
      if (listener instanceof ShaderPackLoader) return;
    }
    listeners.add(new ShaderPackLoader());
  }
}
