package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Generic reagent-capable produce; composition, not this class, supplies capacity. */
public final class ProduceItem extends ItemFoodSolutionItem {
    public ProduceItem(Properties properties) { super(properties); }

    private float configuredCapacity(Level level) {
        var source = ProduceSourceComposition.resolve(this, level);
        return source == null ? 0f : source.capacityCents() / 100f;
    }

    @Override public float getCapacity() { return configuredCapacity(null); }
    @Override public float getCapacity(ItemStack stack, Level level) {
        return ProduceSourceComposition.capacity(stack, () -> ProduceSourceComposition.resolve(this, level));
    }
}
