package com.juicyslew.moonstation14.ms14.equipment;

import net.minecraft.world.item.ItemStack;

/** Shared mouth-access predicate for resolved worn equipment. */
public final class IngestionBlockerPolicy {
    private IngestionBlockerPolicy() { }
    public static boolean blocks(EquipmentItemData profile, ItemStack stack, boolean pocket) {
        if (profile == null || pocket || profile.ingestionBlocker().isEmpty()) return false;
        return profile.breathMask().isEmpty() || !BreathToolItemState.isLowered(stack);
    }
}
