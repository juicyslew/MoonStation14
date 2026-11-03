package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.world.item.ItemStack;

/** Persistent, server-owned lowered state shared by breath-tool behavior and presentation. */
public final class BreathToolItemState {
    private BreathToolItemState() { }
    public static boolean isLowered(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getOrDefault(ModDataComponents.BREATH_MASK_LOWERED.get(), false);
    }
    /** Pure copy operation used by the exact live-owner compare-and-commit path. */
    public static ItemStack toggledCopy(ItemStack source) {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("missing breath tool stack");
        ItemStack changed = source.copy();
        changed.set(ModDataComponents.BREATH_MASK_LOWERED.get(), !isLowered(source));
        return changed;
    }
}
