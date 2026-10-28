package net.dx12mc.api;

/** An API version. Before 1.0 a minor release may break compatibility. */
public record ApiVersion(int major, int minor) implements Comparable<ApiVersion> {
  public ApiVersion {
    if (major < 0 || minor < 0) throw new IllegalArgumentException("negative API version");
  }

  public static ApiVersion parse(String text) {
    int dot = text.indexOf('.');
    if (dot <= 0 || dot == text.length() - 1) {
      throw new IllegalArgumentException("API version must be major.minor: " + text);
    }
    return new ApiVersion(
        Integer.parseInt(text.substring(0, dot)), Integer.parseInt(text.substring(dot + 1)));
  }

  /**
   * Whether a mod built against {@code required} can use this version: the same major version and
   * at least the same minor version, or exactly the same version while the major version is 0.
   */
  public boolean satisfies(ApiVersion required) {
    if (major != required.major) return false;
    return major == 0 ? minor == required.minor : minor >= required.minor;
  }

  @Override
  public int compareTo(ApiVersion other) {
    return major != other.major
        ? Integer.compare(major, other.major)
        : Integer.compare(minor, other.minor);
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }
}
