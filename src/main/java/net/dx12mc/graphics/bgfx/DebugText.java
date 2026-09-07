package net.dx12mc.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

public final class DebugText {
  private static final int DEFAULT_ATTR = 0x0F;

  public void clear() {
    bgfx_dbg_text_clear(0, false);
  }

  public void print(int x, int y, String text) {
    print(x, y, DEFAULT_ATTR, text);
  }

  public void print(int x, int y, int attribute, String text) {
    bgfx_dbg_text_printf(x, y, attribute, sanitize(text));
  }

  public void colorBar(int x, int y, int cellsPerColor) {
    int width = Math.max(1, cellsPerColor);
    String block = " ".repeat(width);
    for (int color = 0; color < 16; color++) {
      int attribute = (color << 4) | 0x0F;
      bgfx_dbg_text_printf(x + color * width, y, attribute, block);
    }
  }

  private static String sanitize(String value) {
    if (value == null || value.isEmpty()) {
      return " ";
    }
    return value.replace("%", "%%");
  }
}
