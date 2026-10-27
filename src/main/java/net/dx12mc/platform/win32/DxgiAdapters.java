package net.dx12mc.platform.win32;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
  private static final int ADAPTER_CHECK_INTERFACE_SUPPORT = 9;
  private static final int DEVICE_CHECK_FEATURE_SUPPORT = 13;
  private static final int FEATURE_LEVELS = 2;
  private static final int FEATURE_LEVELS_DATA_BYTES = 24;
  private static final int MINIMUM_FEATURE_LEVEL = 0xB000;
  private static final int[] REQUESTED_LEVELS = {0xB000, 0xB100, 0xC000, 0xC100, 0xC200};
  private static final int MAX_ADAPTERS = 64;

  private DxgiAdapters() {}

  public record Details(
      String description, long driverVersion, int featureLevel, List<String> problems) {}

  public static Details query(int vendorId, int deviceId) {
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
            if (vendorId(desc) == vendorId && deviceId(desc) == deviceId) {
              return details(adapter, name(desc), stack);
            }
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

  private static Details details(long adapter, String name, MemoryStack stack) {
    List<String> problems = new ArrayList<>();
    long driverVersion = 0L;
    int featureLevel = 0;
    try {
      driverVersion = driverVersion(adapter, stack);
    } catch (RuntimeException | LinkageError failure) {
      problems.add("Unable to read the graphics driver version: " + failure.getMessage());
    }
    try {
      featureLevel = featureLevel(adapter, stack);
    } catch (RuntimeException | LinkageError failure) {
      problems.add("Unable to read the Direct3D feature level: " + failure.getMessage());
    }
    return new Details(name, driverVersion, featureLevel, List.copyOf(problems));
  }

  private static long driverVersion(long adapter, MemoryStack stack) {
    ByteBuffer iid =
        guid(
            stack,
            0x54ec77fa,
            (short) 0x1377,
            (short) 0x44e6,
            0x8c,
            0x32,
            0x88,
            0xfd,
            0x5f,
            0x44,
            0xc8,
            0x4c);
    java.nio.LongBuffer version = stack.mallocLong(1);
    int result =
        JNI.callPPPI(
            adapter,
            memAddress(iid),
            memAddress(version),
            method(adapter, ADAPTER_CHECK_INTERFACE_SUPPORT));
    if (result < 0) throw new IllegalStateException(hresult("CheckInterfaceSupport", result));
    return version.get(0);
  }

  private static int featureLevel(long adapter, MemoryStack stack) {
    WindowsLibrary d3d12 = new WindowsLibrary("d3d12.dll");
    try {
      long createDevice = d3d12.getFunctionAddress("D3D12CreateDevice");
      if (createDevice == 0L) throw new IllegalStateException("D3D12CreateDevice is missing");
      ByteBuffer iid =
          guid(
              stack,
              0x189819f1,
              (short) 0x1db6,
              (short) 0x4b57,
              0xbe,
              0x54,
              0x18,
              0x21,
              0x33,
              0x9b,
              0x85,
              0xf7);
      PointerBuffer out = stack.mallocPointer(1);
      int result =
          JNI.callPPPPI(
              adapter, MINIMUM_FEATURE_LEVEL, memAddress(iid), memAddress(out), createDevice);
      if (result < 0) throw new IllegalStateException(hresult("D3D12CreateDevice", result));
      long device = out.get(0);
      try {
        java.nio.IntBuffer levels = stack.mallocInt(REQUESTED_LEVELS.length);
        levels.put(REQUESTED_LEVELS).flip();
        ByteBuffer data = stack.calloc(8, FEATURE_LEVELS_DATA_BYTES).order(ByteOrder.nativeOrder());
        data.putInt(0, REQUESTED_LEVELS.length);
        data.putLong(8, memAddress(levels));
        result =
            JNI.callPPPPI(
                device,
                FEATURE_LEVELS,
                memAddress(data),
                FEATURE_LEVELS_DATA_BYTES,
                method(device, DEVICE_CHECK_FEATURE_SUPPORT));
        if (result < 0) throw new IllegalStateException(hresult("CheckFeatureSupport", result));
        return data.getInt(16);
      } finally {
        release(device);
      }
    } finally {
      d3d12.free();
    }
  }

  private static ByteBuffer guid(
      MemoryStack stack, int data1, short data2, short data3, int... tail) {
    ByteBuffer iid = stack.malloc(16).order(ByteOrder.LITTLE_ENDIAN);
    iid.putInt(0, data1).putShort(4, data2).putShort(6, data3);
    for (int i = 0; i < tail.length; i++) iid.put(8 + i, (byte) tail[i]);
    return iid;
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
