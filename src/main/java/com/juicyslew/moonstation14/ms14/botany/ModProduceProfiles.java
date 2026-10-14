package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Catalog of resolved produce metadata; ingestion remains the sole food-source authority. */
public final class ModProduceProfiles {
    public static final PrototypeType<ProduceProfile> TYPE = new PrototypeType<>(id("produce_profile"),
            "moonstation14/produce_profile", ProduceProfile.CODEC);
    private ModProduceProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<ProduceProfile> serverCatalog() { return PrototypeRuntime.serverProduceProfiles(); }
    public static PrototypeCatalog<ProduceProfile> clientCatalog() { return PrototypeRuntime.clientProduceProfiles(); }
}
