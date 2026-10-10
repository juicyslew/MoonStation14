package com.juicyslew.moonstation14.ms14.devicenet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-dimension persistence; absence from loaded endpoint registry never means retirement. */
public final class DeviceLinkSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_device_links";
    public static final Factory<DeviceLinkSavedData> FACTORY = new Factory<>(DeviceLinkSavedData::new, DeviceLinkSavedData::load, null);
    private final DeviceLinkGraph graph = new DeviceLinkGraph(this::setDirty);
    private final Map<DeviceIdentity,String> defaultAddresses = new HashMap<>();
    public synchronized Optional<String> defaultAddress(DeviceIdentity identity) { return Optional.ofNullable(defaultAddresses.get(identity)); }
    public synchronized void rememberDefaultAddress(DeviceIdentity identity, String address) {
        Objects.requireNonNull(identity); Objects.requireNonNull(address);
        if (!address.isBlank() && !address.equals(defaultAddresses.put(identity,address))) setDirty();
    }
    public synchronized boolean forgetDefaultAddress(DeviceIdentity identity) {
        if(defaultAddresses.remove(Objects.requireNonNull(identity))==null)return false;
        setDirty();return true;
    }
    public static Optional<DeviceLinkSavedData> existing(ServerLevel level) { return Optional.ofNullable(level.getDataStorage().get(FACTORY, DATA_NAME)); }
    public static DeviceLinkSavedData forMutation(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Device graph mutation requires server thread");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public DeviceLinkGraph graph() { return graph; }
    public boolean link(DeviceLinkGraph.Link link) { return graph.link(link); }
    public boolean unlink(DeviceLinkGraph.Link link) { return graph.unlink(link); }
    public void remember(DeviceLinkGraph.PortRef source, DeviceSignal signal) { graph.signal(source,signal); }
    public void retire(DeviceIdentity identity) { graph.retire(identity); }
    public static DeviceLinkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if(tag.getInt("schema")!=1) throw new IllegalArgumentException("Unsupported device link schema");
        ListTag links=tag.getList("links", CompoundTag.TAG_COMPOUND), states=tag.getList("states", CompoundTag.TAG_COMPOUND);
        Set<DeviceLinkGraph.Link> decodedLinks=new LinkedHashSet<>();
        for(int i=0;i<links.size();i++){ CompoundTag t=links.getCompound(i); decodedLinks.add(new DeviceLinkGraph.Link(port(t,"source"),port(t,"sink"))); }
        Map<DeviceLinkGraph.PortRef,DeviceSignal> decodedStates=new HashMap<>();
        for(int i=0;i<states.size();i++){ CompoundTag t=states.getCompound(i); DeviceSignal signal=DeviceSignal.valueOf(t.getString("signal")); if(signal!=DeviceSignal.HIGH&&signal!=DeviceSignal.LOW) throw new IllegalArgumentException("Non-maintained saved signal"); decodedStates.put(port(t,"port"),signal); }
        DeviceLinkSavedData result=new DeviceLinkSavedData(); result.graph.restore(decodedLinks,decodedStates);
        ListTag addresses=tag.getList("defaultAddresses",CompoundTag.TAG_COMPOUND);
        for(int i=0;i<addresses.size();i++){CompoundTag t=addresses.getCompound(i);result.defaultAddresses.put(new DeviceIdentity(t.getUUID("device")),t.getString("address"));}
        return result;
    }
    private static DeviceLinkGraph.PortRef port(CompoundTag tag,String prefix) { return new DeviceLinkGraph.PortRef(new DeviceIdentity(tag.getUUID(prefix+"Device")),tag.getString(prefix+"Port")); }
    private static void putPort(CompoundTag tag,String prefix,DeviceLinkGraph.PortRef port) { tag.putUUID(prefix+"Device",port.device().value()); tag.putString(prefix+"Port",port.port()); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag links=new ListTag(); graph.links().forEach(link->{ CompoundTag t=new CompoundTag(); putPort(t,"source",link.source()); putPort(t,"sink",link.sink()); links.add(t); }); tag.put("links",links);
        ListTag states=new ListTag(); graph.rememberedSnapshot().forEach((port,signal)->{ CompoundTag t=new CompoundTag(); putPort(t,"port",port); t.putString("signal",signal.name()); states.add(t); }); tag.put("states",states);
        ListTag addresses=new ListTag(); synchronized(this){defaultAddresses.forEach((identity,address)->{CompoundTag t=new CompoundTag();t.putUUID("device",identity.value());t.putString("address",address);addresses.add(t);});} tag.put("defaultAddresses",addresses);
        tag.putInt("schema",1); return tag;
    }
}
