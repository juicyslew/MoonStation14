package com.juicyslew.moonstation14.ms14.chemistry.beaker;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModBeakers {
    public static final ResourceLocation BASE_ID = id(BeakerData.BASE_ID);
    public static final ResourceLocation SMALL_ID = id(BeakerData.SMALL_ID);
    public static final ResourceLocation LARGE_ID = id(BeakerData.LARGE_ID);
    public static final PrototypeType<BeakerData> TYPE = new PrototypeType<>(id("beaker"), "moonstation14/beaker",
            BeakerData.CODEC, new BeakerMergeStrategy());
    private ModBeakers() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<BeakerData> catalog(Level level) {
        return (level.isClientSide() ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager()
                : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
    public static BeakerData resolved(Level level, ResourceLocation id) { return level == null ? null : catalog(level).get(id); }
    public static BeakerData serverResolved(ResourceLocation id) {
        return com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().snapshot(TYPE).get(id);
    }
}

