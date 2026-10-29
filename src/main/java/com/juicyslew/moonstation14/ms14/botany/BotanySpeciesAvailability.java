package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Predicate;

/** Shared short-term gate for species whose harvest product is not fully admitted by ProduceFactory. */
public final class BotanySpeciesAvailability {
    private BotanySpeciesAvailability() { }

    public static Predicate<ResourceLocation> policy(PrototypeCatalog<SeedProfile> seeds,
            PrototypeCatalog<ProduceProfile> produce, Predicate<ResourceLocation> registeredItem,
            Predicate<ResourceLocation> harvestItemSupported) {
        return seedId -> {
            if (seeds == null || produce == null || seedId == null || registeredItem == null
                    || harvestItemSupported == null) return false;
            SeedProfile seed = seeds.get(seedId);
            return seed != null && seed.packet() != null && registeredItem.test(seed.packet())
                    && seed.product() != null && registeredItem.test(seed.product())
                    && produce.get(seed.product()) != null && harvestItemSupported.test(seed.product());
        };
    }

    public static Predicate<ResourceLocation> live() {
        return policy(ModSeedProfiles.serverCatalog(), ModProduceProfiles.serverCatalog(),
                id -> { var item = BuiltInRegistries.ITEM.get(id); return item != net.minecraft.world.item.Items.AIR; },
                id -> { var item = BuiltInRegistries.ITEM.get(id); return item != net.minecraft.world.item.Items.AIR
                        && item instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait; });
    }
}
