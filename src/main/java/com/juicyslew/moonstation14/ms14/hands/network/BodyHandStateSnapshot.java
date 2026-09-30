package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Read-only, account-bound full state. No stack NBT/components or client-supplied ownership. */
public record BodyHandStateSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId,
                                    UUID bodyId, long epoch, long revision, String activeHand,
                                    List<Hand> hands) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, RATE_LIMITED, REPLAY, NOT_READY }

    public record Hand(String id, String token, String itemId, int count) {
        public Hand {
            if (id == null || id.isBlank() || id.length() > HandState.MAX_ID_LENGTH
                    || (token == null ? itemId != null || count != 0
                    : token.isBlank() || token.length() > ItemToken.MAX_LENGTH || itemId == null
                            || itemId.isBlank() || itemId.length() > 256 || count <= 0 || count > 99))
                throw new IllegalArgumentException("Invalid hand snapshot slot");
        }
    }

    public static final Type<BodyHandStateSnapshot> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_hand_state_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyHandStateSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                buf.writeVarLong(snapshot.requestedEpoch);
                buf.writeVarLong(snapshot.sequence);
                buf.writeEnum(snapshot.reason);
                if (snapshot.reason == Reason.OK) {
                    buf.writeUUID(snapshot.accountId);
                    buf.writeUUID(snapshot.bodyId);
                    buf.writeVarLong(snapshot.epoch);
                    buf.writeVarLong(snapshot.revision);
                    buf.writeUtf(snapshot.activeHand, HandState.MAX_ID_LENGTH);
                    buf.writeVarInt(snapshot.hands.size());
                    for (Hand hand : snapshot.hands) {
                        buf.writeUtf(hand.id(), HandState.MAX_ID_LENGTH);
                        buf.writeBoolean(hand.token() != null);
                        if (hand.token() != null) {
                            buf.writeUtf(hand.token(), ItemToken.MAX_LENGTH);
                            buf.writeUtf(hand.itemId(), 256);
                            buf.writeVarInt(hand.count());
                        }
                    }
                }
            }, buf -> {
                long requestedEpoch = buf.readVarLong();
                long sequence = buf.readVarLong();
                Reason reason = buf.readEnum(Reason.class);
                if (reason != Reason.OK)
                    return new BodyHandStateSnapshot(requestedEpoch, sequence, reason, null, null, 0, 0, null, List.of());
                UUID account = buf.readUUID();
                UUID body = buf.readUUID();
                long epoch = buf.readVarLong();
                long revision = buf.readVarLong();
                String active = buf.readUtf(HandState.MAX_ID_LENGTH);
                int size = buf.readVarInt();
                if (size < 1 || size > HandState.MAX_HANDS) throw new IllegalArgumentException("Invalid hand count");
                var slots = new java.util.ArrayList<Hand>(size);
                for (int i = 0; i < size; i++) {
                    String id = buf.readUtf(HandState.MAX_ID_LENGTH);
                    slots.add(buf.readBoolean() ? new Hand(id, buf.readUtf(ItemToken.MAX_LENGTH),
                            buf.readUtf(256), buf.readVarInt()) : new Hand(id, null, null, 0));
                }
                return new BodyHandStateSnapshot(requestedEpoch, sequence, reason, account, body,
                        epoch, revision, active, slots);
            });

    public BodyHandStateSnapshot {
        if (requestedEpoch < 0 || sequence <= 0 || reason == null || hands == null)
            throw new IllegalArgumentException("Invalid hand state snapshot");
        hands = List.copyOf(hands);
        if (reason == Reason.OK) {
            if (accountId == null || bodyId == null || epoch <= 0 || revision < 0
                    || hands.isEmpty() || hands.size() > HandState.MAX_HANDS) throw new IllegalArgumentException("Invalid binding");
            var ids = new HashSet<String>();
            var tokens = new HashSet<String>();
            for (Hand hand : hands) {
                if (!ids.add(hand.id()) || hand.token() != null && !tokens.add(hand.token()))
                    throw new IllegalArgumentException("Duplicate hand or token");
            }
            if (!ids.contains(activeHand)) throw new IllegalArgumentException("Unknown active hand");
        } else if (accountId != null || bodyId != null || epoch != 0 || revision != 0
                || activeHand != null || !hands.isEmpty()) throw new IllegalArgumentException("Invalid rejection");
    }

    public static BodyHandStateSnapshot rejected(BodyHandStateQuery query, Reason reason) {
        if (reason == Reason.OK) throw new IllegalArgumentException("Not a rejection");
        return new BodyHandStateSnapshot(query.epoch(), query.sequence(), reason, null, null, 0, 0, null, List.of());
    }

    // Client-only callers can install a handler after client startup. The common network layer never loads a UI class.
    private static volatile Consumer<BodyHandStateSnapshot> clientHandler;
    public static void setClientHandler(Consumer<BodyHandStateSnapshot> handler) { clientHandler = handler; }
    static void deliverClient(BodyHandStateSnapshot snapshot) {
        Consumer<BodyHandStateSnapshot> handler = clientHandler;
        if (handler != null) handler.accept(snapshot);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
