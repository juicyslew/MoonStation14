package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks delivered manual preset commands and their correlated device acknowledgements. */
public final class ManualPresetAckTracker {
    private final Set<UUID> expectedDevices;
    private final java.util.HashMap<UUID, Long> latestRequestIds = new java.util.HashMap<>();
    private final java.util.HashSet<UUID> acknowledged = new java.util.HashSet<>();

    public ManualPresetAckTracker(Set<UUID> expectedDevices) {
        if (expectedDevices == null || expectedDevices.isEmpty()
                || expectedDevices.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("expected devices must be non-empty and non-null");
        this.expectedDevices = Set.copyOf(expectedDevices);
    }
    public void commandDelivered(UUID deviceId, long requestId, boolean enabled) {
        if (!expectedDevices.contains(deviceId) || requestId <= 0 || enabled) return;
        Long previous = latestRequestIds.put(deviceId, requestId);
        if (previous == null || previous != requestId) acknowledged.remove(deviceId);
    }
    public void resultDelivered(UUID deviceId, long requestId, boolean applied, boolean currentEnabled) {
        if (expectedDevices.contains(deviceId) && applied && !currentEnabled
                && java.util.Objects.equals(latestRequestIds.get(deviceId), requestId)) acknowledged.add(deviceId);
    }
    public boolean ready() { return acknowledged.containsAll(expectedDevices); }
    public Map<UUID, Long> latestRequestIds() { return Map.copyOf(latestRequestIds); }
    public Set<UUID> acknowledged() { return Set.copyOf(acknowledged); }
    public String diagnostic() { return "expected=" + expectedDevices + " latestRequestIds=" + latestRequestIds + " acknowledged=" + acknowledged; }
}
