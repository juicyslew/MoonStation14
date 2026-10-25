package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record MeleeAttackReply(UUID bodyUUID,long epoch,long sequence,Status status,boolean cooldownConsumed,
        MeleeWeaponResolver.Kind currentKind,long handRevision,String itemToken,long cooldownRemainingTicks) implements CustomPacketPayload {
    public static final Type<MeleeAttackReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","melee_attack_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MeleeAttackReply> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeBoolean(v.bodyUUID!=null);if(v.bodyUUID!=null)b.writeUUID(v.bodyUUID);b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeVarInt(v.status.ordinal());b.writeBoolean(v.cooldownConsumed);b.writeBoolean(v.currentKind!=null);if(v.currentKind!=null)b.writeVarInt(v.currentKind.ordinal());b.writeVarLong(v.handRevision);b.writeBoolean(v.itemToken!=null);if(v.itemToken!=null)b.writeUtf(v.itemToken,128);b.writeVarLong(v.cooldownRemainingTicks);
    },b->{UUID id=b.readBoolean()?b.readUUID():null;long epoch=b.readVarLong(),seq=b.readVarLong();int s=b.readVarInt();if(s<0||s>=Status.values().length)throw new IllegalArgumentException("invalid melee status");boolean consumed=b.readBoolean();MeleeWeaponResolver.Kind kind=null;if(b.readBoolean()){int k=b.readVarInt();if(k<0||k>=MeleeWeaponResolver.Kind.values().length)throw new IllegalArgumentException("invalid melee kind");kind=MeleeWeaponResolver.Kind.values()[k];}long rev=b.readVarLong();String token=b.readBoolean()?b.readUtf(128):null;return new MeleeAttackReply(id,epoch,seq,Status.values()[s],consumed,kind,rev,token,b.readVarLong());});
     public enum Status { NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, STALE_WEAPON, REJECTED, PRE_ATTEMPT_CANCELLED, CONSUMED_CANCELLED, MISS, HIT, HANDLED, DISARM_FAILED, DISARM_HANDLED }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
