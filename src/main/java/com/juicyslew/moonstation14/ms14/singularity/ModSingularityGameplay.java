package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;

public final class ModSingularityGameplay {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "singularity");
    public static final PrototypeType<SingularityGameplayProfile> TYPE = new PrototypeType<>(
            id("singularity_gameplay"), "singularity_gameplay", SingularityGameplayProfile.CODEC,
            catalog -> catalog.values().forEach(SingularityGameplayProfile::validate));
    private ModSingularityGameplay() { }
    public static PrototypeCatalog<SingularityGameplayProfile> catalog(Level level) {
        return level.isClientSide ? PrototypeRuntime.clientSingularityGameplay() : PrototypeRuntime.serverSingularityGameplay();
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}

