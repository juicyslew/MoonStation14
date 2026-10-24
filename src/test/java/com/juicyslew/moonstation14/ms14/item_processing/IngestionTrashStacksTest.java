package com.juicyslew.moonstation14.ms14.item_processing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionTrashStacksTest {
    @Test void producesIndependentCountOneStacksInPlanOrderAndRejectsInvalidIds() {
        var ids = List.of(ResourceLocation.withDefaultNamespace("stick"), ResourceLocation.withDefaultNamespace("paper"));
        var built = FreshItemOutputStacks.build(ids);
        assertNotNull(built);
        assertEquals(2, built.size());
        assertEquals(Items.STICK, built.get(0).getItem());
        assertEquals(1, built.get(0).getCount());
        assertEquals(Items.PAPER, built.get(1).getItem());
        assertNotSame(built.get(0), built.get(1));
        assertNull(FreshItemOutputStacks.build(List.of(ResourceLocation.withDefaultNamespace("air"))));
        assertNull(FreshItemOutputStacks.build(List.of(ResourceLocation.parse("missing:trash"))));
    }
}
