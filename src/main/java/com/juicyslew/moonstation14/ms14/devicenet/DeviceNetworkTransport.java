package com.juicyslew.moonstation14.ms14.devicenet;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Loaded-only endpoint directory and deferred addressed/broadcast packet delivery. */
public final class DeviceNetworkTransport {
    public enum DeliveryOutcome { SOURCE_UNAVAILABLE, SOURCE_CONFIGURATION_CHANGED, RECEIVER_UNAVAILABLE, RECEIVER_CONFIGURATION_OR_CONNECTION, RECEIVER_UNPOWERED, ADDRESS_REJECTED, LIST_REJECTED, CONNECTION_REJECTED, HOOK_REJECTED, ENDPOINT_CALLED }
    public record DeliveryAttempt(DeviceNetworkPacket packet, DeviceIdentity receiverDeviceIdentity, DeliveryOutcome outcome) { }
    private record DeliveryObserver(DeviceIdentity sender, DeviceIdentity receiver, java.util.function.Consumer<DeliveryAttempt> callback) { }
    private final List<DeliveryObserver> deliveryObservers = new ArrayList<>();
    /** Registers a transient, endpoint-scoped diagnostic callback. Close it to stop observations. */
    public synchronized AutoCloseable observeDelivery(DeviceIdentity sender, DeviceIdentity receiver, java.util.function.Consumer<DeliveryAttempt> callback) {
        var observer=new DeliveryObserver(Objects.requireNonNull(sender),receiver,Objects.requireNonNull(callback)); deliveryObservers.add(observer);
        return ()->{ synchronized(DeviceNetworkTransport.this){deliveryObservers.remove(observer);} };
    }
    private void observe(DeviceNetworkPacket packet, DeviceIdentity receiver, DeliveryOutcome outcome) {
        DeliveryAttempt attempt=new DeliveryAttempt(packet,receiver,outcome);
        List<DeliveryObserver> observers; synchronized(this){observers=List.copyOf(deliveryObservers);}
        for(var observer:observers) if(observer.sender().equals(packet.sender())&&(observer.receiver()==null||observer.receiver().equals(receiver)))
            try { observer.callback().accept(attempt); } catch(RuntimeException ignored) { /* Diagnostics must not affect delivery. */ }
    }
    public interface AddressStore { Optional<String> get(DeviceIdentity identity); void put(DeviceIdentity identity,String address); }
    private final Map<DeviceIdentity, DeviceEndpoint> loaded = new HashMap<>();
    private final Map<NetworkAddress, DeviceIdentity> connected = new HashMap<>();
    private final Map<DeviceIdentity, NetworkAddress> connectionByIdentity = new HashMap<>();
    private Queue<DeviceNetworkPacket> pending = new ArrayDeque<>();
    private final AddressStore addressStore;
    private final java.util.function.IntSupplier random32;
    private final Map<DeviceIdentity,String> memoryAddresses = new HashMap<>();
    public DeviceNetworkTransport() { this(null, java.util.concurrent.ThreadLocalRandom.current()::nextInt); }
    public DeviceNetworkTransport(AddressStore store, java.util.function.IntSupplier random32) { this.addressStore=store; this.random32=Objects.requireNonNull(random32); }
    private record NetworkAddress(int networkId,String address) { }
    public synchronized boolean register(DeviceEndpoint endpoint) {
        Objects.requireNonNull(endpoint);
        unregisterNetwork(endpoint.identity());
        DeviceEndpoint registered=new BoundEndpoint(endpoint,resolveAddress(endpoint),endpoint.composition().network().map(DeviceComposition.NetworkInterface::address).orElse(""));
        loaded.put(endpoint.identity(),registered);
        var net=registered.composition().network().orElse(null);
        return net==null || !net.autoConnect() || connect(registered.identity());
    }
    private String resolveAddress(DeviceEndpoint endpoint) {
        var net=endpoint.composition().network().orElse(null);
        if(net==null || net.customAddress()) return net==null?"":net.address();
        if(endpoint.ownsAddressOutsideWorldSavedData()) {
            if(net.address().isBlank())throw new IllegalStateException("external address owner must prepare address before registration");
            return net.address();
        }
        String owned=addressStore==null
                ? net.address().isBlank()?memoryAddresses.get(endpoint.identity()):null
                : addressStore.get(endpoint.identity()).orElse(null);
        String retained=owned==null?(net.address().isBlank()?null:net.address()):owned;
        String candidate=allocateDefaultAddress(endpoint.identity(),net.networkId(),net.addressPrefix(),retained);
        if(addressStore==null)memoryAddresses.put(endpoint.identity(),candidate);
        else if(!candidate.equals(addressStore.get(endpoint.identity()).orElse(null)))addressStore.put(endpoint.identity(),candidate);
        return candidate;
    }
    /** Allocate a portable stack-owned address without persisting it in block SavedData. */
    public synchronized String allocateDefaultAddress(DeviceIdentity identity,int networkId,String prefix) {
        return allocateDefaultAddress(identity,networkId,prefix,null);
    }
    /** Shared allocator; a preferred value is retained only while unoccupied or owned by identity. */
    public synchronized String allocateDefaultAddress(DeviceIdentity identity,int networkId,String prefix,String preferred) {
        NetworkAddress preferredKey=preferred==null||preferred.isBlank()?null:new NetworkAddress(networkId,preferred);
        DeviceIdentity occupant=preferredKey==null?null:connected.get(preferredKey);
        if(preferredKey!=null&&(occupant==null||identity.equals(occupant)))return preferred;
        String candidate;
        do { candidate=DeviceAddressPolicy.generate(prefix,random32); }
        while(connected.containsKey(new NetworkAddress(networkId,candidate))
                && !identity.equals(connected.get(new NetworkAddress(networkId,candidate))));
        return candidate;
    }
    public synchronized DeviceEndpoint lookup(DeviceIdentity identity) { return loaded.get(identity); }
    /** Immutable loaded-only snapshot; offline identities are intentionally absent. */
    public synchronized List<DeviceEndpoint> endpoints() { return List.copyOf(loaded.values()); }
    /** Loaded and currently available endpoints at this dimension-local block position. */
    public synchronized List<DeviceEndpoint> lookupAt(BlockPos position) {
        return loaded.values().stream().filter(DeviceEndpoint::available)
                .filter(endpoint -> endpoint.acceptsBlockTarget(position)).toList();
    }
    public synchronized void unload(DeviceIdentity identity) { loaded.remove(identity); unregisterNetwork(identity); }
    public synchronized void retire(DeviceIdentity identity, DeviceLinkGraph graph) { loaded.remove(identity); unregisterNetwork(identity); memoryAddresses.remove(identity); graph.retire(identity); }
    /** Explicitly joins a loaded endpoint to its resolved network, refusing duplicate addresses. */
    public synchronized boolean connect(DeviceIdentity identity) {
        DeviceEndpoint endpoint=loaded.get(identity);
        if(endpoint==null)return false;
        var net=endpoint.composition().network().orElse(null);
        if(net==null||net.address().isBlank())return false;
        NetworkAddress key=new NetworkAddress(net.networkId(),net.address());
        DeviceIdentity occupant=connected.get(key);
        if(occupant!=null&&!occupant.equals(identity))return false;
        unregisterNetwork(identity); connected.put(key,identity); connectionByIdentity.put(identity,key); return true;
    }
    public synchronized boolean disconnect(DeviceIdentity identity) { return unregisterNetwork(identity); }
    public synchronized boolean isConnected(DeviceIdentity identity,int networkId) {
        NetworkAddress key=connectionByIdentity.get(identity);
        DeviceEndpoint endpoint=loaded.get(identity);
        DeviceComposition.NetworkInterface current=endpoint==null?null:endpoint.composition().network().orElse(null);
        return key!=null&&key.networkId()==networkId&&current!=null
                &&current.networkId()==key.networkId()&&current.address().equals(key.address());
    }
    private boolean unregisterNetwork(DeviceIdentity identity) {
        NetworkAddress old=connectionByIdentity.remove(identity);
        return old!=null&&connected.remove(old,identity);
    }
    public boolean enqueue(DeviceNetworkPacket packet) {
        DeviceEndpoint sender;
        synchronized (this) { sender = loaded.get(packet.sender()); }
        if (sender == null || !sender.available()) return false;
        DeviceComposition.NetworkInterface net = sender.composition().network().orElse(null);
        if (net == null || !net.transmit() || net.networkId() != packet.senderNetworkId()
                || net.transmitFrequency() != packet.senderTransmitFrequency()
                || !net.address().equals(packet.senderAddress()) || net.address().isBlank()
                || packet.frequency() < 1 || !isConnected(sender.identity(),packet.senderNetworkId())) return false;
        synchronized (this) { pending.add(packet); }
        return true;
    }
    /** Call once in a later server update. Returns number of endpoint deliveries. */
    public int drain() {
        int delivered = 0;
        Queue<DeviceNetworkPacket> batch;
        synchronized (this) { batch=pending; pending=new ArrayDeque<>(); }
        DeviceNetworkPacket packet;
        while ((packet = batch.poll()) != null) {
            DeviceEndpoint sender;
            List<DeviceEndpoint> candidates;
            synchronized (this) { sender = loaded.get(packet.sender()); candidates = List.copyOf(loaded.values()); }
            if (sender == null || !sender.available()) { observe(packet,null,DeliveryOutcome.SOURCE_UNAVAILABLE); continue; }
            var tx = sender.composition().network().orElse(null);
            // A queued packet belongs to the sender configuration observed at enqueue time.
            // Address/network/frequency edits invalidate it instead of transmitting stale data.
            if (tx == null || !tx.transmit() || tx.networkId() != packet.senderNetworkId()
                    || tx.transmitFrequency() != packet.senderTransmitFrequency()
                    || !tx.address().equals(packet.senderAddress()) || tx.address().isBlank()
                    || !isConnected(sender.identity(),packet.senderNetworkId())) { observe(packet,null,DeliveryOutcome.SOURCE_CONFIGURATION_CHANGED); continue; }
            for (DeviceEndpoint target : candidates) {
                if (target.identity().equals(sender.identity())) continue;
                if (!target.available()) { observe(packet,target.identity(),DeliveryOutcome.RECEIVER_UNAVAILABLE); continue; }
                var rx = target.composition().network().orElse(null);
                if (rx == null || !rx.receive() || rx.networkId()!=packet.networkId()
                        || rx.receiveFrequency() != packet.frequency() || !isConnected(target.identity(),packet.networkId())) { observe(packet,target.identity(),DeliveryOutcome.RECEIVER_CONFIGURATION_OR_CONNECTION); continue; }
                if(rx.requiresPower() && !target.powered()) { observe(packet,target.identity(),DeliveryOutcome.RECEIVER_UNPOWERED); continue; }
                var txConnection = sender.composition().connection().orElse(null);
                var rxConnection = target.composition().connection().orElse(null);
                boolean addressed = !packet.broadcast() && rx.address().equals(packet.destinationAddress());
                if (!packet.broadcast() && !rx.receiveAll() && !addressed) { observe(packet,target.identity(),DeliveryOutcome.ADDRESS_REJECTED); continue; }
                if (target.deviceList()!=null && target.deviceList().filterIncoming()
                        && !target.deviceList().permits(packet.sender().value().toString(), true)) { observe(packet,target.identity(),DeliveryOutcome.LIST_REJECTED); continue; }
                if (packet.broadcast() && tx.sendBroadcastAttempt()) {
                    DeviceList list=sender.deviceList();
                    if (list!=null && ((list.allowList() && list.addresses().isEmpty())
                            || !list.permits(target.identity().value().toString(), false))) { observe(packet,target.identity(),DeliveryOutcome.LIST_REJECTED); continue; }
                }
                boolean sameDimension=sender.dimension().equals(target.dimension());
                // SS14 connection components are independent veto hooks, not matching transport modes.
                // A wired receiver rejects a different grid even if its sender has no wired component.
                if(rxConnection!=null&&rxConnection.wired()&&!sameDimension) { observe(packet,target.identity(),DeliveryOutcome.CONNECTION_REJECTED); continue; }
                // Wireless checks only apply when both endpoints opt into wireless; a failed wireless
                // path cannot be rescued by a second wired connection on the same receiver.
                if(rxConnection!=null&&rxConnection.wireless()&&txConnection!=null&&txConnection.wireless()
                        &&(!sameDimension||sender.position().distSqr(target.position())
                        >txConnection.wirelessRange()*txConnection.wirelessRange())) { observe(packet,target.identity(),DeliveryOutcome.CONNECTION_REJECTED); continue; }
                if(!target.beforePacketReceived(sender,packet)) { observe(packet,target.identity(),DeliveryOutcome.HOOK_REJECTED); continue; }
                target.receive(packet.payload(), packet.senderAddress()); observe(packet,target.identity(),DeliveryOutcome.ENDPOINT_CALLED); delivered++;
            }
        }
        return delivered;
    }
    public synchronized int queuedPackets() { return pending.size(); }

