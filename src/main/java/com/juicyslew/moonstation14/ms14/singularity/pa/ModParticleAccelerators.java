package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;

public final class ModParticleAccelerators {
    public static final PrototypeType<ParticleAcceleratorControlProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "particle_accelerator"),
            "moonstation14/particle_accelerator", ParticleAcceleratorControlProfile.CODEC,
            catalog -> catalog.values().forEach(profile -> {
                if (!profile.parts().keySet().equals(java.util.Set.of("end", "fuel", "power", "port", "fore", "starboard")))
                    throw new IllegalArgumentException("PA profile must define the six required roles");
            }));
    private ModParticleAccelerators() {}
    public static PrototypeCatalog<ParticleAcceleratorControlProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}

