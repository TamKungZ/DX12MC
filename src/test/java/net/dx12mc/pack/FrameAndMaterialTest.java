package net.dx12mc.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

final class FrameAndMaterialTest {
  @Test
  void sunFollowsTheVanillaSkyRotation() {
    float[] sun = new float[4];
    FrameUniforms.sunDirection(0.0f, sun);
    assertArrayEquals(new float[] {0, 1, 0, 0}, sun, 1e-6f);
    FrameUniforms.sunDirection((float) (Math.PI / 2), sun);
    assertEquals(-1.0f, sun[0], 1e-6f);
    assertEquals(0.0f, sun[1], 1e-6f);
  }

  @Test
  void previousViewProjectionReprojectsTheCurrentCameraRelativePosition() {
    FrameUniforms frame = new FrameUniforms();
    Matrix4f projection = new Matrix4f().perspective(1.2f, 1.6f, 0.05f, 512.0f);
    Matrix4f rotation = new Matrix4f().rotateX(0.3f);
    frame.camera(projection, rotation, 100.0, 64.0, -20.0, 0, 0, 0, 0);
    Matrix4f previous = new Matrix4f().set(frame.viewProj);
    frame.camera(projection, rotation, 101.5, 64.0, -19.0, 0, 0, 0, 0);
    Vector4f world = new Vector4f(110.0f, 70.0f, -5.0f, 1.0f);
    Vector4f expected =
        previous.transform(new Vector4f(world.x - 100.0f, world.y - 64.0f, world.z + 20.0f, 1.0f));
    Vector4f actual =
        new Matrix4f()
            .set(frame.prevViewProj)
            .transform(new Vector4f(world.x - 101.5f, world.y - 64.0f, world.z + 19.0f, 1.0f));
    assertEquals(expected.x, actual.x, 1e-3f);
    assertEquals(expected.y, actual.y, 1e-3f);
    assertEquals(expected.z, actual.z, 1e-3f);
    Matrix4f product =
        new Matrix4f().set(frame.invViewProj).mul(new Matrix4f().set(frame.viewProj));
    assertEquals(1.0f, product.m00(), 1e-4f);
    assertEquals(0.0f, product.m10(), 1e-4f);
  }

  @Test
  void depthUsesTheDirect3dRangeLikeTheBuiltInPipelines() {
    FrameUniforms frame = new FrameUniforms();
    Matrix4f projection = new Matrix4f().perspective(1.2f, 1.6f, 0.05f, 512.0f);
    frame.camera(projection, new Matrix4f(), 0, 0, 0, 0, 0, 0, 0);
    Vector4f near = new Matrix4f().set(frame.proj).transform(new Vector4f(0, 0, -0.05f, 1));
    assertEquals(0.0f, near.z / near.w, 1e-4f);
  }

  @Test
  void companionsLandAtTheirSpritePositionAndGapsUseTheDefaults() {
    MaterialAtlas.Layout layout =
        new MaterialAtlas.Layout(
            1,
            8,
            4,
            1,
            List.of(
                new MaterialAtlas.Sprite("a", 0, 0, 2, 2),
                new MaterialAtlas.Sprite("animated", 2, 0, 2, 2),
                new MaterialAtlas.Sprite("wrong", 4, 0, 2, 2)));
    byte[] red = solid(2, 2, 200);
    byte[] strip = new byte[2 * 4 * 4];
    for (int i = 0; i < 2 * 2 * 4; i++) strip[i] = 40;
    for (int i = 2 * 2 * 4; i < strip.length; i++) strip[i] = 90;
    byte[][] levels =
        MaterialAtlas.build(
            layout,
            Map.of(
                "a", new RgbaImage(2, 2, red),
                "animated", new RgbaImage(2, 4, strip),
                "wrong", new RgbaImage(4, 4, solid(4, 4, 10))),
            new int[] {128, 128, 255, 255});
    assertEquals(200, Byte.toUnsignedInt(levels[0][0]));
    assertEquals(40, Byte.toUnsignedInt(levels[0][(1 * 8 + 3) * 4]));
    assertEquals(128, Byte.toUnsignedInt(levels[0][(0 * 8 + 4) * 4]));
    assertEquals(128, Byte.toUnsignedInt(levels[0][(3 * 8 + 0) * 4]));
    assertEquals(2, levels.length);
    assertEquals(4 * 2 * 4, levels[1].length);
    assertEquals(200, Byte.toUnsignedInt(levels[1][0]));
    assertEquals(40, Byte.toUnsignedInt(levels[1][4]));
    assertEquals((8 * 4 + 4 * 2) * 4L, MaterialAtlas.bytes(layout));
  }

  private static byte[] solid(int width, int height, int value) {
    byte[] bytes = new byte[width * height * 4];
    java.util.Arrays.fill(bytes, (byte) value);
    return bytes;
  }
}
