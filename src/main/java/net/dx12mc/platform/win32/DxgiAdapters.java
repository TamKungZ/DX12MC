package net.dx12mc.platform.win32;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.windows.WindowsLibrary;

public final class DxgiAdapters {
  static final int DESC1_BYTES = 312;
  private static final int DESCRIPTION_CHARS = 128;
  private static final int VENDOR_ID_OFFSET = 256;
  private static final int DEVICE_ID_OFFSET = 260;
  private static final int DXGI_ERROR_NOT_FOUND = 0x887A0002;
  private static final int RELEASE = 2;
  private static final int FACTORY1_ENUM_ADAPTERS1 = 12;
  private static final int ADAPTER1_GET_DESC1 = 10;
  private static final int MAX_ADAPTERS = 64;

  private DxgiAdapters() {}

  public static String description(int vendorId, int deviceId) {
    if (!WindowHandle.supported()) return null;
    WindowsLibrary dxgi = new WindowsLibrary("dxgi.dll");
    try (MemoryStack stack = MemoryStack.stackPush()) {
      long createFactory = dxgi.getFunctionAddress("CreateDXGIFactory1");
      if (createFactory == 0L) return null;
      PointerBuffer out = stack.mallocPointer(1);
      int result = JNI.callPPI(memAddress(factory1Iid(stack)), memAddress(out), createFactory);
      if (result < 0) throw new IllegalStateException(hresult("CreateDXGIFactory1", result));
      long factory = out.get(0);
      try {
        ByteBuffer desc = stack.calloc(8, DESC1_BYTES);
        for (int index = 0; index < MAX_ADAPTERS; index++) {
          result =
              JNI.callPPI(
                  factory, index, memAddress(out), method(factory, FACTORY1_ENUM_ADAPTERS1));
          if (result == DXGI_ERROR_NOT_FOUND) return null;
          if (result < 0) throw new IllegalStateException(hresult("EnumAdapters1", result));
          long adapter = out.get(0);
          try {
            result = JNI.callPPI(adapter, memAddress(desc), method(adapter, ADAPTER1_GET_DESC1));
            if (result < 0) throw new IllegalStateException(hresult("GetDesc1", result));
            if (vendorId(desc) == vendorId && deviceId(desc) == deviceId) return name(desc);
          } finally {
            release(adapter);
          }
        }
        return null;
      } finally {
        release(factory);
      }
    } finally {
      dxgi.free();
    }
  }

  static int vendorId(ByteBuffer desc) {
    return desc.order(ByteOrder.LITTLE_ENDIAN).getInt(VENDOR_ID_OFFSET);
  }

  static int deviceId(ByteBuffer desc) {
    return desc.order(ByteOrder.LITTLE_ENDIAN).getInt(DEVICE_ID_OFFSET);
  }

  static String name(ByteBuffer desc) {
    byte[] utf16 = new byte[DESCRIPTION_CHARS * 2];
    int length = 0;
    while (length < DESCRIPTION_CHARS) {
      byte low = desc.get(length * 2);
      byte high = desc.get(length * 2 + 1);
      if (low == 0 && high == 0) break;
      utf16[length * 2] = low;
      utf16[length * 2 + 1] = high;
      length++;
    }
    return new String(utf16, 0, length * 2, StandardCharsets.UTF_16LE).trim();
  }

  private static ByteBuffer factory1Iid(MemoryStack stack) {
    ByteBuffer iid = stack.malloc(16).order(ByteOrder.LITTLE_ENDIAN);
    iid.putInt(0, 0x770aae78).putShort(4, (short) 0xf26f).putShort(6, (short) 0x4dba);
    byte[] tail = {
      (byte) 0xa8, 0x29, 0x25, 0x3c, (byte) 0x83, (byte) 0xd1, (byte) 0xb3, (byte) 0x87
    };
    for (int i = 0; i < tail.length; i++) iid.put(8 + i, tail[i]);
    return iid;
  }

  private static long method(long object, int index) {
    return memGetAddress(memGetAddress(object) + (long) index * Pointer.POINTER_SIZE);
  }

  private static void release(long object) {
    if (object != 0L) JNI.callPI(object, method(object, RELEASE));
  }

  private static String hresult(String call, int result) {
    return call + " failed with HRESULT 0x" + Integer.toHexString(result);
  }
}
