package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Close exactly one server-issued atmospheric machine workspace capability. */
public record AtmosMachineSessionClose(UUID session) implements CustomPacketPayload {
    public static final Type<AtmosMachineSessionClose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "atmos_machine_session_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosMachineSessionClose> STREAM_CODEC = StreamCodec.of(
            (b,v)->b.writeUUID(v.session()), b->new AtmosMachineSessionClose(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
