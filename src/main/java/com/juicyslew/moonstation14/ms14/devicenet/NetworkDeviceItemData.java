package com.juicyslew.moonstation14.ms14.devicenet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

/** Typed, resolved policy for handheld network tools. */
public record NetworkDeviceItemData(DeviceComponents deviceComponents, Optional<Float> useDelaySeconds,
                                    Optional<String> signallerPort, Optional<ConfiguratorPolicy> configurator) {
    public static final Codec<ConfiguratorPolicy> CONFIGURATOR_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("link_default", false).forGetter(ConfiguratorPolicy::linkDefault),
            Codec.BOOL.optionalFieldOf("settings_frequency", false).forGetter(ConfiguratorPolicy::settingsFrequency)
    ).apply(i, ConfiguratorPolicy::new));
    public static final Codec<NetworkDeviceItemData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DeviceComponents.CODEC.fieldOf("deviceComponents").forGetter(NetworkDeviceItemData::deviceComponents),
        Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 ? DataResult.success(value) : DataResult.error(() -> "use delay must be finite and nonnegative")).optionalFieldOf("use_delay_seconds").forGetter(NetworkDeviceItemData::useDelaySeconds),
            Codec.STRING.optionalFieldOf("signaller_port").forGetter(NetworkDeviceItemData::signallerPort),
            CONFIGURATOR_CODEC.optionalFieldOf("configurator").forGetter(NetworkDeviceItemData::configurator)
    ).apply(i, NetworkDeviceItemData::new));

    public NetworkDeviceItemData {
        Objects.requireNonNull(deviceComponents); Objects.requireNonNull(useDelaySeconds);
        Objects.requireNonNull(signallerPort); Objects.requireNonNull(configurator);
        useDelaySeconds.ifPresent(v -> { if (!Float.isFinite(v) || v < 0) throw new IllegalArgumentException("invalid use delay"); });
        signallerPort.ifPresent(v -> { if (v.isBlank()) throw new IllegalArgumentException("signaller port cannot be blank"); });
        if (signallerPort.isPresent() && configurator.isPresent()) throw new IllegalArgumentException("signaller and configurator policies are mutually exclusive");
    }
    public record ConfiguratorPolicy(boolean linkDefault, boolean settingsFrequency) { }
}
