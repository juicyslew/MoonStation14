package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.world.item.ItemStack;

/** Shared read-only item presentation derived from the cartridge's authoritative component. */
public final class SharedCartridgeItemPresentation {
    private SharedCartridgeItemPresentation() {}

    public static float spent(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.CARTRIDGE_SPENT.get())) ? 1f : 0f;
    }
}
