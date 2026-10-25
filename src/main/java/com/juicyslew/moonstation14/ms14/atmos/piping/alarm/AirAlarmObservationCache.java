package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Immutable observation view; membership remains owned by AirAlarmDeviceLinks. */
public final class AirAlarmObservationCache {
    private Map<String, AtmosDevicePayload.State> states = Map.of();

    public synchronized boolean observe(String address, AtmosDevicePayload.State state, Set<String> allowed) {
        if (address == null || address.isBlank() || state == null || !allowed.contains(address)) return false;
        Map<String, AtmosDevicePayload.State> next = new TreeMap<>(states);
        next.put(address, state);
        states = Map.copyOf(next);
        return true;
    }

    public synchronized Map<String, AtmosDevicePayload.State> snapshot(Set<String> allowed) {
        Map<String, AtmosDevicePayload.State> next = new TreeMap<>(states);
        next.keySet().retainAll(allowed);
        return Map.copyOf(next);
    }

    public synchronized Map<String, AtmosDevicePayload.State> snapshot() { return states; }

    public synchronized void clear() { states = Map.of(); }
}

