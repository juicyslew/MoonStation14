package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record DisposalUnitAction(int containerId, UUID session, Operation operation) implements CustomPacketPayload {
    public enum Operation { CLIMB_INSIDE, ENGAGE, POWER, EJECT }
    public static final Type<DisposalUnitAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","disposal_unit_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisposalUnitAction> STREAM_CODEC=StreamCodec.of((buf,v)->{buf.writeVarInt(v.containerId);buf.writeUUID(v.session);buf.writeEnum(v.operation);},buf->new DisposalUnitAction(buf.readVarInt(),buf.readUUID(),buf.readEnum(Operation.class)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
