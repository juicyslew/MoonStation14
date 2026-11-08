package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModDeviceNetworks {
    private ModDeviceNetworks() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    public static final PrototypeType<DeviceFrequency> FREQUENCY_TYPE = new PrototypeType<>(id("device_frequency"), "moonstation14/device_frequency", DeviceFrequency.CODEC);
    public static final PrototypeType<DeviceNetworkPrototype> NETWORK_TYPE = new PrototypeType<>(id("device_network"), "moonstation14/device_network", DeviceNetworkPrototype.CODEC);
    public static final PrototypeType<DevicePortPrototype> PORT_TYPE = new PrototypeType<>(id("device_port"), "moonstation14/device_port", DevicePortPrototype.CODEC);
    public static final ResourceLocation BASIC_DEVICE = id("basic_device");
    public static final ResourceLocation ATMOS_MONITOR = id("atmos_monitor");
    public static final ResourceLocation WIRELESS = id("wireless");
    public static final ResourceLocation ATMOS_DEVICES = id("atmos_devices");
    public static final int WIRELESS_NETWORK_ID = 2;
}

