package com.juicyslew.moonstation14.ms14.disposal.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative mode snapshot. Client delivery is an optional callback installed by client code. */
public record DisposalConstructionState(UUID bodyUUID,long epoch,long sequence,boolean active,String prototype,int rotationOffset,long revision) implements CustomPacketPayload {
    public static final Type<DisposalConstructionState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "disposal_construction_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisposalConstructionState> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeUUID(v.bodyUUID);b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeBoolean(v.active);b.writeUtf(v.prototype,128);b.writeVarInt(v.rotationOffset);b.writeVarLong(v.revision);
    },b->new DisposalConstructionState(b.readUUID(),b.readVarLong(),b.readVarLong(),b.readBoolean(),b.readUtf(128),b.readVarInt(),b.readVarLong()));
    private static volatile Consumer<DisposalConstructionState> clientReceiver;
    public DisposalConstructionState {
        if(bodyUUID==null||epoch<=0||sequence<0||prototype==null||rotationOffset<0||revision<0||(!active&&!prototype.isEmpty())||(active&&prototype.isBlank()))throw new IllegalArgumentException("invalid disposal construction state");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void setClientReceiver(Consumer<DisposalConstructionState> receiver){clientReceiver=receiver;}
    public static void deliverClient(DisposalConstructionState state){var receiver=clientReceiver;if(receiver!=null)receiver.accept(state);}
}

