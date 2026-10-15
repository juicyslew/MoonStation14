package com.juicyslew.moonstation14.ms14.devicenet;

import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Dimension-scoped gameplay adapter holding loaded endpoints, queue, graph and tick cadence. */
public final class DeviceNetworkRuntime {
    private static final java.util.Map<ServerLevel,DeviceNetworkRuntime> RUNTIMES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final ServerLevel level;
    private final DeviceNetworkTransport transport;
    private final DeviceLinkService links;
    private DeviceNetworkRuntime(ServerLevel level) {
        this.level=Objects.requireNonNull(level);
        DeviceLinkSavedData saved=DeviceLinkSavedData.forMutation(level);
        this.transport=new DeviceNetworkTransport(new DeviceNetworkTransport.AddressStore() {
            public java.util.Optional<String> get(DeviceIdentity identity){return saved.defaultAddress(identity);}
            public void put(DeviceIdentity identity,String address){saved.rememberDefaultAddress(identity,address);}
        },java.util.concurrent.ThreadLocalRandom.current()::nextInt);
        this.links=new DeviceLinkService(transport,this::playOverloadEffect);
    }
    public static DeviceNetworkRuntime forLevel(ServerLevel level) {
        synchronized (RUNTIMES) { return RUNTIMES.computeIfAbsent(level,DeviceNetworkRuntime::new); }
    }
    public static void updateServer(net.minecraft.server.MinecraftServer server) {
        synchronized (RUNTIMES) {
            RUNTIMES.values().stream().filter(runtime -> runtime.level.getServer()==server).toList()
                    .forEach(runtime -> { runtime.gameTick(); runtime.update(); });
        }
    }
    public static void stopServer(net.minecraft.server.MinecraftServer server) {
        synchronized (RUNTIMES) { RUNTIMES.keySet().removeIf(level -> level.getServer()==server); }
    }
    public static void unloadLevel(ServerLevel level) { synchronized (RUNTIMES) { RUNTIMES.remove(level); } }
    public DeviceNetworkTransport transport() { return transport; }
    public DeviceLinkService links() { return links; }
    public DeviceLinkGraph graph() { return DeviceLinkSavedData.forMutation(level).graph(); }
    public boolean loaded(DeviceEndpoint endpoint) {
        if (!level.dimension().equals(endpoint.dimension())) throw new IllegalArgumentException("endpoint registered in wrong dimension");
        return transport.register(endpoint);
    }
    public boolean connect(DeviceIdentity identity) { return transport.connect(identity); }
    public boolean disconnect(DeviceIdentity identity) { return transport.disconnect(identity); }
    public void unloaded(DeviceIdentity identity) { transport.unload(identity); }
    public void retired(DeviceIdentity identity) {
        transport.retire(identity,graph());
        DeviceLinkSavedData.forMutation(level).forgetDefaultAddress(identity);
    }
    /** Invoke once in the later server-update phase; enqueue remains non-synchronous. */
    public int update() { return transport.drain(); }
    public void gameTick() { links.nextGameTick(); }
    private void playOverloadEffect(DeviceEndpoint endpoint,DeviceComposition.SoundOnOverload effect) {
        effect.sound().ifPresent(soundId->{
            ModSounds.DEVICE_OVERLOAD.get(); // Ensure the mod sound registry class has been initialized.
            var event=net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(soundId);
            if(event!=net.minecraft.sounds.SoundEvents.EMPTY) level.playSound(null,endpoint.position(),event,SoundSource.BLOCKS,Math.max(0f,1f+effect.volumeModifier()),1f);
        });
    }
}

