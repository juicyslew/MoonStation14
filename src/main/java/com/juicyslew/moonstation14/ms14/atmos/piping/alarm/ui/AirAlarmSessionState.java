package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
/** Authoritative open/close lifecycle edge for an alarm workspace. */
public record AirAlarmSessionState(UUID session, boolean open, String reason) implements CustomPacketPayload {
    public static final Type<AirAlarmSessionState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "air_alarm_session_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirAlarmSessionState> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeUUID(v.session());b.writeBoolean(v.open());b.writeUtf(v.reason(),64);},
            b->new AirAlarmSessionState(b.readUUID(),b.readBoolean(),b.readUtf(64)));
    public AirAlarmSessionState { if(session==null||reason==null||reason.length()>64) throw new IllegalArgumentException("invalid session state"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