    private record BoundEndpoint(DeviceEndpoint delegate,String address,String initialAddress) implements DeviceEndpoint {
        @Override public DeviceIdentity identity(){return delegate.identity();}
        @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(){return delegate.dimension();}
        @Override public BlockPos position(){return delegate.position();}
        @Override public boolean acceptsBlockTarget(BlockPos target){return delegate.acceptsBlockTarget(target);}
        @Override public DeviceComposition composition(){var current=delegate.composition();var net=current.network().orElse(null);if(net==null||net.customAddress()||delegate.ownsAddressOutsideWorldSavedData())return current;String live=net.address();if(!live.isBlank()&&!live.equals(initialAddress)&&!live.equals(address))return current;return current.withAddress(address);}
        @Override public boolean ownsAddressOutsideWorldSavedData(){return delegate.ownsAddressOutsideWorldSavedData();}
        @Override public boolean available(){return delegate.available();}
        @Override public boolean powered(){return delegate.powered();}
        @Override public String displayName(){return delegate.displayName();}
        @Override public DeviceList deviceList(){return delegate.deviceList();}
        @Override public boolean mutateDeviceListMembers(DeviceList replacement){return delegate.mutateDeviceListMembers(replacement);}
        @Override public double linkRange(){return delegate.linkRange();}
        @Override public int invokeLimit(){return delegate.invokeLimit();}
        @Override public void onOverload(){delegate.onOverload();}
        @Override public boolean beforePacketReceived(DeviceEndpoint sender,DeviceNetworkPacket packet){return delegate.beforePacketReceived(sender,packet);}
        @Override public void receive(DevicePayload payload,String sourceAddress){delegate.receive(payload,sourceAddress);}
    }
}

