package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.List;
import java.util.Objects;

/** Explicit device membership and routing policy. */
public record DeviceList(List<String> addresses, int capacity, boolean allowList, boolean filterIncoming) {
    public static final int DEFAULT_CAPACITY = 32;
    public static final DeviceList DEFAULT = new DeviceList(List.of(), DEFAULT_CAPACITY, true, false);
    /** Immutable resolved prototype config; each endpoint instantiates its own mutable membership. */
    public record Config(int capacity, boolean allowList, boolean filterIncoming) {
        public Config { if (capacity < 1) throw new IllegalArgumentException("capacity must be positive"); }
        public DeviceList instantiate() { return new DeviceList(List.of(), capacity, allowList, filterIncoming); }
    }
    public DeviceList {
        addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses"));
        if (capacity < 1 || addresses.size() > capacity) throw new IllegalArgumentException("invalid device-list capacity");
        if (addresses.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException("device addresses must be non-empty");
    }
    public boolean permits(String address, boolean incoming) {
        if (incoming && !filterIncoming) return true;
        return allowList == addresses.contains(address);
    }
}
