package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Explicit payload screening for loose stacks whose owner cannot safely discard nested state. */
public final class SingularityConsumptionSafety {
    private SingularityConsumptionSafety() { }

    public static boolean hasOwnedPayload(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(ModDataComponents.GRID_CONTENTS.get()) || stack.has(ModDataComponents.POUCH_CONTENTS.get())
                || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.CONTAINER_LOOT)
                || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CHARGED_PROJECTILES)
                || stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.ENTITY_DATA)
                || stack.has(DataComponents.BUCKET_ENTITY_DATA) || stack.has(DataComponents.BEES)
                || stack.has(DataComponents.FOOD) && stack.get(DataComponents.FOOD).usingConvertsTo().isPresent();
    }
}
