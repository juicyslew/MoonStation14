package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEmitterImpactParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MoonStation14.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, ParticleType<EmitterImpactParticleOptions>> EMITTER_IMPACT = TYPES.register("emitter_impact",
            () -> new ParticleType<>(false) {
                @Override public com.mojang.serialization.MapCodec<EmitterImpactParticleOptions> codec() { return EmitterImpactParticleOptions.CODEC; }
                @Override public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, EmitterImpactParticleOptions> streamCodec() { return EmitterImpactParticleOptions.STREAM_CODEC; }
            });
    private ModEmitterImpactParticles() { }
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
