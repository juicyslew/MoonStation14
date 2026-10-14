package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Intent only. No stack, entity instance, or body is accepted from the client. */
public record BodyWorldGridAction(Mode mode, long epoch, long sequence, UUID target, UUID session,
                                  long handRevision, long hostRevision, String hand, String token,
                                  int oldX, int oldY, int oldRotation, int newX, int newY, int newRotation)
        implements CustomPacketPayload {
    public enum Mode { STORE, TAKE, MOVE }
    public static final Type<BodyWorldGridAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_grid_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldGridAction> STREAM_CODEC = StreamCodec.of((b, a) -> {
        b.writeEnum(a.mode); b.writeVarLong(a.epoch); b.writeVarLong(a.sequence);
        b.writeUUID(a.target); b.writeUUID(a.session); b.writeVarLong(a.handRevision); b.writeVarLong(a.hostRevision);
        b.writeBoolean(a.hand != null); if (a.hand != null) b.writeUtf(a.hand, HandState.MAX_ID_LENGTH);
        b.writeUtf(a.token, ItemToken.MAX_LENGTH);
        b.writeVarInt(a.oldX); b.writeVarInt(a.oldY); b.writeVarInt(a.oldRotation);
        b.writeVarInt(a.newX); b.writeVarInt(a.newY); b.writeVarInt(a.newRotation);
    }, b -> {
        Mode mode = b.readEnum(Mode.class); long epoch = b.readVarLong(), sequence = b.readVarLong();
        UUID target = b.readUUID(), session = b.readUUID();
        long handRevision = b.readVarLong(), hostRevision = b.readVarLong();
        int flag = b.readUnsignedByte();
        if (flag > 1) throw new IllegalArgumentException("Invalid hand flag");
        String hand = flag == 1 ? b.readUtf(HandState.MAX_ID_LENGTH) : null;
        String token = b.readUtf(ItemToken.MAX_LENGTH);
        int oldX = b.readVarInt(), oldY = b.readVarInt(), oldRotation = b.readVarInt();
        int newX = b.readVarInt(), newY = b.readVarInt(), newRotation = b.readVarInt();
        if (b.isReadable()) throw new IllegalArgumentException("Trailing world action data");
        return new BodyWorldGridAction(mode, epoch, sequence, target, session, handRevision, hostRevision,
                hand, token, oldX, oldY, oldRotation, newX, newY, newRotation);
    });

    public BodyWorldGridAction {
        if (mode == null || epoch <= 0 || sequence <= 0 || target == null || session == null
                || handRevision < 0 || hostRevision < 0 || token == null || token.isBlank()
                || token.length() > ItemToken.MAX_LENGTH
                || (mode == Mode.MOVE ? hand != null : hand == null || hand.isBlank() || hand.length() > HandState.MAX_ID_LENGTH)
                || (mode == Mode.STORE ? oldX != -1 || oldY != -1 || oldRotation != -1
                    : !cell(oldX, oldY) || !rotation(oldRotation))
                || (mode == Mode.TAKE ? newX != -1 || newY != -1 || newRotation != -1
                    : !cell(newX, newY) || !rotation(newRotation)))
            throw new IllegalArgumentException("Invalid world grid action");
    }
    private static boolean cell(int x, int y) { return x >= 0 && y >= 0 && x < GridGeometry.MAX_WIDTH && y < GridGeometry.MAX_HEIGHT; }
    private static boolean rotation(int r) { return r == 0 || r == 90 || r == 180 || r == 270; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

