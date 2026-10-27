package net.dx12mc.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import net.dx12mc.DX12MC;
import net.dx12mc.bridge.Bridge;
import net.dx12mc.graphics.bgfx.Target;
import net.dx12mc.graphics.bgfx.ViewSequence;
import net.dx12mc.graphics.pipeline.PostPipeline;
import net.dx12mc.graphics.pipeline.PostPipeline.Effect;
import net.dx12mc.mixin.PostChainAccessor;
import net.dx12mc.mixin.PostPassAccessor;
import net.dx12mc.mixin.RenderTargetAccessor;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.lwjgl.bgfx.BGFX;

final class PostProcessor {
  private static final int GL_LINEAR = 9729;
  private static final float CHAIN_TIME_PERIOD = 20.0f;
  private static final float FULL_STAMP = 1.0f;
  private final Bridge bridge;
  private final Controller controller;
  private final float[] values = new float[PostPipeline.UNIFORM_FLOATS];
  private final Set<String> reported = new HashSet<>();

  PostProcessor(Bridge bridge, Controller controller) {
    this.bridge = bridge;
    this.controller = controller;
  }

  boolean process(PostChain chain, float partialTick) {
    PostChainAccessor access = (PostChainAccessor) chain;
    List<PostPass> passes = access.dx12mc$passes();
    RenderTarget main = access.dx12mc$screenTarget();
    for (int i = 0; i < passes.size(); i++) {
      PostPass pass = passes.get(i);
      if (Effect.byName(pass.getName()) == null) {
        report(chain, "program " + pass.getName() + " has no native D3D12 port");
        return true;
      }
      if (pass.inTarget == main && !controller.mainRedirected()) {
        DX12MC.LOGGER.debug(
            "Post-processing chain {} skipped: the main colour buffer was not readable on this"
                + " frame",
            chain.getName());
        return true;
      }
    }
    float lastStamp = access.dx12mc$lastStamp();
    float time = access.dx12mc$time();
    if (partialTick < lastStamp) {
      time += FULL_STAMP - lastStamp;
      time += partialTick;
    } else {
      time += partialTick - lastStamp;
    }
    access.dx12mc$setLastStamp(partialTick);
    while (time > CHAIN_TIME_PERIOD) time -= CHAIN_TIME_PERIOD;
    access.dx12mc$setTime(time);
    for (int i = 0; i < passes.size(); i++) run(chain, passes.get(i));
    controller.finishPostChain();
    return true;
  }

  private void run(PostChain chain, PostPass pass) {
    Effect effect = Effect.byName(pass.getName());
    RenderTarget in = pass.inTarget;
    RenderTarget out = pass.outTarget;
    controller.materializeClear(in);
    Target source = controller.nativeTarget(in);
    List<IntSupplier> assets = ((PostPassAccessor) pass).dx12mc$auxAssets();
    Target aux = assets.isEmpty() ? null : bridge.findTarget(assets.get(0).getAsInt());
    if (source == null || (!assets.isEmpty() && aux == null)) {
      report(chain, "pass " + pass.getName() + " has an input without a native target");
      return;
    }
    controller.applyTarget(out);
    float[] channels = ((RenderTargetAccessor) out).dx12mc$clearChannels();
    controller.clearApplied(
        ViewSequence.rgba(channels[0], channels[1], channels[2], channels[3]), out.useDepth);
    fill(effect, pass.getEffect(), in, out);
    long state = bridge.post().blendState(effect.alphaBlend(), out.useDepth);
    if (!bridge
        .post()
        .draw(
            effect,
            source.colorTexture(),
            aux == null ? BGFX.BGFX_INVALID_HANDLE : aux.colorTexture(),
            in.filterMode == GL_LINEAR,
            values,
            state)) {
      throw new IllegalStateException(
          "bgfx/D3D12 rejected post-processing pass " + pass.getName() + " of " + chain.getName());
    }
  }

  private void fill(Effect effect, EffectInstance fx, RenderTarget in, RenderTarget out) {
    Arrays.fill(values, 0.0f);
    values[0] = in.width;
    values[1] = in.height;
    values[2] = out.width;
    values[3] = out.height;
    switch (effect) {
      case BLUR -> {
        read(fx, "BlurDir", 4, 2);
        read(fx, "Radius", 6, 1);
      }
      case BLIT -> read(fx, "ColorModulate", 4, 4);
      case INVERT -> read(fx, "InverseAmount", 4, 1);
      case COLOR_CONVOLVE -> {
        read(fx, "Gray", 4, 3);
        read(fx, "Saturation", 7, 1);
        read(fx, "RedMatrix", 8, 3);
        read(fx, "GreenMatrix", 12, 3);
        read(fx, "BlueMatrix", 16, 3);
        read(fx, "Offset", 20, 3);
        read(fx, "ColorScale", 24, 3);
      }
      case BITS -> {
        read(fx, "Resolution", 4, 1);
        read(fx, "Saturation", 5, 1);
        read(fx, "MosaicSize", 6, 1);
      }
      case SPIDER -> {
        read(fx, "InScale", 4, 2);
        read(fx, "InOffset", 6, 2);
        read(fx, "InRotation", 8, 1);
        read(fx, "Scissor", 12, 4);
        read(fx, "Vignette", 16, 4);
      }
      case ENTITY_OUTLINE -> {}
    }
  }

  private void read(EffectInstance fx, String name, int offset, int count) {
    Uniform uniform = fx.getUniform(name);
    if (uniform == null) return;
    FloatBuffer buffer = uniform.getFloatBuffer();
    for (int i = 0; i < count; i++) values[offset + i] = buffer.get(i);
  }

  private void report(PostChain chain, String reason) {
    if (reported.add(chain.getName() + reason)) {
      DX12MC.LOGGER.warn("Post-processing chain {} is skipped: {}", chain.getName(), reason);
    }
  }
}
