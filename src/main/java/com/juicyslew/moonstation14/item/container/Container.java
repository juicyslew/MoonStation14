package com.juicyslew.moonstation14.item.container;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.juicyslew.moonstation14.enums.ReagentEnum;
import com.juicyslew.moonstation14.reagent.Reagent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;

public abstract class Container extends Item {
    // TODO: Split container functionality from Item
    public Map<Reagent, Integer> ContainedReagents;
    public final int capacity; // In MilliUnits

    public Container(Item.Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // TODO: Add Give vs Take Mode.
        // If possible, feed to entity.
        // If possible, give to another object.
        // Else, drink it?
        Level level = context.getLevel();

        if (level.isClientSide()) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        ReagentContainerData old_data = stack.getOrDefault(
                ModDataComponents.REAGENT_CONTAINER,
                new ReagentContainerData()
        );
        stack.set(ModDataComponents.REAGENT_CONTAINER.get(), old_data.withClampedAdd(ReagentEnum.BICARIDINE, 100, capacity));

        return InteractionResult.SUCCESS;
    }

//    @Override
//    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
//        // TODO: Add Give vs Take Mode.
//
//        // If possible, feed to entity.
//        // If possible, give to another object.
//        // Else, eat it.
//        // Give to something if possible
//
//        //Else start drinkin it.
//        if (level.isClientSide()) return InteractionResultHolder.pass(player.getItemInHand(hand));
//        ItemStack stack = player.getItemInHand(hand).set(ModDataComponents.REAGENT_CONTAINER, );
//
//        return InteractionResultHolder.success(stack);
//    }
}