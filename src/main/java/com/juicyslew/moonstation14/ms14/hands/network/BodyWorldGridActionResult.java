package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Consumer;

/** Every response requires a fresh hand and world query; recovery also invalidates the session. */
public record BodyWorldGridActionResult(long epoch, long sequence, UUID target, UUID session,
                                        long hostRevision, Reason reason) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, STALE, DENIED, RECOVERY_REQUIRED }
    public static final Type<BodyWorldGridActionResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_grid_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldGridActionResult> STREAM_CODEC = StreamCodec.of(
            (b, r) -> { b.writeVarLong(r.epoch); b.writeVarLong(r.sequence); b.writeUUID(r.target);
                b.writeUUID(r.session); b.writeVarLong(r.hostRevision); b.writeEnum(r.reason); },
            b -> { long epoch = b.readVarLong(), sequence = b.readVarLong(); UUID target = b.readUUID(), session = b.readUUID();
                long revision = b.readVarLong(); Reason reason = b.readEnum(Reason.class);
                if (b.isReadable()) throw new IllegalArgumentException("Trailing world action result");
                return new BodyWorldGridActionResult(epoch, sequence, target, session, revision, reason); });
    public BodyWorldGridActionResult {
        if (epoch <= 0 || sequence <= 0 || target == null || session == null || hostRevision < 0 || reason == null)
            throw new IllegalArgumentException("Invalid world action result");
    }
    private static volatile Consumer<BodyWorldGridActionResult> clientHandler;
    public static void setClientHandler(Consumer<BodyWorldGridActionResult> handler) { clientHandler = handler; }
    static void deliverClient(BodyWorldGridActionResult result) {
        var handler = clientHandler;
        if (handler != null) handler.accept(result);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

