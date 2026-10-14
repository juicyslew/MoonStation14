package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Registered typed composition for native PA particle projectiles. */
public final class ModParticleAcceleratorParticles {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "particle_accelerator_particle");
    public static final PrototypeType<ParticleAcceleratorParticleProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "pa_particle"),
            "moonstation14/pa_particle", ParticleAcceleratorParticleProfile.CODEC);
    private ModParticleAcceleratorParticles() { }
    public static PrototypeCatalog<ParticleAcceleratorParticleProfile> catalog(Level level) {
        return (level.isClientSide ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}
