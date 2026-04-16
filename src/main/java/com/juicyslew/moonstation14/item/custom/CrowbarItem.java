package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.sounds.ModSounds;
import com.juicyslew.moonstation14.util.ConstructionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

public class CrowbarItem extends Item {
    private static final Map<Block, ConstructionResult> CROWBAR_MAP =
            Map.of(
                    ModBlocks.STEEL_WALL_BLOCK.get(), new ConstructionResult(
                            ModBlocks.STEEL_WALL_GIRDER_BLOCK.get(),
                            ModSounds.CROWBAR_USE.get(),
                            0,
                            ModItems.STEEL.get(),
                            2),
                    ModBlocks.STEEL_WALL_GIRDER_BLOCK.get(), new ConstructionResult(
                            Blocks.AIR,
                            ModSounds.CROWBAR_USE.get(),
                            0,
                            ModItems.STEEL.get(),
                            2)
            );

    public CrowbarItem(Properties properties){
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Block clickedBlock = level.getBlockState(context.getClickedPos()).getBlock();

        if(CROWBAR_MAP.containsKey(clickedBlock)) {
            if (!level.isClientSide()) {
                // ONLY ON SERVER!!!
                ConstructionResult result = CROWBAR_MAP.get(clickedBlock);
                level.setBlockAndUpdate(context.getClickedPos(), result.resultBlock.defaultBlockState());

                if (result.extraDropItem != null){
                    ItemStack stack = new ItemStack(result.extraDropItem, result.extraDropAmount);
                    ItemEntity itemEntity = new ItemEntity(
                            context.getLevel(),
                            context.getPlayer().getX(),
                            context.getPlayer().getY(),
                            context.getPlayer().getZ(),
                            stack
                    );
                    itemEntity.setDefaultPickUpDelay(); // or setPickupDelay(int)

                    level.addFreshEntity(itemEntity);
                }
                context.getItemInHand().hurtAndBreak(1, ((ServerLevel) level), context.getPlayer(),
                        item -> context.getPlayer().onEquippedItemBroken(item, EquipmentSlot.MAINHAND));

                level.playSound(null, context.getClickedPos(), result.interactionSound, SoundSource.BLOCKS);

                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }
}
