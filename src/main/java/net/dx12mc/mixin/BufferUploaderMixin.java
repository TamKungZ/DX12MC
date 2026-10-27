package net.dx12mc.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.dx12mc.DX12MC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public abstract class BufferUploaderMixin {

  @Inject(method = "_drawWithShader", at = @At("HEAD"), cancellable = true)
  private static void dx12mc$drawWithShader(BufferBuilder.RenderedBuffer mesh, CallbackInfo ci) {
    dx12mc$submit(mesh, ci);
  }

  @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
  private static void dx12mc$draw(BufferBuilder.RenderedBuffer mesh, CallbackInfo ci) {
    dx12mc$submit(mesh, ci);
  }

  private static void dx12mc$submit(BufferBuilder.RenderedBuffer mesh, CallbackInfo ci) {
    if (mesh.isEmpty()) return;
    final boolean submitted;
    try {
      submitted = DX12MC.runtime().drawImmediate(mesh);
    } catch (Throwable throwable) {
      mesh.release();
      throw throwable;
    }
    if (!submitted) return;
    mesh.release();
    ci.cancel();
  }
}
