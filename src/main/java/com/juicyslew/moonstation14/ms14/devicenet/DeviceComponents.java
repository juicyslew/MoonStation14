package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable entity-family device declarations. Decode resolved/inherited JSON through {@link #CODEC}. */
public record DeviceComponents(Optional<Network> network, Optional<Connection> connection,
                               Optional<Source> source, Optional<Sink> sink, Optional<DeviceListConfig> list, Optional<SoundOnOverload> soundOnOverload) {
    private static final Codec<ResourceLocation> ID = ResourceLocation.CODEC;
    public static final Codec<DeviceComponents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Network.CODEC.optionalFieldOf("deviceNetwork").forGetter(DeviceComponents::network),
            Connection.CODEC.optionalFieldOf("deviceConnection").forGetter(DeviceComponents::connection),
            Source.CODEC.optionalFieldOf("deviceLinkSource").forGetter(DeviceComponents::source),
            Sink.CODEC.optionalFieldOf("deviceLinkSink").forGetter(DeviceComponents::sink),
            DeviceListConfig.CODEC.optionalFieldOf("deviceList").forGetter(DeviceComponents::list),
            SoundOnOverload.CODEC.optionalFieldOf("soundOnOverload").forGetter(DeviceComponents::soundOnOverload)
    ).apply(i, DeviceComponents::new));

    public DeviceComponents {
        Objects.requireNonNull(network); Objects.requireNonNull(connection); Objects.requireNonNull(source);
        Objects.requireNonNull(sink); Objects.requireNonNull(list); Objects.requireNonNull(soundOnOverload);
    }
    public DeviceComponents(Optional<Network> network,Optional<Connection> connection,Optional<Source> source,Optional<Sink> sink,Optional<DeviceListConfig> list) {
        this(network,connection,source,sink,list,Optional.empty());
    }
    public static DeviceComponents empty() { return new DeviceComponents(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),Optional.empty()); }

    /** Resolve only against the registered shared catalogs; unresolved IDs fail closed. */
    public DeviceComposition resolve(PrototypeManager prototypes) {
        Objects.requireNonNull(prototypes);
        List<DevicePort> sources = source.map(s -> s.ports().stream().map(id -> port(prototypes, id, DevicePortPrototype.Direction.SOURCE)).toList()).orElse(List.of());
        List<DevicePort> sinks = sink.map(s -> s.ports().stream().map(id -> port(prototypes, id, DevicePortPrototype.Direction.SINK)).toList()).orElse(List.of());
        Optional<DeviceComposition.NetworkInterface> resolvedNetwork = network.map(n -> {
            DeviceNetworkPrototype net = prototypes.snapshot(ModDeviceNetworks.NETWORK_TYPE).asMap().get(n.networkPrototypeId());
            if (net == null) throw new IllegalArgumentException("Unknown device network prototype: " + n.networkPrototypeId());
            int tx = frequency(prototypes, n.transmitFrequencyPrototypeId());
            int rx = frequency(prototypes, n.receiveFrequencyPrototypeId());
            return new DeviceComposition.NetworkInterface(net.networkId(), n.transmit(), n.receive(), "", n.addressPrefix(), tx, rx,
                    n.receiveAll(), n.requiresPower(), n.autoConnect(),
                    n.savableAddress(), n.customAddress(), n.broadcastHook());
        });
        Optional<DeviceList.Config> resolvedList = list.map(l -> new DeviceList.Config(l.capacity(), l.allowList(), l.filterIncoming()));
        return new DeviceComposition(sources, sinks, resolvedNetwork, resolvedList,
                source.map(s -> new DeviceComposition.SourceConfig(s.range(), s.lastSignals().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(e -> port(prototypes, e.getKey(), DevicePortPrototype.Direction.SOURCE).id(), java.util.Map.Entry::getValue)))),
                sink.map(s -> new DeviceComposition.SinkConfig(s.limit())),
                 connection.map(c -> new DeviceComposition.ConnectionConfig(c.wired(), c.wireless(), c.wirelessRange())),
                 soundOnOverload.map(s -> new DeviceComposition.SoundOnOverload(s.sound(),s.volumeModifier())));
    }
    private static DevicePort port(PrototypeManager manager, ResourceLocation id, DevicePortPrototype.Direction expected) {
        DevicePortPrototype value = manager.snapshot(ModDeviceNetworks.PORT_TYPE).asMap().get(id);
        if (value == null) throw new IllegalArgumentException("Unknown device port prototype: " + id);
        if (value.direction() != expected) throw new IllegalArgumentException("Device port " + id + " has direction " + value.direction() + ", expected " + expected);
        return value.port();
    }
    private static int frequency(PrototypeManager manager, Optional<ResourceLocation> id) {
        return id.map(key -> {
            DeviceFrequency value = manager.snapshot(ModDeviceNetworks.FREQUENCY_TYPE).asMap().get(key);
            if (value == null) throw new IllegalArgumentException("Unknown device frequency prototype: " + key);
            return value.frequency();
        }).orElse(0);
    }

    public record Network(ResourceLocation networkPrototypeId, boolean transmit, boolean receive,
                          Optional<ResourceLocation> transmitFrequencyPrototypeId, Optional<ResourceLocation> receiveFrequencyPrototypeId,
                          String addressPrefix, boolean autoConnect, boolean savableAddress, boolean customAddress,
                          boolean receiveAll, boolean broadcastHook, boolean requiresPower) {
        static final Codec<Network> CODEC = RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("network").forGetter(Network::networkPrototypeId), Codec.BOOL.optionalFieldOf("transmit", true).forGetter(Network::transmit),
                Codec.BOOL.optionalFieldOf("receive", true).forGetter(Network::receive), ID.optionalFieldOf("transmitFrequency").forGetter(Network::transmitFrequencyPrototypeId),
                ID.optionalFieldOf("receiveFrequency").forGetter(Network::receiveFrequencyPrototypeId), Codec.STRING.optionalFieldOf("addressPrefix", "").forGetter(Network::addressPrefix),
                Codec.BOOL.optionalFieldOf("autoConnect", true).forGetter(Network::autoConnect), Codec.BOOL.optionalFieldOf("savableAddress", true).forGetter(Network::savableAddress),
                Codec.BOOL.optionalFieldOf("customAddress", false).forGetter(Network::customAddress), Codec.BOOL.optionalFieldOf("receiveAll", false).forGetter(Network::receiveAll),
                Codec.BOOL.optionalFieldOf("broadcastHook", false).forGetter(Network::broadcastHook), Codec.BOOL.optionalFieldOf("requiresPower", false).forGetter(Network::requiresPower)
        ).apply(i, Network::new));
        public Network { Objects.requireNonNull(networkPrototypeId); Objects.requireNonNull(transmitFrequencyPrototypeId); Objects.requireNonNull(receiveFrequencyPrototypeId); Objects.requireNonNull(addressPrefix); }
    }
    public record Connection(boolean wired, boolean wireless, double wirelessRange) {
        static final Codec<Connection> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.BOOL.optionalFieldOf("wired", false).forGetter(Connection::wired), Codec.BOOL.optionalFieldOf("wireless", false).forGetter(Connection::wireless), Codec.DOUBLE.optionalFieldOf("wirelessRange", 0.0).forGetter(Connection::wirelessRange)).apply(i, Connection::new));
        public Connection { if (!Double.isFinite(wirelessRange) || wirelessRange < 0) throw new IllegalArgumentException("invalid connection range"); }
    }
    public record Source(List<ResourceLocation> ports, double range, java.util.Map<ResourceLocation, DeviceSignal> lastSignals) {
        static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(ID.listOf().fieldOf("ports").forGetter(Source::ports), Codec.DOUBLE.optionalFieldOf("range", 30.0).forGetter(Source::range), Codec.unboundedMap(ID, Codec.STRING.xmap(DeviceSignal::valueOf, DeviceSignal::name)).optionalFieldOf("lastSignals", java.util.Map.of()).forGetter(Source::lastSignals)).apply(i, Source::new));
        public Source { ports = List.copyOf(ports); lastSignals = java.util.Map.copyOf(lastSignals); if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("invalid source range"); if (lastSignals.values().stream().anyMatch(signal -> signal != DeviceSignal.HIGH && signal != DeviceSignal.LOW)) throw new IllegalArgumentException("initial last signals must be High or Low"); }
    }
    public record Sink(List<ResourceLocation> ports, int limit) {
        static final Codec<Sink> CODEC = RecordCodecBuilder.create(i -> i.group(ID.listOf().fieldOf("ports").forGetter(Sink::ports), Codec.INT.optionalFieldOf("limit", 10).forGetter(Sink::limit)).apply(i, Sink::new));
        public Sink { ports = List.copyOf(ports); }
    }
    public record DeviceListConfig(int capacity, boolean allowList, boolean filterIncoming) {
        static final Codec<DeviceListConfig> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.optionalFieldOf("capacity", 32).forGetter(DeviceListConfig::capacity), Codec.BOOL.optionalFieldOf("allowList", true).forGetter(DeviceListConfig::allowList), Codec.BOOL.optionalFieldOf("filterIncoming", false).forGetter(DeviceListConfig::filterIncoming)).apply(i, DeviceListConfig::new));
        public DeviceListConfig { if (capacity < 1) throw new IllegalArgumentException("device-list capacity must be positive"); }
    }
    public record SoundOnOverload(Optional<ResourceLocation> sound,float volumeModifier) {
        private static final ResourceLocation DEFAULT_SOUND=ResourceLocation.fromNamespaceAndPath("moonstation14","device/defib_zap");
        static final Codec<SoundOnOverload> CODEC=RecordCodecBuilder.create(i->i.group(
                ID.optionalFieldOf("sound",DEFAULT_SOUND).xmap(Optional::of,sound->sound.orElse(DEFAULT_SOUND)).forGetter(SoundOnOverload::sound),
                Codec.FLOAT.optionalFieldOf("volumeModifier",0f).forGetter(SoundOnOverload::volumeModifier)
        ).apply(i,SoundOnOverload::new));
        public SoundOnOverload { Objects.requireNonNull(sound); if(!Float.isFinite(volumeModifier))throw new IllegalArgumentException("invalid overload volume modifier"); }
    }
}
