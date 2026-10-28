package net.dx12mc.api;

import java.nio.FloatBuffer;

/** Writes the values of one pack uniform. Called on the render thread once per frame. */
@FunctionalInterface
public interface UniformProvider {
  /**
   * @param values four floats per declared vec4, sixteen per mat4 in column-major order. The buffer
   *     is reused; do not keep a reference to it.
   */
  void update(FloatBuffer values);
}
