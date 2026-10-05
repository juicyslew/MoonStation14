package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemPresentationTest {
    @Test void preservesNativeAppearanceAndReturnsDetachedCopies() {
        ItemStack source = new ItemStack(Items.IRON_PICKAXE);
        source.set(DataComponents.DAMAGE, 1);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Snapshot tool"));
        source.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(42));
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        ItemPresentation presentation = ItemPresentation.fromStack(source);
        source.set(DataComponents.DAMAGE, 10);

        ItemStack first = presentation.stack();
        assertEquals(1, first.getDamageValue());
        assertEquals(Component.literal("Snapshot tool"), first.get(DataComponents.CUSTOM_NAME));
        assertEquals(new CustomModelData(42), first.get(DataComponents.CUSTOM_MODEL_DATA));
        assertEquals(Boolean.TRUE, first.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));

        first.set(DataComponents.DAMAGE, 20);
        assertEquals(1, presentation.stack().getDamageValue());
        assertNotEquals(presentation, ItemPresentation.fromStack(source));
        assertEquals(presentation, ItemPresentation.fromStack(presentation.stack()));
    }
}
