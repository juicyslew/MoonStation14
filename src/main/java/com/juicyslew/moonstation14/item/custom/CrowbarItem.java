package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyMetalTool;
import com.juicyslew.moonstation14.ms14.hands.network.BodyUseItemContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Crowbar construction graph work is handled by the shared construction service, not instant block swaps. */
public class CrowbarItem extends Item implements BodyMetalTool {
    public CrowbarItem(Properties properties) { super(properties); }
    @Override public boolean supports(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == this; }
    @Override public BodyBlockUseResult.Outcome use(BodyUseItemContext context) { return BodyBlockUseResult.Outcome.DENIED; }
    @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.FAIL; }
}
