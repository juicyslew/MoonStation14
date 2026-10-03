package com.juicyslew.moonstation14.ms14.storage.grid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.item.ModItems;

import java.util.List;

/** Bounded initial footprint policy. Unknown item identities deliberately remain one cell. */
public final class ItemFootprints {
    private static final GridGeometry.Footprint ONE = GridGeometry.Footprint.of(List.of(new GridGeometry.Cell(0, 0)));
    private static final GridGeometry.Footprint PICKAXE = GridGeometry.Footprint.of(List.of(
            new GridGeometry.Cell(0, 0), new GridGeometry.Cell(1, 0), new GridGeometry.Cell(2, 0),
            new GridGeometry.Cell(1, 1), new GridGeometry.Cell(1, 2)));

    private ItemFootprints() { }

    public static GridGeometry.Footprint forStack(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.equals(ModItems.PICKAXE.getId()) ? PICKAXE : ONE;
    }

    public static GridGeometry.Footprint forId(ResourceLocation id) {
        return id != null && id.equals(ModItems.PICKAXE.getId()) ? PICKAXE : ONE;
    }
}
