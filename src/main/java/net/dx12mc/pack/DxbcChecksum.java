package net.dx12mc.pack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class DxbcChecksum {
  private static final int[] SHIFT = {
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9,
    14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
  };
  private static final int[] SINES = new int[64];

  static {
    for (int i = 0; i < 64; i++)
      SINES[i] = (int) (long) Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296.0);
  }

  private DxbcChecksum() {}

  static boolean matches(byte[] dxbc) {
    if (dxbc.length < 32) return false;
    int[] expected = new int[4];
    ByteBuffer header = ByteBuffer.wrap(dxbc).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 4; i++) expected[i] = header.getInt(4 + i * 4);
    int[] actual = compute(dxbc);
    for (int i = 0; i < 4; i++) if (expected[i] != actual[i]) return false;
    return true;
  }

  static int[] compute(byte[] dxbc) {
    int start = 20;
    int length = dxbc.length - start;
    int bits = length * 8;
    int[] state = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476};
    int full = length / 64 * 64;
    int[] words = new int[16];
    ByteBuffer data = ByteBuffer.wrap(dxbc).order(ByteOrder.LITTLE_ENDIAN);
    for (int offset = 0; offset < full; offset += 64) {
      for (int i = 0; i < 16; i++) words[i] = data.getInt(start + offset + i * 4);
      transform(state, words);
    }
    int left = length - full;
    byte[] block = new byte[64];
    ByteBuffer last = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
    if (left < 56) {
      last.putInt(0, bits);
      System.arraycopy(dxbc, start + full, block, 4, left);
      block[4 + left] = (byte) 0x80;
      last.putInt(60, (bits >>> 2) | 1);
      transform(state, toWords(last, words));
    } else {
      System.arraycopy(dxbc, start + full, block, 0, left);
      block[left] = (byte) 0x80;
      transform(state, toWords(last, words));
      java.util.Arrays.fill(block, (byte) 0);
      last.putInt(0, bits);
      last.putInt(60, (bits >>> 2) | 1);
      transform(state, toWords(last, words));
    }
    return state;
  }

  private static int[] toWords(ByteBuffer block, int[] words) {
    for (int i = 0; i < 16; i++) words[i] = block.getInt(i * 4);
    return words;
  }

  private static void transform(int[] state, int[] m) {
    int a = state[0];
    int b = state[1];
    int c = state[2];
    int d = state[3];
    for (int i = 0; i < 64; i++) {
      int f;
      int g;
      if (i < 16) {
        f = (b & c) | (~b & d);
        g = i;
      } else if (i < 32) {
        f = (d & b) | (~d & c);
        g = (5 * i + 1) % 16;
      } else if (i < 48) {
        f = b ^ c ^ d;
        g = (3 * i + 5) % 16;
      } else {
        f = c ^ (b | ~d);
        g = (7 * i) % 16;
      }
      f = f + a + SINES[i] + m[g];
      a = d;
      d = c;
      c = b;
      b = b + Integer.rotateLeft(f, SHIFT[i]);
    }
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
  }
}
