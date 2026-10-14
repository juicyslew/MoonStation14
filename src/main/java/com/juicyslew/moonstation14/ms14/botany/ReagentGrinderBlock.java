package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

public final class ReagentGrinderBlock extends Block implements EntityBlock {
    public ReagentGrinderBlock(Properties properties) { super(properties); }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return ModBlockEntities.REAGENT_GRINDER.get().create(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.REAGENT_GRINDER.get() ? (world, pos, blockState, entity) -> {
            if (entity instanceof ReagentGrinderBlockEntity grinder) ReagentGrinderBlockEntity.tick(world, pos, grinder);
        } : null;
    }
}
