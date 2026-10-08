package com.juicyslew.moonstation14.item;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicMaterialStackLimitTest {
    @Test
    void basicMaterialItemsHaveThirtyItemMaximumStacksWithoutChangingDefaultSpawnCount() {
        for (var holder : List.of(ModItems.STEEL, ModItems.GLASS, ModItems.PLASTIC, ModItems.PAPER,
                ModItems.CLOTH, ModItems.WOOD, ModItems.METAL_ROD)) {
            var item = holder.get();
            assertEquals(30, new ItemStack(item).getMaxStackSize(), item.toString());
            assertEquals(1, new ItemStack(item).getCount(), item + " default ItemStack count");
        }
    }

    @Test
    void unrelatedSingleStackItemsRemainSingleStack() {
        assertEquals(1, new ItemStack(ModItems.BAG.get()).getMaxStackSize());
        assertEquals(1, new ItemStack(ModItems.WRENCH.get()).getMaxStackSize());
    }
}
