package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.world.item.ItemStack;

/** Owner-independent stack mutations. Call only from authoritative server code. */
public final class WelderFuelService {
    private WelderFuelService() { }

    public static boolean toggle(ItemStack stack, ToolPrototypeData tool, long globalTick) {
        if (stack == null || stack.isEmpty() || tool == null || tool.welder().isEmpty()) return false;
        var composition = tool.welder().orElseThrow();
        var fuel = stack.getOrDefault(ModDataComponents.WELDER_FUEL.get(), WelderFuelData.initial(composition))
                .tick(composition, globalTick);
        var next = fuel.toggle(composition, globalTick);
        if (next.isEmpty()) return false;
        stack.set(ModDataComponents.WELDER_FUEL.get(), next.orElseThrow());
        return true;
    }

    /** Update a detached stack copy belonging to any owner (including a disposal payload). */
    public static ItemStack tickOwnerStack(ItemStack ownedStack, ToolPrototypeData tool, long globalTick) {
        if (ownedStack == null || ownedStack.isEmpty() || !ownedStack.is(ModItems.WELDER)
                || tool == null || tool.welder().isEmpty()) return ownedStack;
        ItemStack updated = ownedStack.copy();
        var prototype = tool.welder().orElseThrow();
        var current = updated.getOrDefault(ModDataComponents.WELDER_FUEL.get(), WelderFuelData.initial(prototype));
        WelderFuelData next = current.tick(prototype, globalTick);
        if (next.equals(current)) return ownedStack;
        updated.set(ModDataComponents.WELDER_FUEL.get(), next);
        return updated;
    }

    public static boolean canWeld(ItemStack stack) {
        WelderFuelData fuel = stack == null ? null : stack.get(ModDataComponents.WELDER_FUEL.get());
        return fuel != null && fuel.lit() && fuel.fuel() > 0;
    }
}

