package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Intent only: moves one child between two distinct WORLD grid hosts. */
public record BodyWorldWorldCrossAction(long epoch, long sequence,
        UUID sourceTarget, UUID sourceSession, long sourceHostRevision,
        UUID destinationTarget, UUID destinationSession, long destinationHostRevision,
        long bodyHandRevision, String childToken,
        int oldX, int oldY, int oldRotation, int newX, int newY, int newRotation)
        implements CustomPacketPayload {
    public static final Type<BodyWorldWorldCrossAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_world_cross_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldWorldCrossAction> STREAM_CODEC = StreamCodec.of(
            (b, a) -> { b.writeVarLong(a.epoch); b.writeVarLong(a.sequence);
                b.writeUUID(a.sourceTarget); b.writeUUID(a.sourceSession); b.writeVarLong(a.sourceHostRevision);
                b.writeUUID(a.destinationTarget); b.writeUUID(a.destinationSession); b.writeVarLong(a.destinationHostRevision);
                b.writeVarLong(a.bodyHandRevision); b.writeUtf(a.childToken, ItemToken.MAX_LENGTH);
                b.writeVarInt(a.oldX); b.writeVarInt(a.oldY); b.writeVarInt(a.oldRotation);
                b.writeVarInt(a.newX); b.writeVarInt(a.newY); b.writeVarInt(a.newRotation); },
            b -> { long epoch = b.readVarLong(), sequence = b.readVarLong();
                UUID source = b.readUUID(), sourceSession = b.readUUID(); long sourceRevision = b.readVarLong();
                UUID destination = b.readUUID(), destinationSession = b.readUUID(); long destinationRevision = b.readVarLong();
                long bodyRevision = b.readVarLong(); String child = b.readUtf(ItemToken.MAX_LENGTH);
                int ox = b.readVarInt(), oy = b.readVarInt(), or = b.readVarInt();
                int nx = b.readVarInt(), ny = b.readVarInt(), nr = b.readVarInt();
                if (b.isReadable()) throw new IllegalArgumentException("Trailing world cross action data");
                return new BodyWorldWorldCrossAction(epoch, sequence, source, sourceSession, sourceRevision,
                        destination, destinationSession, destinationRevision, bodyRevision, child, ox, oy, or, nx, ny, nr); });
    public BodyWorldWorldCrossAction {
        if (epoch <= 0 || sequence <= 0 || sourceTarget == null || sourceSession == null
                || destinationTarget == null || destinationSession == null || sourceTarget.equals(destinationTarget)
                || sourceSession.equals(destinationSession) || sourceHostRevision < 0 || destinationHostRevision < 0
                || bodyHandRevision < 0 || childToken == null || childToken.isBlank() || childToken.length() > ItemToken.MAX_LENGTH
                || !cell(oldX, oldY) || !cell(newX, newY) || !rotation(oldRotation) || !rotation(newRotation))
            throw new IllegalArgumentException("Invalid world cross action");
    }
    private static boolean cell(int x, int y) { return x >= 0 && y >= 0 && x < GridGeometry.MAX_WIDTH && y < GridGeometry.MAX_HEIGHT; }
    private static boolean rotation(int n) { return n == 0 || n == 90 || n == 180 || n == 270; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
