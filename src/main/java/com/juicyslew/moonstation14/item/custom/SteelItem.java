package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.structs.ConstructionResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class SteelItem extends Item {
    private static final Map<Block, ConstructionResult> STEEL_MAP =
            Map.of(
                    ModBlocks.STEEL_WALL_GIRDER_BLOCK.get(), new ConstructionResult(
                            ModBlocks.STEEL_WALL_BLOCK.get(), SoundEvents.COPPER_GRATE_PLACE, 2, null,0)
            );

    public SteelItem(Properties properties){
        super(properties.stacksTo(8));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Block clickedBlock = level.getBlockState(context.getClickedPos()).getBlock();

        if(STEEL_MAP.containsKey(clickedBlock)) {
            if (!level.isClientSide()) {
                // ONLY ON SERVER!!!
                ConstructionResult result = STEEL_MAP.get(clickedBlock);
                ItemStack hand_stack = context.getItemInHand();

                if (hand_stack.getCount() < result.requiredStack) {
                    return InteractionResult.FAIL;
                }
                hand_stack.consume(result.requiredStack, context.getPlayer());
                level.setBlockAndUpdate(context.getClickedPos(), result.resultBlock.defaultBlockState());
                level.playSound(null, context.getClickedPos(), result.interactionSound, SoundSource.BLOCKS);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.FAIL;
    }
}
