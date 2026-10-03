package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Correlated completion of one body block-use intent; carries no owner or inventory data. */
public record BodyBlockUseResult(long epoch, long sequence, Outcome outcome) implements CustomPacketPayload {
    public enum Outcome { PLACED, DENIED, RECOVERY_REQUIRED, NO_CHANGE }
    public static final Type<BodyBlockUseResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_block_use_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyBlockUseResult> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarLong(value.epoch); buf.writeVarLong(value.sequence); buf.writeEnum(value.outcome); },
            buf -> new BodyBlockUseResult(buf.readVarLong(), buf.readVarLong(), buf.readEnum(Outcome.class)));
    public BodyBlockUseResult {
        if (epoch <= 0 || sequence <= 0 || outcome == null) throw new IllegalArgumentException("Invalid block-use result");
    }
    private static volatile java.util.function.Consumer<BodyBlockUseResult> clientHandler;
    public static void setClientHandler(java.util.function.Consumer<BodyBlockUseResult> handler) { clientHandler = handler; }
    public static void deliverClient(BodyBlockUseResult result) { var handler = clientHandler; if (handler != null) handler.accept(result); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
