package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.world.item.ItemStack;

/** Closed adapter contract for the existing steel and crowbar tools. */
public interface BodyMetalTool {
    boolean supports(ItemStack stack);
    BodyBlockUseResult.Outcome use(BodyUseItemContext context);
}

