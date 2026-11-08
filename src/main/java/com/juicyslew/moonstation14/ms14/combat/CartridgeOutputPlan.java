package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** Already-committed ejection outputs; spent rounds remain persistent, recoverable world items. */
public record CartridgeOutputPlan(List<ItemStack> liveOutputs, List<ItemStack> casings) {
    public CartridgeOutputPlan {
        liveOutputs = liveOutputs.stream().map(ItemStack::copy).toList();
        casings = casings.stream().map(ItemStack::copy).toList();
    }

    public static CartridgeOutputPlan partition(List<ItemStack> outputs, Map<ResourceLocation, CartridgeProfile> profiles) {
        var live = new java.util.ArrayList<ItemStack>();
        var casings = new java.util.ArrayList<ItemStack>();
        for (var output : outputs) {
            if (output == null || output.isEmpty()) continue;
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
            CartridgeProfile profile = id == null ? null : profiles.get(id);
            if (Boolean.TRUE.equals(output.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())) && profile != null)
                casings.add(output.copy());
            else live.add(output.copy());
        }
        return new CartridgeOutputPlan(live, casings);
    }
}
