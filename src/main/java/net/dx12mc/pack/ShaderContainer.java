package net.dx12mc.pack;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ShaderContainer {
  public static final int FORMAT = 1;
  private static final byte[] MAGIC = {'D', 'X', 'S', 'H'};

  private ShaderContainer() {}

  public static ShaderBinary read(byte[] file) {
    if (file.length < 16 || file.length > ShaderBinary.MAX_BYTES + 64) {
      throw new InvalidPackException("shader file size " + file.length + " is out of range");
    }
    if (!Arrays.equals(Arrays.copyOf(file, 4), MAGIC)) {
      throw new InvalidPackException(
          "not a DX12MC shader file (compile it with compileShaderPack)");
    }
    ByteBuffer in = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
    in.position(4);
    try {
      int format = Short.toUnsignedInt(in.getShort());
      if (format != FORMAT)
        throw new InvalidPackException("shader file format " + format + " is not " + FORMAT);
      int contract = Short.toUnsignedInt(in.getShort());
      if (contract != ShaderContract.VERSION) {
        throw new InvalidPackException(
            "built for shader contract "
                + contract
                + ", this DX12MC supports "
                + ShaderContract.VERSION);
      }
      char stage = (char) in.get();
      int bgfxVersion = Byte.toUnsignedInt(in.get());
      byte[] profile = new byte[Byte.toUnsignedInt(in.get())];
      in.get(profile);
      String profileName = new String(profile, StandardCharsets.US_ASCII);
      if (!profileName.equals(ShaderContract.PROFILE)) {
        throw new InvalidPackException(
            "built for profile " + profileName + ", this DX12MC needs " + ShaderContract.PROFILE);
      }
      if (bgfxVersion != ShaderContract.BGFX_SHADER_VERSION) {
        throw new InvalidPackException(
            "built for bgfx shader binary version "
                + bgfxVersion
                + ", this DX12MC loads "
                + ShaderContract.BGFX_SHADER_VERSION);
      }
      int length = in.getInt();
      if (length != in.remaining())
        throw new InvalidPackException("shader payload length is wrong");
      byte[] payload = new byte[length];
      in.get(payload);
      ShaderBinary binary = ShaderBinary.parse(payload);
      if (binary.stage() != stage || binary.version() != bgfxVersion) {
        throw new InvalidPackException("shader header disagrees with its bgfx binary");
      }
      return binary;
    } catch (BufferUnderflowException exception) {
      throw new InvalidPackException("shader file is truncated");
    }
  }

  public static byte[] write(byte[] bgfxBinary, int contract, String profile) {
    ShaderBinary binary = ShaderBinary.parse(bgfxBinary);
    byte[] profileBytes = profile.getBytes(StandardCharsets.US_ASCII);
    ByteBuffer out =
        ByteBuffer.allocate(4 + 2 + 2 + 1 + 1 + 1 + profileBytes.length + 4 + bgfxBinary.length)
            .order(ByteOrder.LITTLE_ENDIAN);
    out.put(MAGIC).putShort((short) FORMAT).putShort((short) contract);
    out.put((byte) binary.stage()).put((byte) binary.version());
    out.put((byte) profileBytes.length).put(profileBytes);
    out.putInt(bgfxBinary.length).put(bgfxBinary);
    return out.array();
  }
}
