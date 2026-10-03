package com.juicyslew.moonstation14.ms14.storage.grid;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridContentsTest {
    private static GridContents.Entry entry(String token, int x) {
        return new GridContents.Entry(token, new ItemStack(Items.STICK), x, 0, GridGeometry.Rotation.DEG_0);
    }

    @Test
    void equalityPreservesEntryOrderAndComparesDetachedStackCopies() {
        GridContents first = GridContents.of(List.of(entry("first", 0), entry("second", 1)));
        GridContents sameOrder = GridContents.of(List.of(entry("first", 0), entry("second", 1)));
        GridContents swapped = GridContents.of(List.of(entry("second", 1), entry("first", 0)));

        assertEquals(first, sameOrder);
        assertEquals(first.hashCode(), sameOrder.hashCode());
        assertNotEquals(first, swapped);
        assertNotSame(first.entries().getFirst().stack(), first.entries().getFirst().stack());
    }
}
