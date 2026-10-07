package com.juicyslew.moonstation14.ms14.doors.ui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record AirlockWireOpen(int windowId, UUID session) implements CustomPacketPayload {
    public static final Type<AirlockWireOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "airlock_wire_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockWireOpen> STREAM_CODEC = StreamCodec.of((b,v)->{b.writeVarInt(v.windowId);b.writeUUID(v.session);}, b->new AirlockWireOpen(b.readVarInt(),b.readUUID()));
    public AirlockWireOpen { if(windowId<0||session==null) throw new IllegalArgumentException("invalid wire window"); }
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
