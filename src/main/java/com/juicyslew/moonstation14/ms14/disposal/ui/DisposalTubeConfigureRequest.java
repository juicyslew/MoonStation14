package com.juicyslew.moonstation14.ms14.disposal.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Routing edit bound to the already-open menu and exact tube incarnation. */
public record DisposalTubeConfigureRequest(int containerId, UUID session, long requestId, UUID tubeId,
                                          long revision, String tag, String filter) implements CustomPacketPayload {
    public static final Type<DisposalTubeConfigureRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "disposal_tube_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisposalTubeConfigureRequest> STREAM_CODEC = StreamCodec.of(
            (b,r)->{b.writeVarInt(r.containerId);b.writeUUID(r.session);b.writeVarLong(r.requestId);b.writeUUID(r.tubeId);b.writeVarLong(r.revision);b.writeUtf(r.tag,64);b.writeUtf(r.filter,128);},
            b->new DisposalTubeConfigureRequest(b.readVarInt(),b.readUUID(),b.readVarLong(),b.readUUID(),b.readVarLong(),b.readUtf(64),b.readUtf(128)));
    public DisposalTubeConfigureRequest {
        if (containerId < 0 || session == null || requestId <= 0 || tubeId == null || revision < 0 || tag == null || filter == null
                || tag.length() > 64 || filter.length() > 128) throw new IllegalArgumentException("Invalid disposal routing edit");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

