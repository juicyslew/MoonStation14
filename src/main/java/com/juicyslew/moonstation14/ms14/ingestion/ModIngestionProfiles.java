package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Ingestion profile catalog; callers must use explicit serverCatalog on mutation paths. */
public final class ModIngestionProfiles {
    public static final PrototypeType<IngestionProfile> TYPE = new PrototypeType<>(id("ingestion_profile"),
            "moonstation14/ingestion_profile", IngestionProfile.CODEC, new IngestionProfileMergeStrategy());
    private ModIngestionProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<IngestionProfile> serverCatalog() { return PrototypeRuntime.serverIngestionProfiles(); }
    public static PrototypeCatalog<IngestionProfile> clientCatalog() { return PrototypeRuntime.clientIngestionProfiles(); }
    public static IngestionProfile serverLookup(ResourceLocation itemPrototype) {
        if (itemPrototype == null) return null;
        return serverCatalog().get(itemPrototype);
    }
    public static IngestionProfile clientLookup(ResourceLocation itemPrototype) {
        if (itemPrototype == null) return null;
        return clientCatalog().get(itemPrototype);
    }
    public static boolean serverReady() { return ready(serverCatalog()); }
    public static boolean clientReady() { return ready(clientCatalog()); }
    private static boolean ready(PrototypeCatalog<IngestionProfile> catalog) {
        return catalog.contains(id("jug")) && catalog.contains(id("bottle")) && catalog.contains(id("pill")) && catalog.contains(id("puddle"));
    }
}
