package net.dx12mc.render;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class SectionRenderContextTest {

  @Test
  void precomputedLayerTransformMatchesPerDrawMatrixComposition() {
    Matrix4f modelView = new Matrix4f().rotateX(0.31f).rotateY(-0.72f).translate(4.0f, -3.0f, 8.0f);
    Matrix4f projection = new Matrix4f().perspective(1.1f, 16.0f / 9.0f, 0.05f, 512.0f);
    Matrix4f expected =
        new Matrix4f()
            .identity()
            .m22(0.5f)
            .m32(0.5f)
            .mul(projection)
            .mul(modelView)
            .translate(32.0f, -16.0f, 48.0f);

    SectionRenderContext context = new SectionRenderContext();
    context.begin(modelView, projection, 1920, 1080);
    context.offset(32.0f, -16.0f, 48.0f);

    assertTrue(expected.equals(context.drawClipTransform(), 1.0e-6f));
    assertEquals(1920, context.framebufferWidth());
    assertEquals(1080, context.framebufferHeight());
  }
}
