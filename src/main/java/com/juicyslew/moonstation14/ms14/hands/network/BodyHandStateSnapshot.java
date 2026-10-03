package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Read-only, account-bound full state. No stack NBT/components or client-supplied ownership. */
public record BodyHandStateSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId,
                                     UUID bodyId, long epoch, long revision, String activeHand,
                                     List<Hand> hands, List<EquipmentSlot> equipment, List<GridHost> grids) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, RATE_LIMITED, REPLAY, NOT_READY }

    public BodyHandStateSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId,
                                 UUID bodyId, long epoch, long revision, String activeHand, List<Hand> hands) {
        this(requestedEpoch, sequence, reason, accountId, bodyId, epoch, revision, activeHand, hands, List.of(), List.of());
    }

    public BodyHandStateSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId,
                                 UUID bodyId, long epoch, long revision, String activeHand,
                                 List<Hand> hands, List<EquipmentSlot> equipment) {
        this(requestedEpoch, sequence, reason, accountId, bodyId, epoch, revision, activeHand, hands, equipment, List.of());
    }

    public record GridEntry(String token, String itemId, int count, int x, int y, int rotation) {
        public GridEntry {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH || itemId == null || itemId.isBlank()
                    || itemId.length() > 256 || count < 1 || count > 99 || x < 0 || x > 7 || y < 0 || y > 3
                    || rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
                throw new IllegalArgumentException("Invalid grid entry");
        }
    }

    public record GridHost(String hostToken, String kind, int width, int height, List<GridEntry> entries) {
        public GridHost {
            if (hostToken == null || hostToken.isBlank() || hostToken.length() > ItemToken.MAX_LENGTH
                    || !("bag".equals(kind) || "belt".equals(kind)) || width != ("bag".equals(kind) ? 7 : 8)
                    || height != ("bag".equals(kind) ? 4 : 2) || entries == null || entries.size() > 32)
                throw new IllegalArgumentException("Invalid grid host");
            entries = List.copyOf(entries);
            var tokens = new HashSet<String>();
            var occupied = GridGeometry.Snapshot.empty(GridGeometry.StorageMask.rectangle(width, height));
            for (GridEntry entry : entries) {
                if (!tokens.add(entry.token())) throw new IllegalArgumentException("Duplicate grid token");
                ResourceLocation id = ResourceLocation.tryParse(entry.itemId());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unregistered grid item");
                if (entry.itemId().equals("moonstation14:pouch") || entry.itemId().equals(ModItems.BAG.getId().toString())
                        || entry.itemId().equals(ModItems.BELT.getId().toString())) throw new IllegalArgumentException("Nested grid host");
                var rotation = GridGeometry.Rotation.values()[entry.rotation() / 90];
                var placed = occupied.place(entry.token(), ItemFootprints.forId(id), new GridGeometry.Cell(entry.x(), entry.y()), rotation);
                if (!placed.accepted()) throw new IllegalArgumentException("Invalid grid placement");
                occupied = placed.snapshot();
            }
        }
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
                                            || childItemId.equals("moonstation14:pouch")
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
                    || pouch
                    || (token != null && "moonstation14:pouch".equals(itemId))
                    || (childToken == null ? childItemId != null || childCount != 0
                            : !(pouch || token != null && (ModItems.BAG.getId().toString().equals(itemId)
                                    || ModItems.BELT.getId().toString().equals(itemId)))
                                    || childToken.isBlank() || childToken.length() > ItemToken.MAX_LENGTH
                                    || childToken.equals(token) || childItemId == null || childItemId.isBlank()
                                     || childItemId.length() > 256 || childItemId.equals("moonstation14:pouch")
                                    || childItemId.equals(ModItems.BAG.getId().toString()) || childItemId.equals(ModItems.BELT.getId().toString())
                                     || childCount < 1 || childCount > 64
                                     || "moonstation14:pouch".equals(childItemId)))
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
                        if (hand.token() != null) {
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
                    buf.writeVarInt(snapshot.grids.size());
                    for (GridHost grid : snapshot.grids) {
                        buf.writeUtf(grid.hostToken(), ItemToken.MAX_LENGTH);
                        buf.writeUtf(grid.kind(), 4);
                        buf.writeByte(grid.width()); buf.writeByte(grid.height()); buf.writeVarInt(grid.entries().size());
                        for (GridEntry entry : grid.entries()) {
                            buf.writeUtf(entry.token(), ItemToken.MAX_LENGTH); buf.writeUtf(entry.itemId(), 256);
                            buf.writeVarInt(entry.count()); buf.writeByte(entry.x()); buf.writeByte(entry.y()); buf.writeVarInt(entry.rotation());
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
                    boolean child = occupied && buf.readBoolean();
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
                int gridCount = buf.isReadable() ? buf.readVarInt() : 0;
                if (gridCount < 0 || gridCount > size + equipmentSize) throw new IllegalArgumentException("Invalid grid host count");
                var grids = new java.util.ArrayList<GridHost>(gridCount);
                int totalEntries = 0;
                for (int i = 0; i < gridCount; i++) {
                    String host = buf.readUtf(ItemToken.MAX_LENGTH), kind = buf.readUtf(4);
                    int width = buf.readUnsignedByte(), height = buf.readUnsignedByte(), count = buf.readVarInt();
                    totalEntries += count;
                    if (count < 0 || count > 32 || totalEntries > 32 * (HandState.MAX_HANDS + 2)) throw new IllegalArgumentException("Invalid grid entry count");
                    var entries = new java.util.ArrayList<GridEntry>(count);
                    for (int j = 0; j < count; j++) entries.add(new GridEntry(buf.readUtf(ItemToken.MAX_LENGTH), buf.readUtf(256),
                            buf.readVarInt(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readVarInt()));
                    grids.add(new GridHost(host, kind, width, height, entries));
                }
                if (buf.isReadable()) throw new IllegalArgumentException("Unexpected snapshot payload");
                return new BodyHandStateSnapshot(requestedEpoch, sequence, reason, account, body,
                        epoch, revision, active, slots, equipment, grids);
            });

    public BodyHandStateSnapshot {
        if (requestedEpoch < 0 || sequence <= 0 || reason == null || hands == null || equipment == null || grids == null)
            throw new IllegalArgumentException("Invalid hand state snapshot");
        hands = List.copyOf(hands);
        equipment = List.copyOf(equipment);
        grids = List.copyOf(grids);
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
            var gridHosts = new HashSet<String>();
            if (grids.size() > hands.size() + equipment.size()) throw new IllegalArgumentException("Too many grid hosts");
            for (GridHost grid : grids) {
                boolean matching = hands.stream().anyMatch(h -> grid.hostToken().equals(h.token())
                        && hostKind(h.itemId()) != null && grid.kind().equals(hostKind(h.itemId())))
                        || equipment.stream().anyMatch(e -> grid.hostToken().equals(e.token())
                        && hostKind(e.itemId()) != null && grid.kind().equals(hostKind(e.itemId())));
                if (!matching || !gridHosts.add(grid.hostToken())) throw new IllegalArgumentException("Invalid or duplicate grid host");
                if (hands.stream().anyMatch(h -> grid.hostToken().equals(h.token()) && h.childToken() != null)
                        || equipment.stream().anyMatch(e -> grid.hostToken().equals(e.token()) && e.childToken() != null))
                    throw new IllegalArgumentException("Grid host also has legacy child summary");
                for (GridEntry entry : grid.entries()) if (!tokens.add(entry.token())) throw new IllegalArgumentException("Duplicate grid entry token");
            }
            if (!ids.contains(activeHand)) throw new IllegalArgumentException("Unknown active hand");
        } else if (accountId != null || bodyId != null || epoch != 0 || revision != 0
                || activeHand != null || !hands.isEmpty() || !equipment.isEmpty() || !grids.isEmpty()) throw new IllegalArgumentException("Invalid rejection");
    }

    private static String hostKind(String itemId) {
        if (ModItems.BAG.getId().toString().equals(itemId)) return "bag";
        if (ModItems.BELT.getId().toString().equals(itemId)) return "belt";
        return null;
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
