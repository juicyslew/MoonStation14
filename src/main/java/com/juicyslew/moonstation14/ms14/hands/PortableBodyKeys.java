package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.world.item.ItemStack;

/** Shared recognition for the non-owning adapter stack; never resolves or owns the referenced body. */
public final class PortableBodyKeys {
    private PortableBodyKeys() { }

    public static boolean isKey(ItemStack stack) {
        return isKeyItem(stack)
                && stack.getCount() == 1 && stack.has(ModDataComponents.PORTABLE_BODY_REFERENCE.get());
    }
    public static boolean isKeyItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.PORTABLE_BODY_KEY.get());
    }

    public static PortableBodyReference reference(ItemStack stack) {
        return isKey(stack) ? stack.get(ModDataComponents.PORTABLE_BODY_REFERENCE.get()) : null;
    }
}
