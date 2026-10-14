package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerData;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Reagent-bearing beaker item. Capacity is supplied by the resolved beaker composition. */
public final class BeakerItem extends Item implements IReagentTrait, com.juicyslew.moonstation14.ms14.chemistry.DispenserCompatibleItem {
    private final ResourceLocation prototypeId;

    public BeakerItem(Properties properties, ResourceLocation prototypeId) {
        super(properties);
        this.prototypeId = prototypeId;
    }

    @Override
    public float getCapacity() {
        BeakerData data = ModBeakers.serverResolved(prototypeId);
        return capacityFromResolved(data);
    }

    @Override
    public float getCapacity(ItemStack stack, net.minecraft.world.level.Level level) {
        BeakerData data = ModBeakers.resolved(level, prototypeId);
        return capacityFromResolved(data);
    }

    public boolean fitsInDispenser() {
        BeakerData data = ModBeakers.serverResolved(prototypeId);
        return data != null && data.fitsInDispenser();
    }
    public ResourceLocation prototypeId() { return prototypeId; }

    @Override public com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy drainableSolution(ItemStack stack, Level level) {
        BeakerData data = ModBeakers.resolved(level, prototypeId);
        return drainableSolutionFromResolved(data);
    }
    @Override public String solutionName(ItemStack stack, Level level) {
        BeakerData data = ModBeakers.resolved(level, prototypeId);
        return data == null ? "default" : data.solutionContainer().solutionName();
    }
    @Override public com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolution(ItemStack stack, Level level) { return refillableSolutionFromResolved(ModBeakers.resolved(level, prototypeId)); }
    @Override public com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransfer(ItemStack stack, Level level) { return solutionTransferFromResolved(ModBeakers.resolved(level, prototypeId)); }
    @Override public com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy drinkSolution(ItemStack stack, Level level) { return drinkSolutionFromResolved(ModBeakers.resolved(level, prototypeId)); }
    public static com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolutionFromResolved(BeakerData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy.DISABLED : data.refillableSolution(); }
    public static com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransferFromResolved(BeakerData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy.DISABLED : data.solutionTransfer(); }
    public static com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy drinkSolutionFromResolved(BeakerData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy.DISABLED : data.drinkSolution(); }
    public static com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy drainableSolutionFromResolved(BeakerData data) {
        return data == null ? null : data.drainableSolution();
    }

    public boolean fitsInDispenser(net.minecraft.world.level.Level level) {
        BeakerData data = ModBeakers.resolved(level, prototypeId);
        return fitsInDispenserFromResolved(data);
    }
    public static float capacityFromResolved(BeakerData data) { return data == null ? 0f : data.solutionContainer().maxVolume(); }
    public static boolean fitsInDispenserFromResolved(BeakerData data) { return data != null && data.fitsInDispenser(); }
}

