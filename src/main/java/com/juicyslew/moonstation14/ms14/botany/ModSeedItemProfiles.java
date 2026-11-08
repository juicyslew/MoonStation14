package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModSeedItemProfiles {
    public static final PrototypeType<SeedItemProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "seed_item_profile"),
            "moonstation14/seed_item_profile", SeedItemProfile.CODEC);
    private ModSeedItemProfiles() { }
    public static PrototypeCatalog<SeedItemProfile> serverCatalog() { return PrototypeRuntime.serverSeedItemProfiles(); }
    public static PrototypeCatalog<SeedItemProfile> clientCatalog() { return PrototypeRuntime.clientSeedItemProfiles(); }
    public static SeedItemProfile serverLookup(ResourceLocation itemId) { return itemId == null ? null : serverCatalog().get(itemId); }
    public static SeedItemProfile clientLookup(ResourceLocation itemId) { return itemId == null ? null : clientCatalog().get(itemId); }
}
