package com.juicyslew.moonstation14.ms14.construction.world;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionWelderContinuityTest {
    @Test void passiveFuelAlonePreservesTimedToolContinuity() {
        var before = new ItemStack(ModItems.WELDER.get());
        before.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(100, true, 10));
        var after = before.copy();
        after.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(99, true, 30));
        assertTrue(ConstructionWorldService.sameStackOrPassiveFuel(before, after));
        assertTrue(ConstructionWorldService.passiveFuelChanged(before, after));
        assertTrue(ConstructionWorldService.revisionContinuity(4, 5, true));
    }

    @Test void arbitraryItemOrNonFuelMutationDoesNotPreserveContinuity() {
        var before = new ItemStack(ModItems.WELDER.get());
        before.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(100, true, 10));
        var changed = before.copy();
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("tampered"));
        var swapped = new ItemStack(Items.STICK);
        assertFalse(ConstructionWorldService.sameStackOrPassiveFuel(before, changed));
        assertFalse(ConstructionWorldService.sameStackOrPassiveFuel(before, swapped));
        assertFalse(ConstructionWorldService.revisionContinuity(4, 5, false));
        assertFalse(ConstructionWorldService.revisionContinuity(5, 4, true));
    }
}
