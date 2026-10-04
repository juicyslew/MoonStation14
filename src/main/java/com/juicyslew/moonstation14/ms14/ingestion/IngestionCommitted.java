package com.juicyslew.moonstation14.ms14.ingestion;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Consumer;

/** Owner-only hint that an authoritative ingestion dose committed; carries no item or reagent data. */
public record IngestionCommitted(UUID bodyId, long controlEpoch) implements CustomPacketPayload {
    public static final Type<IngestionCommitted> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "ingestion_committed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IngestionCommitted> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.bodyId); buf.writeVarLong(value.controlEpoch); },
            buf -> new IngestionCommitted(buf.readUUID(), buf.readVarLong()));
    private static volatile Consumer<IngestionCommitted> clientHandler;
    public IngestionCommitted {
        if (bodyId == null || controlEpoch <= 0) throw new IllegalArgumentException("invalid ingestion commit hint");
    }
    public static void setClientHandler(Consumer<IngestionCommitted> handler) { clientHandler = handler; }
    public static void deliverClient(IngestionCommitted hint) { var handler = clientHandler; if (handler != null) handler.accept(hint); }
    public boolean matchesAuthority(UUID currentBodyId, long currentEpoch) {
        return bodyId.equals(currentBodyId) && controlEpoch == currentEpoch;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
