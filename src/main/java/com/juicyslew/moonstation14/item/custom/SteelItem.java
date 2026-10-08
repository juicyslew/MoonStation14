package com.juicyslew.moonstation14.item.custom;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.ms14.hands.network.BodyMetalTool;
import com.juicyslew.moonstation14.ms14.hands.network.BodyUseItemContext;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;


public class SteelItem extends Item implements BodyMetalTool {
    public SteelItem(Properties properties){
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override public boolean supports(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == this; }

    @Override public BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        String recipe = com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldService
                .selectedRecipe(context.actor(), context.authority().body());
        if (recipe == null) return BodyBlockUseResult.Outcome.DENIED;
        return com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldService.beginFoundation(context, recipe)
                ? BodyBlockUseResult.Outcome.WORK_STARTED : BodyBlockUseResult.Outcome.DENIED;
    }
}
