package com.juicyslew.moonstation14.entities;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModSpentCasingParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE,MoonStation14.MOD_ID);
    public static final DeferredHolder<ParticleType<?>,ParticleType<SpentCasingParticleOptions>> TYPE=TYPES.register("spent_casing",()->new ParticleType<>(false){
        @Override public com.mojang.serialization.MapCodec<SpentCasingParticleOptions> codec(){return SpentCasingParticleOptions.CODEC;}
        @Override public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,SpentCasingParticleOptions> streamCodec(){return (net.minecraft.network.codec.StreamCodec)SpentCasingParticleOptions.STREAM_CODEC;}
    });
    private ModSpentCasingParticles(){}
    public static void register(IEventBus bus){TYPES.register(bus);}
}
