package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authenticated-session operator command; all authority and values are revalidated server-side. */
public record PaControlRequest(UUID sessionId, long requestId, long revision, Operation operation,
                               boolean enabled, int strength) implements CustomPacketPayload {
    public enum Operation { ENABLE, STRENGTH, RESCAN, CLOSE }
    public static final Type<PaControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"pa_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PaControlRequest> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.sessionId);b.writeLong(p.requestId);b.writeLong(p.revision);b.writeEnum(p.operation);b.writeBoolean(p.enabled);b.writeVarInt(p.strength);
    },b->new PaControlRequest(b.readUUID(),b.readLong(),b.readLong(),b.readEnum(Operation.class),b.readBoolean(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
