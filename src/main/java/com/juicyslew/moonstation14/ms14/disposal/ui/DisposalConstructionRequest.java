package com.juicyslew.moonstation14.ms14.disposal.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authenticated-body intent; it carries no client-authoritative orientation or material data. */
public record DisposalConstructionRequest(UUID bodyUUID, long epoch, long sequence, Action action, String prototype) implements CustomPacketPayload {
    public enum Action { ENTER, SELECT, ROTATE, EXIT }
    public static final Type<DisposalConstructionRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "disposal_construction_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisposalConstructionRequest> STREAM_CODEC = StreamCodec.of((b,v)->{
        b.writeUUID(v.bodyUUID); b.writeVarLong(v.epoch); b.writeVarLong(v.sequence); b.writeEnum(v.action); b.writeUtf(v.prototype,128);
    }, b->new DisposalConstructionRequest(b.readUUID(),b.readVarLong(),b.readVarLong(),b.readEnum(Action.class),b.readUtf(128)));
    public DisposalConstructionRequest {
        if(bodyUUID==null||epoch<=0||sequence<=0||action==null||prototype==null)throw new IllegalArgumentException("invalid disposal construction intent");
        if(action==Action.SELECT ? prototype.isBlank() : !prototype.isEmpty())throw new IllegalArgumentException("prototype only valid for SELECT");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

