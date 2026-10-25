package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Link invocation/replay and upstream per-tick sink overload accounting. */
public final class DeviceLinkService {
    private final DeviceNetworkTransport transport;
    private final BiConsumer<DeviceEndpoint,DeviceComposition.SoundOnOverload> overloadEffect;
    private final Map<DeviceIdentity, Integer> sinkCounts = new HashMap<>();
    public DeviceLinkService(DeviceNetworkTransport transport) { this(transport,(endpoint,effect)->{}); }
    public DeviceLinkService(DeviceNetworkTransport transport, BiConsumer<DeviceEndpoint,DeviceComposition.SoundOnOverload> overloadEffect) { this.transport = Objects.requireNonNull(transport); this.overloadEffect=Objects.requireNonNull(overloadEffect); }
    public boolean link(DeviceLinkGraph graph, DeviceEndpoint source, String sourcePort, DeviceEndpoint target, String sinkPort) {
        source=transport.lookup(source.identity()); target=transport.lookup(target.identity());
        if(source==null||target==null)return false;
        if (!source.available() || !target.available() || !source.dimension().equals(target.dimension())
                || source.position().distSqr(target.position()) > source.linkRange() * source.linkRange()
                || source.composition().sources().stream().noneMatch(port -> port.id().equals(sourcePort))
                || target.composition().sinks().stream().noneMatch(port -> port.id().equals(sinkPort))) return false;
        DeviceLinkGraph.PortRef from=new DeviceLinkGraph.PortRef(source.identity(),sourcePort);
        DeviceLinkGraph.Link link=new DeviceLinkGraph.Link(from,new DeviceLinkGraph.PortRef(target.identity(),sinkPort));
        if (!graph.link(link)) return false;
        DeviceEndpoint linkedSource = source, linkedTarget = target;
        graph.remembered(from).ifPresent(signal -> invokeSingle(graph, linkedSource, linkedTarget, link, signal));
        return true;
    }
    public void nextGameTick() { sinkCounts.replaceAll((id,count)->Math.max(0,count-1)); sinkCounts.values().removeIf(count->count==0); }
    public boolean invoke(DeviceLinkGraph graph, DeviceEndpoint source, String sourcePort, DeviceSignal signal) {
        source=transport.lookup(source.identity());
        if(source==null)return false;
        if (!source.available() || source.composition().sources().stream().noneMatch(port -> port.id().equals(sourcePort))) return false;
        DeviceLinkGraph.PortRef from=new DeviceLinkGraph.PortRef(source.identity(),sourcePort);
        graph.signal(from,signal);
        if (signal == DeviceSignal.UNKNOWN) return true;
        boolean all=true;
        for(DeviceLinkGraph.Link link:graph.linksFrom(from)) {
            DeviceEndpoint target=transport.lookup(link.sink().device());
            if(target==null) continue;
            all &= invokeSingle(graph,source,target,link,signal);
        }
        return all;
    }
    private boolean invokeSingle(DeviceLinkGraph graph, DeviceEndpoint source, DeviceEndpoint target, DeviceLinkGraph.Link link, DeviceSignal signal) {
        if (!source.available() || !target.available()
                || source.composition().sources().stream().noneMatch(port -> port.id().equals(link.source().port()))
                || target.composition().sinks().stream().noneMatch(port -> port.id().equals(link.sink().port()))) return false;
        int count=sinkCounts.getOrDefault(target.identity(),0);
        if(count>target.invokeLimit()) {
            sinkCounts.remove(target.identity()); target.onOverload(); target.composition().soundOnOverload().ifPresent(effect->overloadEffect.accept(target,effect)); graph.retireLinksTo(target.identity()); return false;
        }
        sinkCounts.put(target.identity(),count+1);
        DeviceComposition.NetworkInterface a=source.composition().network().orElse(null), b=target.composition().network().orElse(null);
        if(a!=null&&b!=null) {
            if (!a.transmit() || a.address().isBlank() || b.receiveFrequency() < 1) return false;
            DeviceNetworkPacket packet=new DeviceNetworkPacket(source.identity(),ModDeviceNetworks.WIRELESS_NETWORK_ID,
                    b.receiveFrequency(),a.address(),b.address(),false,new SignalPayload(link.sink().port(),signal),a.networkId(),a.transmitFrequency());
            return transport.enqueue(packet);
        }
        target.receive(new SignalPayload(link.sink().port(),signal),""); return true;
    }
    public void replay(DeviceLinkGraph graph, DeviceLinkGraph.PortRef source, DeviceLinkGraph.Link link, DeviceEndpoint target) {
        DeviceEndpoint origin=transport.lookup(source.device());
        if(origin!=null) graph.remembered(source).ifPresent(signal->invokeSingle(graph,origin,target,link,signal));
    }
}

