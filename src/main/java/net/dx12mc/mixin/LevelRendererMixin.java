package net.dx12mc.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.dx12mc.DX12MC;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

  @Inject(method = "renderLevel", at = @At("HEAD"))
  private void dx12mc$beginWorld(
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      LightTexture lightTexture,
      Matrix4f cameraRotation,
      Matrix4f projection,
      CallbackInfo ci) {
    var level = Minecraft.getInstance().level;
    if (level == null) return;
    float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
    var position = camera.getPosition();
    DX12MC
        .runtime()
        .beginWorld(
            cameraRotation,
            projection,
            position.x,
            position.y,
            position.z,
            level.getSunAngle(partialTick),
            level.getDayTime() % 24000L,
            partialTick);
  }

  @Inject(method = "renderLevel", at = @At("RETURN"))
  private void dx12mc$endWorld(
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      LightTexture lightTexture,
      Matrix4f cameraRotation,
      Matrix4f projection,
      CallbackInfo ci) {
    DX12MC.runtime().endWorld();
  }

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
    DX12MC
        .runtime()
        .beginSectionLayer(renderType, cameraX, cameraY, cameraZ, modelView, projection);
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
