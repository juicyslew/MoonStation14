package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record PostureRollRequest(UUID target) implements CustomPacketPayload {
    public static final Type<PostureRollRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "posture_roll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PostureRollRequest> STREAM_CODEC = StreamCodec.of(
            (buf, request) -> { buf.writeBoolean(request.target != null); if (request.target != null) buf.writeUUID(request.target); },
            buf -> new PostureRollRequest(buf.readBoolean() ? buf.readUUID() : null));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
