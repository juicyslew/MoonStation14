package com.juicyslew.moonstation14.ms14.stripping;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** OPEN/QUERY/CLOSE are observation requests; target action credentials are never sent. */
public record StripViewRequest(Operation operation, long epoch, long sequence, UUID target,
                               long targetRevision) implements CustomPacketPayload {
    public enum Operation { OPEN, QUERY, CLOSE }
    public static final Type<StripViewRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "strip_view_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StripViewRequest> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeByte(p.operation.ordinal()); b.writeVarLong(p.epoch); b.writeVarLong(p.sequence); b.writeUUID(p.target); b.writeVarLong(p.targetRevision); },
            b -> { int op=b.readUnsignedByte(); if(op>=Operation.values().length) throw new IllegalArgumentException("Bad strip operation"); var p=new StripViewRequest(Operation.values()[op],b.readVarLong(),b.readVarLong(),b.readUUID(),b.readVarLong()); if(b.isReadable())throw new IllegalArgumentException("Trailing strip request"); return p; });
    public StripViewRequest { if(operation==null||epoch<=0||sequence<=0||target==null||targetRevision<0)throw new IllegalArgumentException("Invalid strip request"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
