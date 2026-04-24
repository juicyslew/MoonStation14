package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.JugBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;

public class JugBlock extends TransparentBlock implements EntityBlock {
    public static IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 6);

    public JugBlock(Properties properties) {

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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Use your registry holder here
        return ModBlockEntities.JUG.get().create(pos, state);
    }

    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof JugBlockEntity jug) {
            // 1. Get the component from the item
            var reagentData = stack.get(ModDataComponents.REAGENT.get());

            if (reagentData != null) {
                // 2. Set the data into your Block Entity's Data Attachment
                // This assumes you've exposed a setter or the attachment is accessible
                MS14Provider.update(jug, ReagentSystem.bridge, new ReagentAttachment(reagentData));
                if (level.isClientSide) {
                    // On the client, we force the update NOW before the frame ends
                    //jug.requestModelDataUpdate();
                } else {
                    jug.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof JugBlockEntity jug) {
            // This helper method automatically calls collectComponents and applies them
            stack.applyComponents(jug.collectComponents());
        }
        return stack;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof JugBlockEntity jug) {
                ItemStack stack = new ItemStack(this);
                jug.saveToItem(stack); // Hand-off Attachment -> Component
                Block.popResource(level, pos, stack);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
