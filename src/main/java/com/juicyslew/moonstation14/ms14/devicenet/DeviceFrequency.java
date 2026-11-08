package com.juicyslew.moonstation14.ms14.devicenet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DeviceFrequency(int frequency) {
    public static final Codec<DeviceFrequency> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("frequency").forGetter(DeviceFrequency::frequency)).apply(i, DeviceFrequency::new));
    public DeviceFrequency { if (frequency < 1 || frequency > 9999) throw new IllegalArgumentException("frequency out of range"); }
}

