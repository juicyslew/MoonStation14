package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Immutable observation view; membership remains owned by AirAlarmDeviceLinks. */
public final class AirAlarmObservationCache {
    private Map<String, AtmosDevicePayload.State> states = Map.of();
    private Map<UUID, AtmosAlarmPayload.Measurement> reports = Map.of();

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

    /** Retains the last valid report across source unload/power loss; identity is canonical linked UUID. */
    public synchronized boolean observeReport(UUID source, AtmosAlarmPayload.Measurement measurement, Set<UUID> members) {
        if (source == null || measurement == null || members == null || !members.contains(source)) return false;
        if (!measurement.valid()) return true;
        var next = new java.util.HashMap<>(reports);
        next.put(source, measurement);
        reports = Map.copyOf(next);
        return true;
    }

    public synchronized Map<UUID, AtmosAlarmPayload.Measurement> reportSnapshot(Set<UUID> members) {
        var next = new java.util.HashMap<>(reports);
        next.keySet().retainAll(members);
        return Map.copyOf(next);
    }
    public synchronized void removeReport(UUID source) { var next=new java.util.HashMap<>(reports); next.remove(source); reports=Map.copyOf(next); }

    public synchronized void clear() { states = Map.of(); reports = Map.of(); }
}
