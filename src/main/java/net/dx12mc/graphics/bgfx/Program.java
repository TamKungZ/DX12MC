package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import java.io.IOException;
import java.io.InputStream;
import org.lwjgl.bgfx.BGFXMemory;

public final class Program implements AutoCloseable {
  private short handle = BGFX_INVALID_HANDLE;

  private Program(short handle) {
    this.handle = handle;
  }

  public static Program load(String vertexResource, String fragmentResource) {
    short vertex = loadShader(vertexResource, "vertex");
    short fragment = BGFX_INVALID_HANDLE;
    try {
      fragment = loadShader(fragmentResource, "fragment");
      short program = bgfx_create_program(vertex, fragment, true);
      if (!BGFX_HANDLE_IS_VALID(program)) {
        throw new IllegalStateException(
            "bgfx rejected shader program " + vertexResource + " + " + fragmentResource);
      }
      return new Program(program);
    } catch (Throwable throwable) {
      if (BGFX_HANDLE_IS_VALID(vertex)) {
        bgfx_destroy_shader(vertex);
      }
      if (BGFX_HANDLE_IS_VALID(fragment)) {
        bgfx_destroy_shader(fragment);
      }
      throw throwable;
    }
  }

  public short handle() {
    if (!BGFX_HANDLE_IS_VALID(handle)) {
      throw new IllegalStateException("program is closed");
    }
    return handle;
  }

  @Override
  public void close() {
    if (BGFX_HANDLE_IS_VALID(handle)) {
      bgfx_destroy_program(handle);
      handle = BGFX_INVALID_HANDLE;
    }
  }

  private static short loadShader(String resource, String stage) {
    byte[] bytes;
    try (InputStream input = Program.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException(
            "Missing compiled " + stage + " shader resource: " + resource);
      }
      bytes = input.readAllBytes();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to read " + stage + " shader resource: " + resource, exception);
    }
    BGFXMemory memory = StagingMemory.copy(bytes);
    short shader = bgfx_create_shader(memory);
    if (!BGFX_HANDLE_IS_VALID(shader)) {
      throw new IllegalStateException("bgfx rejected compiled " + stage + " shader: " + resource);
    }
    bgfx_set_shader_name(shader, "DX12MC " + stage);
    return shader;
  }
}
