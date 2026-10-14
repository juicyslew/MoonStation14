package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import java.util.List;
import java.util.UUID;

/** Authoritative port-link view; never contains client-authored graph state. */
public record NetworkConfiguratorSnapshot(UUID session, long requestId, long epoch, boolean open,
                                         DeviceIdentity source, DeviceIdentity sink, String sourceAddress,
                                         String sinkAddress, List<Port> sources, List<Port> sinks,
                                         List<Pair> links) {
    public record Port(String id, String name, String description) { }
    public record Pair(String sourcePort, String sinkPort) { }
    public NetworkConfiguratorSnapshot { sources=List.copyOf(sources); sinks=List.copyOf(sinks); links=List.copyOf(links); }
}

