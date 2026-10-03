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
import com.juicyslew.moonstation14.ms14.hands.network.BodyMetalTool;
import com.juicyslew.moonstation14.ms14.hands.network.BodyUseItemContext;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class CrowbarItem extends Item implements BodyMetalTool {
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

    @Override public boolean supports(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == this; }

    @Override public BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        if (!supports(context.stack()) || !(context.authority().body().level() instanceof ServerLevel level)) return BodyBlockUseResult.Outcome.DENIED;
        BlockState old = level.getBlockState(context.pos());
        ConstructionResult result = CROWBAR_MAP.get(old.getBlock());
        if (result == null || !level.hasChunkAt(context.pos()) || !level.getWorldBorder().isWithinBounds(context.pos())
                || level.isOutsideBuildHeight(context.pos()) || !level.mayInteract(context.actor(), context.pos())) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack before = context.stack(), after = before.copy();
        if (after.getMaxDamage() > 0) after.setDamageValue(after.getDamageValue() + 1);
        if (BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(), context.token(), context.revision(), before, after).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        BlockState replacement = result.resultBlock.defaultBlockState();
        if (!level.setBlock(context.pos(), replacement, Block.UPDATE_ALL)) return BodyBlockUseResult.Outcome.DENIED;
        ItemEntity drop = null;
        if (result.extraDropItem != null) {
            drop = new ItemEntity(level, context.authority().body().getX(), context.authority().body().getY(), context.authority().body().getZ(), new ItemStack(result.extraDropItem, result.extraDropAmount));
            drop.setDefaultPickUpDelay();
            if (!level.addFreshEntity(drop)) {
                if (level.getBlockState(context.pos()) == replacement && level.getBlockEntity(context.pos()) == null
                        && level.setBlock(context.pos(), old, Block.UPDATE_ALL)) return BodyBlockUseResult.Outcome.DENIED;
                return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            }
        }
        var committed = BodyHeldItemMutation.commit(context.actor(), context.authority(), context.hands(), context.handId(), context.token(), context.revision(), before, after);
        if (committed != BodyHeldItemMutation.Result.SUCCESS) {
            if (drop != null && drop.isAlive() && level.getEntity(drop.getUUID()) == drop) drop.discard();
            if (level.getBlockState(context.pos()) == replacement && level.getBlockEntity(context.pos()) == null
                    && !level.setBlock(context.pos(), old, Block.UPDATE_ALL)) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            if (level.getBlockState(context.pos()) != old) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return committed == BodyHeldItemMutation.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        level.playSound(null, context.pos(), result.interactionSound, SoundSource.BLOCKS);
        return BodyBlockUseResult.Outcome.PLACED;
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
