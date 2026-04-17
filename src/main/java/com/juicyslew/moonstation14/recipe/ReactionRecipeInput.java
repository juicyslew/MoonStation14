package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.enums.ReagentEnum;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import java.util.Map;

public record ReactionRecipeInput(Map<ReagentEnum, Integer> container) implements RecipeInput{

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
