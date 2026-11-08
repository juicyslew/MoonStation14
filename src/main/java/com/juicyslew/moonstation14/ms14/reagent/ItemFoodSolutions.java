package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared lookup, capacity, and guarded source materialization for an item's food solution. */
public final class ItemFoodSolutions {
    private ItemFoodSolutions() { }
    public static ItemFoodSolutionProfile resolve(ResourceLocation id, Level level) {
        return level != null && level.isClientSide ? ModItemFoodSolutionProfiles.clientLookup(id) : ModItemFoodSolutionProfiles.serverLookup(id);
    }
    public static float capacity(ResourceLocation id, Level level) {
        var profile = resolve(id, level);
        return profile == null ? 0f : profile.capacityCents() / 100f;
    }
    public static void initializeResolved(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty()) return;
        initialize(stack, resolve(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()), level));
    }
    public static void initialize(ItemStack stack, ItemFoodSolutionProfile profile) {
        if (stack.isEmpty() || stack.get(ModDataComponents.REAGENT.get()) != null) return;
        if (stack.get(ModDataComponents.PRODUCE_INSTANCE.get()) != null) {
            stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent());
            return;
        }
        if (profile == null) return;
        var contents = new java.util.LinkedHashMap<ResourceKey<ReagentData>, Long>();
        profile.contentsCents().forEach((id, cents) -> contents.put(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id), cents));
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(contents));
    }
}
