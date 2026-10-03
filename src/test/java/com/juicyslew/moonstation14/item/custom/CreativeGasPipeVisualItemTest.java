package com.juicyslew.moonstation14.item.custom;

import net.minecraft.world.item.BlockItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeGasPipeVisualItemTest {
    @Test
    void fixtureIsAPlaceableBlockItemRatherThanAnAtmosphereDevice() {
        assertTrue(BlockItem.class.isAssignableFrom(CreativeGasPipeVisualItem.class));
    }

    @Test
    void constructorAcceptsOnlyIndividualLanes() {
        assertEquals(1, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL.get().getLane());
        assertEquals(2, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_SECONDARY.get().getLane());
        assertEquals(4, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_TERTIARY.get().getLane());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CreativeGasPipeVisualItem(com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get(),
                        new net.minecraft.world.item.Item.Properties(), 3));
    }

    @Test
    void inventoryNamesAreDistinctForAddedLanes() {
        assertEquals("block.moonstation14.gas_pipe_visual", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL.get().getDescriptionId());
        assertEquals("item.moonstation14.gas_pipe_visual_secondary", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_SECONDARY.get().getDescriptionId());
        assertEquals("item.moonstation14.gas_pipe_visual_tertiary", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_TERTIARY.get().getDescriptionId());
    }
}
