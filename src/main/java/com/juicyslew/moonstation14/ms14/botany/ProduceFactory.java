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
        return createHarvest(new GeneticValue(seedId, seed), potency);
    }
    public static ItemStack createHarvest(GeneticValue genetics, float potency) {
        if (genetics == null || !Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid harvest input");
        ResourceLocation seedId = genetics.species(); SeedProfile seed = genetics.profile();
        ResourceLocation productId = seed.product();
        var item = BuiltInRegistries.ITEM.get(productId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("unregistered produce item " + productId);
        var produceProfile = ModProduceProfiles.serverCatalog().get(productId);
        if (!(item instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait))
            throw new IllegalStateException("produce item has no supported reagent source trait: " + productId);
        var source = ProduceSourceComposition.resolve(item, null);
        if (produceProfile == null)
            throw new IllegalStateException("produce item has no resolved produce composition: " + productId);
        // BotanySystem.GenerateProduct calls EnsureSolution(food, 0) for every
        // generated product. Fresh non-food prototypes can have no source profile;
        // the generated instance still has a named, empty food solution. Do not
        // change fresh-item source resolution to manufacture that solution.
        float sourceCapacity = source == null ? 0f : source.capacityCents() / 100f;
        if (!Float.isFinite(sourceCapacity) || sourceCapacity < 0f)
            throw new IllegalStateException("invalid resolved initial source capacity for produce item " + productId);
        // SS14 EnsureSolution(..., 0) preserves the prototype MaxVolume; harvesting
        // clears its contents, then adds each generated reagent amount to capacity.
        int initialCapacity = Math.toIntExact(ReagentUnits.fromFloat(sourceCapacity));
        LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> chemistry = generatedChemistry(seed, potency);
        int capacity = Math.addExact(initialCapacity, Math.toIntExact(ReagentUnits.total(chemistry.values())));
        ItemStack stack = new ItemStack(item);
        // BotanySystem.Produce applies captured produce mutations to the new product
        // before replacing its food solution. Keep these snapshots on the instance;
        // never consult the current mutation table by mutation name.
        for (PlantMutationRecord mutation : genetics.mutations()) {
            if (mutation.appliesToProduce())
                stack.set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), mutation.slippery());
        }
        // Harvest contents replace prototype food contents, never merge with them.
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(chemistry));
        stack.set(ModDataComponents.PRODUCE_INSTANCE.get(), new ProduceInstanceData(seedId, potency, capacity));
        stack.set(ModDataComponents.GENETIC_VALUE.get(), genetics);
        return stack;
    }

    /** Preserves the holder's live lineage while reusing the canonical chemistry/item builder. */
    public static ItemStack createHarvest(PlantSeedReference reference, float potency) {
        if (reference == null) throw new IllegalArgumentException("missing seed reference");
        ItemStack stack = createHarvest(reference.current(), potency);
        GeneticCarriers.attach(stack, reference);
        return stack;
    }

    /** Single authoritative seed-chemistry conversion used only when constructing a harvest. */
    public static LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> generatedChemistry(SeedProfile seed, float potency) {
        if (seed == null || !Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid harvest input");
        LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> chemistry = new LinkedHashMap<>();
        seed.chemicals().forEach((id, profile) -> {
            long cents = profile.amountCents(potency);
            if (cents > 0) chemistry.put(ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id), cents);
        });
        return chemistry;
    }
}
