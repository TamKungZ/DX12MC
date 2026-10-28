package net.dx12mc.pack;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.bridge.DrawContext;

public final class PackProgram {
  final String pack;
  final PackPlan.Program plan;
  final short handle;
  final short[] uniformHandles;
  final float[][] uniformValues;
  final int[] uniformCounts;
  final short[] samplerHandles;
  final int[] samplerSlots;
  final String[] samplerNames;
  short[] samplerTextures;
  int[] samplerFlags;
  float[] targetSize;

  PackProgram(
      String pack,
      PackPlan.Program plan,
      short handle,
      short[] uniformHandles,
      float[][] uniformValues,
      int[] uniformCounts,
      String[] samplerNames,
      short[] samplerHandles,
      int[] samplerSlots) {
    this.pack = pack;
    this.plan = plan;
    this.handle = handle;
    this.uniformHandles = uniformHandles;
    this.uniformValues = uniformValues;
    this.uniformCounts = uniformCounts;
    this.samplerNames = samplerNames;
    this.samplerHandles = samplerHandles;
    this.samplerSlots = samplerSlots;
    this.samplerTextures = new short[samplerHandles.length];
    this.samplerFlags = new int[samplerHandles.length];
  }

  public DrawContext.Kind kind() {
    return plan.family().kind();
  }

  public short handle() {
    return handle;
  }

  public String name() {
    return pack + ":" + plan.name();
  }

  PackSet packs;

  public void bind(DrawContext context) {
    setUniforms();
    for (int i = 0; i < samplerHandles.length; i++) {
      short texture = samplerTextures[i];
      int flags = samplerFlags[i];
      String name = samplerNames[i];
      if (name.equals("s_normals") || name.equals("s_specular")) {
        boolean normals = name.equals("s_normals");
        texture = packs.materialTexture(normals, context.texture());
        flags = packs.materialFlags(context.texture());
      }
      if (BGFX_HANDLE_IS_VALID(texture)) {
        bgfx_set_texture(samplerSlots[i], samplerHandles[i], texture, flags);
      }
    }
  }

  void setUniforms() {
    for (int i = 0; i < uniformHandles.length; i++) {
      bgfx_set_uniform(uniformHandles[i], uniformValues[i], uniformCounts[i]);
    }
  }
}
