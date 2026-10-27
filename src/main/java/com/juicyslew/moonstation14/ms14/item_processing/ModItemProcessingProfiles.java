package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Resolved generic item-processing component compositions. */
public final class ModItemProcessingProfiles {
    public static final PrototypeType<ItemProcessingProfile> TYPE = new PrototypeType<>(id("item_processing_profile"),
            "moonstation14/item_processing_profile", ItemProcessingProfile.CODEC,
            catalog -> catalog.asMap().values().forEach(ItemProcessingProfile::validate));
    private ModItemProcessingProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<ItemProcessingProfile> serverCatalog() { return PrototypeRuntime.serverItemProcessingProfiles(); }
    public static PrototypeCatalog<ItemProcessingProfile> clientCatalog() { return PrototypeRuntime.clientItemProcessingProfiles(); }
    public static ItemProcessingProfile serverLookup(ResourceLocation sourceItem) {
        return sourceItem == null ? null : serverCatalog().get(sourceItem);
    }
    public static ItemProcessingProfile clientLookup(ResourceLocation sourceItem) {
        return sourceItem == null ? null : clientCatalog().get(sourceItem);
    }

    /** Validates output references against the same complete catalog snapshot being considered for publication. */
    public static void validateResolvedSliceOutputs(Map<ResourceLocation, PrototypeCatalog<?>> catalogs) {
        PrototypeCatalog<?> rawProfiles = catalogs.get(TYPE.typeId());
        if (rawProfiles == null) return;
        @SuppressWarnings("unchecked")
        PrototypeCatalog<ItemProcessingProfile> profiles = (PrototypeCatalog<ItemProcessingProfile>) rawProfiles;
        PrototypeCatalog<?> rawIngestion = catalogs.get(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE.typeId());
        PrototypeCatalog<?> rawFoodSolutions = catalogs.get(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE.typeId());
        for (var entry : profiles.asMap().entrySet()) {
            var slice = entry.getValue().sliceableFood();
            if (slice == null) continue;
            var output = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(slice.output());
            if (output == null || output == net.minecraft.world.item.Items.AIR
                    || !slice.output().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output))
                    || !(output instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait))
                throw new IllegalArgumentException("slice profile '" + entry.getKey()
                        + "' output must be a registered reagent-capable item: " + slice.output());
            if (rawIngestion == null || !rawIngestion.contains(slice.output()))
                throw new IllegalArgumentException("slice profile '" + entry.getKey()
                        + "' output has no resolved edible solution profile: " + slice.output());
            @SuppressWarnings("unchecked")
            PrototypeCatalog<com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile> ingestionCatalog =
                    (PrototypeCatalog<com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile>) rawIngestion;
            var ingestion = ingestionCatalog.get(slice.output());
            @SuppressWarnings("unchecked")
            PrototypeCatalog<com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile> foodSolutionCatalog =
                    (PrototypeCatalog<com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile>) rawFoodSolutions;
            var foodSolution = foodSolutionCatalog == null ? null : foodSolutionCatalog.get(slice.output());
            if (ingestion == null || ingestion.edible().kind() != com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Kind.FOOD
                    || foodSolution == null || foodSolution.capacityCents() <= 0 || !"food".equals(foodSolution.solutionName()))
                throw new IllegalArgumentException("slice profile '" + entry.getKey()
                        + "' output requires a supported food solution and positive capacity: " + slice.output());
        }
    }
}
