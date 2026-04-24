package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.*;

public class ModReagents {
    public static Registry<ReagentData> REAGENT_REGISTRY;
    public static final ResourceKey<Registry<ReagentData>> REAGENT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "reagent"));

    public static Registry<ReagentData> getRegistry(Level level) {
        return level.registryAccess().registryOrThrow(REAGENT_REGISTRY_KEY);
    }

    public static ResourceKey<ReagentData> createKey(String path) {
        return ResourceKey.create(REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    @SubscribeEvent
    public static void registerDatapackRegistry(DataPackRegistryEvent.NewRegistry event) {
        // This tells the game: "This registry is populated by JSONs using this Codec"
        System.out.println("REAGENT REGISTRY REGISTERED!");
        event.dataPackRegistry(REAGENT_REGISTRY_KEY, ReagentData.CODEC, ReagentData.CODEC);
    }
}
