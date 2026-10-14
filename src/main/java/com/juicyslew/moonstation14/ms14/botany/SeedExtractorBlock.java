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

/** Static Minecraft footprint for the SS14 seed extractor; artwork is a native texture fallback. */
public final class SeedExtractorBlock extends Block implements EntityBlock {
    public SeedExtractorBlock(Properties properties) { super(properties); }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SEED_EXTRACTOR.get().create(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.SEED_EXTRACTOR.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof SeedExtractorBlockEntity extractor) SeedExtractorBlockEntity.tick(world, pos, extractor);
        };
    }
}
