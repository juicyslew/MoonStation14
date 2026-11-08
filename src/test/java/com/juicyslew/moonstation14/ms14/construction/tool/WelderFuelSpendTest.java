package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WelderFuelSpendTest {
    private static final ToolPrototypeData TOOL = new ToolPrototypeData(Set.of(ToolPrototypeData.Quality.Welding),
            1, Optional.of(new ToolPrototypeData.Welder(100, 1, .5)));

    @Test void spendingPassivelyBurnsThenChargesOnlyDetachedCandidate() {
        ItemStack original = new ItemStack(ModItems.WELDER.get());
        original.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(20, true, 0));

        ItemStack after = WelderFuelService.spendFuel(original, TOOL, 5, 20).orElseThrow();

        assertEquals(new WelderFuelData(14.5, true, 20), after.get(ModDataComponents.WELDER_FUEL.get()));
        assertEquals(new WelderFuelData(20, true, 0), original.get(ModDataComponents.WELDER_FUEL.get()));
        assertTrue(WelderFuelService.canSpendFuel(original, TOOL, 5, 20));
    }

    @Test void unlitOrInsufficientAfterPassiveBurnIsRejectedWithoutMutation() {
        ItemStack unlit = new ItemStack(ModItems.WELDER.get());
        unlit.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(20, false, -1));
        assertTrue(WelderFuelService.spendFuel(unlit, TOOL, 5, 20).isEmpty());
        assertFalse(WelderFuelService.canSpendFuel(unlit, TOOL, 5, 20));

        ItemStack insufficient = new ItemStack(ModItems.WELDER.get());
        insufficient.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(5.25, true, 0));
        assertTrue(WelderFuelService.spendFuel(insufficient, TOOL, 5, 20).isEmpty());
        assertFalse(WelderFuelService.canSpendFuel(insufficient, TOOL, 5, 20));
        assertEquals(new WelderFuelData(5.25, true, 0), insufficient.get(ModDataComponents.WELDER_FUEL.get()));
    }
}
