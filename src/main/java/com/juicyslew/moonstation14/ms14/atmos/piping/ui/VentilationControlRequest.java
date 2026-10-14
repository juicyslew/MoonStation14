package com.juicyslew.moonstation14.ms14.atmos.piping.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Bounded desired-state action; never contains a target position or complete configuration. */
public record VentilationControlRequest(int containerId, UUID session, long requestId, long expectedRevision,
                                        Operation operation, boolean desiredBoolean, int enumIndex)
        implements CustomPacketPayload {
    public enum Operation { SET_ENABLED, SET_SCRUBBER_MODE, SET_FILTER_GAS, QUERY }
    public static final Type<VentilationControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "ventilation_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VentilationControlRequest> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId); b.writeVarLong(v.expectedRevision); b.writeEnum(v.operation); b.writeBoolean(v.desiredBoolean); b.writeVarInt(v.enumIndex); },
            b -> new VentilationControlRequest(b.readVarInt(), b.readUUID(), b.readVarLong(), b.readVarLong(), b.readEnum(Operation.class), b.readBoolean(), b.readVarInt()));
    public VentilationControlRequest {
         if (containerId < 0 || session == null || requestId < 0 || expectedRevision < 0 || operation == null
                 || enumIndex < 0 || enumIndex >= GasType.values().length)
            throw new IllegalArgumentException("Invalid ventilation control request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

