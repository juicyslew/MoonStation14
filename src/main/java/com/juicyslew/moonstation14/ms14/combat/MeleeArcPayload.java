package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Server-authored visual snapshot only; this payload is never damage or inventory authority. */
public record MeleeArcPayload(int bodyEntityId, UUID bodyUUID, ResourceLocation dimension,
                               UUID initiatingPlayerUUID, ResourceLocation arc, float attackRate, float angleDegrees,
                              float animationOffset, float wideAnimationRotation, Vec3 frozenBodyPosition,
                               Vec3 direction, Vec3 attackPoint, ItemPresentation copiedWeapon, boolean swingLeft)
        implements CustomPacketPayload {
    public static final Type<MeleeArcPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "melee_arc"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeArcPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> {
                b.writeVarInt(v.bodyEntityId); b.writeUUID(v.bodyUUID); b.writeResourceLocation(v.dimension);
                 b.writeUUID(v.initiatingPlayerUUID); b.writeResourceLocation(v.arc); b.writeFloat(v.attackRate); b.writeFloat(v.angleDegrees);
                b.writeFloat(v.animationOffset); b.writeFloat(v.wideAnimationRotation);
                 writeVec(b,v.frozenBodyPosition); writeVec(b,v.direction); writeVec(b,v.attackPoint);
                 b.writeBoolean(v.copiedWeapon != null); if(v.copiedWeapon != null) ItemPresentation.STREAM_CODEC.encode(b,v.copiedWeapon);
                 b.writeBoolean(v.swingLeft);
            },
            b -> new MeleeArcPayload(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readUUID(),b.readResourceLocation(),
                     b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),readVec(b),readVec(b),readVec(b),
                     b.readBoolean()?ItemPresentation.STREAM_CODEC.decode(b):null,b.readBoolean()));
    public MeleeArcPayload {
        if(bodyEntityId < 0 || bodyUUID == null || dimension == null || initiatingPlayerUUID == null || arc == null ||
                 !Float.isFinite(attackRate) || attackRate <= 0 || !Float.isFinite(angleDegrees) || angleDegrees < 0 || angleDegrees > 360 || !Float.isFinite(animationOffset) || animationOffset < 0 ||
                !Float.isFinite(wideAnimationRotation) || !finite(frozenBodyPosition) || !finite(direction) || !finite(attackPoint))
            throw new IllegalArgumentException("invalid melee arc snapshot");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
}
