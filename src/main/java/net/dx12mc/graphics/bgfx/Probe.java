package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.bgfx.BGFX;

public final class Probe {

  private Probe() {}

  public static ProbeResult detect() {
    int[] rendererTypes = new int[BGFX_RENDERER_TYPE_COUNT];
    int count = Byte.toUnsignedInt(bgfx_get_supported_renderers(rendererTypes));
    List<String> names = new ArrayList<>(count);
    boolean hasD3D12 = false;
    for (int i = 0; i < count; i++) {
      int type = rendererTypes[i];
      String name = BGFX.bgfx_get_renderer_name(type);
      names.add(name == null ? Integer.toString(type) : name);
      hasD3D12 |= type == BGFX_RENDERER_TYPE_DIRECT3D12;
    }
    return new ProbeResult(hasD3D12, List.copyOf(names));
  }
}
