package com.juicyslew.moonstation14.ms14.construction.world;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionMaterialContinuityTest {
    @Test
    void permitsCountDriftOnlyForTheSameSurvivingStackAndSufficientMaterial() {
        ItemStack initial = new ItemStack(Items.IRON_INGOT, 4);
        ItemStack current = new ItemStack(Items.IRON_INGOT, 2);
        assertTrue(ConstructionWorldService.materialSourceContinuous("same", "same", initial, current, 2));
        assertFalse(ConstructionWorldService.materialSourceContinuous("same", "same", initial, current, 3));
        assertFalse(ConstructionWorldService.materialSourceContinuous("same", "replaced", initial, current, 1));
        assertFalse(ConstructionWorldService.materialSourceContinuous("same", "same", initial,
                new ItemStack(Items.GOLD_INGOT, 4), 1));
        assertFalse(ConstructionWorldService.materialSourceContinuous("same", "same", initial,
                ItemStack.EMPTY, 1));
    }

    @Test
    void initialPlacementKeysAreUniqueForRepeatedCandidateReservations() {
        var candidate="pipe-target:sealed-host";
        var first=ConstructionWorldService.reservationSourceKey(candidate,new java.util.UUID(1,2));
        var second=ConstructionWorldService.reservationSourceKey(candidate,new java.util.UUID(1,3));
        assertNotEquals(first,second);
        assertTrue(first.startsWith(candidate+":reservation:"));
    }
}
