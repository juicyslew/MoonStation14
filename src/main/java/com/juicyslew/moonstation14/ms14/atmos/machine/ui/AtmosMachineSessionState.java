package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authoritative lifecycle edge; open is followed by the normal settings snapshot. */
public record AtmosMachineSessionState(UUID session, boolean open, String reason) implements CustomPacketPayload {
    public static final Type<AtmosMachineSessionState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "atmos_machine_session_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosMachineSessionState> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeUUID(v.session());b.writeBoolean(v.open());b.writeUtf(v.reason(),64);},
            b->new AtmosMachineSessionState(b.readUUID(),b.readBoolean(),b.readUtf(64)));
    public AtmosMachineSessionState { if(session==null||reason==null||reason.length()>64) throw new IllegalArgumentException("invalid session state"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
