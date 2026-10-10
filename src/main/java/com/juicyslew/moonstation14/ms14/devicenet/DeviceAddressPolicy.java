package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** Short network-address generation and retained-address binding policy. */
public final class DeviceAddressPolicy {
    private DeviceAddressPolicy() { }

    public static String generate(String prefix) {
        return generate(prefix, ThreadLocalRandom.current()::nextInt);
    }

    public static String generate(String prefix, IntSupplier random32) {
        Objects.requireNonNull(prefix); Objects.requireNonNull(random32);
        int value=random32.getAsInt();
        return prefix + String.format(Locale.ROOT, "%04X-%04X", value >>> 16, value & 0xffff);
    }

    public static DeviceComposition retained(DeviceComposition composition, Optional<String> address) {
        Objects.requireNonNull(composition); Objects.requireNonNull(address);
        var network=composition.network().orElse(null);
        if(network==null || network.customAddress()) return composition;
        return address.filter(value -> !value.isBlank()).map(composition::withAddress).orElse(composition);
    }
}
