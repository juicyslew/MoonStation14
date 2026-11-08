package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

/** Generic item-side adapter for edible/source fields of a resolved ingestion prototype. */
public class PrototypeEdibleItem extends ItemFoodSolutionItem {
    public PrototypeEdibleItem(Properties properties) { super(properties); }
}
