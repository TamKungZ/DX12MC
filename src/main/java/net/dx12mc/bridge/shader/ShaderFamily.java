package net.dx12mc.bridge.shader;

import java.util.Locale;

public enum ShaderFamily {
  GUI(false),
  TEXT(true),
  TERRAIN(true),
  ENTITY(true),
  PARTICLE(true),
  BASIC(false);
  private final boolean lightmapCandidate;

  ShaderFamily(boolean lightmapCandidate) {
    this.lightmapCandidate = lightmapCandidate;
  }

  public boolean lightmapCandidate() {
    return lightmapCandidate;
  }

  public static boolean usesGlintAlpha(String shaderName) {
    return shaderName != null && shaderName.toLowerCase(Locale.ROOT).contains("glint");
  }

  public static ShaderFamily classify(String shaderName) {
    if (shaderName == null || shaderName.isBlank()) return BASIC;
    String n = shaderName.toLowerCase(Locale.ROOT);
    if (n.contains("gui") || n.contains("position_tex") || n.contains("blit")) return GUI;
    if (n.contains("text")) return TEXT;
    if (n.contains("particle")) return PARTICLE;
    if (n.contains("entity")
        || n.contains("armor")
        || n.contains("eyes")
        || n.contains("leash")
        || n.contains("beacon")
        || n.contains("item")) return ENTITY;
    if (n.contains("solid")
        || n.contains("cutout")
        || n.contains("translucent")
        || n.contains("tripwire")
        || n.contains("moving_block")
        || n.contains("terrain")) return TERRAIN;
    return BASIC;
  }
}
