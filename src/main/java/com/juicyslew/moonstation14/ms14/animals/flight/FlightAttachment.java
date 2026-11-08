package com.juicyslew.moonstation14.ms14.animals.flight;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class FlightAttachment {
    private FlightState state;
    public FlightAttachment() { this(FlightState.GROUNDED); }
    public FlightAttachment(FlightState state) { this.state = java.util.Objects.requireNonNull(state); }
    public FlightState state() { return state; }
    public void set(FlightState value) { state = java.util.Objects.requireNonNull(value); }
    public static final Codec<FlightAttachment> CODEC = Codec.STRING.xmap(
            value -> new FlightAttachment(FlightState.valueOf(value)), value -> value.state.name());
    private static final StreamCodec<RegistryFriendlyByteBuf, FlightState> STATE_CODEC = StreamCodec.of(
            (buffer, state) -> ByteBufCodecs.VAR_INT.encode(buffer, state.ordinal()),
            buffer -> FlightState.values()[ByteBufCodecs.VAR_INT.decode(buffer)]);
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightAttachment> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> STATE_CODEC.encode(buffer, value.state),
            buffer -> new FlightAttachment(STATE_CODEC.decode(buffer)));
}
