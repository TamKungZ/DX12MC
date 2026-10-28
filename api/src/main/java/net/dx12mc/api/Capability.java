package net.dx12mc.api;

/** Optional features. Check {@link Dx12mc#supports} before relying on one. */
public enum Capability {
  /** Shader packs with program overrides, new programs, uniforms, textures and post passes. */
  SHADER_PACKS,
  /** A pack may render the world into a G-buffer and sample the scene colour and depth. */
  SCENE_TARGETS,
  /** A pack may request normal and specular companion atlases for the block atlas. */
  MATERIAL_TEXTURES
}
