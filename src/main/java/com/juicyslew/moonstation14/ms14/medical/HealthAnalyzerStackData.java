package com.juicyslew.moonstation14.ms14.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.UUID;

/** Persistent identity and toggle only; patient/session state remains server-owned and transient. */
public record HealthAnalyzerStackData(UUID scannerId, boolean active) {
    public static final Codec<HealthAnalyzerStackData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("scanner_id").forGetter(v -> v.scannerId.toString()),
            Codec.BOOL.fieldOf("active").forGetter(HealthAnalyzerStackData::active)
    ).apply(i, (id, active) -> new HealthAnalyzerStackData(UUID.fromString(id), active)));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthAnalyzerStackData> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.scannerId); b.writeBoolean(v.active); },
            b -> new HealthAnalyzerStackData(b.readUUID(), b.readBoolean()));
    public HealthAnalyzerStackData { if (scannerId == null) throw new IllegalArgumentException("scanner UUID required"); }
    public static HealthAnalyzerStackData create() { return new HealthAnalyzerStackData(UUID.randomUUID(), false); }
}

