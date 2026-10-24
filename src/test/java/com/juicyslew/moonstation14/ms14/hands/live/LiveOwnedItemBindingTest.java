package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveOwnedItemBindingTest {
    @Test void validatesBindingValuesAndComponentReplacementShape() {
        assertTrue(new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, "right", 3, "token").valid());
        assertFalse(new LiveOwnedItemAccess.Binding(null, "right", 3, "token").valid());
        assertFalse(new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, " ", 3, "token").valid());
        assertFalse(new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, "right", -1, "token").valid());
        assertFalse(new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, "right", 3, " ").valid());

        ItemStack expected = new ItemStack(Items.STICK, 2);
        ItemStack replacement = expected.copy();
        replacement.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("updated"));
        assertTrue(LiveOwnedItemAccess.validComponentReplacement(expected, replacement));
        assertFalse(LiveOwnedItemAccess.validComponentReplacement(expected, new ItemStack(Items.APPLE, 2)));
        assertFalse(LiveOwnedItemAccess.validComponentReplacement(expected, new ItemStack(Items.STICK, 1)));
        assertFalse(LiveOwnedItemAccess.validComponentReplacement(expected, ItemStack.EMPTY));
        assertFalse(expected.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
    }
}
