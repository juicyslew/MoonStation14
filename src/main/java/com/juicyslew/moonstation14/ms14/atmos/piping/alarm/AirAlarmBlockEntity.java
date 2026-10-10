package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Resolved wall composition and indexed APC receiver; no device-network simulation. */
public final class AirAlarmBlockEntity extends BlockEntity {
    private static final String ID_TAG = "AirAlarmDeviceIdentity";
    private java.util.UUID controllerId;
    private final AirAlarmDeviceLinks deviceLinks = new AirAlarmDeviceLinks();
    private final AirAlarmObservationCache observations = new AirAlarmObservationCache();
    private DeviceEndpoint networkEndpoint;
    public AirAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_ALARM.get(), pos, state);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            PowerRuntime.airAlarmChanged(server, worldPosition);
            if (prototype() != null) { ensureControllerId(); DeviceNetworkRuntime.forLevel(server).loaded(networkEndpoint(server)); refreshObserved(); }
        }
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) {
            var runtime = DeviceNetworkRuntime.forLevel(server);
            DeviceIdentity identity = controllerId == null ? null : new DeviceIdentity(controllerId);
            if (identity != null) {
                var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
                boolean replaced = chunk != null && !(chunk.getBlockState(worldPosition).getBlock() instanceof AirAlarmBlock);
                if (networkEndpoint != null && replaced) runtime.retired(identity);
                else runtime.unloaded(identity);
            }
            PowerRuntime.airAlarmRemoved(server, worldPosition);
        }
        super.setRemoved();
    }

    public java.util.List<AirAlarmDeviceLinks.Link> deviceLinks() { return deviceLinks.snapshot(); }
    public boolean deviceLinksValid() { return deviceLinks.valid(); }
    public java.util.Map<String, AtmosDevicePayload.State> observedStates() { return observations.snapshot(); }

    public void requestDeviceSync() { refreshObserved(); }

    private void refreshObserved() {
        if (!(level instanceof ServerLevel server) || isRemoved() || !server.getServer().isSameThread()
                || networkEndpoint == null || !networkEndpoint.available()) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        var sender = runtime.transport().lookup(networkEndpoint.identity());
        var source = sender == null ? null : sender.composition().network().orElse(null);
        if (source == null || !source.transmit()) return;
        var links = deviceLinks.snapshot();
        for (var link : links) queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Register());
        for (var link : links) queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Sync());
    }

    private static void queueMembership(DeviceNetworkRuntime runtime, DeviceEndpoint sender,
            DeviceComposition.NetworkInterface source, AirAlarmDeviceLinks.Link link, DevicePayload payload) {
        var target = runtime.transport().lookup(new DeviceIdentity(link.deviceId()));
        var destination = target == null ? null : target.composition().network().orElse(null);
        if (destination == null || destination.address().isBlank() || !destination.receive()) return;
        runtime.transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                sender.identity(), source.networkId(), source.transmitFrequency(), source.address(),
                destination.address(), false, payload, source.networkId(), source.transmitFrequency()));
    }

    private void membershipChanged(java.util.List<AirAlarmDeviceLinks.Link> previous) {
        observations.clear(); setChanged();
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        var sender = networkEndpoint;
        if (sender == null || !sender.available()) return;
        var source = sender.composition().network().orElse(null);
        if (source == null || !source.transmit()) return;
        var current = deviceLinks.snapshot();
        var ids = current.stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet());
        for (var link : previous) if (!ids.contains(link.deviceId()))
            queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Deregister());
        refreshObserved();
    }

    private DeviceEndpoint networkEndpoint(ServerLevel server) {
        if (networkEndpoint == null) networkEndpoint = new DeviceEndpoint() {
            private DeviceComposition resolved() {
                AirAlarmData data = prototype();
                if (data == null) return DeviceComposition.empty();
                DeviceComposition composition = data.deviceComponents().resolve(PrototypeRuntime.serverManager());
                return com.juicyslew.moonstation14.ms14.devicenet.DeviceAddressPolicy.retained(composition,
                        DeviceLinkSavedData.existing(server).flatMap(saved->saved.defaultAddress(identity())));
            }
            public DeviceIdentity identity() { ensureControllerId(); return new DeviceIdentity(controllerId); }
            public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() { return server.dimension(); }
            public BlockPos position() { return worldPosition; }
            public String displayName() { return getBlockState().getBlock().getName().getString(); }
            public DeviceComposition composition() { return resolved(); }
            public boolean available() { var chunk=server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4); return !isRemoved() && getLevel() == server && chunk != null && chunk.getBlockEntity(worldPosition) == AirAlarmBlockEntity.this && prototype() != null; }
            public boolean powered() { AirAlarmData data=prototype(); if(data==null)return false; var meter=PowerRuntime.airAlarmMeter(server, AirAlarmBlockEntity.this); return meter.known() && meter.deliveredWatts() == data.apcPowerReceiver().requestedWatts(); }
            public com.juicyslew.moonstation14.ms14.devicenet.DeviceList deviceList() { var composition=resolved(); return composition.deviceList().map(deviceLinks::deviceList).orElse(null); }
            public boolean mutateDeviceListMembers(com.juicyslew.moonstation14.ms14.devicenet.DeviceList replacement) {
                var current=deviceList();
                if (!available() || replacement == null || current == null || replacement.capacity() != current.capacity()
                        || replacement.allowList() != current.allowList() || replacement.filterIncoming() != current.filterIncoming()) return false;
                var ids=new java.util.LinkedHashSet<java.util.UUID>();
                try { for(String member:replacement.addresses()) if(!ids.add(java.util.UUID.fromString(member)))return false; }
                catch(IllegalArgumentException malformed){return false;}
                if(ids.size()>replacement.capacity())return false;
                if(ids.equals(deviceLinks.snapshot().stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet())))return true;
                return replaceDeviceLinks(ids);
            }
            public void receive(DevicePayload payload, String sourceAddress) {
                if (!(payload instanceof AtmosDevicePayload.State state) || !available() || sourceAddress == null) return;
                if (deviceList() == null) return;
                for (var link : deviceLinks.snapshot()) {
                    var vent=AirAlarmDeviceLinks.resolve(server,link);
                    if(vent==null||!link.deviceId().equals(vent.deviceId()))continue;
                    var sender=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(link.deviceId()));
                    if(sender==null||!sender.identity().value().equals(link.deviceId())
                            ||!sender.composition().network().map(DeviceComposition.NetworkInterface::address).orElse("").equals(sourceAddress))continue;
                    observations.observe(sourceAddress, state, java.util.Set.of(sourceAddress));
                    setChanged();
                    return;
                }
            }
        };
        return networkEndpoint;
    }

    private void ensureControllerId() {
        if (controllerId == null && level instanceof ServerLevel server && server.getServer().isSameThread()) {
            controllerId=java.util.UUID.randomUUID(); setChanged();
        }
    }

    public java.util.UUID controllerId() { ensureControllerId(); return controllerId; }

    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server && networkEndpoint != null)
            DeviceNetworkRuntime.forLevel(server).unloaded(networkEndpoint.identity());
    }

    /** Server-only explicit association; admission requires the resolved concrete DeviceList limit. */
    public boolean addDeviceLink(VentilationDeviceBlockEntity device) {
        AirAlarmData data = prototype();
        if (!(level instanceof ServerLevel server) || data == null || isRemoved()
                || !server.getServer().isSameThread()
                || server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) == null
                || server.getBlockEntity(worldPosition) != this) return false;
        var previous=deviceLinks.snapshot();
        if (!deviceLinks.add(server, device, data.deviceList().maxDevices())) return false;
        setChanged();
        membershipChanged(previous);
        return true;
    }

    public boolean removeDeviceLink(java.util.UUID deviceId) {
        if (!(level instanceof ServerLevel server) || prototype() == null || isRemoved()
                || !server.getServer().isSameThread() || !deviceLinks.valid()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || chunk.getBlockEntity(worldPosition) != this) return false;
        var previous=deviceLinks.snapshot();
        if (!deviceLinks.remove(deviceId)) return false;
        setChanged();
        membershipChanged(previous);
        return true;
    }

    private boolean replaceDeviceLinks(java.util.Set<java.util.UUID> ids) {
        if (!(level instanceof ServerLevel server)) return false;
        var links=new java.util.ArrayList<AirAlarmDeviceLinks.Link>();
        for(var id:ids){
            var existing=deviceLinks.snapshot().stream().filter(link->link.deviceId().equals(id)).findFirst().orElse(null);
            if(existing!=null){links.add(existing);continue;}
            var vent=findLoadedVent(server,id);if(vent==null)return false;
            links.add(new AirAlarmDeviceLinks.Link(server.dimension(),vent.getBlockPos(),vent.kind(),id));
        }
        var previous=deviceLinks.snapshot();
        if(!deviceLinks.replaceLinks(links,ids.size()))return false;
        membershipChanged(previous); return true;
    }
    private static VentilationDeviceBlockEntity findLoadedVent(ServerLevel server, java.util.UUID id) {
        var endpoint=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(id));
        if(endpoint==null)return null;
        var chunk=server.getChunkSource().getChunkNow(endpoint.position().getX()>>4,endpoint.position().getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(endpoint.position()) instanceof VentilationDeviceBlockEntity vent
                &&id.equals(vent.deviceId())?vent:null;
    }

    public VentilationDeviceBlockEntity resolveDeviceLink(AirAlarmDeviceLinks.Link link) {
        if (!(level instanceof ServerLevel server) || prototype() == null || !deviceLinks.valid()
                || !deviceLinks.snapshot().contains(link)) return null;
        return AirAlarmDeviceLinks.resolve(server, link);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        deviceLinks.save(tag);
        if(controllerId!=null)tag.putUUID(ID_TAG, controllerId);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if(tag.hasUUID(ID_TAG))controllerId=tag.getUUID(ID_TAG);
        // The concrete prototype validates 32; refuse association if resolution later fails.
        deviceLinks.load(tag, 32);
    }

    /** Fail closed until the matching concrete alarm definition has been published. */
    public AirAlarmData prototype() {
        if (level == null || !(getBlockState().getBlock() instanceof AirAlarmBlock)) return null;
        AirAlarmData data = (level.isClientSide ? PrototypeRuntime.clientAirAlarms()
                : PrototypeRuntime.serverAirAlarms()).get(ModAirAlarms.AIR_ALARM);
        if (data == null) return null;
        try {
            data.validate(); // Includes inherited anchored/non-colliding wallmount and 100 W APC demand.
            return data;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
