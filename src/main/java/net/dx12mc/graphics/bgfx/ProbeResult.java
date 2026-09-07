package net.dx12mc.graphics.bgfx;

import java.util.List;

public record ProbeResult(boolean direct3D12Available, List<String> renderers) {}
