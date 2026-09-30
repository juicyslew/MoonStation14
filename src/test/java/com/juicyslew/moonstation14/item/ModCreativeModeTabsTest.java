package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.block.ModBlocks;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCreativeModeTabsTest {
    @Test
    void everyRegisteredItemAndBlockItemIsListedExactlyOnceInItsDedicatedTab() throws IllegalAccessException {
        List<Object> entries = new ArrayList<>();
        entries.addAll(ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES);
        entries.addAll(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES);
        assertEquals(entries.size(), entries.stream().distinct().count(), "duplicate dedicated-tab entries");

        List<Object> registeredItems = deferredFields(ModItems.class, "ITEMS");
        List<Object> registeredBlocks = deferredFields(ModBlocks.class, "BLOCKS");
        registeredBlocks.removeIf(block -> block == ModBlocks.PUDDLE);
        // The custom jug BlockItem is registered separately, but represents the jug block in the Blocks tab.
        registeredItems.removeIf(item -> item == ModItems.JUG);
        assertTrue(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModBlocks.JUG),
                "the jug BlockItem must be represented by the jug block in the Blocks tab");
        registeredItems.addAll(registeredBlocks);

        assertEquals(registeredItems.size(), entries.size(), "every registered item/block needs a dedicated tab entry");
        assertTrue(entries.containsAll(registeredItems), "dedicated tab entries must be the registered holders");
    }

    private static List<Object> deferredFields(Class<?> owner, String registry) throws IllegalAccessException {
        List<Object> result = new ArrayList<>();
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !field.getName().equals(registry)
                    && Supplier.class.isAssignableFrom(field.getType())) {
                result.add(field.get(null));
            }
        }
        return result;
    }
}
