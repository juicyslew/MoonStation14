package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

/** Stable portable endpoint identity/address, stored on the actual nonstackable item stack. */
public record RemoteSignallerStackData(DeviceIdentity identity, String address) {
    public static final Codec<RemoteSignallerStackData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("identity").forGetter(v -> v.identity.value().toString()),
            Codec.STRING.fieldOf("address").forGetter(RemoteSignallerStackData::address)
    ).apply(i, (id, address) -> new RemoteSignallerStackData(new DeviceIdentity(UUID.fromString(id)), address)));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteSignallerStackData> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.identity.value()); b.writeUtf(v.address); },
            b -> new RemoteSignallerStackData(new DeviceIdentity(b.readUUID()), b.readUtf()));
    public RemoteSignallerStackData { Objects.requireNonNull(identity); Objects.requireNonNull(address); }
    public static RemoteSignallerStackData create() { return new RemoteSignallerStackData(DeviceIdentity.random(), ""); }
}

