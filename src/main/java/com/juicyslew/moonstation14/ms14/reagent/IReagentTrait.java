package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.ms14.TraitHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface IReagentTrait {
    float getCapacity();

    /** Null means this resolved item composition is not authorized as a world-dump source. */
    default DrainableSolutionPolicy drainableSolution(ItemStack stack, Level level) { return null; }

    /** Primary logical solution represented by a single-solution item trait. */
    default String solutionName(ItemStack stack, Level level) { return "default"; }

    /** Whether an item solution permits reagent reaction after accepted transfer. */
    default boolean canReact(ItemStack stack, Level level) { return true; }

    default RefillableSolutionPolicy refillableSolution(ItemStack stack, Level level) { return RefillableSolutionPolicy.DISABLED; }
    default SolutionTransferPolicy solutionTransfer(ItemStack stack, Level level) { return SolutionTransferPolicy.DISABLED; }
    default DrinkSolutionPolicy drinkSolution(ItemStack stack, Level level) { return DrinkSolutionPolicy.DISABLED; }

    /** Context-aware capacity for prototype-backed containers; legacy traits retain their fixed capacity. */
    default float getCapacity(ItemStack stack, Level level) { return getCapacity(); }

    /** Shared operational resolver: stack traits must receive the world context when available. */
    static float resolveCapacity(IReagentTrait trait, Object holder, Level level) {
        if (holder instanceof ItemStack stack && level != null) return trait.getCapacity(stack, level);
        return trait.getCapacity();
    }

    default TraitHandler<IReagentTrait> toHandle(Object holder) {
        return new TraitHandler<>(holder, this);
    }

    default TraitHandler<IReagentTrait> toHandleSelf() {
        return new TraitHandler<>(this, this);
    }
}
