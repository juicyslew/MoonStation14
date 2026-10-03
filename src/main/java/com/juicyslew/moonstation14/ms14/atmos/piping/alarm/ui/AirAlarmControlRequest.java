package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Intent only: no client-specified coordinates, stacks, or wholesale settings. */
public record AirAlarmControlRequest(int containerId, UUID session, long requestId, UUID deviceId,
                                     long expectedRevision, Operation operation, boolean desired, int enumIndex, double numericValue)
        implements CustomPacketPayload {
    public AirAlarmControlRequest(int containerId, UUID session, long requestId, UUID deviceId, long expectedRevision,
                                  Operation operation, boolean desired, int enumIndex) {
        this(containerId, session, requestId, deviceId, expectedRevision, operation, desired, enumIndex, 0);
    }
    public enum Operation { QUERY, PAIR, UNPAIR, SET_ENABLED, SET_SCRUBBER_MODE, SET_FILTER_GAS, SET_EXTERNAL_PRESSURE_BOUND, SET_VOLUME_RATE }
    public static final Type<AirAlarmControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "air_alarm_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirAlarmControlRequest> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId);
                b.writeUUID(v.deviceId); b.writeVarLong(v.expectedRevision); b.writeEnum(v.operation);
                b.writeBoolean(v.desired); b.writeVarInt(v.enumIndex); b.writeDouble(v.numericValue); },
            b -> new AirAlarmControlRequest(b.readVarInt(), b.readUUID(), b.readVarLong(), b.readUUID(),
                    b.readVarLong(), b.readEnum(Operation.class), b.readBoolean(), b.readVarInt(), b.readDouble()));

    public AirAlarmControlRequest {
        if (containerId < 0 || session == null || requestId < 0 || deviceId == null || expectedRevision < 0
                || operation == null || enumIndex < 0 || enumIndex >= GasType.values().length
                || !Double.isFinite(numericValue) || numericValue < 0)
            throw new IllegalArgumentException("Invalid air alarm intent");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
