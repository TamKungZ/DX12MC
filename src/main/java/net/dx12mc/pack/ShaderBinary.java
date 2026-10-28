package net.dx12mc.pack;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ShaderBinary(
    char stage,
    int version,
    int hashIn,
    int hashOut,
    List<Uniform> uniforms,
    byte[] bytes,
    int[] attributes,
    int constantBufferBytes) {

  public static final int MAX_BYTES = 1 << 20;
  static final int MAX_UNIFORMS = 64;
  static final int MAX_ATTRIBUTES = 18;
  static final int TYPE_SAMPLER = 0;
  static final int TYPE_VEC4 = 2;
  static final int TYPE_MAT3 = 3;
  static final int TYPE_MAT4 = 4;
  static final int SAMPLER_BIT = 0x20;
  public static final int ATTRIB_POSITION = 0x0001;
  public static final int ATTRIB_NORMAL = 0x0002;
  public static final int ATTRIB_COLOR0 = 0x0005;
  public static final int ATTRIB_TEXCOORD0 = 0x0010;
  public static final int ATTRIB_TEXCOORD1 = 0x0011;
  public static final int ATTRIB_TEXCOORD2 = 0x0012;
  private static final int DXBC_PIXEL = 0;
  private static final int DXBC_VERTEX = 1;

  public record Uniform(String name, int type, int count, int register, int registerCount) {
    public boolean sampler() {
      return (type & SAMPLER_BIT) != 0;
    }

    public int baseType() {
      return type & 0x0F;
    }
  }

  public static ShaderBinary parse(byte[] bytes) {
    if (bytes.length < 16 || bytes.length > MAX_BYTES) {
      throw new InvalidPackException("shader binary size " + bytes.length + " is out of range");
    }
    try {
      return read(bytes);
    } catch (BufferUnderflowException | IndexOutOfBoundsException exception) {
      throw new InvalidPackException("shader binary is truncated");
    }
  }

  private static ShaderBinary read(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    char stage = (char) in.get();
    if ((stage != 'V' && stage != 'F') || in.get() != 'S' || in.get() != 'H') {
      throw new InvalidPackException("not a bgfx vertex or fragment shader");
    }
    int version = Byte.toUnsignedInt(in.get());
    int hashIn = in.getInt();
    int hashOut = in.getInt();
    int count = Short.toUnsignedInt(in.getShort());
    if (count > MAX_UNIFORMS)
      throw new InvalidPackException(count + " uniforms exceed " + MAX_UNIFORMS);
    List<Uniform> uniforms = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      int nameLength = Byte.toUnsignedInt(in.get());
      if (nameLength == 0 || nameLength > 64)
        throw new InvalidPackException("bad uniform name length");
      byte[] name = new byte[nameLength];
      in.get(name);
      for (byte character : name) {
        if (!(character == '_' || Character.isLetterOrDigit(character))) {
          throw new InvalidPackException("bad uniform name");
        }
      }
      int type = Byte.toUnsignedInt(in.get());
      int num = Byte.toUnsignedInt(in.get());
      int register = Short.toUnsignedInt(in.getShort());
      int registerCount = Short.toUnsignedInt(in.getShort());
      in.getShort();
      in.getShort();
      int base = type & 0x0F;
      if (base != TYPE_SAMPLER && base != TYPE_VEC4 && base != TYPE_MAT3 && base != TYPE_MAT4) {
        throw new InvalidPackException("unknown uniform type " + type);
      }
      uniforms.add(
          new Uniform(
              new String(name, StandardCharsets.US_ASCII), type, num, register, registerCount));
    }
    int codeSize = in.getInt();
    if (codeSize < 32 || codeSize > in.remaining()) throw new InvalidPackException("bad code size");
    byte[] code = new byte[codeSize];
    in.get(code);
    if (in.get() != 0) throw new InvalidPackException("code is not terminated");
    int attributeCount = Byte.toUnsignedInt(in.get());
    if (attributeCount > MAX_ATTRIBUTES) throw new InvalidPackException("too many attributes");
    int[] attributes = new int[attributeCount];
    for (int i = 0; i < attributeCount; i++) {
      attributes[i] = Short.toUnsignedInt(in.getShort());
      if (!knownAttribute(attributes[i])) {
        throw new InvalidPackException(
            "unknown vertex attribute 0x" + Integer.toHexString(attributes[i]));
      }
    }
    int constantBufferBytes = Short.toUnsignedInt(in.getShort());
    if (in.hasRemaining()) throw new InvalidPackException("trailing bytes after the shader");
    for (Uniform uniform : uniforms) {
      if (!uniform.sampler()
          && uniform.register() + uniform.registerCount() * 16L > constantBufferBytes) {
        throw new InvalidPackException(
            "uniform " + uniform.name() + " lies outside the constant buffer");
      }
      if (uniform.sampler() && uniform.register() >= 16) {
        throw new InvalidPackException(
            "sampler " + uniform.name() + " uses register " + uniform.register());
      }
    }
    checkDxbc(code, stage);
    return new ShaderBinary(
        stage,
        version,
        hashIn,
        hashOut,
        List.copyOf(uniforms),
        bytes,
        attributes,
        constantBufferBytes);
  }

  private static boolean knownAttribute(int id) {
    return (id >= 0x0001 && id <= 0x0006)
        || id == 0x000e
        || id == 0x000f
        || (id >= 0x0010 && id <= 0x0019);
  }

  private static void checkDxbc(byte[] code, char stage) {
    ByteBuffer in = ByteBuffer.wrap(code).order(ByteOrder.LITTLE_ENDIAN);
    if (code[0] != 'D' || code[1] != 'X' || code[2] != 'B' || code[3] != 'C') {
      throw new InvalidPackException("shader code is not DXBC (profile mismatch)");
    }
    if (in.getInt(20) != 1 || in.getInt(24) != code.length) {
      throw new InvalidPackException("DXBC header is inconsistent");
    }
    if (!DxbcChecksum.matches(code)) throw new InvalidPackException("DXBC checksum does not match");
    int chunks = in.getInt(28);
    if (chunks <= 0 || chunks > 32 || 32 + chunks * 4 > code.length) {
      throw new InvalidPackException("DXBC chunk table is invalid");
    }
    boolean program = false;
    for (int i = 0; i < chunks; i++) {
      int offset = in.getInt(32 + i * 4);
      if (offset < 32 || offset + 8L > code.length)
        throw new InvalidPackException("DXBC chunk outside the code");
      int size = in.getInt(offset + 4);
      if (size < 0 || offset + 8L + size > code.length)
        throw new InvalidPackException("DXBC chunk outside the code");
      String tag = new String(code, offset, 4, StandardCharsets.US_ASCII);
      if (tag.equals("SHDR") || tag.equals("SHEX")) {
        int token = in.getInt(offset + 8);
        int type = token >>> 16;
        int major = (token >>> 4) & 0xF;
        int minor = token & 0xF;
        if (type != (stage == 'V' ? DXBC_VERTEX : DXBC_PIXEL)) {
          throw new InvalidPackException("DXBC stage does not match the bgfx shader stage");
        }
        if (major != 5 || minor != 0) {
          throw new InvalidPackException("shader model " + major + "." + minor + " is not 5.0");
        }
        program = true;
      }
    }
    if (!program) throw new InvalidPackException("DXBC has no shader program");
  }

  public Uniform uniform(String name) {
    for (Uniform uniform : uniforms) if (uniform.name().equals(name)) return uniform;
    return null;
  }

  public boolean hasAttribute(int id) {
    return Arrays.stream(attributes).anyMatch(attribute -> attribute == id);
  }
}
