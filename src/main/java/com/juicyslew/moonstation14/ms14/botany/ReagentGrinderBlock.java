package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import javax.annotation.Nullable;

public final class ReagentGrinderBlock extends Block implements EntityBlock {
    public static final BooleanProperty BEAKER_ATTACHED = BooleanProperty.create("beaker_attached");
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public ReagentGrinderBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(BEAKER_ATTACHED, false).setValue(WORKING, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BEAKER_ATTACHED, WORKING); }
    @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(WORKING) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL; }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide && state.hasProperty(WORKING) && oldState.hasProperty(WORKING)
                && state.getValue(WORKING) != oldState.getValue(WORKING)
                && level.getBlockEntity(pos) instanceof ReagentGrinderBlockEntity grinder)
            grinder.noteClientWorkingTransition();
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return ModBlockEntities.REAGENT_GRINDER.get().create(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.REAGENT_GRINDER.get() ? (world, pos, blockState, entity) -> {
            if (entity instanceof ReagentGrinderBlockEntity grinder) ReagentGrinderBlockEntity.tick(world, pos, grinder);
        } : null;
    }
}
