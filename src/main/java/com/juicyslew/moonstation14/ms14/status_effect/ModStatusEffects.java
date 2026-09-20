package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public class ModStatusEffects {

    // The Registry Key (The "Phone Book" ID)
    public static final ResourceKey<Registry<StatusEffectData>> STATUS_EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "status_effect"));

    // Helper to create a key from a string (e.g., "jittery")
    public static ResourceKey<StatusEffectData> createKey(String id) {
        return ResourceKey.create(STATUS_EFFECT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, id.toLowerCase()));
    }

    // Helper to get the actual registry at runtime
    public static Registry<StatusEffectData> getRegistry(Level level) {
        return level.registryAccess().registryOrThrow(STATUS_EFFECT_REGISTRY_KEY);
    }

    @SubscribeEvent
    public static void registerDatapackRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Register the Datapack Registry using our Codec
        event.dataPackRegistry(STATUS_EFFECT_REGISTRY_KEY, StatusEffectData.CODEC, StatusEffectData.CODEC);
    }
}
