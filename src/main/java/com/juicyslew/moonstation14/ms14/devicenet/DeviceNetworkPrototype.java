package com.juicyslew.moonstation14.ms14.devicenet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Network IDs are distinct from connection technologies such as wired/wireless. */
public record DeviceNetworkPrototype(int networkId) {
    public static final Codec<DeviceNetworkPrototype> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("networkId").forGetter(DeviceNetworkPrototype::networkId)).apply(i, DeviceNetworkPrototype::new));
    public DeviceNetworkPrototype { if (networkId < 0) throw new IllegalArgumentException("negative network ID"); }
}
