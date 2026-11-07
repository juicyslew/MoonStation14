package com.juicyslew.moonstation14.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record SpentCasingParticleOptions(ResourceLocation sprite, ResourceLocation landingEvent, int lifetimeTicks, float initialSpeed, float gravity, float damping, float volumeDb, float pitchVariation) implements ParticleOptions {
    public static final com.mojang.serialization.MapCodec<SpentCasingParticleOptions> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            ResourceLocation.CODEC.fieldOf("sprite").forGetter(SpentCasingParticleOptions::sprite),
            ResourceLocation.CODEC.fieldOf("landing_event").forGetter(SpentCasingParticleOptions::landingEvent),
            Codec.INT.fieldOf("lifetime_ticks").forGetter(SpentCasingParticleOptions::lifetimeTicks),
            Codec.FLOAT.fieldOf("initial_speed").forGetter(SpentCasingParticleOptions::initialSpeed),Codec.FLOAT.fieldOf("gravity").forGetter(SpentCasingParticleOptions::gravity),
            Codec.FLOAT.fieldOf("damping").forGetter(SpentCasingParticleOptions::damping),Codec.FLOAT.fieldOf("volume_db").forGetter(SpentCasingParticleOptions::volumeDb),
            Codec.FLOAT.fieldOf("pitch_variation").forGetter(SpentCasingParticleOptions::pitchVariation)).apply(i,SpentCasingParticleOptions::new));
    public static final StreamCodec<FriendlyByteBuf,SpentCasingParticleOptions> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeResourceLocation(v.sprite);b.writeResourceLocation(v.landingEvent);b.writeVarInt(v.lifetimeTicks);b.writeFloat(v.initialSpeed);b.writeFloat(v.gravity);b.writeFloat(v.damping);b.writeFloat(v.volumeDb);b.writeFloat(v.pitchVariation);
    },b->new SpentCasingParticleOptions(b.readResourceLocation(),b.readResourceLocation(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));
    public SpentCasingParticleOptions { if(sprite==null||landingEvent==null||lifetimeTicks<1||lifetimeTicks>600||!Float.isFinite(initialSpeed)||initialSpeed<0||initialSpeed>2||!Float.isFinite(gravity)||gravity<0||gravity>2||!Float.isFinite(damping)||damping<0||damping>1||!Float.isFinite(volumeDb)||volumeDb < -60||volumeDb>6||!Float.isFinite(pitchVariation)||pitchVariation<0||pitchVariation>1)throw new IllegalArgumentException("invalid spent casing options"); }
    @Override public ParticleType<?> getType(){return ModSpentCasingParticles.TYPE.get();}
}
