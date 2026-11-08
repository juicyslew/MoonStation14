package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client nomination and expected body/weapon binding only; gameplay facts remain server-owned. */
public record MeleeAttackRequest(long epoch, long sequence, MeleeWeaponResolver.Kind expectedKind,
        long expectedHandRevision, String expectedActiveHand, String expectedItemToken, Mode attackMode,
        List<MeleeTargetNomination> nominations, boolean swingPredicted) implements CustomPacketPayload {
    public enum Mode { LIGHT, WIDE, DISARM }
    public static final int MAX_WIDE_TARGETS = 5;
    public static final Type<MeleeAttackRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "melee_attack_request"));

    private static void writeNomination(RegistryFriendlyByteBuf b, MeleeTargetNomination n) {
        if (n instanceof MeleeTargetNomination.Entity e) { b.writeVarInt(0); b.writeUUID(e.entityUUID()); }
        else if (n instanceof MeleeTargetNomination.Block x) {
            b.writeVarInt(1); b.writeBlockPos(x.pos()); b.writeVarInt(x.face().ordinal());
            b.writeDouble(x.hitpoint().x); b.writeDouble(x.hitpoint().y); b.writeDouble(x.hitpoint().z); b.writeVarInt(x.expectedBlockStateID());
        } else throw new IllegalArgumentException("unknown melee nomination");
    }
    private static MeleeTargetNomination readNomination(RegistryFriendlyByteBuf b) {
        int tag=b.readVarInt();
        if(tag==0)return new MeleeTargetNomination.Entity(b.readUUID());
        if(tag!=1)throw new IllegalArgumentException("invalid nomination discriminant");
        BlockPos pos=b.readBlockPos(); int face=b.readVarInt();
        if(face<0||face>=Direction.values().length)throw new IllegalArgumentException("invalid block face");
        return new MeleeTargetNomination.Block(pos,Direction.values()[face],new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt());
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,MeleeAttackRequest> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeVarInt(v.expectedKind.ordinal());b.writeVarLong(v.expectedHandRevision);
        b.writeUtf(v.expectedActiveHand,32);b.writeUtf(v.expectedItemToken,128);b.writeVarInt(v.attackMode.ordinal());
         b.writeVarInt(v.nominations.size());for(var n:v.nominations)writeNomination(b,n);b.writeBoolean(v.swingPredicted);
     },b->{long epoch=b.readVarLong(),seq=b.readVarLong();int k=b.readVarInt();if(k<0||k>=MeleeWeaponResolver.Kind.values().length)throw new IllegalArgumentException("invalid melee kind");long rev=b.readVarLong();String hand=b.readUtf(32),token=b.readUtf(128);int m=b.readVarInt();if(m<0||m>=Mode.values().length)throw new IllegalArgumentException("invalid melee mode");int count=b.readVarInt();if(count<0||count>MAX_WIDE_TARGETS)throw new IllegalArgumentException("invalid nomination count");List<MeleeTargetNomination> nominations=new ArrayList<>();for(int i=0;i<count;i++)nominations.add(readNomination(b));boolean swing=b.readBoolean();return new MeleeAttackRequest(epoch,seq,MeleeWeaponResolver.Kind.values()[k],rev,hand,token,Mode.values()[m],nominations,swing);});

    public MeleeAttackRequest(long epoch,long sequence,MeleeWeaponResolver.Kind kind,long rev,String hand,String token,UUID target) { this(epoch,sequence,kind,rev,hand,token,Mode.LIGHT,target==null?List.of():List.of(new MeleeTargetNomination.Entity(target)),false); }
    public MeleeAttackRequest(long epoch,long sequence,MeleeWeaponResolver.Kind kind,long rev,String hand,String token,Mode mode,UUID target,List<UUID> wide) {
        this(epoch,sequence,kind,rev,hand,token,mode, legacyNominations(mode,target,wide),false);
    }
    private static List<MeleeTargetNomination> legacyNominations(Mode mode,UUID target,List<UUID> wide) {
        if(mode==null||wide==null)throw new IllegalArgumentException("invalid legacy nomination fields");
        if(mode==Mode.WIDE){if(target!=null)throw new IllegalArgumentException("wide target field must be null");return wide.stream().map(MeleeTargetNomination.Entity::new).map(n->(MeleeTargetNomination)n).toList();}
        if(!wide.isEmpty())throw new IllegalArgumentException("non-wide nominations must be empty");
        return target==null?List.of():List.of(new MeleeTargetNomination.Entity(target));
    }
    public UUID targetUUID(){return attackMode==Mode.WIDE||nominations.isEmpty()||!(nominations.get(0) instanceof MeleeTargetNomination.Entity e)?null:e.entityUUID();}
    public List<UUID> wideTargets(){return attackMode!=Mode.WIDE?List.of():nominations.stream().filter(MeleeTargetNomination.Entity.class::isInstance).map(MeleeTargetNomination.Entity.class::cast).map(MeleeTargetNomination.Entity::entityUUID).toList();}
    public MeleeTargetNomination typedTarget(){return nominations.isEmpty()?null:nominations.get(0);}
    public MeleeAttackRequest withSwingPredicted(boolean predicted) { return new MeleeAttackRequest(epoch,sequence,expectedKind,expectedHandRevision,expectedActiveHand,expectedItemToken,attackMode,nominations,predicted); }
    public MeleeAttackRequest { if(epoch<=0||sequence<=0||expectedKind==null||expectedHandRevision<0||expectedActiveHand==null||expectedItemToken==null||attackMode==null||nominations==null||nominations.size()>MAX_WIDE_TARGETS)throw new IllegalArgumentException("invalid melee request");nominations=List.copyOf(nominations);if(nominations.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("null nomination");if(attackMode!=Mode.WIDE&&nominations.size()>1||attackMode==Mode.DISARM&&nominations.stream().anyMatch(n->!(n instanceof MeleeTargetNomination.Entity)))throw new IllegalArgumentException("invalid mode nomination"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
