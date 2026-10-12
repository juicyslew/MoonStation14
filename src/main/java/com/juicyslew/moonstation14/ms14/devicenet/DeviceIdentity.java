package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.Objects;
import java.util.UUID;

/** Stable device identity independent of its address or current owner. */
public record DeviceIdentity(UUID value) {
    public DeviceIdentity { Objects.requireNonNull(value, "value"); }
    public static DeviceIdentity random() { return new DeviceIdentity(UUID.randomUUID()); }
}
