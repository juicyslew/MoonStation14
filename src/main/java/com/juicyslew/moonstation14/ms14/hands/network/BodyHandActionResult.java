package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

/** Acknowledgement, not a client-owned item or authority grant. Occupancy describes the
 * requested hand only, NOT activeHand when those IDs differ. Query full state after success or staleness. */
public record BodyHandActionResult(long epoch, long sequence, boolean accepted, Reason reason,
                                   boolean hasSnapshot, long revision, String activeHand,
                                   String itemToken, String itemId, int itemCount) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, WRONG_EPOCH, RATE_LIMITED, REPLAY, STALE_HAND,
        PICKUP_DISABLED, DENIED, RECOVERY_REQUIRED }

    public static final Type<BodyHandActionResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_hand_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyHandActionResult> STREAM_CODEC =
            StreamCodec.of((buf, result) -> {
                buf.writeVarLong(result.epoch);
                buf.writeVarLong(result.sequence);
                buf.writeBoolean(result.accepted);
                buf.writeEnum(result.reason);
                buf.writeBoolean(result.hasSnapshot);
                if (result.hasSnapshot) {
                    buf.writeVarLong(result.revision);
                    buf.writeUtf(result.activeHand, HandState.MAX_ID_LENGTH);
                    buf.writeBoolean(result.itemToken != null);
                    if (result.itemToken != null) {
                        buf.writeUtf(result.itemToken, ItemToken.MAX_LENGTH);
                        buf.writeUtf(result.itemId, 256);
                        buf.writeVarInt(result.itemCount);
                    }
                }
            }, buf -> {
                long epoch = buf.readVarLong();
                long sequence = buf.readVarLong();
                boolean accepted = buf.readBoolean();
                Reason reason = buf.readEnum(Reason.class);
                if (!buf.readBoolean())
                    return new BodyHandActionResult(epoch, sequence, accepted, reason, false, 0, null, null, null, 0);
                long revision = buf.readVarLong();
                String active = buf.readUtf(HandState.MAX_ID_LENGTH);
                if (!buf.readBoolean())
                    return new BodyHandActionResult(epoch, sequence, accepted, reason, true, revision, active, null, null, 0);
                return new BodyHandActionResult(epoch, sequence, accepted, reason, true, revision, active,
                        buf.readUtf(ItemToken.MAX_LENGTH), buf.readUtf(256), buf.readVarInt());
            });

    public BodyHandActionResult {
        if (epoch <= 0 || sequence <= 0 || reason == null || (accepted != (reason == Reason.OK))
                || (hasSnapshot && (revision < 0 || activeHand == null || activeHand.isBlank()
                        || activeHand.length() > HandState.MAX_ID_LENGTH))
                || (itemToken != null && (!hasSnapshot || itemToken.isBlank()
                        || itemToken.length() > ItemToken.MAX_LENGTH || itemId == null
                        || itemId.isBlank() || itemId.length() > 256 || itemCount <= 0))
                || (itemToken == null && (itemId != null || itemCount != 0)))
            throw new IllegalArgumentException("Invalid body hand result");
    }

    // A client consumer must invalidate its full snapshot on OK or STALE_HAND and query again.
    // This packet's token is for the request's handId, not necessarily activeHand.
    private static volatile Consumer<BodyHandActionResult> clientHandler;
    public static void setClientHandler(Consumer<BodyHandActionResult> handler) { clientHandler = handler; }
    static void deliverClient(BodyHandActionResult result) {
        Consumer<BodyHandActionResult> handler = clientHandler;
        if (handler != null) handler.accept(result);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
