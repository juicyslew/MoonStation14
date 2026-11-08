package com.juicyslew.moonstation14.ms14.singularity.emitter;

import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModEmitters {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_emitter");
    public static final PrototypeType<EmitterProfile> TYPE = EmitterProfile.TYPE;
    private ModEmitters() {}
    public static PrototypeCatalog<EmitterProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}

