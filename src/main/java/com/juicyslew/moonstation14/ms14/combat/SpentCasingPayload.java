package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Observer-routed cosmetic casing instruction; it never represents persistent world state. */
public record SpentCasingPayload(UUID body, ResourceLocation dimension, ResourceLocation sprite,
                                ResourceLocation landingEvent, Vec3 position, Vec3 direction,
                                int count, long seed, int lifetimeTicks, float initialSpeed, float gravity, float damping,
                                float volumeDb, float pitchVariation)
        implements CustomPacketPayload {
    public static final Type<SpentCasingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "spent_casing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpentCasingPayload> STREAM_CODEC = StreamCodec.of((b,v)->{
        b.writeUUID(v.body); b.writeResourceLocation(v.dimension); b.writeResourceLocation(v.sprite); b.writeResourceLocation(v.landingEvent);
        writeVec(b,v.position); writeVec(b,v.direction); b.writeVarInt(v.count); b.writeLong(v.seed); b.writeVarInt(v.lifetimeTicks);
        b.writeFloat(v.initialSpeed);b.writeFloat(v.gravity); b.writeFloat(v.damping);b.writeFloat(v.volumeDb);b.writeFloat(v.pitchVariation);
    },b->new SpentCasingPayload(b.readUUID(),b.readResourceLocation(),b.readResourceLocation(),b.readResourceLocation(),readVec(b),readVec(b),b.readVarInt(),b.readLong(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));
    public SpentCasingPayload {
        if(body==null||dimension==null||sprite==null||landingEvent==null||!finite(position)||!finite(direction)||direction.lengthSqr()<1e-8
                ||count<1||count>64||lifetimeTicks<1||lifetimeTicks>600||!Float.isFinite(initialSpeed)||initialSpeed<0||initialSpeed>2
                ||!Float.isFinite(gravity)||gravity<0||gravity>2||!Float.isFinite(damping)||damping<0||damping>1
                ||!Float.isFinite(volumeDb)||volumeDb < -60||volumeDb>6||!Float.isFinite(pitchVariation)||pitchVariation<0||pitchVariation>1) throw new IllegalArgumentException("invalid spent casing payload");
        direction=direction.normalize();
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
