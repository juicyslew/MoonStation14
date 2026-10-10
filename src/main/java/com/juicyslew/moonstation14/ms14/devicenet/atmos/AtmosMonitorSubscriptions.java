package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import java.util.HashSet;
import java.util.Set;

/** Transient monitor-address subscriptions; intentionally separate from device-list membership. */
public final class AtmosMonitorSubscriptions {
    private final Set<String> addresses = new HashSet<>();

    /** Applies registration control and returns true only when the caller should answer with state. */
    public boolean receive(AtmosDevicePayload payload, String address) {
        if (payload == null || address == null || address.isBlank()) return false;
        if (payload instanceof AtmosDevicePayload.Register) addresses.add(address);
        else if (payload instanceof AtmosDevicePayload.Deregister) addresses.remove(address);
        return payload instanceof AtmosDevicePayload.Sync;
    }

    public boolean isRegistered(String address) { return addresses.contains(address); }
}
