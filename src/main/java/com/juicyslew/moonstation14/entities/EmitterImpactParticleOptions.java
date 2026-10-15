package com.juicyslew.moonstation14.entities;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Server-authored emitter impact presentation; lifetime is carried in the native particle packet. */
public record EmitterImpactParticleOptions(int lifetimeTicks) implements ParticleOptions {
    public static final com.mojang.serialization.MapCodec<EmitterImpactParticleOptions> CODEC = Codec.INT.fieldOf("lifetime_ticks").xmap(EmitterImpactParticleOptions::new,
            EmitterImpactParticleOptions::lifetimeTicks);
    public static final StreamCodec<FriendlyByteBuf, EmitterImpactParticleOptions> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeVarInt(value.lifetimeTicks), buf -> new EmitterImpactParticleOptions(buf.readVarInt()));
    public EmitterImpactParticleOptions { if (lifetimeTicks < 1 || lifetimeTicks > 1200) throw new IllegalArgumentException("Invalid impact lifetime"); }
    @Override public ParticleType<?> getType() { return ModEmitterImpactParticles.EMITTER_IMPACT.get(); }
}
