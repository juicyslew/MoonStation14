package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;

/** Pure item-stack harvest product construction; caller retains world/authority ownership. */
public final class ProduceFactory {
    private ProduceFactory() { }
    public static ItemStack createHarvest(ResourceLocation seedId, float potency) {
        if (seedId == null || !Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid harvest input");
        SeedProfile seed = ModSeedProfiles.serverCatalog().get(seedId);
        if (seed == null) throw new IllegalArgumentException("unknown seed profile: " + seedId);
        return createHarvest(seedId, seed, potency);
    }
    public static ItemStack createHarvest(ResourceLocation seedId, SeedProfile seed, float potency) {
        if (seedId == null || seed == null || !Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid harvest input");
        ResourceLocation productId = seed.product();
        var item = BuiltInRegistries.ITEM.get(productId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("unregistered produce item " + productId);
        ItemStack stack = new ItemStack(item);
        LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> chemistry = generatedChemistry(seed, potency);
        // SS14 EnsureSolution(..., 0) preserves the prototype MaxVolume; harvesting
        // clears its contents, then adds each generated reagent amount to capacity.
        if (!(item instanceof com.juicyslew.moonstation14.item.custom.ProduceEdibleItem edible))
            throw new IllegalStateException("produce item has no edible source-capacity owner: " + productId);
        float sourceCapacity = edible.getCapacity(stack, null);
        if (!Float.isFinite(sourceCapacity) || sourceCapacity <= 0f)
            throw new IllegalStateException("missing resolved initial source profile for produce item " + productId);
        int initialCapacity = Math.toIntExact(ReagentUnits.fromFloat(sourceCapacity));
        int capacity = Math.addExact(initialCapacity, Math.toIntExact(ReagentUnits.total(chemistry.values())));
        // Harvest contents replace prototype food contents, never merge with them.
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(chemistry));
        stack.set(ModDataComponents.PRODUCE_INSTANCE.get(), new ProduceInstanceData(seedId, potency, capacity));
        return stack;
    }

    /** Single authoritative seed-chemistry conversion used only when constructing a harvest. */
    public static LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> generatedChemistry(SeedProfile seed, float potency) {
        if (seed == null || !Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid harvest input");
        LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> chemistry = new LinkedHashMap<>();
        seed.chemicals().forEach((id, profile) -> {
            long cents = ReagentUnits.fromFloat(profile.amount(potency));
            if (cents > 0) chemistry.put(ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id), cents);
        });
        return chemistry;
    }
}
