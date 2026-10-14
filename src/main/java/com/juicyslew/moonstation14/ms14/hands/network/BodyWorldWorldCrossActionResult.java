package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Consumer;

/** Correlated result for a two-WORLD-owner transfer. */
public record BodyWorldWorldCrossActionResult(long epoch, long sequence,
        UUID sourceTarget, UUID sourceSession, long sourceHostRevision,
        UUID destinationTarget, UUID destinationSession, long destinationHostRevision, Reason reason)
        implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, STALE, DENIED, RECOVERY_REQUIRED }
    public static final Type<BodyWorldWorldCrossActionResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_world_cross_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldWorldCrossActionResult> STREAM_CODEC = StreamCodec.of(
            (b, r) -> { b.writeVarLong(r.epoch); b.writeVarLong(r.sequence);
                b.writeUUID(r.sourceTarget); b.writeUUID(r.sourceSession); b.writeVarLong(r.sourceHostRevision);
                b.writeUUID(r.destinationTarget); b.writeUUID(r.destinationSession); b.writeVarLong(r.destinationHostRevision);
                b.writeEnum(r.reason); },
            b -> { long epoch = b.readVarLong(), sequence = b.readVarLong();
                UUID source = b.readUUID(), sourceSession = b.readUUID(); long sourceRevision = b.readVarLong();
                UUID destination = b.readUUID(), destinationSession = b.readUUID(); long destinationRevision = b.readVarLong();
                Reason reason = b.readEnum(Reason.class);
                if (b.isReadable()) throw new IllegalArgumentException("Trailing world cross result data");
                return new BodyWorldWorldCrossActionResult(epoch, sequence, source, sourceSession, sourceRevision,
                        destination, destinationSession, destinationRevision, reason); });
    public BodyWorldWorldCrossActionResult {
        if (epoch <= 0 || sequence <= 0 || sourceTarget == null || sourceSession == null
                || destinationTarget == null || destinationSession == null || sourceTarget.equals(destinationTarget)
                || sourceSession.equals(destinationSession) || sourceHostRevision < 0 || destinationHostRevision < 0 || reason == null)
            throw new IllegalArgumentException("Invalid world cross result");
    }
    private static volatile Consumer<BodyWorldWorldCrossActionResult> clientHandler;
    public static void setClientHandler(Consumer<BodyWorldWorldCrossActionResult> handler) { clientHandler = handler; }
    static void deliverClient(BodyWorldWorldCrossActionResult result) { var handler = clientHandler; if (handler != null) handler.accept(result); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

