package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModSingularityGenerators {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "default");
    public static final SingularityGeneratorProfile TYPE_PROFILE = new SingularityGeneratorProfile(16, false, true, 16, 10,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity"));
    private ModSingularityGenerators() { }
    public static PrototypeCatalog<SingularityGeneratorProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager())
                .snapshot(SingularityGeneratorProfile.TYPE);
    }
}

