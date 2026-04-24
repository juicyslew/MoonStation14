package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;
import static com.juicyslew.moonstation14.util.Helpers.findFirstSurfaceBelow;

public class PuddleBlock extends TransparentBlock implements EntityBlock {
    public static IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 3);

    public PuddleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FILL_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL_LEVEL);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof IReagentTrait blockHolder && stack.getItem() instanceof IReagentTrait itemHolder) {
            if (!level.isClientSide) {
                // Use the itemHolder (the Bottle) logic
                ReagentSystem.handleTransfer(itemHolder.toHandle(stack), blockHolder.toHandleSelf(), level, UNITS_PER_SIP);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // If it's NOT a reagent item, tell the game to try useWithoutItem (e.g. for empty hands)
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {

        // 1. Fetch the BlockEntity (The Data Instance)
        BlockEntity be = level.getBlockEntity(pos);
        // 2. Check if it's our Holder
        if (be instanceof IReagentTrait blockHolder) {
            ReagentSystem.handleTransfer(
                    blockHolder.toHandleSelf(),
                    ((IReagentTrait) player).toHandleSelf(), // Player will implement this because of LivingEntityMixin
                    level,
                    UNITS_PER_SIP
            );

            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, .25D, 15.0D);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Use your registry holder here
        return ModBlockEntities.PUDDLE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.PUDDLE.get()) {
            return null;
        }

        return (lvl, pos, st, be) -> {
            if (be instanceof PuddleBlockEntity puddle) {
                // Call your static tick method in the BlockEntity
                PuddleBlockEntity.tick(lvl, pos, st, puddle);
            }
        };
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);

        // Only care if the block directly BELOW us changed
        if (neighborPos.equals(pos.below())) {
            checkGravity(level, pos, state);
        }
    }

    private void checkGravity(Level level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        BlockState belowstate = level.getBlockState(below);

        // If the new block below is air (or replaceable), we need to move
        if (belowstate.canBeReplaced() || belowstate.is(ModBlocks.PUDDLE)) {
            if (!level.isClientSide) {
                teleportDown(level, pos);
            }
        }
    }

    private void teleportDown(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PuddleBlockEntity sourceBE) {
            // 1. Find the new landing spot
            BlockPos landingPos = findFirstSurfaceBelow(level, pos, 100,
                    (s) -> s.is(ModBlocks.PUDDLE.get()), (s) -> !s.canBeReplaced());

            if (landingPos != null && !landingPos.equals(pos)) {
                // 2. Extract the reagents
                ReagentAttachment reagents = sourceBE.getData(ModDataAttachments.REAGENT);

                BlockState targetState = level.getBlockState(landingPos);
                if (!targetState.is(ModBlocks.PUDDLE.get())) {
                    level.setBlock(landingPos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
                }

                // 3. Place or Merge at the bottom
                if (level.getBlockEntity(landingPos) instanceof PuddleBlockEntity targetBE) {
                    ReagentAttachment destCont = targetBE.getData(ModDataAttachments.REAGENT);
                    destCont.mergeAdd(reagents.getMap(), targetBE.getCapacity());
                    MS14Provider.update(targetBE, MS14Bridges.REAGENT, destCont);
                }
            }
            // 4. No Matter what, delete the puddle.
            level.removeBlock(pos, false);
        }
    }
}
