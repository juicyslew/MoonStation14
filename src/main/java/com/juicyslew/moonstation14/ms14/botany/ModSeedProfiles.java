package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModSeedProfiles {
    public static final PrototypeType<SeedProfile> TYPE = new PrototypeType<>(id("seed_profile"), "moonstation14/seed_profile",
            SeedProfile.CODEC, new SeedProfileMergeStrategy(), ModSeedProfiles::validateResolvedSeeds);
    private ModSeedProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    private static void validateResolvedSeeds(PrototypeCatalog<SeedProfile> catalog) {
        for (var entry : catalog.asMap().entrySet()) {
            if (entry.getKey().equals(id("defaults"))) continue;
            SeedProfile profile = entry.getValue();
            if (profile.lifecycle().maturation() <= 0 || profile.biology().endurance() <= 0)
                throw new IllegalArgumentException("concrete seed '" + entry.getKey()
                        + "' requires positive maturation and endurance");
        }
    }
    public static PrototypeCatalog<SeedProfile> serverCatalog() { return PrototypeRuntime.serverSeedProfiles(); }
    public static PrototypeCatalog<SeedProfile> clientCatalog() { return PrototypeRuntime.clientSeedProfiles(); }
    public static ResourceLocation seedId(String name) { return id(name); }
    public static SeedProfile serverLookup(ResourceLocation seedId) {
        return seedId == null ? null : serverCatalog().get(seedId);
    }
    public static SeedProfile clientLookup(ResourceLocation seedId) {
        return seedId == null ? null : clientCatalog().get(seedId);
    }
    public static boolean serverReady() { return ready(serverCatalog()); }
    public static boolean clientReady() { return ready(clientCatalog()); }
    private static boolean ready(PrototypeCatalog<SeedProfile> catalog) {
        return catalog.contains(id("wheat")) && catalog.contains(id("tomato")) && catalog.contains(id("potato"))
                && catalog.contains(id("poppy")) && catalog.contains(id("aloe"));
    }
}
