package com.juicyslew.moonstation14.ms14.construction.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Read-only request for guidance at the body's current, server-ray-validated target. */
public record ConstructionGuidanceRequest(long epoch, long sequence, BlockPos target, Direction face,
                                          UUID expectedHost) implements CustomPacketPayload {
    public static final Type<ConstructionGuidanceRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "construction_guidance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionGuidanceRequest> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeBlockPos(v.target);b.writeEnum(v.face);b.writeBoolean(v.expectedHost!=null);if(v.expectedHost!=null)b.writeUUID(v.expectedHost);},
            b->{long epoch=b.readVarLong(),sequence=b.readVarLong();BlockPos target=b.readBlockPos();Direction face=b.readEnum(Direction.class);UUID host=b.readBoolean()?b.readUUID():null;return new ConstructionGuidanceRequest(epoch,sequence,target,face,host);});
    public ConstructionGuidanceRequest {
        if (epoch<=0||sequence<=0||target==null||face==null) throw new IllegalArgumentException("Invalid guidance request");
        target=target.immutable();
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
