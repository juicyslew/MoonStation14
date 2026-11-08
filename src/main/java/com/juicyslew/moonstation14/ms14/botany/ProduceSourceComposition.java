package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.botany.ProduceInstanceData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.function.Supplier;

/** Shared resolved source owner for produce capacity and lazy source materialization. */
public final class ProduceSourceComposition {
    private ProduceSourceComposition() { }

    public static ItemFoodSolutionProfile resolve(Item item, Level level) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        return ItemFoodSolutions.resolve(id, level);
    }

    /** Capacity for a stack, preferring harvest metadata without touching its contents. */
    public static float capacity(ItemStack stack, Supplier<ItemFoodSolutionProfile> source) {
        ProduceInstanceData data = stack == null ? null : stack.get(ModDataComponents.PRODUCE_INSTANCE.get());
        if (data != null) return data.generatedCapacityCents() / 100f;
        var resolved = source == null ? null : source.get();
        return resolved == null ? 0f : resolved.capacityCents() / 100f;
    }
}
