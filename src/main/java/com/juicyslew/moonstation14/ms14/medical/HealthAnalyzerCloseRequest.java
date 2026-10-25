package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Untrusted close intent; actor qualification is checked against the bound server subscriber. */
public record HealthAnalyzerCloseRequest(UUID scannerId, long epoch, UUID actorId) implements CustomPacketPayload {
    public static final Type<HealthAnalyzerCloseRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "health_analyzer_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthAnalyzerCloseRequest> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.scannerId); buf.writeVarLong(value.epoch); buf.writeUUID(value.actorId); },
            buf -> new HealthAnalyzerCloseRequest(buf.readUUID(), buf.readVarLong(), buf.readUUID()));
    public HealthAnalyzerCloseRequest { if (scannerId == null || actorId == null || epoch < 0) throw new IllegalArgumentException("invalid close request"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

