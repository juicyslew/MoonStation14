package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Untrusted block-use nomination; it never requests an edit or item consumption. */
public record BodyBlockUseRequest(long epoch, long sequence, long expectedRevision, String handId, String expectedToken,
                                 BlockPos target, Direction face, boolean sneaking) implements CustomPacketPayload {
    public static final Type<BodyBlockUseRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_block_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyBlockUseRequest> STREAM_CODEC = StreamCodec.of((b,r)->{
        b.writeVarLong(r.epoch); b.writeVarLong(r.sequence); b.writeVarLong(r.expectedRevision);
        b.writeUtf(r.handId, HandState.MAX_ID_LENGTH); b.writeBoolean(r.expectedToken != null);
        if (r.expectedToken != null) b.writeUtf(r.expectedToken, ItemToken.MAX_LENGTH);
        b.writeBlockPos(r.target); b.writeEnum(r.face); b.writeBoolean(r.sneaking);
    }, b -> new BodyBlockUseRequest(b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readUtf(HandState.MAX_ID_LENGTH),
            b.readBoolean() ? b.readUtf(ItemToken.MAX_LENGTH) : null, b.readBlockPos(), b.readEnum(Direction.class), b.readBoolean()));
    public BodyBlockUseRequest {
        if(epoch<=0||sequence<=0||expectedRevision<0||handId==null||handId.isBlank()
                || expectedToken != null && (expectedToken.isBlank() || expectedToken.length() > ItemToken.MAX_LENGTH)
                ||target==null||face==null) throw new IllegalArgumentException("Invalid block-use intent");
    }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
}
