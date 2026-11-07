package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Immutable server-authored observer presentation snapshot. */
public record GunMuzzleFlashPayload(Kind kind, UUID shooter, UUID initiator, ResourceLocation dimension, Vec3 position,
                                    Vec3 direction, ProjectileProfile.ProjectileSpriteEffectData effect, Vec3 endpoint, ShotVisualSource source) implements CustomPacketPayload {
    public enum Kind { MUZZLE, IMPACT, BEAM }
    public GunMuzzleFlashPayload(Kind kind, UUID shooter, UUID initiator, ResourceLocation dimension, Vec3 position, Vec3 direction, ProjectileProfile.ProjectileSpriteEffectData effect) { this(kind,shooter,initiator,dimension,position,direction,effect,null,null); }
    public GunMuzzleFlashPayload(Kind kind, UUID shooter, UUID initiator, ResourceLocation dimension, Vec3 position, Vec3 direction, ProjectileProfile.ProjectileSpriteEffectData effect, Vec3 endpoint) { this(kind,shooter,initiator,dimension,position,direction,effect,endpoint,null); }
    public static final Type<GunMuzzleFlashPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","gun_muzzle_flash"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunMuzzleFlashPayload> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeVarInt(v.kind.ordinal()); b.writeUUID(v.shooter); b.writeUUID(v.initiator); b.writeResourceLocation(v.dimension);
        writeVec(b,v.position); writeVec(b,v.direction);
        var json=ProjectileProfile.ProjectileSpriteEffectData.CODEC.encodeStart(JsonOps.INSTANCE,v.effect).getOrThrow();
        b.writeUtf(json.toString(),4096); b.writeBoolean(v.endpoint!=null); if(v.endpoint!=null) writeVec(b,v.endpoint);
        b.writeBoolean(v.source!=null); if(v.source!=null) { b.writeUtf(v.source.handId(),128); b.writeResourceLocation(v.source.itemId()); b.writeVarLong(v.source.shotSequence()); }
    },b->new GunMuzzleFlashPayload(Kind.values()[b.readVarInt()],b.readUUID(),b.readUUID(),b.readResourceLocation(),readVec(b),readVec(b),
             ProjectileProfile.ProjectileSpriteEffectData.CODEC.parse(JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(b.readUtf(4096))).getOrThrow(),b.readBoolean()?readVec(b):null,
             b.readBoolean()?new ShotVisualSource(b.readUtf(128),b.readResourceLocation(),b.readVarLong()):null));
    public GunMuzzleFlashPayload { if(kind==null||shooter==null||initiator==null||dimension==null||!finite(position)||!finite(direction)||effect==null||(endpoint!=null&&!finite(endpoint))||(kind==Kind.BEAM)!=(endpoint!=null)||(endpoint!=null&&(endpoint.distanceTo(position)<1e-5||endpoint.distanceTo(position)>20.01))) throw new IllegalArgumentException("invalid gun effect payload"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
}
