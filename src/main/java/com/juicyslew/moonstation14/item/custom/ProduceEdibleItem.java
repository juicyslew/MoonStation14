package com.juicyslew.moonstation14.item.custom;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reuses the established edible/reagent trait while allowing harvest-specific capacity. */
public final class ProduceEdibleItem extends PrototypeEdibleItem {
    public ProduceEdibleItem(Properties properties) { super(properties); }
    @Override public float getCapacity() {
        var source = com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition.resolve(this, null);
        return source == null ? 0f : source.capacityCents() / 100f;
    }
    @Override public float getCapacity(ItemStack stack, Level level) {
        return com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition.capacity(
                stack, () -> com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition.resolve(this, level));
    }
}
