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

    /** Passive-burn then consume fuel from a detached, lit welder copy. */
    public static java.util.Optional<ItemStack> spendFuel(ItemStack before, ToolPrototypeData tool, int amount, long now) {
        if (before == null || before.isEmpty() || amount < 0 || tool == null || tool.welder().isEmpty())
            return java.util.Optional.empty();
        var state = before.get(ModDataComponents.WELDER_FUEL.get());
        if (state == null || !state.lit()) return java.util.Optional.empty();
        var composition = tool.welder().orElseThrow();
        var current = state.tick(composition, now);
        if (!current.lit() || current.fuel() < amount) return java.util.Optional.empty();
        ItemStack after = before.copy();
        after.set(ModDataComponents.WELDER_FUEL.get(), new WelderFuelData(current.fuel() - amount, true, now));
        return java.util.Optional.of(after);
    }

    public static boolean canSpendFuel(ItemStack before, ToolPrototypeData tool, int amount, long now) {
        return spendFuel(before, tool, amount, now).isPresent();
    }

    /** True only when identity/count and non-fuel components are unchanged and fuel only passively decreased. */
    public static boolean sameStackOrPassiveFuel(ItemStack before, ItemStack after) {
        if (before == null || after == null) return before == after;
        if (!ItemStack.isSameItem(before, after) || before.getCount() != after.getCount()) return false;
        if (ItemStack.isSameItemSameComponents(before, after)) return true;
        if (!before.is(ModItems.WELDER)) return false;
        var oldFuel = before.get(ModDataComponents.WELDER_FUEL.get());
        var newFuel = after.get(ModDataComponents.WELDER_FUEL.get());
        if (oldFuel == null || newFuel == null || newFuel.fuel() > oldFuel.fuel()
                || (newFuel.lastBurnTick() < oldFuel.lastBurnTick()
                && !(newFuel.fuel() == 0 && !newFuel.lit()))) return false;
        ItemStack a = before.copy(), b = after.copy();
        a.remove(ModDataComponents.WELDER_FUEL.get()); b.remove(ModDataComponents.WELDER_FUEL.get());
        return ItemStack.isSameItemSameComponents(a, b);
    }

    public static boolean passiveFuelChanged(ItemStack before, ItemStack after) {
        return before != null && after != null && before.is(ModItems.WELDER)
                && !java.util.Objects.equals(before.get(ModDataComponents.WELDER_FUEL.get()),
                        after.get(ModDataComponents.WELDER_FUEL.get()));
    }

    public static boolean revisionContinuity(long initial, long current, boolean fuelChanged) {
        return current >= initial && (current == initial || fuelChanged);
    }
}

