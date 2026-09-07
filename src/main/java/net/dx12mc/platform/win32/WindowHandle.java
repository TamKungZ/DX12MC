package net.dx12mc.platform.win32;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;

public final class WindowHandle {

  private WindowHandle() {}

  public static boolean supported() {
    return Platform.get() == Platform.WINDOWS;
  }

  public static long fromGlfw(long glfwWindow) {
    if (!supported()) {
      throw new UnsupportedOperationException("DX12MC currently supports Windows only");
    }
    long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
    if (hwnd == 0L) {
      throw new IllegalStateException("GLFW returned a null Win32 HWND");
    }
    return hwnd;
  }
}
