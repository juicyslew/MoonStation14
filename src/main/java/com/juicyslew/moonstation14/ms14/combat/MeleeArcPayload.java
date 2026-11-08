package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.List;

/** Server-authored visual snapshot only; this payload is never damage or inventory authority. */
public record MeleeArcPayload(int bodyEntityId, UUID bodyUUID, ResourceLocation dimension,
                               UUID initiatingPlayerUUID, ResourceLocation arc, float attackRate, float angleDegrees,
                              float animationOffset, float wideAnimationRotation, Vec3 frozenBodyPosition,
                               Vec3 direction, Vec3 attackPoint, ItemPresentation copiedWeapon, boolean swingLeft,
                               MeleeArcGeometry.StrikeCapture strikeGeometry, String activeHand)
        implements CustomPacketPayload {
    public MeleeArcPayload(int bodyEntityId, UUID bodyUUID, ResourceLocation dimension, UUID initiatingPlayerUUID,
            ResourceLocation arc, float attackRate, float angleDegrees, float animationOffset, float wideAnimationRotation,
            Vec3 frozenBodyPosition, Vec3 direction, Vec3 attackPoint, ItemPresentation copiedWeapon, boolean swingLeft) {
        this(bodyEntityId,bodyUUID,dimension,initiatingPlayerUUID,arc,attackRate,angleDegrees,animationOffset,wideAnimationRotation,
                frozenBodyPosition,direction,attackPoint,copiedWeapon,swingLeft,null,"legacy");
    }
    public static final Type<MeleeArcPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "melee_arc"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeArcPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> {
                b.writeVarInt(v.bodyEntityId); b.writeUUID(v.bodyUUID); b.writeResourceLocation(v.dimension);
                 b.writeBoolean(v.initiatingPlayerUUID != null); if (v.initiatingPlayerUUID != null) b.writeUUID(v.initiatingPlayerUUID); b.writeResourceLocation(v.arc); b.writeFloat(v.attackRate); b.writeFloat(v.angleDegrees);
                b.writeFloat(v.animationOffset); b.writeFloat(v.wideAnimationRotation);
                 writeVec(b,v.frozenBodyPosition); writeVec(b,v.direction); writeVec(b,v.attackPoint);
                 b.writeBoolean(v.copiedWeapon != null); if(v.copiedWeapon != null) ItemPresentation.STREAM_CODEC.encode(b,v.copiedWeapon);
                  b.writeBoolean(v.swingLeft);
                 b.writeBoolean(v.strikeGeometry != null);
                 if (v.strikeGeometry != null) writeStrike(b,v.strikeGeometry);
                 b.writeUtf(v.activeHand,64);
            },
             b -> new MeleeArcPayload(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readBoolean()?b.readUUID():null,b.readResourceLocation(),
                     b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),readVec(b),readVec(b),readVec(b),
                      b.readBoolean()?ItemPresentation.STREAM_CODEC.decode(b):null,b.readBoolean(),
                      b.readBoolean()?readStrike(b):null,b.readUtf(64)));
    public MeleeArcPayload {
         if(bodyEntityId < 0 || bodyUUID == null || dimension == null || arc == null ||
                 !Float.isFinite(attackRate) || attackRate <= 0 || !Float.isFinite(angleDegrees) || angleDegrees < 0 || angleDegrees > 360 || !Float.isFinite(animationOffset) || animationOffset < 0 ||
                 !Float.isFinite(wideAnimationRotation) || !finite(frozenBodyPosition) || !finite(direction) || !finite(attackPoint)
                  || activeHand == null || (!activeHand.isEmpty() && activeHand.isBlank()) || activeHand.length() > 64
                  || (activeHand.isEmpty() && copiedWeapon != null)
                  || (strikeGeometry != null && !validStrike(strikeGeometry)))
            throw new IllegalArgumentException("invalid melee arc snapshot");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    private static void writeStrike(RegistryFriendlyByteBuf b, MeleeArcGeometry.StrikeCapture s) {
        writeVec(b,s.frozenBodyPosition()); writeVec(b,s.eyeOrigin()); writeVec(b,s.emissionOrigin()); writeVec(b,s.fullDirection()); b.writeFloat(s.facingYaw());
         b.writeDouble(s.range()); b.writeVarInt(s.mode().ordinal()); b.writeDouble(s.angleDegrees()); writeVec(b,s.centerEndpoint());
         b.writeBoolean(s.sourceBounds()!=null); if(s.sourceBounds()!=null){b.writeDouble(s.sourceBounds().minX);b.writeDouble(s.sourceBounds().minY);b.writeDouble(s.sourceBounds().minZ);b.writeDouble(s.sourceBounds().maxX);b.writeDouble(s.sourceBounds().maxY);b.writeDouble(s.sourceBounds().maxZ);}
        b.writeVarInt(s.boundaryEndpoints().size()); for (Vec3 p:s.boundaryEndpoints()) writeVec(b,p);
    }
    private static MeleeArcGeometry.StrikeCapture readStrike(RegistryFriendlyByteBuf b) {
        Vec3 body=readVec(b), eye=readVec(b), emission=readVec(b), direction=readVec(b); float yaw=b.readFloat(); double range=b.readDouble();
        int mode=b.readVarInt(); if(mode<0||mode>=MeleeAttackRequest.Mode.values().length) throw new IllegalArgumentException("invalid strike mode");
         double angle=b.readDouble(); Vec3 center=readVec(b);
         net.minecraft.world.phys.AABB bounds=null;
         if(b.readBoolean()) {
             double minX=b.readDouble(),minY=b.readDouble(),minZ=b.readDouble(),maxX=b.readDouble(),maxY=b.readDouble(),maxZ=b.readDouble();
             if(!validBounds(minX,minY,minZ,maxX,maxY,maxZ)) throw new IllegalArgumentException("invalid strike source bounds");
             bounds=new net.minecraft.world.phys.AABB(minX,minY,minZ,maxX,maxY,maxZ);
         }
         int count=b.readVarInt();
        if(count<0||count>13) throw new IllegalArgumentException("invalid strike endpoints");
         java.util.ArrayList<Vec3> points=new java.util.ArrayList<>(count); for(int i=0;i<count;i++) points.add(readVec(b));
          return new MeleeArcGeometry.StrikeCapture(body,eye,emission,direction,yaw,range,MeleeAttackRequest.Mode.values()[mode],angle,center,points,bounds);
    }
    private static boolean validStrike(MeleeArcGeometry.StrikeCapture s) {
        return finite(s.frozenBodyPosition())&&finite(s.eyeOrigin())&&finite(s.emissionOrigin())&&finite(s.fullDirection())&&finite(s.centerEndpoint())
                &&Float.isFinite(s.facingYaw())&&Double.isFinite(s.range())&&s.range()>0&&Double.isFinite(s.angleDegrees())
                 &&s.mode()!=null&&s.boundaryEndpoints()!=null&&s.boundaryEndpoints().size()<=13&&s.boundaryEndpoints().stream().allMatch(MeleeArcPayload::finite)
                 &&(s.sourceBounds()==null||validBounds(s.sourceBounds().minX,s.sourceBounds().minY,s.sourceBounds().minZ,s.sourceBounds().maxX,s.sourceBounds().maxY,s.sourceBounds().maxZ));
    }
    private static boolean validBounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(minZ)&&Double.isFinite(maxX)&&Double.isFinite(maxY)&&Double.isFinite(maxZ)
                &&minX<maxX&&minY<maxY&&minZ<maxZ;
    }
}
