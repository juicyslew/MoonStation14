package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** SS14 Pill solution vessel; chemistry is retained in the ordinary reagent component. */
public final class PillItem extends Item implements IReagentTrait {
    public PillItem(Properties properties) { super(properties); }
    @Override public float getCapacity() {
        var data = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolveServer(
                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill"));
        return capacityFromResolved(data);
    }
    @Override public float getCapacity(ItemStack stack, Level level) {
        var data = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level,
                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill"));
        return capacityFromResolved(data);
    }
    public static float capacityFromResolved(com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData data) {
        return data == null ? 0f : data.solution().map(solution -> (float) solution.capacity()).orElse(0f);
    }
    @Override public String solutionName(ItemStack stack, Level level) {
        var data = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level,
                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill"));
        return data == null ? "" : data.solution().map(com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData.Solution::name).orElse("");
    }
    @Override public boolean canReact(ItemStack stack, Level level) {
        var data = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level,
                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill"));
        return data != null && data.solution().map(com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData.Solution::canReact).orElse(true);
    }
}
