package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModItemFoodSolutionProfiles {
    public static final PrototypeType<ItemFoodSolutionProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "item_food_solution_profile"),
            "moonstation14/item_food_solution_profile", ItemFoodSolutionProfile.CODEC);
    private ModItemFoodSolutionProfiles() { }
    public static PrototypeCatalog<ItemFoodSolutionProfile> serverCatalog() { return PrototypeRuntime.serverItemFoodSolutionProfiles(); }
    public static PrototypeCatalog<ItemFoodSolutionProfile> clientCatalog() { return PrototypeRuntime.clientItemFoodSolutionProfiles(); }
    public static ItemFoodSolutionProfile serverLookup(ResourceLocation id) { return id == null ? null : serverCatalog().get(id); }
    public static ItemFoodSolutionProfile clientLookup(ResourceLocation id) { return id == null ? null : clientCatalog().get(id); }
}
