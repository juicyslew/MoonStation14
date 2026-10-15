package com.juicyslew.moonstation14.ms14.doors.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;

import java.util.UUID;

/** Bounded intent; selector IDs are resolved only by the server's cached layout. */
public record AirlockWireRequest(int containerId, UUID session, long requestId, long expectedRootRevision,
                                 int rowId, Operation operation) implements CustomPacketPayload {
    public enum Operation { QUERY, CLICK, CUT, MEND, PULSE }
    public static final Type<AirlockWireRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "airlock_wire_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockWireRequest> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId);
                b.writeVarLong(v.expectedRootRevision); b.writeVarInt(v.rowId); b.writeEnum(v.operation); },
            b -> new AirlockWireRequest(b.readVarInt(), b.readUUID(), b.readVarLong(), b.readVarLong(), b.readVarInt(), b.readEnum(Operation.class)));
    public AirlockWireRequest {
        if (containerId < 0 || session == null || requestId < 0 || expectedRootRevision < 0
                || rowId < -1 || rowId >= AirlockState.Wire.values().length || operation == null
                || (operation == Operation.QUERY) != (rowId == -1)) throw new IllegalArgumentException("invalid airlock wire request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

