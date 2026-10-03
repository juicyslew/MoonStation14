package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.util.ConstructionResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import com.juicyslew.moonstation14.ms14.hands.network.BodyMetalTool;
import com.juicyslew.moonstation14.ms14.hands.network.BodyUseItemContext;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;

import java.util.Map;

public class SteelItem extends Item implements BodyMetalTool {
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

    @Override public boolean supports(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == this; }

    @Override public BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        if (!supports(context.stack()) || !(context.authority().body().level() instanceof net.minecraft.server.level.ServerLevel level)) return BodyBlockUseResult.Outcome.DENIED;
        Block clicked = level.getBlockState(context.pos()).getBlock();
        ConstructionResult result = STEEL_MAP.get(clicked);
        if (result == null) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack before = context.stack();
        if (before.getCount() < result.requiredStack) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack after = before.copy(); after.shrink(result.requiredStack);
        var state = result.resultBlock.defaultBlockState();
        if (!level.hasChunkAt(context.pos()) || !level.getWorldBorder().isWithinBounds(context.pos())
                || level.isOutsideBuildHeight(context.pos()) || !level.mayInteract(context.actor(), context.pos())
                || level.getBlockState(context.pos()).getBlock() != clicked
                || com.juicyslew.moonstation14.ms14.atmos.piping.BodyPlacementCollision.blockIntersects(level, context.authority().body(), context.pos(), state)
                || com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(), context.token(), context.revision(), before, after).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        if (!level.setBlock(context.pos(), state, Block.UPDATE_ALL)) return BodyBlockUseResult.Outcome.DENIED;
        var committed = com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.commit(context.actor(), context.authority(), context.hands(), context.handId(), context.token(), context.revision(), before, after);
        if (committed != com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.SUCCESS) {
            if (level.getBlockState(context.pos()) == state && level.getBlockEntity(context.pos()) == null
                    && !level.setBlock(context.pos(), clicked.defaultBlockState(), Block.UPDATE_ALL))
                return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            if (level.getBlockState(context.pos()) != clicked.defaultBlockState()) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return committed == com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        level.playSound(null, context.pos(), result.interactionSound, SoundSource.BLOCKS);
        return BodyBlockUseResult.Outcome.PLACED;
    }
}
