package com.juicyslew.moonstation14.ms14.disposal.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisposalBuildSelection(int containerId, java.util.UUID session, long requestId, String prototypeId) implements CustomPacketPayload {
    public static final Type<DisposalBuildSelection> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","disposal_build_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisposalBuildSelection> STREAM_CODEC=StreamCodec.of((b,r)->{b.writeVarInt(r.containerId);b.writeUUID(r.session);b.writeVarLong(r.requestId);b.writeUtf(r.prototypeId,128);},b->new DisposalBuildSelection(b.readVarInt(),b.readUUID(),b.readVarLong(),b.readUtf(128)));
    public DisposalBuildSelection { if(containerId<0||session==null||requestId<=0||prototypeId==null||prototypeId.isBlank())throw new IllegalArgumentException("Invalid disposal selection"); }
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
