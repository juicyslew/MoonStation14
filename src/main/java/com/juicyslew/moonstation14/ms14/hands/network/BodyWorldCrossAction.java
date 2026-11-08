package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Intent only: WORLD_TO_BODY uses the world old anchor; BODY_TO_WORLD uses the world new anchor. */
public record BodyWorldCrossAction(Direction direction, long epoch, long sequence, UUID target, UUID session,
        long worldRevision, long bodyRevision, boolean worn, String bodyHostId, String bodyHostToken,
        String childToken, int oldX, int oldY, int oldRotation, int newX, int newY, int newRotation)
        implements CustomPacketPayload {
    public enum Direction { WORLD_TO_BODY, BODY_TO_WORLD, WORLD_TO_EQUIPMENT, EQUIPMENT_TO_WORLD }
    public static final Type<BodyWorldCrossAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_cross_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldCrossAction> STREAM_CODEC = StreamCodec.of((b, a) -> {
        b.writeEnum(a.direction); b.writeVarLong(a.epoch); b.writeVarLong(a.sequence); b.writeUUID(a.target);
        b.writeUUID(a.session); b.writeVarLong(a.worldRevision); b.writeVarLong(a.bodyRevision);
        b.writeBoolean(a.worn); b.writeUtf(a.bodyHostId, HandState.MAX_ID_LENGTH);
        b.writeBoolean(a.bodyHostToken != null);
        if (a.bodyHostToken != null) b.writeUtf(a.bodyHostToken, ItemToken.MAX_LENGTH);
        b.writeUtf(a.childToken, ItemToken.MAX_LENGTH);
        b.writeVarInt(a.oldX); b.writeVarInt(a.oldY); b.writeVarInt(a.oldRotation);
        b.writeVarInt(a.newX); b.writeVarInt(a.newY); b.writeVarInt(a.newRotation);
    }, b -> {
        var direction = b.readEnum(Direction.class); long epoch = b.readVarLong(), sequence = b.readVarLong();
        UUID target = b.readUUID(), session = b.readUUID(); long world = b.readVarLong(), body = b.readVarLong();
        int flag = b.readUnsignedByte(); if (flag > 1) throw new IllegalArgumentException("Invalid host flag");
        String id = b.readUtf(HandState.MAX_ID_LENGTH);
        int hasHostToken = b.readUnsignedByte(); if (hasHostToken > 1) throw new IllegalArgumentException("Invalid host-token flag");
        String hostToken = hasHostToken == 1 ? b.readUtf(ItemToken.MAX_LENGTH) : null;
        String child = b.readUtf(ItemToken.MAX_LENGTH);
        int ox = b.readVarInt(), oy = b.readVarInt(), or = b.readVarInt();
        int nx = b.readVarInt(), ny = b.readVarInt(), nr = b.readVarInt();
        if (b.isReadable()) throw new IllegalArgumentException("Trailing cross action data");
        return new BodyWorldCrossAction(direction, epoch, sequence, target, session, world, body, flag == 1,
                id, hostToken, child, ox, oy, or, nx, ny, nr);
    });
    public BodyWorldCrossAction {
        if (direction == null || epoch <= 0 || sequence <= 0 || target == null || session == null
                || worldRevision < 0 || bodyRevision < 0 || bodyHostId == null || bodyHostId.isBlank()
                || bodyHostId.length() > HandState.MAX_ID_LENGTH
                 || (direction == Direction.WORLD_TO_EQUIPMENT ? bodyHostToken != null
                    : bodyHostToken == null || bodyHostToken.isBlank())
                || bodyHostToken != null && bodyHostToken.length() > ItemToken.MAX_LENGTH || childToken == null || childToken.isBlank()
                 || childToken.length() > ItemToken.MAX_LENGTH || !cell(oldX, oldY) || !cell(newX, newY)
                 || !rotation(oldRotation) || !rotation(newRotation)) throw new IllegalArgumentException("Invalid cross action");
        if (direction == Direction.EQUIPMENT_TO_WORLD && (oldX != 0 || oldY != 0 || oldRotation != 0))
            throw new IllegalArgumentException("Equipment has no source grid cell");
    }
    private static boolean cell(int x, int y) { return x >= 0 && y >= 0 && x < GridGeometry.MAX_WIDTH && y < GridGeometry.MAX_HEIGHT; }
    private static boolean rotation(int n) { return n == 0 || n == 90 || n == 180 || n == 270; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

