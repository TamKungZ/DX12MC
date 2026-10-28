package net.dx12mc.pack;

import java.util.Map;

public record PackInput(
    String id,
    String source,
    PackManifest manifest,
    Map<String, byte[]> shaders,
    Map<String, RgbaImage> images) {}
