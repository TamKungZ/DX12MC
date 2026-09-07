package net.dx12mc.debug;

import net.dx12mc.graphics.bgfx.DebugText;

final class OverlayWriter {
  private final DebugText text;
  private int row = 1;

  OverlayWriter(DebugText text) {
    this.text = text;
  }

  void line(int color, String value) {
    text.print(2, row++, color, value);
  }

  void gap() {
    row++;
  }

  void colorBar(int x, int height) {
    text.colorBar(x, row++, height);
  }
}
