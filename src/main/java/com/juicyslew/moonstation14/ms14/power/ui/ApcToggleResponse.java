package com.juicyslew.moonstation14.ms14.power.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Result correlated to one intent. Snapshot fields are populated only for a validated menu session. */
public record ApcToggleResponse(int containerId, UUID session, long requestId, boolean accepted,
                                boolean hasSnapshot, boolean breakerClosed, long revision,
                                int batteryPermille, boolean tripLatched) implements CustomPacketPayload {
    public static final Type<ApcToggleResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            "moonstation14", "apc_toggle_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApcToggleResponse> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeVarInt(value.containerId);
                buf.writeUUID(value.session);
                buf.writeVarLong(value.requestId);
                buf.writeBoolean(value.accepted);
                buf.writeBoolean(value.hasSnapshot);
                if (value.hasSnapshot) {
                    buf.writeBoolean(value.breakerClosed);
                    buf.writeVarLong(value.revision);
                    buf.writeVarInt(value.batteryPermille);
                    buf.writeBoolean(value.tripLatched);
                }
            }, buf -> {
                int containerId = buf.readVarInt();
                UUID session = buf.readUUID();
                long requestId = buf.readVarLong();
                boolean accepted = buf.readBoolean();
                boolean hasSnapshot = buf.readBoolean();
                return hasSnapshot
                        ? new ApcToggleResponse(containerId, session, requestId, accepted, true,
                            buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readBoolean())
                        : new ApcToggleResponse(containerId, session, requestId, accepted, false, false, 0, 0, false);
            });

    public ApcToggleResponse {
        if (containerId < 0 || requestId < 0 || (hasSnapshot && (revision < 0 || batteryPermille < 0
                || batteryPermille > 1000))) throw new IllegalArgumentException("Invalid APC result");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
