package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistent per-tool mode and staged device references; membership remains owned by DeviceList. */
public record NetworkConfiguratorStackData(DeviceIdentity identity, String address, Mode mode,
                                           List<StagedDevice> staged, UUID selectedSource, String selectedSourcePort) {
    public enum Mode { LINK, LIST }
    public record StagedDevice(UUID identity, String address, String name) {
        public static final Codec<StagedDevice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("identity").forGetter(v -> v.identity.toString()),
                Codec.STRING.fieldOf("address").forGetter(StagedDevice::address),
                Codec.STRING.fieldOf("name").forGetter(StagedDevice::name)
        ).apply(i, (id, address, name) -> new StagedDevice(UUID.fromString(id), address, name)));
        public StagedDevice { Objects.requireNonNull(identity); Objects.requireNonNull(address); Objects.requireNonNull(name); }
    }
    public static final Codec<NetworkConfiguratorStackData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("identity").forGetter(v -> v.identity.value().toString()),
            Codec.STRING.fieldOf("address").forGetter(NetworkConfiguratorStackData::address),
            Codec.STRING.fieldOf("mode").forGetter(v -> v.mode.name().toLowerCase(java.util.Locale.ROOT)),
            StagedDevice.CODEC.listOf().fieldOf("staged").forGetter(NetworkConfiguratorStackData::staged),
            Codec.STRING.optionalFieldOf("selected_source", "").forGetter(v -> v.selectedSource == null ? "" : v.selectedSource.toString()),
            Codec.STRING.optionalFieldOf("selected_source_port", "").forGetter(v -> v.selectedSourcePort == null ? "" : v.selectedSourcePort)
    ).apply(i, (id, address, mode, staged, source, port) -> new NetworkConfiguratorStackData(
            new DeviceIdentity(UUID.fromString(id)), address, Mode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)), staged,
            source.isEmpty() ? null : UUID.fromString(source), port.isEmpty() ? null : port)));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkConfiguratorStackData> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.identity.value()); b.writeUtf(v.address); b.writeEnum(v.mode); b.writeVarInt(v.staged.size());
                for (var entry : v.staged) { b.writeUUID(entry.identity); b.writeUtf(entry.address); b.writeUtf(entry.name); }
                b.writeBoolean(v.selectedSource != null); if (v.selectedSource != null) b.writeUUID(v.selectedSource);
                b.writeUtf(v.selectedSourcePort == null ? "" : v.selectedSourcePort); },
            b -> { var id = new DeviceIdentity(b.readUUID()); var address = b.readUtf(); var mode = b.readEnum(Mode.class);
                int size = b.readVarInt(); if (size < 0 || size > 4096) throw new IllegalArgumentException("invalid staged list size");
                var entries = new java.util.ArrayList<StagedDevice>(size);
                for (int n = 0; n < size; n++) entries.add(new StagedDevice(b.readUUID(), b.readUtf(), b.readUtf()));
                var source = b.readBoolean() ? b.readUUID() : null; var port = b.readUtf();
                return new NetworkConfiguratorStackData(id, address, mode, entries, source, port.isEmpty() ? null : port); });
    public NetworkConfiguratorStackData {
        Objects.requireNonNull(identity); Objects.requireNonNull(address); Objects.requireNonNull(mode);
        staged = List.copyOf(staged); if (staged.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("null staged entry");
        if ((selectedSource == null) != (selectedSourcePort == null)) throw new IllegalArgumentException("incomplete selected source");
    }
    public static NetworkConfiguratorStackData create() { return new NetworkConfiguratorStackData(DeviceIdentity.random(), "", Mode.LINK, List.of(), null, null); }
}
