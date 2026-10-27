package net.dx12mc.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Slot;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {

  @Shadow @Final private VertexBuffer.Usage usage;

  @Unique private Slot dx12mc$slot;

  @Inject(method = "upload", at = @At("HEAD"))
  private void dx12mc$capture(BufferBuilder.RenderedBuffer mesh, CallbackInfo ci) {
    if (mesh.drawState().indexOnly()) {
      DX12MC.runtime().updateIndices(dx12mc$slot(), mesh.indexBuffer());
    } else {
      DX12MC.runtime().capture(dx12mc$slot(), mesh, this.usage);
    }
    DX12MC.runtime().beginLegacyVertexBufferUpload();
  }

  @Inject(method = "upload", at = @At("RETURN"))
  private void dx12mc$finishUpload(BufferBuilder.RenderedBuffer mesh, CallbackInfo ci) {
    DX12MC.runtime().endLegacyVertexBufferUpload();
  }

  @Inject(method = "draw()V", at = @At("HEAD"), cancellable = true)
  private void dx12mc$drawSection(CallbackInfo ci) {
    if (DX12MC.runtime().drawSection(dx12mc$slot())) {
      ci.cancel();
    }
  }

  @Inject(method = "_drawWithShader", at = @At("HEAD"), cancellable = true)
  private void dx12mc$draw(
      Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
    if (DX12MC.runtime().draw(dx12mc$slot(), modelView, projection)) {
      ci.cancel();
    }
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void dx12mc$release(CallbackInfo ci) {
    if (this.dx12mc$slot != null) {
      DX12MC.runtime().release(this.dx12mc$slot);
      this.dx12mc$slot = null;
    }
  }

  @Unique
  private Slot dx12mc$slot() {
    if (this.dx12mc$slot == null) {
      this.dx12mc$slot = new Slot();
    }
    return this.dx12mc$slot;
  }
}
