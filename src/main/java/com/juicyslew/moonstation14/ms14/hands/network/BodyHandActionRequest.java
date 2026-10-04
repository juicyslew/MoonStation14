package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;

import java.util.UUID;

/** Intent only: actor and body are derived exclusively from the authenticated packet context. */
public record BodyHandActionRequest(Action action, String handId, long epoch, long sequence,
                                    long expectedRevision, UUID pickupTarget, String expectedToken,
                                     String expectedChildOrSourceToken, int cellX, int cellY, int rotation,
                                      int oldCellX, int oldCellY, int oldRotation, String destinationHostToken,
                                       String sourceHostId, String destinationHostId, BlockPos targetBlock)
        implements CustomPacketPayload {
    public enum Action { PICKUP, DROP, INSERT_POUCH, EXTRACT_POUCH, SELECT_HAND,
        EQUIP_BELT, EQUIP_BACK, UNEQUIP_BELT, UNEQUIP_BACK,
        STORE_BELT, STORE_BACK, TAKE_BELT, TAKE_BACK,
        GRID_STORE_BELT, GRID_STORE_BACK, GRID_TAKE_BELT, GRID_TAKE_BACK, GRID_MOVE_BELT, GRID_MOVE_BACK,
        HELD_GRID_STORE, HELD_GRID_TAKE, HELD_GRID_MOVE,
        SMART_STORE_BELT, SMART_STORE_BACK, SMART_TAKE_BELT, SMART_TAKE_BACK,
        GRID_CROSS_BELT_TO_BACK, GRID_CROSS_BACK_TO_BELT,
        GRID_BODY_WORN_TO_HELD, GRID_BODY_HELD_TO_WORN, GRID_BODY_HELD_TO_HELD,
        TRANSFER_HAND, PRIME_GRENADE, THROW_GRENADE, PICKUP_SELECTED, THROW_ITEM,
         USE_TARGET, USE_IN_HAND, ALTERNATE_TARGET, TOGGLE_PULL, STOP_PULL, SIP_PUDDLE }

    /** Compatibility constructor preserving the prior canonical signature. */
    public BodyHandActionRequest(Action action, String handId, long epoch, long sequence, long expectedRevision,
            UUID pickupTarget, String expectedToken, String child, int x, int y, int rotation,
            int oldX, int oldY, int oldRotation, String destinationHostToken, String sourceHostId, String destinationHostId) {
        this(action, handId, epoch, sequence, expectedRevision, pickupTarget, expectedToken, child, x, y, rotation,
                oldX, oldY, oldRotation, destinationHostToken, sourceHostId, destinationHostId, null);
    }

    public BodyHandActionRequest(Action action, String handId, long epoch, long sequence, long expectedRevision,
            UUID pickupTarget, String expectedToken, String child, int x, int y, int rotation,
            int oldX, int oldY, int oldRotation, String destinationHostToken) {
        this(action, handId, epoch, sequence, expectedRevision, pickupTarget, expectedToken, child,
                x, y, rotation, oldX, oldY, oldRotation, destinationHostToken, null, null);
    }

    /** Preserve the original grid request construction and its on-wire shape. */
    public BodyHandActionRequest(Action action, String handId, long epoch, long sequence, long expectedRevision,
                                 UUID pickupTarget, String expectedToken, String child, int x, int y, int rotation,
                                 int oldX, int oldY, int oldRotation) {
        this(action, handId, epoch, sequence, expectedRevision, pickupTarget, expectedToken, child,
                x, y, rotation, oldX, oldY, oldRotation, null);
    }

    /** Compatibility constructor for the original thirteen wire actions. */
    public BodyHandActionRequest(Action action, String handId, long epoch, long sequence,
                                 long expectedRevision, UUID pickupTarget, String expectedToken) {
        this(action, handId, epoch, sequence, expectedRevision, pickupTarget, expectedToken, null, -1, -1, -1, -1, -1, -1);
    }

    public BodyHandActionRequest(Action action, String handId, long epoch, long sequence, long expectedRevision,
                                 UUID pickupTarget, String expectedToken, String child, int x, int y, int rotation) {
        this(action, handId, epoch, sequence, expectedRevision, pickupTarget, expectedToken, child, x, y, rotation, -1, -1, -1);
    }
    public static final Type<BodyHandActionRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_hand_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyHandActionRequest> STREAM_CODEC =
            StreamCodec.of((buf, request) -> {
                buf.writeEnum(request.action);
                buf.writeUtf(request.handId, HandState.MAX_ID_LENGTH);
                buf.writeVarLong(request.epoch);
                buf.writeVarLong(request.sequence);
                buf.writeVarLong(request.expectedRevision);
                if (request.action == Action.SIP_PUDDLE) buf.writeBlockPos(request.targetBlock);
                if (isWorldAction(request.action)) buf.writeUUID(request.pickupTarget);
                if (request.action == Action.ALTERNATE_TARGET || request.action == Action.TOGGLE_PULL) {
                    buf.writeBoolean(request.expectedToken != null);
                    if (request.expectedToken != null) buf.writeUtf(request.expectedToken, ItemToken.MAX_LENGTH);
                } else if (requiresToken(request.action)) buf.writeUtf(request.expectedToken, ItemToken.MAX_LENGTH);
                if (isGrid(request.action)) {
                    buf.writeUtf(request.expectedChildOrSourceToken, ItemToken.MAX_LENGTH);
                    buf.writeVarInt(request.cellX); buf.writeVarInt(request.cellY);
                    if (isGridStore(request.action) || isGridMove(request.action)) buf.writeVarInt(request.rotation);
                    if (isGridMove(request.action)) {
                        buf.writeVarInt(request.oldCellX); buf.writeVarInt(request.oldCellY); buf.writeVarInt(request.oldRotation);
                    }
                     if (isCross(request.action)) buf.writeUtf(request.destinationHostToken, ItemToken.MAX_LENGTH);
                     if (isBodyCross(request.action)) {
                         buf.writeUtf(request.sourceHostId, HandState.MAX_ID_LENGTH);
                         buf.writeUtf(request.destinationHostId, HandState.MAX_ID_LENGTH);
                     }
                } else if (request.action() == Action.TRANSFER_HAND) buf.writeUtf(request.destinationHostId, HandState.MAX_ID_LENGTH);
                else if (isSmart(request.action())) buf.writeUtf(request.expectedChildOrSourceToken, ItemToken.MAX_LENGTH);
            }, buf -> {
                Action action = buf.readEnum(Action.class);
                String hand = buf.readUtf(HandState.MAX_ID_LENGTH);
                long epoch = buf.readVarLong();
                long sequence = buf.readVarLong();
                long revision = buf.readVarLong();
                if (action == Action.SIP_PUDDLE) {
                    BlockPos pos = buf.readBlockPos();
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected puddle sip payload");
                    return sipPuddle(hand, epoch, sequence, revision, pos);
                }
                if (isWorldAction(action)) {
                    UUID target = buf.readUUID();
                    String token = action == Action.ALTERNATE_TARGET || action == Action.TOGGLE_PULL
                            ? buf.readBoolean() ? buf.readUtf(ItemToken.MAX_LENGTH) : null
                            : requiresToken(action) ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected world action payload");
                    return new BodyHandActionRequest(action, hand, epoch, sequence, revision, target, token);
                }
                if (action == Action.SELECT_HAND) {
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected hand-selection payload");
                    return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, null);
                }
                if (!requiresToken(action)) {
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected token-free action payload");
                    return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, null);
                }
                String token = buf.readUtf(ItemToken.MAX_LENGTH);
                if (action == Action.TRANSFER_HAND) {
                    String destination = buf.readUtf(HandState.MAX_ID_LENGTH);
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected hand transfer payload");
                    return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, token,
                            null, -1, -1, -1, -1, -1, -1, null, null, destination);
                }
                if (isGrid(action)) {
                    String child = buf.readUtf(ItemToken.MAX_LENGTH);
                    int x = buf.readVarInt(), y = buf.readVarInt();
                    int rotation = isGridStore(action) || isGridMove(action) ? buf.readVarInt() : -1;
                    int oldX = isGridMove(action) ? buf.readVarInt() : -1;
                    int oldY = isGridMove(action) ? buf.readVarInt() : -1;
                    int oldRotation = isGridMove(action) ? buf.readVarInt() : -1;
                     String destination = isCross(action) ? buf.readUtf(ItemToken.MAX_LENGTH) : null;
                     String sourceId = isBodyCross(action) ? buf.readUtf(HandState.MAX_ID_LENGTH) : null;
                     String destinationId = isBodyCross(action) ? buf.readUtf(HandState.MAX_ID_LENGTH) : null;
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected grid action payload");
                     return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, token, child, x, y, rotation, oldX, oldY, oldRotation, destination, sourceId, destinationId);
                }
                if (isSmart(action)) {
                    String child = buf.readUtf(ItemToken.MAX_LENGTH);
                    if (buf.isReadable()) throw new IllegalArgumentException("Unexpected smart storage payload");
                    return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, token, child, -1, -1, -1);
                }
                if (buf.isReadable()) throw new IllegalArgumentException("Unexpected hand action payload");
                return new BodyHandActionRequest(action, hand, epoch, sequence, revision, null, token);
            });

    public BodyHandActionRequest {
        if (action == null || handId == null || handId.isBlank() || handId.length() > HandState.MAX_ID_LENGTH
                || epoch <= 0 || sequence <= 0 || expectedRevision < 0
                  || (isWorldAction(action) && pickupTarget == null)
                  || (action == Action.PICKUP && expectedToken != null)
                  || (action == Action.USE_TARGET && (expectedToken == null || expectedToken.isBlank()
                            || expectedToken.length() > ItemToken.MAX_LENGTH))
                  || (action == Action.SELECT_HAND && (pickupTarget != null || expectedToken != null))
                  || (action == Action.SIP_PUDDLE ? targetBlock == null || pickupTarget != null || expectedToken != null
                          : targetBlock != null)
                  || (requiresToken(action) && !isWorldAction(action)
                           && (pickupTarget != null || expectedToken == null
                            || expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH))
                   || ((action == Action.ALTERNATE_TARGET || action == Action.TOGGLE_PULL) && expectedToken != null
                            && (expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH))
                  || (!isWorldAction(action) && pickupTarget != null)
                  || (action == Action.TRANSFER_HAND ? !validHostId(destinationHostId) || sourceHostId != null || destinationHostToken != null
                          : isCross(action) ? destinationHostToken == null || destinationHostToken.isBlank()
                         || destinationHostToken.length() > ItemToken.MAX_LENGTH || destinationHostToken.equals(expectedToken)
                         : destinationHostToken != null)
          || (isBodyCross(action) ? !validHostId(sourceHostId) || !validHostId(destinationHostId)
                          || action == Action.GRID_BODY_WORN_TO_HELD && !wornId(sourceHostId)
                          || action == Action.GRID_BODY_HELD_TO_WORN && !wornId(destinationHostId)
                          || action == Action.GRID_BODY_HELD_TO_HELD && sourceHostId.equals(destinationHostId)
                           : sourceHostId != null || action != Action.TRANSFER_HAND && destinationHostId != null)
                 || (isGrid(action) ? expectedChildOrSourceToken == null || expectedChildOrSourceToken.isBlank()
                          || expectedChildOrSourceToken.length() > ItemToken.MAX_LENGTH || cellX < 0 || cellY < 0
                           || cellX >= GridGeometry.MAX_WIDTH || cellY >= GridGeometry.MAX_HEIGHT
                           || isGridStore(action) && !validRotation(rotation)
                           || isGridMove(action) && (!validRotation(rotation) || !validCell(oldCellX, oldCellY) || !validRotation(oldRotation))
                           || !isGridStore(action) && !isGridMove(action) && rotation != -1
                           || !isGridMove(action) && (oldCellX != -1 || oldCellY != -1 || oldRotation != -1)
                      : isSmart(action) ? expectedChildOrSourceToken == null || expectedChildOrSourceToken.isBlank()
                          || expectedChildOrSourceToken.length() > ItemToken.MAX_LENGTH || cellX != -1 || cellY != -1 || rotation != -1 || oldCellX != -1 || oldCellY != -1 || oldRotation != -1
                       : expectedChildOrSourceToken != null || cellX != -1 || cellY != -1 || rotation != -1 || oldCellX != -1 || oldCellY != -1 || oldRotation != -1)
                   )
            throw new IllegalArgumentException("Invalid body hand intent");
    }

    public static BodyHandActionRequest sipPuddle(String hand, long epoch, long sequence, long revision, BlockPos pos) {
        if (pos == null) throw new IllegalArgumentException("Missing puddle target");
        return new BodyHandActionRequest(Action.SIP_PUDDLE, hand, epoch, sequence, revision, null, null,
                null, -1, -1, -1, -1, -1, -1, null, null, null, pos.immutable());
    }

     private static boolean isWorldAction(Action action) { return action == Action.PICKUP || action == Action.PICKUP_SELECTED || action == Action.USE_TARGET || action == Action.ALTERNATE_TARGET || action == Action.TOGGLE_PULL; }
      private static boolean requiresToken(Action action) { return action != Action.SELECT_HAND && action != Action.PICKUP && action != Action.PICKUP_SELECTED && action != Action.STOP_PULL && action != Action.ALTERNATE_TARGET && action != Action.SIP_PUDDLE; }
     private static boolean isBodyCross(Action action) { return action == Action.GRID_BODY_WORN_TO_HELD || action == Action.GRID_BODY_HELD_TO_WORN || action == Action.GRID_BODY_HELD_TO_HELD; }
     private static boolean wornId(String id) { return "back".equals(id) || "belt".equals(id); }
     private static boolean validHostId(String id) { return id != null && !id.isBlank() && id.length() <= HandState.MAX_ID_LENGTH; }
     private static boolean isCross(Action action) { return action == Action.GRID_CROSS_BELT_TO_BACK || action == Action.GRID_CROSS_BACK_TO_BELT || isBodyCross(action); }
    private static boolean isGrid(Action action) { return action.ordinal() >= Action.GRID_STORE_BELT.ordinal() && action.ordinal() <= Action.HELD_GRID_MOVE.ordinal() || isCross(action); }
    private static boolean isSmart(Action action) { return action == Action.SMART_STORE_BELT || action == Action.SMART_STORE_BACK || action == Action.SMART_TAKE_BELT || action == Action.SMART_TAKE_BACK; }
    private static boolean isGridStore(Action action) { return action == Action.GRID_STORE_BELT || action == Action.GRID_STORE_BACK || action == Action.HELD_GRID_STORE; }
    private static boolean isGridMove(Action action) { return action == Action.GRID_MOVE_BELT || action == Action.GRID_MOVE_BACK || action == Action.HELD_GRID_MOVE || isCross(action); }
    private static boolean validCell(int x, int y) { return x >= 0 && y >= 0 && x < GridGeometry.MAX_WIDTH && y < GridGeometry.MAX_HEIGHT; }
    private static boolean validRotation(int rotation) { return rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270; }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
