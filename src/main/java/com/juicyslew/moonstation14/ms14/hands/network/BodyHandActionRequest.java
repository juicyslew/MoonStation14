package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Intent only: actor and body are derived exclusively from the authenticated packet context. */
public record BodyHandActionRequest(Action action, String handId, long epoch, long sequence,
                                    long expectedRevision, UUID pickupTarget, String expectedToken)
        implements CustomPacketPayload {
    public enum Action { PICKUP, DROP }

    public static final Type<BodyHandActionRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_hand_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyHandActionRequest> STREAM_CODEC =
            StreamCodec.of((buf, request) -> {
                buf.writeEnum(request.action);
                buf.writeUtf(request.handId, HandState.MAX_ID_LENGTH);
                buf.writeVarLong(request.epoch);
                buf.writeVarLong(request.sequence);
                buf.writeVarLong(request.expectedRevision);
                if (request.action == Action.PICKUP) buf.writeUUID(request.pickupTarget);
                else buf.writeUtf(request.expectedToken, ItemToken.MAX_LENGTH);
            }, buf -> {
                Action action = buf.readEnum(Action.class);
                String hand = buf.readUtf(HandState.MAX_ID_LENGTH);
                long epoch = buf.readVarLong();
                long sequence = buf.readVarLong();
                long revision = buf.readVarLong();
                return action == Action.PICKUP
                        ? new BodyHandActionRequest(action, hand, epoch, sequence, revision, buf.readUUID(), null)
                        : new BodyHandActionRequest(action, hand, epoch, sequence, revision, null,
                                buf.readUtf(ItemToken.MAX_LENGTH));
            });

    public BodyHandActionRequest {
        if (action == null || handId == null || handId.isBlank() || handId.length() > HandState.MAX_ID_LENGTH
                || epoch <= 0 || sequence <= 0 || expectedRevision < 0
                || (action == Action.PICKUP && (pickupTarget == null || expectedToken != null))
                || (action == Action.DROP && (pickupTarget != null || expectedToken == null
                        || expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH)))
            throw new IllegalArgumentException("Invalid body hand intent");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
