package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record GunShotReply(UUID bodyUUID,long epoch,long sequence,GunFireSystem.Status status,boolean consumedSource,
                           long handRevision,String itemToken,long remainingTicks) implements CustomPacketPayload {
    public static final Type<GunShotReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","gun_shot_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunShotReply> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeBoolean(v.bodyUUID!=null);if(v.bodyUUID!=null)b.writeUUID(v.bodyUUID);b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);
        b.writeVarInt(v.status.ordinal());b.writeBoolean(v.consumedSource);b.writeVarLong(v.handRevision);
        b.writeBoolean(v.itemToken!=null);if(v.itemToken!=null)b.writeUtf(v.itemToken,128);b.writeVarLong(v.remainingTicks);
    },b->{UUID id=b.readBoolean()?b.readUUID():null;long epoch=b.readVarLong(),seq=b.readVarLong();int s=b.readVarInt();
        if(s<0||s>=GunFireSystem.Status.values().length)throw new IllegalArgumentException("invalid gun status");boolean consumed=b.readBoolean();
        long rev=b.readVarLong();String token=b.readBoolean()?b.readUtf(128):null;return new GunShotReply(id,epoch,seq,GunFireSystem.Status.values()[s],consumed,rev,token,b.readVarLong());});
    public GunShotReply {if(epoch<0||sequence<0||status==null||handRevision<0||remainingTicks<0)throw new IllegalArgumentException("invalid gun shot reply");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
