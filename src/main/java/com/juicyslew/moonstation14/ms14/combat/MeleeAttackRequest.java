package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Client nomination and expected body/weapon binding only; gameplay facts remain server-owned. */
public record MeleeAttackRequest(long epoch, long sequence, MeleeWeaponResolver.Kind expectedKind,
        long expectedHandRevision, String expectedActiveHand, String expectedItemToken, Mode attackMode,
        UUID targetUUID, List<UUID> wideTargets)
        implements CustomPacketPayload {
    public enum Mode { LIGHT, WIDE, DISARM }
    public static final int MAX_WIDE_TARGETS = 5;
    public static final Type<MeleeAttackRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "melee_attack_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeAttackRequest> STREAM_CODEC = StreamCodec.of(
             (b,v)->{ b.writeVarLong(v.epoch); b.writeVarLong(v.sequence); b.writeVarInt(v.expectedKind.ordinal()); b.writeVarLong(v.expectedHandRevision); b.writeUtf(v.expectedActiveHand,32); b.writeUtf(v.expectedItemToken,128); b.writeVarInt(v.attackMode.ordinal()); if(v.attackMode==Mode.WIDE){b.writeVarInt(v.wideTargets.size());for(UUID id:v.wideTargets)b.writeUUID(id);}else{b.writeBoolean(v.targetUUID!=null);if(v.targetUUID!=null)b.writeUUID(v.targetUUID);} },
             b->{ long epoch=b.readVarLong(), seq=b.readVarLong(); int kind=b.readVarInt(); if(kind<0||kind>=MeleeWeaponResolver.Kind.values().length)throw new IllegalArgumentException("invalid melee kind"); long rev=b.readVarLong(); String hand=b.readUtf(32), token=b.readUtf(128); int mode=b.readVarInt(); if(mode<0||mode>=Mode.values().length)throw new IllegalArgumentException("invalid melee mode"); Mode m=Mode.values()[mode]; if(m!=Mode.WIDE)return new MeleeAttackRequest(epoch,seq,MeleeWeaponResolver.Kind.values()[kind],rev,hand,token,m,b.readBoolean()?b.readUUID():null,List.of()); int count=b.readVarInt(); if(count<0||count>MAX_WIDE_TARGETS)throw new IllegalArgumentException("invalid wide nomination count"); List<UUID> targets=new ArrayList<>();for(int i=0;i<count;i++)targets.add(b.readUUID());return new MeleeAttackRequest(epoch,seq,MeleeWeaponResolver.Kind.values()[kind],rev,hand,token,m,null,targets); });
    public MeleeAttackRequest(long epoch,long sequence,MeleeWeaponResolver.Kind kind,long rev,String hand,String token,UUID target){this(epoch,sequence,kind,rev,hand,token,Mode.LIGHT,target,List.of());}
     public MeleeAttackRequest { if(epoch<=0||sequence<=0||expectedKind==null||expectedHandRevision<0||expectedActiveHand==null||expectedItemToken==null||attackMode==null||wideTargets==null||wideTargets.size()>MAX_WIDE_TARGETS)throw new IllegalArgumentException("invalid melee request"); wideTargets=List.copyOf(wideTargets); if(attackMode!=Mode.WIDE&&!wideTargets.isEmpty()||attackMode==Mode.WIDE&&targetUUID!=null)throw new IllegalArgumentException("invalid melee mode fields"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
