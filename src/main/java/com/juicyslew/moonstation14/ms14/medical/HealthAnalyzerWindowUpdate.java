package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client read-only analyzer workspace update. */
public record HealthAnalyzerWindowUpdate(HealthAnalyzerWindowState state) implements CustomPacketPayload {
    public static final Type<HealthAnalyzerWindowUpdate> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "health_analyzer_window"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthAnalyzerWindowUpdate> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> HealthAnalyzerPayloadCodec.writeState(buf, value.state),
            buf -> new HealthAnalyzerWindowUpdate(HealthAnalyzerPayloadCodec.readState(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
