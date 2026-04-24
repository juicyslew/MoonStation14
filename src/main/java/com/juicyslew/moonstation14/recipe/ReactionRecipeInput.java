package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import java.util.Map;

public record ReactionRecipeInput(Map<ResourceKey<ReagentData>, Float> container) implements RecipeInput{

    @Override
    public ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return container().isEmpty();
    }
}
