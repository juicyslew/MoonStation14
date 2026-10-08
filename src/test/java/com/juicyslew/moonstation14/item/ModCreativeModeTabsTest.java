package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.block.ModBlocks;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCreativeModeTabsTest {
    @Test
    void everyRegisteredItemAndBlockItemIsListedExactlyOnceInItsDedicatedTab() throws IllegalAccessException {
        List<Object> entries = new ArrayList<>();
        entries.addAll(ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES);
        entries.addAll(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES);
        assertEquals(entries.size(), entries.stream().distinct().count(), "duplicate dedicated-tab entries");

        List<Object> registeredItems = deferredFields(ModItems.class, "ITEMS");
        registeredItems.addAll(deferredFields(ModEquipmentItems.class, "ITEMS"));
        for (var segment : List.of(ModItems.GAS_PIPE_SEGMENT, ModItems.GAS_PIPE_SEGMENT_SECONDARY,
                ModItems.GAS_PIPE_SEGMENT_TERTIARY)) {
            assertEquals(1, ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.stream()
                    .filter(entry -> entry == segment).count(), "each functional pipe segment must appear once in Items");
            assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(segment));
            assertFalse(segment.get() instanceof net.minecraft.world.item.BlockItem);
        }
        // The body inventory scanner is handheld equipment, not an ordinary Creative item.
        registeredItems.removeIf(item -> item == ModItems.T_RAY_SCANNER);
        assertFalse(ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.contains(ModItems.T_RAY_SCANNER));
        assertFalse(ModItems.T_RAY_SCANNER.get() instanceof net.minecraft.world.item.BlockItem);
        // Functional ventilation items are distinct from their blocks and belong in Items.
         for (var device : List.of(ModItems.GAS_VENT, ModItems.GAS_SCRUBBER, ModItems.AIR_ALARM)) {
            assertEquals(1, ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.stream()
                    .filter(entry -> entry == device).count(), "each functional ventilation item must appear once in Items");
            assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(device));
            assertFalse(device.get() instanceof net.minecraft.world.item.BlockItem);
        }
        List<Object> registeredBlocks = deferredFields(ModBlocks.class, "BLOCKS");
        registeredBlocks.removeIf(block -> block == ModBlocks.PUDDLE);
        // The cosmetic pipe block has no default BlockItem; its three custom items represent it.
        registeredBlocks.removeIf(block -> block == ModBlocks.GAS_PIPE_VISUAL);
        assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModBlocks.GAS_PIPE_VISUAL));
        // Disposal tubes are catalogued in Blocks, but not as standalone crafting materials.
        assertEquals(1, ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.stream()
                .filter(entry -> entry == ModBlocks.DISPOSAL_TUBE).count());
        assertFalse(ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.contains(ModBlocks.DISPOSAL_TUBE));
        assertTrue(ModBlocks.DISPOSAL_TUBE.get().asItem() instanceof
                com.juicyslew.moonstation14.item.custom.CatalogOnlyDisposalPipeBlockItem);
        var disposalTubeItem = (com.juicyslew.moonstation14.item.custom.CatalogOnlyDisposalPipeBlockItem)
                ModBlocks.DISPOSAL_TUBE.get().asItem();
        assertEquals(net.minecraft.world.InteractionResult.FAIL, disposalTubeItem.place(null));
        assertEquals(ModBlocks.DISPOSAL_TUBE.get(), disposalTubeItem.getBlock());
        // Functional ventilation blocks are not Blocks-tab entries; their items belong in Items.
         registeredBlocks.removeIf(block -> block == ModBlocks.GAS_VENT || block == ModBlocks.GAS_SCRUBBER
                 || block == ModBlocks.AIR_ALARM);
        assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModBlocks.GAS_VENT));
         assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModBlocks.GAS_SCRUBBER));
         assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModBlocks.AIR_ALARM));
        // Custom BlockItems are represented by their blocks in the Blocks tab, not as standalone items.
        assertEquals(1, ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.stream()
                .filter(entry -> entry == ModItems.JUG).count(), "the portable jug must appear once in Items");
        assertFalse(ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.contains(ModItems.JUG));
        assertFalse(ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES.contains(ModItems.GAS_PIPE_VISUAL));
        for (var visual : List.of(ModItems.GAS_PIPE_VISUAL, ModItems.GAS_PIPE_VISUAL_SECONDARY,
                ModItems.GAS_PIPE_VISUAL_TERTIARY)) {
            assertEquals(1, ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.stream()
                    .filter(entry -> entry == visual).count(), "each cosmetic pipe lane must appear once");
        }
        registeredItems.addAll(registeredBlocks);

        assertEquals(registeredItems.size(), entries.size(), "every exposed registered item/block needs a dedicated tab entry; missing="
                + registeredItems.stream().filter(item -> !entries.contains(item)).map(item -> String.valueOf(((Supplier<?>) item).get())).toList());
        assertTrue(entries.containsAll(registeredItems), "dedicated tab entries must be the exposed registered holders");
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
