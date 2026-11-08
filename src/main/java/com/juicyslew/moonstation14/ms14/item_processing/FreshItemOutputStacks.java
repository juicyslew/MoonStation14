package com.juicyslew.moonstation14.ms14.item_processing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Builds independent, unmodified count-one output stacks from item ids. */
public final class FreshItemOutputStacks {
    private FreshItemOutputStacks() { }

    public static List<ItemStack> build(List<ResourceLocation> ids) {
        return build(ids, null);
    }

    public static List<ItemStack> build(List<ResourceLocation> ids, net.minecraft.world.level.Level level) {
        if (ids == null) return null;
        var outputs = new ArrayList<ItemStack>(ids.size());
        for (var id : ids) {
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            if (item == null || item == net.minecraft.world.item.Items.AIR
                    || !id.equals(BuiltInRegistries.ITEM.getKey(item))) return null;
            var stack = new ItemStack(item);
            var source = com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.resolve(id, level);
            if (source != null) com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initialize(stack, source);
            outputs.add(stack);
        }
        return List.copyOf(outputs);
    }
}
