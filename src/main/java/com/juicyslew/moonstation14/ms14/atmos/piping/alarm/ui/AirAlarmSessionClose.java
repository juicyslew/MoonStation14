package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
/** Close exactly one server-issued alarm workspace. */
public record AirAlarmSessionClose(UUID session) implements CustomPacketPayload {
    public static final Type<AirAlarmSessionClose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "air_alarm_session_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirAlarmSessionClose> STREAM_CODEC = StreamCodec.of(
            (b,v)->b.writeUUID(v.session()),b->new AirAlarmSessionClose(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
