package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.item.ModItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPipePlacementPolicyTest {
    @Test
    void functionalItemsSelectTheirExactVisualLayer() {
        assertEquals(VisualPipeBlockEntity.PRIMARY,
                BodyPipePlacementPolicy.selectedLayer(new net.minecraft.world.item.ItemStack(ModItems.GAS_PIPE_SEGMENT.get())).orElseThrow());
        assertEquals(VisualPipeBlockEntity.SECONDARY,
                BodyPipePlacementPolicy.selectedLayer(new net.minecraft.world.item.ItemStack(ModItems.GAS_PIPE_SEGMENT_SECONDARY.get())).orElseThrow());
        assertEquals(VisualPipeBlockEntity.TERTIARY,
                BodyPipePlacementPolicy.selectedLayer(new net.minecraft.world.item.ItemStack(ModItems.GAS_PIPE_SEGMENT_TERTIARY.get())).orElseThrow());
        assertTrue(BodyPipePlacementPolicy.selectedLayer(net.minecraft.world.item.ItemStack.EMPTY).isEmpty());
    }
}
