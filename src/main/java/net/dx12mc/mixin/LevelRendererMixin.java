package net.dx12mc.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.dx12mc.DX12MC;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

  @Inject(
      method =
          "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
      at = @At("HEAD"))
  private void dx12mc$beginSky(
      Matrix4f modelView,
      Matrix4f projection,
      float partialTick,
      Camera camera,
      boolean thickFog,
      Runnable fogSetup,
      CallbackInfo ci) {
    DX12MC.runtime().beginSky();
  }

  @Inject(
      method =
          "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
      at = @At("RETURN"))
  private void dx12mc$endSky(
      Matrix4f modelView,
      Matrix4f projection,
      float partialTick,
      Camera camera,
      boolean thickFog,
      Runnable fogSetup,
      CallbackInfo ci) {
    DX12MC.runtime().endSky();
  }

  @Inject(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At("HEAD"))
  private void dx12mc$beginSectionLayer(
      RenderType renderType,
      double cameraX,
      double cameraY,
      double cameraZ,
      Matrix4f modelView,
      Matrix4f projection,
      CallbackInfo ci) {
    DX12MC.runtime().beginSectionLayer(modelView, projection);
  }

  @Redirect(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;set(FFF)V"))
  private void dx12mc$chunkOffset(Uniform uniform, float x, float y, float z) {
    DX12MC.runtime().sectionChunkOffset(x, y, z);
    if (!DX12MC.runtime().suppressLegacySectionDriverCalls()) uniform.set(x, y, z);
  }

  @Redirect(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"))
  private void dx12mc$skipLegacyChunkOffsetUpload(Uniform uniform) {
    if (!DX12MC.runtime().suppressLegacySectionDriverCalls()) uniform.upload();
  }

  @Redirect(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;bind()V"))
  private void dx12mc$skipLegacySectionBufferBind(VertexBuffer buffer) {
    if (!DX12MC.runtime().suppressLegacySectionDriverCalls()) buffer.bind();
  }

  @Redirect(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;unbind()V"))
  private void dx12mc$skipLegacySectionBufferUnbind() {
    if (!DX12MC.runtime().suppressLegacySectionDriverCalls()) VertexBuffer.unbind();
  }

  @Inject(
      method =
          "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At("RETURN"))
  private void dx12mc$endSectionLayer(
      RenderType renderType,
      double cameraX,
      double cameraY,
      double cameraZ,
      Matrix4f modelView,
      Matrix4f projection,
      CallbackInfo ci) {
    DX12MC.runtime().endSectionLayer();
  }
}
