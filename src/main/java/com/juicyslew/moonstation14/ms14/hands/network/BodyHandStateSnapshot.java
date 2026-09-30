package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.item.ModItems;
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
                                    List<Hand> hands, List<EquipmentSlot> equipment) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, RATE_LIMITED, REPLAY, NOT_READY }

    public BodyHandStateSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId,
                                 UUID bodyId, long epoch, long revision, String activeHand, List<Hand> hands) {
        this(requestedEpoch, sequence, reason, accountId, bodyId, epoch, revision, activeHand, hands, List.of());
    }

    public record EquipmentSlot(String id, String token, String itemId, int count,
                                String childToken, String childItemId, int childCount) {
        public EquipmentSlot {
            if (!("belt".equals(id) || "back".equals(id))
                    || (token == null ? itemId != null || count != 0 || childToken != null
                            || childItemId != null || childCount != 0
                            : token.isBlank() || token.length() > ItemToken.MAX_LENGTH || count != 1
                            || !("belt".equals(id) ? ModItems.BELT.getId().toString() : ModItems.BAG.getId().toString()).equals(itemId)
                            || (childToken == null ? childItemId != null || childCount != 0
                                    : childToken.isBlank() || childToken.length() > ItemToken.MAX_LENGTH
                                            || childToken.equals(token) || childItemId == null || childItemId.isBlank()
                                            || childItemId.length() > 256 || childCount < 1 || childCount > 64
                                            || childItemId.equals(ModItems.POUCH.getId().toString())
                                            || childItemId.equals(ModItems.BELT.getId().toString())
                                            || childItemId.equals(ModItems.BAG.getId().toString()))))
                throw new IllegalArgumentException("Invalid equipment snapshot slot");
        }
    }

    public record Hand(String id, String token, String itemId, int count,
                       boolean pouch, String childToken, String childItemId, int childCount) {
        public Hand(String id, String token, String itemId, int count) {
            this(id, token, itemId, count, false, null, null, 0);
        }

        public Hand {
            if (id == null || id.isBlank() || id.length() > HandState.MAX_ID_LENGTH
                    || (token == null ? itemId != null || count != 0
                    : token.isBlank() || token.length() > ItemToken.MAX_LENGTH || itemId == null
                             || itemId.isBlank() || itemId.length() > 256 || count <= 0 || count > 99)
                    || (pouch && (token == null || !ModItems.POUCH.getId().toString().equals(itemId) || count != 1))
                    || (!pouch && token != null && ModItems.POUCH.getId().toString().equals(itemId))
                    || (!pouch && (childToken != null || childItemId != null || childCount != 0))
                    || (childToken == null ? childItemId != null || childCount != 0
                            : !pouch || childToken.isBlank() || childToken.length() > ItemToken.MAX_LENGTH
                                    || childToken.equals(token) || childItemId == null || childItemId.isBlank()
                                    || childItemId.length() > 256 || childItemId.equals(ModItems.POUCH.getId().toString())
                                    || childCount < 1 || childCount > 64))
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
                        buf.writeBoolean(hand.pouch());
                        if (hand.pouch()) {
                            buf.writeBoolean(hand.childToken() != null);
                            if (hand.childToken() != null) {
                                buf.writeUtf(hand.childToken(), ItemToken.MAX_LENGTH);
                                buf.writeUtf(hand.childItemId(), 256);
                                buf.writeVarInt(hand.childCount());
                            }
                        }
                    }
                    buf.writeVarInt(snapshot.equipment.size());
                    for (EquipmentSlot slot : snapshot.equipment) {
                        buf.writeUtf(slot.id(), HandState.MAX_ID_LENGTH);
                        buf.writeBoolean(slot.token() != null);
                        if (slot.token() != null) {
                            buf.writeUtf(slot.token(), ItemToken.MAX_LENGTH);
                            buf.writeUtf(slot.itemId(), 256);
                            buf.writeVarInt(slot.count());
                            buf.writeBoolean(slot.childToken() != null);
                            if (slot.childToken() != null) {
                                buf.writeUtf(slot.childToken(), ItemToken.MAX_LENGTH);
                                buf.writeUtf(slot.childItemId(), 256);
                                buf.writeVarInt(slot.childCount());
                            }
                        }
                    }
                }
            }, buf -> {
                long requestedEpoch = buf.readVarLong();
                long sequence = buf.readVarLong();
                Reason reason = buf.readEnum(Reason.class);
                if (reason != Reason.OK) {
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected rejection payload");
                    return new BodyHandStateSnapshot(requestedEpoch, sequence, reason, null, null, 0, 0, null, List.of());
                }
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
                    boolean occupied = buf.readBoolean();
                    String token = occupied ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                    String itemId = occupied ? buf.readUtf(256) : null;
                    int count = occupied ? buf.readVarInt() : 0;
                    boolean pouch = buf.readBoolean();
                    boolean child = pouch && buf.readBoolean();
                    slots.add(new Hand(id, token, itemId, count, pouch,
                            child ? buf.readUtf(ItemToken.MAX_LENGTH) : null,
                            child ? buf.readUtf(256) : null, child ? buf.readVarInt() : 0));
                }
                int equipmentSize = buf.readVarInt();
                if (equipmentSize < 0 || equipmentSize > 2) throw new IllegalArgumentException("Invalid equipment count");
                var equipment = new java.util.ArrayList<EquipmentSlot>(equipmentSize);
                for (int i = 0; i < equipmentSize; i++) {
                    String id = buf.readUtf(HandState.MAX_ID_LENGTH);
                    boolean occupied = buf.readBoolean();
                    String token = occupied ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                    String item = occupied ? buf.readUtf(256) : null;
                    int count = occupied ? buf.readVarInt() : 0;
                    boolean child = occupied && buf.readBoolean();
                    equipment.add(new EquipmentSlot(id, token, item, count,
                            child ? buf.readUtf(ItemToken.MAX_LENGTH) : null,
                            child ? buf.readUtf(256) : null, child ? buf.readVarInt() : 0));
                }
                if (buf.isReadable()) throw new IllegalArgumentException("Unexpected snapshot payload");
                return new BodyHandStateSnapshot(requestedEpoch, sequence, reason, account, body,
                        epoch, revision, active, slots, equipment);
            });

    public BodyHandStateSnapshot {
        if (requestedEpoch < 0 || sequence <= 0 || reason == null || hands == null || equipment == null)
            throw new IllegalArgumentException("Invalid hand state snapshot");
        hands = List.copyOf(hands);
        equipment = List.copyOf(equipment);
        if (reason == Reason.OK) {
            if (accountId == null || bodyId == null || epoch <= 0 || revision < 0
                    || hands.isEmpty() || hands.size() > HandState.MAX_HANDS) throw new IllegalArgumentException("Invalid binding");
            var ids = new HashSet<String>();
            var tokens = new HashSet<String>();
            for (Hand hand : hands) {
                if (!ids.add(hand.id()) || hand.token() != null && !tokens.add(hand.token()))
                    throw new IllegalArgumentException("Duplicate hand or token");
            }
            for (Hand hand : hands) {
                if (hand.childToken() != null && !tokens.add(hand.childToken()))
                    throw new IllegalArgumentException("Duplicate pouch child token");
            }
            if (equipment.size() > 2) throw new IllegalArgumentException("Too many equipment slots");
            var equipmentIds = new HashSet<String>();
            for (EquipmentSlot slot : equipment) {
                if (!equipmentIds.add(slot.id()) || slot.token() != null && !tokens.add(slot.token())
                        || slot.childToken() != null && !tokens.add(slot.childToken()))
                    throw new IllegalArgumentException("Duplicate equipment slot or token");
            }
            if (!ids.contains(activeHand)) throw new IllegalArgumentException("Unknown active hand");
        } else if (accountId != null || bodyId != null || epoch != 0 || revision != 0
                || activeHand != null || !hands.isEmpty() || !equipment.isEmpty()) throw new IllegalArgumentException("Invalid rejection");
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
