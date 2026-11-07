package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyBlockItemPlacementTest {
    @Test
    void registeredAtmosMachineBlockItemsAreSupported() {
        long machineItems = BuiltInRegistries.ITEM.stream()
                .filter(BlockItem.class::isInstance)
                .map(BlockItem.class::cast)
                .filter(item -> item.getBlock() instanceof AtmosMachineBlock)
                .peek(item -> assertTrue(BodyBlockItemPlacement.supports(new ItemStack(item)),
                        "Atmos machine item must be accepted: " + BuiltInRegistries.ITEM.getKey(item)))
                .count();

        assertEquals(11, machineItems, "expected current core/circulator/pumps/mixer/filter/valve/heater/freezer/exchanger/manifold registrations");
        assertTrue(BodyBlockItemPlacement.supports(new ItemStack(ModBlocks.GAS_PRESSURE_PUMP.get().asItem())));
        assertTrue(BodyBlockItemPlacement.supports(new ItemStack(ModBlocks.GAS_PIPE_MANIFOLD.get().asItem())));
        assertTrue(BodyBlockItemPlacement.supports(new ItemStack(ModBlocks.GAS_MINER_OXYGEN.get().asItem())));
        assertTrue(BodyBlockItemPlacement.supports(new ItemStack(ModBlocks.GAS_MINER_NITROGEN.get().asItem())));
        assertTrue(BodyBlockItemPlacement.supports(new ItemStack(ModBlocks.GAS_MINER_PLASMA.get().asItem())));
    }

    @Test
    void excludesUnsupportedAndSpecializedItems() {
        assertFalse(BodyBlockItemPlacement.supports(new ItemStack(Items.STONE)));
        assertFalse(BodyBlockItemPlacement.supports(new ItemStack(ModItems.GAS_PIPE_VISUAL.get())));
        assertFalse(BodyBlockItemPlacement.supports(new ItemStack(ModItems.JUG.get())));
        assertFalse(BodyBlockItemPlacement.supports(ItemStack.EMPTY));
        assertFalse(BodyBlockItemPlacement.supports(new ItemStack(Items.STICK)));
    }

}
