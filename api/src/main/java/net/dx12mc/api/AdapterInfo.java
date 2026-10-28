package net.dx12mc.api;

/**
 * The graphics adapter the renderer opened. Driver version and feature level are empty strings when
 * they could not be read.
 *
 * @param featureLevel the Direct3D feature level, for example {@code 12_1}
 */
public record AdapterInfo(
    String name, int vendorId, int deviceId, String driverVersion, String featureLevel) {}
