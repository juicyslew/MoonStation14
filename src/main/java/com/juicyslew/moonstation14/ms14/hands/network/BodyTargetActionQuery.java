package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Authenticated, proof-bound request for actions available against one nominated body target. */
public record BodyTargetActionQuery(long bodyEpoch, long sequence, long expectedRevision,
                                    String handId, String expectedToken, UUID targetId)
        implements CustomPacketPayload {
    public static final Type<BodyTargetActionQuery> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_target_action_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyTargetActionQuery> STREAM_CODEC = StreamCodec.of(
            (buf, query) -> {
                buf.writeVarLong(query.bodyEpoch()); buf.writeVarLong(query.sequence());
                buf.writeVarLong(query.expectedRevision()); buf.writeUtf(query.handId(), HandState.MAX_ID_LENGTH);
                buf.writeBoolean(query.expectedToken() != null);
                if (query.expectedToken() != null) buf.writeUtf(query.expectedToken(), ItemToken.MAX_LENGTH);
                buf.writeUUID(query.targetId());
            }, buf -> {
                long epoch = buf.readVarLong(), sequence = buf.readVarLong(), revision = buf.readVarLong();
                String hand = buf.readUtf(HandState.MAX_ID_LENGTH);
                int present = buf.readUnsignedByte();
                if (present > 1) throw new IllegalArgumentException("Invalid target-action token flag");
                String token = present == 1 ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                UUID target = buf.readUUID();
                if (buf.isReadable()) throw new IllegalArgumentException("Trailing target-action query data");
                return new BodyTargetActionQuery(epoch, sequence, revision, hand, token, target);
            });

    public BodyTargetActionQuery {
        if (bodyEpoch <= 0 || sequence <= 0 || expectedRevision < 0 || handId == null || handId.isBlank()
                || handId.length() > HandState.MAX_ID_LENGTH || targetId == null
                || expectedToken != null && (expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH))
            throw new IllegalArgumentException("Invalid target-action query");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
