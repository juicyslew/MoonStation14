package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reagent-capable item adapter for a food solution without granting ingestion behavior. */
public class ItemFoodSolutionItem extends Item implements IReagentTrait {
    public ItemFoodSolutionItem(Properties properties) { super(properties); }
    @Override public float getCapacity() { return ItemFoodSolutions.capacity(BuiltInRegistries.ITEM.getKey(this), null); }
    @Override public float getCapacity(ItemStack stack, Level level) {
        // Harvested produce carries its effective container capacity on the instance.
        // This adapter is also used by edible produce; do not require a separate item
        // subclass for the composition metadata to participate in runtime admission.
        if (stack != null && stack.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) != null)
            return com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition.capacity(
                    stack, () -> ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(this), level));
        return ItemFoodSolutions.capacity(BuiltInRegistries.ITEM.getKey(this), level);
    }
    @Override public boolean canReact(ItemStack stack, Level level) {
        var profile = ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(this), level);
        return profile == null || profile.canReact();
    }
    @Override public String solutionName(ItemStack stack, Level level) {
        var profile = ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(this), level);
        return profile == null ? "default" : profile.solutionName();
    }
    @Override public com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolution(ItemStack stack, Level level) {
        return refillableSolutionFromResolved(ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(this), level));
    }
    @Override public com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransfer(ItemStack stack, Level level) {
        return solutionTransferFromResolved(ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(this), level));
    }
    public static com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolutionFromResolved(com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile profile) {
        return profile == null || !profile.refillable() ? com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy.DISABLED
                : new com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy(true, profile.solutionName(), java.util.Optional.empty());
    }
    public static com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransferFromResolved(com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile profile) {
        return profile == null || !profile.refillable() ? com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy.DISABLED
                : new com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy(true, 5f, false, true);
    }
}
