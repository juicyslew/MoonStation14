package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Consumer;

/** OK increments world revision; RECOVERY_REQUIRED revokes the world session. Query both owners again. */
public record BodyWorldCrossActionResult(long epoch, long sequence, UUID target, UUID session,
        long worldRevision, Reason reason) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, STALE, DENIED, RECOVERY_REQUIRED }
    public static final Type<BodyWorldCrossActionResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_cross_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldCrossActionResult> STREAM_CODEC = StreamCodec.of(
            (b, r) -> { b.writeVarLong(r.epoch); b.writeVarLong(r.sequence); b.writeUUID(r.target);
                b.writeUUID(r.session); b.writeVarLong(r.worldRevision); b.writeEnum(r.reason); },
            b -> { long epoch = b.readVarLong(), seq = b.readVarLong(); UUID target = b.readUUID(), session = b.readUUID();
                long revision = b.readVarLong(); Reason reason = b.readEnum(Reason.class);
                if (b.isReadable()) throw new IllegalArgumentException("Trailing cross result");
                return new BodyWorldCrossActionResult(epoch, seq, target, session, revision, reason); });
    public BodyWorldCrossActionResult {
        if (epoch <= 0 || sequence <= 0 || target == null || session == null || worldRevision < 0 || reason == null)
            throw new IllegalArgumentException("Invalid cross result");
    }
    private static volatile Consumer<BodyWorldCrossActionResult> clientHandler;
    public static void setClientHandler(Consumer<BodyWorldCrossActionResult> handler) { clientHandler = handler; }
    static void deliverClient(BodyWorldCrossActionResult result) {
        var handler = clientHandler; if (handler != null) handler.accept(result);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

